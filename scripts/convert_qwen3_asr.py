#!/usr/bin/env python3
"""
Convert Qwen3-ASR to ONNX format for DualQuickIME Android deployment.

Downloads Qwen/Qwen3-ASR-0.6B (or 1.7B) from HuggingFace and exports it to
optimized ONNX format for on-device inference via ONNX Runtime for Android.

Why Qwen3-ASR?
  - WER on Cantonese (Dialog-Cantonese): ~4.12% (vs Whisper-large-v3: 31.04%)
  - Native Cantonese-English code-switching support
  - Outperforms existing models on Cantonese by a large margin
  - 0.6B variant is mobile-friendly (~700 MB INT8 quantized)

Output files:
  encoder.onnx    - Audio encoder (mel → audio_embeds), ~100 MB INT8
  decoder.onnx    - LLM decoder with KV-cache, ~600 MB INT8
  vocab.json      - Token ID → string map (tiktoken byte-level BPE)
  config.json     - Model configuration (mel params, bos/eos tokens, etc.)

Usage:
  python scripts/convert_qwen3_asr.py [OPTIONS]

  --model-id MODEL_ID   HuggingFace model (default: Qwen/Qwen3-ASR-0.6B)
  --output-dir DIR      Output directory (default: ./qwen3-asr-0.6b-int8)
  --no-quantize         Skip INT8 quantization (larger, but faster to convert)
  --opset OPSET         ONNX opset version (default: 17)

Requirements:
  pip install torch transformers onnx onnxruntime accelerate
"""

import argparse
import json
import os
import shutil
import sys
from pathlib import Path

import torch
import torch.nn as nn
from torch import Tensor


def parse_args():
    p = argparse.ArgumentParser(description="Convert Qwen3-ASR to ONNX for Android")
    p.add_argument("--model-id", default="Qwen/Qwen3-ASR-0.6B",
                   help="HuggingFace model ID (default: Qwen/Qwen3-ASR-0.6B)")
    p.add_argument("--output-dir", default="./qwen3-asr-0.6b-int8",
                   help="Output directory for ONNX files")
    p.add_argument("--no-quantize", action="store_true",
                   help="Skip INT8 quantization")
    p.add_argument("--opset", type=int, default=17,
                   help="ONNX opset version (default: 17)")
    return p.parse_args()


# ---------------------------------------------------------------------------
# Audio encoder wrapper
# ---------------------------------------------------------------------------

class AudioEncoderWrapper(nn.Module):
    """
    Wraps the Qwen3-ASR audio encoder + projection layer.
    Input:  mel_features [batch, n_mels, n_frames]
    Output: audio_embeds [batch, audio_seq_len, hidden_size]
    """
    def __init__(self, audio_encoder, projection):
        super().__init__()
        self.audio_encoder = audio_encoder
        self.projection = projection

    def forward(self, mel_features: Tensor) -> Tensor:
        # audio_encoder typically outputs [batch, seq_len, encoder_dim]
        audio_out = self.audio_encoder(mel_features)
        # projection maps encoder_dim → LLM hidden_size
        return self.projection(audio_out)


# ---------------------------------------------------------------------------
# Decoder wrapper with KV-cache
# ---------------------------------------------------------------------------

class DecoderWrapper(nn.Module):
    """
    Wraps the Qwen3 LLM decoder for autoregressive generation with KV-cache.

    Inputs:
      input_ids     [batch, seq_len]          — token IDs to embed
      audio_embeds  [batch, audio_len, hidden] — audio prefix (zero seq_len after first step)
      past_kv_keys  [n_layers, batch, heads, past_seq, head_dim]
      past_kv_vals  [n_layers, batch, heads, past_seq, head_dim]

    Outputs:
      logits        [batch, total_seq_len, vocab_size]
      new_kv_keys   [n_layers, batch, heads, new_seq, head_dim]
      new_kv_vals   [n_layers, batch, heads, new_seq, head_dim]
    """
    def __init__(self, model):
        super().__init__()
        # The transformer backbone (without lm_head)
        self.transformer = model.model
        self.lm_head = model.lm_head
        self.embed_tokens = model.model.embed_tokens
        self.n_layers = model.config.num_hidden_layers

    def forward(
        self,
        input_ids: Tensor,
        audio_embeds: Tensor,
        past_kv_keys: Tensor,
        past_kv_vals: Tensor,
    ):
        # Embed text tokens
        token_embeds = self.embed_tokens(input_ids)  # [b, seq, h]

        # Prepend audio embeddings (audio_embeds has seq_len=0 on non-first steps)
        inputs_embeds = torch.cat([audio_embeds, token_embeds], dim=1)

        # Reconstruct past_key_values from stacked tensors
        past_seq_len = past_kv_keys.shape[3]
        if past_seq_len > 0:
            past_kv = tuple(
                (past_kv_keys[i], past_kv_vals[i])
                for i in range(self.n_layers)
            )
        else:
            past_kv = None

        outputs = self.transformer(
            inputs_embeds=inputs_embeds,
            past_key_values=past_kv,
            use_cache=True,
        )

        logits = self.lm_head(outputs.last_hidden_state)

        new_keys = torch.stack([kv[0] for kv in outputs.past_key_values])
        new_vals = torch.stack([kv[1] for kv in outputs.past_key_values])

        return logits, new_keys, new_vals


