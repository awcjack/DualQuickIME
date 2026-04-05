#!/usr/bin/env python3
"""
Convert khleeloo/whisper-large-v3-cantonese (HuggingFace format) to sherpa-onnx format.

This script adapts the whisper-small-cantonese conversion for the larger model.
The main differences are:
- Uses large-v3 model dimensions (1280 d_model, 32 heads, 32 encoder layers, 32 decoder layers)
- Outputs files with "large-v3" prefix instead of "small"
- Uses "whisper-large" as model_type in metadata

Model source: https://huggingface.co/khleeloo/whisper-large-v3-cantonese
Performance: 7.26% CER on Common Voice 17 yue test set

Usage:
    python convert_whisper_large_v3_cantonese.py --output-dir ./sherpa-onnx-whisper-large-v3-cantonese

Requirements:
    pip install torch transformers openai-whisper onnx onnxruntime

Note: This model is ~3GB when converted. Ensure sufficient disk space and RAM (~16GB recommended).
"""

import argparse
import os
import sys
from pathlib import Path
from typing import Dict, Any, Optional

import torch
import torch.nn as nn
import torch.nn.functional as F
from torch import Tensor


def export_sherpa_onnx(model_id: str, output_dir: str, quantize: bool = True):
    """
    Export HuggingFace Whisper Large v3 model to sherpa-onnx compatible ONNX format.

    This follows the sherpa-onnx export approach but adapts it for HuggingFace models.
    """
    from transformers import WhisperForConditionalGeneration, WhisperProcessor
    import onnx
    from onnxruntime.quantization import QuantType, quantize_dynamic

    output_path = Path(output_dir)
    output_path.mkdir(parents=True, exist_ok=True)

    print(f"Loading model: {model_id}")
    print("Note: Large-v3 model is ~3GB, this may take a while...")
    hf_model = WhisperForConditionalGeneration.from_pretrained(model_id)
    processor = WhisperProcessor.from_pretrained(model_id)
    hf_model.eval()

    config = hf_model.config

    # Model dimensions
    n_mels = config.num_mel_bins
    n_audio_ctx = config.max_source_positions
    n_audio_state = config.d_model
    n_text_ctx = config.max_target_positions
    n_text_state = config.d_model
    n_text_layer = config.decoder_layers
    n_vocab = config.vocab_size

    print(f"Model config: n_mels={n_mels}, d_model={n_audio_state}, n_text_layer={n_text_layer}")

    # Create encoder wrapper that outputs cross-attention KV for all decoder layers
    class AudioEncoderWithCrossKV(nn.Module):
        def __init__(self, encoder, decoder):
            super().__init__()
            self.conv1 = encoder.conv1
            self.conv2 = encoder.conv2
            self.embed_positions = encoder.embed_positions
            self.layers = encoder.layers
            self.layer_norm = encoder.layer_norm
            self.decoder_layers = decoder.layers

        def forward(self, mel):
            # mel: (batch, n_mels, T)
            x = F.gelu(self.conv1(mel))
            x = F.gelu(self.conv2(x))
            x = x.permute(0, 2, 1)

            seq_len = x.shape[1]
            x = (x + self.embed_positions.weight[:seq_len]).to(x.dtype)

            for layer in self.layers:
                layer_out = layer(x, attention_mask=None, layer_head_mask=None)
                x = layer_out[0] if isinstance(layer_out, tuple) else layer_out

            x = self.layer_norm(x)

            cross_k_list = []
            cross_v_list = []

            for layer in self.decoder_layers:
                cross_k = layer.encoder_attn.k_proj(x)
                cross_v = layer.encoder_attn.v_proj(x)
                cross_k_list.append(cross_k)
                cross_v_list.append(cross_v)

            n_layer_cross_k = torch.stack(cross_k_list, dim=0)
            n_layer_cross_v = torch.stack(cross_v_list, dim=0)

            return n_layer_cross_k, n_layer_cross_v

    # Create decoder wrapper with KV-cache support
    class TextDecoderWithKVCache(nn.Module):
        def __init__(self, decoder, embed_tokens, proj_out, n_text_ctx, n_text_state, n_text_head):
            super().__init__()
            self.decoder = decoder
            self.embed_tokens = embed_tokens
            self.proj_out = proj_out
            self.n_text_ctx = n_text_ctx
            self.n_text_state = n_text_state
            self.n_text_head = n_text_head
            self.head_dim = n_text_state // n_text_head

            mask = torch.empty(n_text_ctx, n_text_ctx).fill_(-10000.0)
            mask = torch.triu(mask, diagonal=1)
            self.register_buffer("mask", mask, persistent=False)

        def forward(
            self,
            tokens,
            in_n_layer_self_k_cache,
            in_n_layer_self_v_cache,
            n_layer_cross_k,
            n_layer_cross_v,
            offset,
        ):
            seq_len = tokens.shape[1]
            offset_val = offset[0]

            x = self.embed_tokens(tokens)
            pos_embed = self.decoder.embed_positions.weight[offset_val : offset_val + seq_len]
            x = (x + pos_embed).to(x.dtype)

            out_k_list = []
            out_v_list = []

            for i, layer in enumerate(self.decoder.layers):
                layer_k_cache = in_n_layer_self_k_cache[i]
                layer_v_cache = in_n_layer_self_v_cache[i]

                residual = x
                x = layer.self_attn_layer_norm(x)

                q = layer.self_attn.q_proj(x)
                k = layer.self_attn.k_proj(x)
                v = layer.self_attn.v_proj(x)

                k_cache_updated = torch.cat([layer_k_cache, k], dim=1)[:, -self.n_text_ctx:, :]
                v_cache_updated = torch.cat([layer_v_cache, v], dim=1)[:, -self.n_text_ctx:, :]

                out_k_list.append(k_cache_updated)
                out_v_list.append(v_cache_updated)

                k_for_attn = k_cache_updated[:, :offset_val + seq_len, :]
                v_for_attn = v_cache_updated[:, :offset_val + seq_len, :]
                attn_out = self._multi_head_attention(
                    q, k_for_attn, v_for_attn,
                    mask=self.mask[:offset_val + seq_len, :offset_val + seq_len]
                )
                attn_out = layer.self_attn.out_proj(attn_out)
                x = residual + attn_out

                residual = x
                x = layer.encoder_attn_layer_norm(x)

                q_cross = layer.encoder_attn.q_proj(x)
                k_cross = n_layer_cross_k[i]
                v_cross = n_layer_cross_v[i]

                cross_attn_out = self._multi_head_attention(q_cross, k_cross, v_cross, mask=None)
                cross_attn_out = layer.encoder_attn.out_proj(cross_attn_out)
                x = residual + cross_attn_out

                residual = x
                x = layer.final_layer_norm(x)
                x = layer.fc1(x)
                x = F.gelu(x)
                x = layer.fc2(x)
                x = residual + x

            x = self.decoder.layer_norm(x)
            logits = self.proj_out(x)

            out_self_k_cache = torch.stack(out_k_list, dim=0)
            out_self_v_cache = torch.stack(out_v_list, dim=0)

            return logits, out_self_k_cache, out_self_v_cache

        def _multi_head_attention(self, q, k, v, mask=None):
            batch_size = q.shape[0]
            seq_len_q = q.shape[1]
            seq_len_k = k.shape[1]

            q = q.view(batch_size, seq_len_q, self.n_text_head, self.head_dim).transpose(1, 2)
            k = k.view(batch_size, seq_len_k, self.n_text_head, self.head_dim).transpose(1, 2)
            v = v.view(batch_size, seq_len_k, self.n_text_head, self.head_dim).transpose(1, 2)

            scale = self.head_dim ** -0.5
            attn_weights = torch.matmul(q, k.transpose(-2, -1)) * scale

            if mask is not None:
                mask_slice = mask[seq_len_k - seq_len_q : seq_len_k, :seq_len_k]
                attn_weights = attn_weights + mask_slice

            attn_weights = F.softmax(attn_weights, dim=-1)
            attn_output = torch.matmul(attn_weights, v)

            attn_output = attn_output.transpose(1, 2).contiguous().view(batch_size, seq_len_q, -1)

            return attn_output

    # Export encoder
    print("\nStep 1: Exporting encoder (this may take a while for large-v3)...")
    encoder_wrapper = AudioEncoderWithCrossKV(hf_model.model.encoder, hf_model.model.decoder)
    encoder_wrapper.eval()

    dummy_mel = torch.randn(1, n_mels, 3000)

    encoder_filename = output_path / "large-v3-encoder.onnx"

    with torch.no_grad():
        torch.onnx.export(
            encoder_wrapper,
            dummy_mel,
            str(encoder_filename),
            opset_version=17,
            input_names=["mel"],
            output_names=["n_layer_cross_k", "n_layer_cross_v"],
            dynamic_axes={
                "mel": {0: "n_audio", 2: "T"},
                "n_layer_cross_k": {1: "n_audio", 2: "T"},
                "n_layer_cross_v": {1: "n_audio", 2: "T"},
            },
            do_constant_folding=True,
            dynamo=False,
        )

    add_encoder_metadata(str(encoder_filename), config, processor)
    print(f"  Saved: {encoder_filename}")

    # Export decoder
    print("\nStep 2: Exporting decoder...")
    n_text_head = config.decoder_attention_heads
    decoder_wrapper = TextDecoderWithKVCache(
        hf_model.model.decoder,
        hf_model.model.decoder.embed_tokens,
        hf_model.proj_out,
        n_text_ctx,
        n_text_state,
        n_text_head
    )
    decoder_wrapper.eval()

    batch_size = 1
    seq_len = 3
    audio_len = 1500

    dummy_tokens = torch.tensor([[50258, 50259, 50359]], dtype=torch.long)
    dummy_self_k_cache = torch.zeros(n_text_layer, batch_size, n_text_ctx, n_text_state)
    dummy_self_v_cache = torch.zeros(n_text_layer, batch_size, n_text_ctx, n_text_state)
    dummy_cross_k = torch.randn(n_text_layer, batch_size, audio_len, n_text_state)
    dummy_cross_v = torch.randn(n_text_layer, batch_size, audio_len, n_text_state)
    dummy_offset = torch.tensor([0], dtype=torch.int64)

    decoder_filename = output_path / "large-v3-decoder.onnx"

    with torch.no_grad():
        torch.onnx.export(
            decoder_wrapper,
            (dummy_tokens, dummy_self_k_cache, dummy_self_v_cache,
             dummy_cross_k, dummy_cross_v, dummy_offset),
            str(decoder_filename),
            opset_version=17,
            input_names=[
                "tokens",
                "in_n_layer_self_k_cache",
                "in_n_layer_self_v_cache",
                "n_layer_cross_k",
                "n_layer_cross_v",
                "offset",
            ],
            output_names=["logits", "out_n_layer_self_k_cache", "out_n_layer_self_v_cache"],
            dynamic_axes={
                "tokens": {0: "n_audio", 1: "n_tokens"},
                "in_n_layer_self_k_cache": {1: "n_audio"},
                "in_n_layer_self_v_cache": {1: "n_audio"},
                "n_layer_cross_k": {1: "n_audio", 2: "T"},
                "n_layer_cross_v": {1: "n_audio", 2: "T"},
            },
            do_constant_folding=True,
            dynamo=False,
        )

    print(f"  Saved: {decoder_filename}")

    # Export tokens
    print("\nStep 3: Exporting tokens...")
    tokens_filename = output_path / "large-v3-tokens.txt"
    export_tokens(processor, tokens_filename)
    print(f"  Saved: {tokens_filename}")

    # Quantize
    if quantize:
        print("\nStep 4: Quantizing models to int8 (this may take a while for large-v3)...")

        encoder_int8 = output_path / "large-v3-encoder.int8.onnx"
        quantize_dynamic(
            model_input=str(encoder_filename),
            model_output=str(encoder_int8),
            op_types_to_quantize=["MatMul"],
            weight_type=QuantType.QInt8,
        )
        print(f"  Created: {encoder_int8}")
        encoder_filename.unlink()

        decoder_int8 = output_path / "large-v3-decoder.int8.onnx"
        quantize_dynamic(
            model_input=str(decoder_filename),
            model_output=str(decoder_int8),
            op_types_to_quantize=["MatMul"],
            weight_type=QuantType.QInt8,
        )
        print(f"  Created: {decoder_int8}")
        decoder_filename.unlink()

    print(f"\n✓ Conversion complete! Output files in: {output_path}")
    print("\nFinal files:")
    for f in sorted(output_path.glob("*")):
        if f.is_file():
            size_mb = f.stat().st_size / (1024 * 1024)
            print(f"  {f.name}: {size_mb:.1f} MB")