# ---------------------------------------------------------------------------
# Model loading helpers
# ---------------------------------------------------------------------------

def load_qwen3_asr_model(model_id: str):
    """
    Load Qwen3-ASR model from HuggingFace.
    Returns (model, processor/feature_extractor, config).
    """
    from transformers import AutoProcessor, AutoConfig

    print(f"Loading model config from {model_id} ...")
    config = AutoConfig.from_pretrained(model_id, trust_remote_code=True)

    print(f"Loading processor from {model_id} ...")
    try:
        processor = AutoProcessor.from_pretrained(model_id, trust_remote_code=True)
    except Exception:
        processor = None

    print(f"Loading model weights from {model_id} ...")
    try:
        # Try the standard qwen_asr package first
        from qwen_asr import Qwen3ASRModel as QwenModel
        model = QwenModel.from_pretrained(
            model_id,
            dtype=torch.float32,
            device_map="cpu",
        )
        # Access the underlying HuggingFace model
        hf_model = model.model if hasattr(model, "model") else model
    except ImportError:
        # Fall back to transformers with trust_remote_code
        from transformers import AutoModelForCausalLM
        hf_model = AutoModelForCausalLM.from_pretrained(
            model_id,
            trust_remote_code=True,
            torch_dtype=torch.float32,
            device_map="cpu",
        )

    hf_model.eval()
    return hf_model, processor, config


def find_audio_encoder_and_projection(model):
    """
    Locate the audio encoder and projection layer in the Qwen3-ASR model.
    Different versions may use different attribute names.
    """
    # Common attribute names across Qwen3-ASR versions
    encoder_candidates = ["audio_encoder", "encoder", "speech_encoder", "audio_tower"]
    proj_candidates = ["audio_projection", "projection", "audio_proj", "mm_projector"]

    audio_encoder = None
    for name in encoder_candidates:
        if hasattr(model, name):
            audio_encoder = getattr(model, name)
            print(f"Found audio encoder at model.{name}")
            break

    projection = None
    for name in proj_candidates:
        if hasattr(model, name):
            projection = getattr(model, name)
            print(f"Found projection at model.{name}")
            break

    if audio_encoder is None:
        raise AttributeError(
            f"Cannot find audio encoder in {type(model).__name__}. "
            f"Tried: {encoder_candidates}. "
            f"Available attributes: {[k for k in dir(model) if not k.startswith('_')]}"
        )

    return audio_encoder, projection


# ---------------------------------------------------------------------------
# ONNX export
# ---------------------------------------------------------------------------

def export_encoder(model, output_dir: Path, opset: int):
    """Export the audio encoder to encoder.onnx."""
    print("\n--- Exporting audio encoder ---")
    output_path = output_dir / "encoder.onnx"

    audio_encoder, projection = find_audio_encoder_and_projection(model)
    wrapper = AudioEncoderWrapper(audio_encoder, projection).eval()

    # Determine mel parameters from model config or processor
    n_mels = getattr(model.config, "num_mel_bins", None) or \
             getattr(model.config, "n_mels", None) or 128
    n_frames = 3000  # 30s at hop_length=160, sample_rate=16000

    dummy_mel = torch.zeros(1, n_mels, n_frames)

    with torch.no_grad():
        torch.onnx.export(
            wrapper,
            (dummy_mel,),
            str(output_path),
            opset_version=opset,
            input_names=["mel_features"],
            output_names=["audio_embeds"],
            dynamic_axes={
                "mel_features": {0: "batch", 2: "n_frames"},
                "audio_embeds": {0: "batch", 1: "audio_seq_len"},
            },
            do_constant_folding=True,
        )

    print(f"Saved encoder.onnx ({output_path.stat().st_size / 1e6:.1f} MB)")
    return str(output_path)


def export_decoder(model, output_dir: Path, opset: int):
    """Export the Qwen3 LLM decoder with KV-cache to decoder.onnx."""
    print("\n--- Exporting LLM decoder ---")
    output_path = output_dir / "decoder.onnx"

    n_layers = model.config.num_hidden_layers
    n_heads = getattr(model.config, "num_key_value_heads", model.config.num_attention_heads)
    head_dim = model.config.hidden_size // model.config.num_attention_heads
    hidden_size = model.config.hidden_size

    wrapper = DecoderWrapper(model).eval()

    # Dummy inputs for tracing
    dummy_input_ids = torch.zeros(1, 1, dtype=torch.long)
    dummy_audio_embeds = torch.zeros(1, 10, hidden_size)  # non-empty for first pass trace
    dummy_past_keys = torch.zeros(n_layers, 1, n_heads, 0, head_dim)
    dummy_past_vals = torch.zeros(n_layers, 1, n_heads, 0, head_dim)

    with torch.no_grad():
        torch.onnx.export(
            wrapper,
            (dummy_input_ids, dummy_audio_embeds, dummy_past_keys, dummy_past_vals),
            str(output_path),
            opset_version=opset,
            input_names=["input_ids", "audio_embeds", "past_kv_keys", "past_kv_vals"],
            output_names=["logits", "new_kv_keys", "new_kv_vals"],
            dynamic_axes={
                "input_ids":      {0: "batch", 1: "seq_len"},
                "audio_embeds":   {0: "batch", 1: "audio_len"},
                "past_kv_keys":   {1: "batch", 3: "past_seq"},
                "past_kv_vals":   {1: "batch", 3: "past_seq"},
                "logits":         {0: "batch", 1: "out_seq_len"},
                "new_kv_keys":    {1: "batch", 3: "new_seq"},
                "new_kv_vals":    {1: "batch", 3: "new_seq"},
            },
            do_constant_folding=True,
        )

    print(f"Saved decoder.onnx ({output_path.stat().st_size / 1e6:.1f} MB)")
    return str(output_path)


# ---------------------------------------------------------------------------
# INT8 quantization
# ---------------------------------------------------------------------------

def quantize_model(onnx_path: str, output_path: str):
    """Apply static INT8 quantization to an ONNX model."""
    from onnxruntime.quantization import quantize_dynamic, QuantType

    print(f"Quantizing {os.path.basename(onnx_path)} to INT8 ...")
    quantize_dynamic(
        onnx_path,
        output_path,
        weight_type=QuantType.QInt8,
        per_channel=True,
        reduce_range=True,
    )
    original_mb = os.path.getsize(onnx_path) / 1e6
    quantized_mb = os.path.getsize(output_path) / 1e6
    print(f"  {original_mb:.1f} MB → {quantized_mb:.1f} MB ({100*quantized_mb/original_mb:.0f}%)")


# ---------------------------------------------------------------------------
# Vocabulary export
# ---------------------------------------------------------------------------

def export_vocabulary(model_id: str, output_dir: Path):
    """Export token ID → string mapping as vocab.json (tiktoken byte-level BPE)."""
    print("\n--- Exporting vocabulary ---")
    output_path = output_dir / "vocab.json"

    from transformers import AutoTokenizer
    tokenizer = AutoTokenizer.from_pretrained(model_id, trust_remote_code=True)

    # Build {token_id: token_string} mapping
    vocab = {}
    if hasattr(tokenizer, "get_vocab"):
        raw_vocab = tokenizer.get_vocab()
        # Invert to id→string
        id_to_str = {v: k for k, v in raw_vocab.items()}
        vocab = {str(i): id_to_str[i] for i in sorted(id_to_str.keys())}
    else:
        # Fallback: decode individual token IDs
        vocab_size = tokenizer.vocab_size
        for i in range(vocab_size):
            try:
                token = tokenizer.convert_ids_to_tokens(i)
                if token is not None:
                    vocab[str(i)] = token
            except Exception:
                pass

    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(vocab, f, ensure_ascii=False, indent=None)

    print(f"Saved vocab.json ({len(vocab)} tokens, {output_path.stat().st_size / 1e6:.2f} MB)")


# ---------------------------------------------------------------------------
# Config export
# ---------------------------------------------------------------------------