def add_encoder_metadata(filename: str, config, processor):
    """Add metadata required by sherpa-onnx to the encoder ONNX file."""
    import onnx

    model = onnx.load(filename)

    while len(model.metadata_props):
        model.metadata_props.pop()

    tokenizer = processor.tokenizer

    sot = tokenizer.convert_tokens_to_ids("<|startoftranscript|>")
    eot = tokenizer.convert_tokens_to_ids("<|endoftext|>")
    transcribe = tokenizer.convert_tokens_to_ids("<|transcribe|>")
    translate = tokenizer.convert_tokens_to_ids("<|translate|>")
    no_timestamps = tokenizer.convert_tokens_to_ids("<|notimestamps|>")
    no_speech = tokenizer.convert_tokens_to_ids("<|nocaptions|>")

    blank_id = tokenizer.encode(" ", add_special_tokens=False)[0]

    sot_sequence = [sot]
    sot_index = 0

    lang_tokens = []
    lang_codes = []
    for tid in range(50259, 50358):
        tok = tokenizer.convert_ids_to_tokens(tid)
        if tok and tok.startswith('<|') and tok.endswith('|>'):
            code = tok[2:-2]
            lang_tokens.append(tid)
            lang_codes.append(code)

    non_speech_tokens = [eot, sot, translate, transcribe, no_timestamps]
    non_speech_tokens.extend(lang_tokens)

    sot_prev = tokenizer.convert_tokens_to_ids("<|startofprev|>")
    sot_lm = tokenizer.convert_tokens_to_ids("<|startoflm|>")

    if sot_prev is None or sot_prev == tokenizer.unk_token_id:
        sot_prev = 50361
    if sot_lm is None or sot_lm == tokenizer.unk_token_id:
        sot_lm = 50360

    meta_data = {
        "model_type": "whisper-large",
        "version": "1",
        "maintainer": "dualquickime",
        "n_mels": config.num_mel_bins,
        "n_audio_ctx": config.max_source_positions,
        "n_audio_state": config.d_model,
        "n_audio_head": config.encoder_attention_heads,
        "n_audio_layer": config.encoder_layers,
        "n_vocab": config.vocab_size,
        "n_text_ctx": config.max_target_positions,
        "n_text_state": config.d_model,
        "n_text_head": config.decoder_attention_heads,
        "n_text_layer": config.decoder_layers,
        "sot": sot,
        "sot_index": sot_index,
        "sot_sequence": ",".join(map(str, sot_sequence)),
        "eot": eot,
        "transcribe": transcribe,
        "translate": translate,
        "no_timestamps": no_timestamps,
        "no_speech": no_speech,
        "blank_id": blank_id,
        "is_multilingual": 1,
        "all_language_tokens": ",".join(map(str, lang_tokens)),
        "all_language_codes": ",".join(lang_codes),
        "non_speech_tokens": ",".join(map(str, non_speech_tokens)),
        "sot_prev": sot_prev,
        "sot_lm": sot_lm,
    }

    for key, value in meta_data.items():
        meta = model.metadata_props.add()
        meta.key = key
        meta.value = str(value)

    onnx.save(model, filename)