def export_config(model, model_id: str, output_dir: Path):
    """Export runtime configuration to config.json."""
    print("\n--- Exporting config ---")
    output_path = output_dir / "config.json"

    from transformers import AutoTokenizer
    tokenizer = AutoTokenizer.from_pretrained(model_id, trust_remote_code=True)

    n_heads = getattr(model.config, "num_key_value_heads", model.config.num_attention_heads)
    head_dim = model.config.hidden_size // model.config.num_attention_heads

    # Mel feature extractor params (try feature_extractor config, fall back to defaults)
    try:
        from transformers import AutoFeatureExtractor
        fe = AutoFeatureExtractor.from_pretrained(model_id, trust_remote_code=True)
        n_mels = getattr(fe, "num_mel_bins", 128)
        n_fft = getattr(fe, "n_fft", 400)
        hop_length = getattr(fe, "hop_length", 160)
        sample_rate = getattr(fe, "sampling_rate", 16000)
    except Exception:
        n_mels, n_fft, hop_length, sample_rate = 128, 400, 160, 16000

    config = {
        "model_id": model_id,
        "bos_token_id": int(tokenizer.bos_token_id or tokenizer.convert_tokens_to_ids("<|im_start|>") or 151644),
        "eos_token_id": int(tokenizer.eos_token_id or tokenizer.convert_tokens_to_ids("<|im_end|>") or 151645),
        "max_new_tokens": 448,
        "max_audio_seconds": 30,
        "num_hidden_layers": int(model.config.num_hidden_layers),
        "num_key_value_heads": int(n_heads),
        "head_dim": int(head_dim),
        "hidden_size": int(model.config.hidden_size),
        "n_mels": int(n_mels),
        "n_fft": int(n_fft),
        "hop_length": int(hop_length),
        "sample_rate": int(sample_rate),
    }

    with open(output_path, "w") as f:
        json.dump(config, f, indent=2)

    print(f"Config: {json.dumps(config, indent=2)}")
    print(f"Saved config.json")


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main():
    args = parse_args()
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    print(f"Converting {args.model_id} → {output_dir}")
    print(f"Quantization: {'disabled' if args.no_quantize else 'INT8'}")

    # Load model
    model, processor, config = load_qwen3_asr_model(args.model_id)

    # Export encoder
    encoder_fp32 = str(output_dir / "encoder_fp32.onnx")
    _enc_path = export_encoder(model, output_dir, args.opset)
    shutil.copy(_enc_path, encoder_fp32)

    # Export decoder
    decoder_fp32 = str(output_dir / "decoder_fp32.onnx")
    _dec_path = export_decoder(model, output_dir, args.opset)
    shutil.copy(_dec_path, decoder_fp32)

    # Quantize if requested
    if not args.no_quantize:
        quantize_model(encoder_fp32, str(output_dir / "encoder.onnx"))
        quantize_model(decoder_fp32, str(output_dir / "decoder.onnx"))
        os.remove(encoder_fp32)
        os.remove(decoder_fp32)
        # Remove intermediate fp32 files that were placed by export functions
        for f in ["encoder_fp32.onnx", "decoder_fp32.onnx"]:
            p = output_dir / f
            if p.exists():
                p.unlink()
    else:
        # Rename fp32 to final names
        (output_dir / "encoder_fp32.onnx").rename(output_dir / "encoder.onnx")
        (output_dir / "decoder_fp32.onnx").rename(output_dir / "decoder.onnx")

    # Export vocabulary and config
    export_vocabulary(args.model_id, output_dir)
    export_config(model, args.model_id, output_dir)

    # Print summary
    print("\n=== Conversion complete ===")
    total_size = sum(f.stat().st_size for f in output_dir.iterdir() if f.is_file())
    for f in sorted(output_dir.iterdir()):
        if f.is_file():
            print(f"  {f.name:30s}  {f.stat().st_size / 1e6:7.1f} MB")
    print(f"  {'TOTAL':30s}  {total_size / 1e6:7.1f} MB")

    print(f"""
Next steps:
  1. Run the CI workflow or upload files manually to a GitHub release:
       gh release create qwen3-asr-v1 \\
         {output_dir}/encoder.onnx \\
         {output_dir}/decoder.onnx \\
         {output_dir}/vocab.json \\
         {output_dir}/config.json \\
         --title "Qwen3-ASR Model (ONNX for Android)" \\
         --notes "Converted from {args.model_id}"

  2. Update QWEN3_ASR_FALLBACK_URL in ModelDownloadManager.kt if needed.
""")


if __name__ == "__main__":
    main()