def export_tokens(processor, output_path: Path):
    """Export tokenizer vocabulary in sherpa-onnx tiktoken format."""
    import base64

    try:
        import whisper
        from pathlib import Path as WhisperPath
        whisper_dir = WhisperPath(whisper.__file__).parent
        tiktoken_file = whisper_dir / "assets" / "multilingual.tiktoken"

        if tiktoken_file.is_file():
            print(f"  Using original tiktoken file: {tiktoken_file}")
            with open(tiktoken_file, "r") as f:
                contents = f.read()
                tokens = {
                    token: int(rank)
                    for token, rank in (line.split() for line in contents.splitlines() if line)
                }

            with open(output_path, "w", encoding="utf-8") as f:
                for t, i in tokens.items():
                    f.write(f"{t} {i}\n")

            print(f"  Exported {len(tokens)} tokens from tiktoken")
            return
    except ImportError:
        print("  openai-whisper not available, using HuggingFace tokenizer")

    print("  Converting HuggingFace tokenizer to tiktoken format")
    tokenizer = processor.tokenizer

    def bytes_to_unicode():
        bs = list(range(ord("!"), ord("~") + 1)) + list(range(ord("¡"), ord("¬") + 1)) + list(range(ord("®"), ord("ÿ") + 1))
        cs = bs[:]
        n = 0
        for b in range(2**8):
            if b not in bs:
                bs.append(b)
                cs.append(2**8 + n)
                n += 1
        cs = [chr(n) for n in cs]
        return dict(zip(bs, cs))

    byte_encoder = bytes_to_unicode()
    byte_decoder = {v: k for k, v in byte_encoder.items()}

    with open(output_path, "w", encoding="utf-8") as f:
        vocab = tokenizer.get_vocab()
        sorted_vocab = sorted(vocab.items(), key=lambda x: x[1])

        for token, idx in sorted_vocab:
            try:
                raw_bytes = bytes([byte_decoder[c] for c in token])
                token_b64 = base64.b64encode(raw_bytes).decode("ascii")
            except KeyError:
                raw_bytes = token.encode("utf-8")
                token_b64 = base64.b64encode(raw_bytes).decode("ascii")

            f.write(f"{token_b64} {idx}\n")

    print(f"  Exported {len(sorted_vocab)} tokens from HuggingFace tokenizer")


def main():
    parser = argparse.ArgumentParser(
        description="Convert Whisper Large v3 Cantonese model to sherpa-onnx format"
    )
    parser.add_argument(
        "--model-id",
        default="khleeloo/whisper-large-v3-cantonese",
        help="HuggingFace model ID",
    )
    parser.add_argument(
        "--output-dir",
        default="./sherpa-onnx-whisper-large-v3-cantonese",
        help="Output directory for ONNX files",
    )
    parser.add_argument(
        "--no-quantize",
        action="store_true",
        help="Skip int8 quantization",
    )

    args = parser.parse_args()

    export_sherpa_onnx(
        model_id=args.model_id,
        output_dir=args.output_dir,
        quantize=not args.no_quantize,
    )


if __name__ == "__main__":
    main()
