#!/usr/bin/env python3
"""
Convert alvanlii/whisper-small-cantonese (HuggingFace format) to sherpa-onnx format.

This script:
1. Downloads the HuggingFace model
2. Converts weights from HuggingFace to OpenAI Whisper format
3. Uses sherpa-onnx's export approach to generate compatible ONNX files

The sherpa-onnx Whisper format has specific requirements:
- Encoder outputs n_layer_cross_k and n_layer_cross_v (pre-computed cross-attention KV)
- Decoder uses KV-cache for efficient autoregressive decoding
- Specific input/output tensor names and metadata

Usage:
    python convert_whisper_cantonese.py --output-dir ./sherpa-onnx-whisper-small-cantonese

Requirements:
    pip install torch transformers openai-whisper onnx onnxruntime
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


def convert_hf_to_openai_checkpoint(model_id: str, output_path: Path) -> Path:
    """
    Convert HuggingFace Whisper model to OpenAI Whisper checkpoint format.

    Returns the path to the saved checkpoint file.
    """
    from transformers import WhisperForConditionalGeneration, WhisperProcessor

    print(f"Loading HuggingFace model: {model_id}")
    hf_model = WhisperForConditionalGeneration.from_pretrained(model_id)
    processor = WhisperProcessor.from_pretrained(model_id)

    # Get model dimensions
    config = hf_model.config

    # Create the dims object that OpenAI Whisper expects
    dims = {
        "n_mels": config.num_mel_bins,
        "n_vocab": config.vocab_size,
        "n_audio_ctx": config.max_source_positions,
        "n_audio_state": config.d_model,
        "n_audio_head": config.encoder_attention_heads,
        "n_audio_layer": config.encoder_layers,
        "n_text_ctx": config.max_target_positions,
        "n_text_state": config.d_model,
        "n_text_head": config.decoder_attention_heads,
        "n_text_layer": config.decoder_layers,
    }

    print(f"Model dimensions: {dims}")

    # Convert state dict from HuggingFace to OpenAI format
    hf_state = hf_model.state_dict()
    openai_state = {}

    # Key mapping from HuggingFace to OpenAI
    for hf_key, value in hf_state.items():
        openai_key = hf_key

        # Remove "model." prefix
        if openai_key.startswith("model."):
            openai_key = openai_key[6:]

        # proj_out -> decoder.token_embedding (weight is shared/tied)
        if openai_key == "proj_out.weight":
            # In OpenAI Whisper, the output projection shares weights with token embedding
            # Skip this as it will be handled by token_embedding
            continue

        # Map encoder
        openai_key = openai_key.replace("encoder.layers.", "encoder.blocks.")
        openai_key = openai_key.replace("encoder.embed_positions.weight", "encoder.positional_embedding")
        openai_key = openai_key.replace("encoder.layer_norm.", "encoder.ln_post.")
        openai_key = openai_key.replace("encoder.conv1.", "encoder.conv1.")
        openai_key = openai_key.replace("encoder.conv2.", "encoder.conv2.")

        # Map decoder
        openai_key = openai_key.replace("decoder.layers.", "decoder.blocks.")
        openai_key = openai_key.replace("decoder.embed_positions.weight", "decoder.positional_embedding")
        openai_key = openai_key.replace("decoder.embed_tokens.weight", "decoder.token_embedding.weight")
        openai_key = openai_key.replace("decoder.layer_norm.", "decoder.ln.")

        # Map attention components
        openai_key = openai_key.replace(".self_attn.", ".attn.")
        openai_key = openai_key.replace(".encoder_attn.", ".cross_attn.")
        openai_key = openai_key.replace(".self_attn_layer_norm.", ".attn_ln.")
        openai_key = openai_key.replace(".encoder_attn_layer_norm.", ".cross_attn_ln.")
        openai_key = openai_key.replace(".final_layer_norm.", ".mlp_ln.")

        # Map Q/K/V projections
        openai_key = openai_key.replace(".q_proj.", ".query.")
        openai_key = openai_key.replace(".k_proj.", ".key.")
        openai_key = openai_key.replace(".v_proj.", ".value.")
        openai_key = openai_key.replace(".out_proj.", ".out.")

        # Map FFN
        openai_key = openai_key.replace(".fc1.", ".mlp.0.")
        openai_key = openai_key.replace(".fc2.", ".mlp.2.")

        openai_state[openai_key] = value

    # Save checkpoint in OpenAI format
    checkpoint_path = output_path / "whisper-small-cantonese.pt"
    output_path.mkdir(parents=True, exist_ok=True)

    # OpenAI checkpoint format
    checkpoint = {
        "model_state_dict": openai_state,
        "dims": type('Dims', (), dims)(),  # Create a simple object with dims as attributes
    }

    # Actually we need to save it in a format that whisper.load_model can understand
    # The simplest approach is to create a full model state dict
    torch.save(openai_state, checkpoint_path)

    print(f"Saved OpenAI-format checkpoint to {checkpoint_path}")
    return checkpoint_path, dims, processor


def export_sherpa_onnx(model_id: str, output_dir: str, quantize: bool = True):
    """
    Export HuggingFace Whisper model to sherpa-onnx compatible ONNX format.

    This follows the sherpa-onnx export approach but adapts it for HuggingFace models.
    """
    from transformers import WhisperForConditionalGeneration, WhisperProcessor
    import onnx
    from onnxruntime.quantization import QuantType, quantize_dynamic

    output_path = Path(output_dir)
    output_path.mkdir(parents=True, exist_ok=True)

    print(f"Loading model: {model_id}")
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

    print(f"Model config: n_mels={n_mels}, n_audio_ctx={n_audio_ctx}, n_text_layer={n_text_layer}")

    # Create encoder wrapper that outputs cross-attention KV for all decoder layers
    # We need to manually implement the encoder forward to handle dynamic positional embeddings
    class AudioEncoderWithCrossKV(nn.Module):
        def __init__(self, encoder, decoder):
            super().__init__()
            # Copy the encoder components we need
            self.conv1 = encoder.conv1
            self.conv2 = encoder.conv2
            self.embed_positions = encoder.embed_positions
            self.layers = encoder.layers
            self.layer_norm = encoder.layer_norm
            self.decoder_layers = decoder.layers

        def forward(self, mel):
            # mel: (batch, n_mels, T)
            # Manual encoder forward pass for proper dynamic positional embedding handling

            # Convolution layers
            x = F.gelu(self.conv1(mel))
            x = F.gelu(self.conv2(x))

            # x is now (batch, d_model, T/2), transpose to (batch, T/2, d_model)
            x = x.permute(0, 2, 1)

            # Add positional embeddings with DYNAMIC slicing
            # This is the key fix - use x.shape[1] for dynamic length
            seq_len = x.shape[1]
            x = x + self.embed_positions.weight[:seq_len]

            # Pass through transformer layers
            for layer in self.layers:
                # The layer returns a tensor directly (not a tuple)
                x = layer(x, attention_mask=None)

            # Final layer norm
            x = self.layer_norm(x)

            # Pre-compute cross-attention K and V for all decoder layers
            cross_k_list = []
            cross_v_list = []

            for layer in self.decoder_layers:
                # Get cross-attention K and V
                cross_k = layer.encoder_attn.k_proj(x)
                cross_v = layer.encoder_attn.v_proj(x)
                cross_k_list.append(cross_k)
                cross_v_list.append(cross_v)

            # Stack: (n_layers, batch, T, d_model)
            n_layer_cross_k = torch.stack(cross_k_list, dim=0)
            n_layer_cross_v = torch.stack(cross_v_list, dim=0)

            return n_layer_cross_k, n_layer_cross_v

    # Create decoder wrapper with KV-cache support
    class TextDecoderWithKVCache(nn.Module):
        def __init__(self, decoder, embed_tokens, proj_out, n_text_ctx):
            super().__init__()
            self.decoder = decoder
            self.embed_tokens = embed_tokens
            self.proj_out = proj_out
            self.n_text_ctx = n_text_ctx

        def forward(
            self,
            tokens,                    # (batch, seq_len)
            in_n_layer_self_k_cache,   # (n_layers, batch, n_text_ctx, d_model)
            in_n_layer_self_v_cache,   # (n_layers, batch, n_text_ctx, d_model)
            n_layer_cross_k,           # (n_layers, batch, audio_len, d_model)
            n_layer_cross_v,           # (n_layers, batch, audio_len, d_model)
            offset,                    # (1,) - position offset
        ):
            seq_len = tokens.shape[1]

            # Get token embeddings
            x = self.embed_tokens(tokens)

            # Add positional embeddings using tensor indexing (not .item())
            # This preserves the computation graph for ONNX export
            pos_embed = self.decoder.embed_positions.weight[offset[0] : offset[0] + seq_len]
            x = x + pos_embed

            # Process through decoder layers with KV cache
            # Clone to avoid in-place modification issues
            out_self_k_cache = in_n_layer_self_k_cache.clone()
            out_self_v_cache = in_n_layer_self_v_cache.clone()

            for i, layer in enumerate(self.decoder.layers):
                # Self-attention with KV cache
                residual = x
                x = layer.self_attn_layer_norm(x)

                # Compute Q, K, V for self-attention
                q = layer.self_attn.q_proj(x)
                k = layer.self_attn.k_proj(x)
                v = layer.self_attn.v_proj(x)

                # Update KV cache using tensor indexing
                out_self_k_cache[i, :, offset[0]:offset[0]+seq_len, :] = k
                out_self_v_cache[i, :, offset[0]:offset[0]+seq_len, :] = v

                # Use cached K, V for attention (up to current position)
                k_for_attn = out_self_k_cache[i, :, :offset[0]+seq_len, :]
                v_for_attn = out_self_v_cache[i, :, :offset[0]+seq_len, :]

                # Compute self-attention
                attn_out, _ = self._scaled_dot_product_attention(
                    q, k_for_attn, v_for_attn,
                    layer.self_attn.num_heads,
                    is_causal=True
                )
                attn_out = layer.self_attn.out_proj(attn_out)
                x = residual + attn_out

                # Cross-attention
                residual = x
                x = layer.encoder_attn_layer_norm(x)

                q_cross = layer.encoder_attn.q_proj(x)
                # Use pre-computed cross K, V
                k_cross = n_layer_cross_k[i]
                v_cross = n_layer_cross_v[i]

                cross_attn_out, _ = self._scaled_dot_product_attention(
                    q_cross, k_cross, v_cross,
                    layer.encoder_attn.num_heads,
                    is_causal=False
                )
                cross_attn_out = layer.encoder_attn.out_proj(cross_attn_out)
                x = residual + cross_attn_out

                # FFN
                residual = x
                x = layer.final_layer_norm(x)
                x = layer.fc1(x)
                x = F.gelu(x)
                x = layer.fc2(x)
                x = residual + x

            # Final layer norm
            x = self.decoder.layer_norm(x)

            # Project to vocabulary
            logits = self.proj_out(x)

            return logits, out_self_k_cache, out_self_v_cache

        def _scaled_dot_product_attention(self, q, k, v, num_heads, is_causal=False):
            batch_size = q.shape[0]
            seq_len_q = q.shape[1]
            seq_len_k = k.shape[1]
            head_dim = q.shape[2] // num_heads

            # Reshape for multi-head attention
            q = q.view(batch_size, seq_len_q, num_heads, head_dim).transpose(1, 2)
            k = k.view(batch_size, seq_len_k, num_heads, head_dim).transpose(1, 2)
            v = v.view(batch_size, seq_len_k, num_heads, head_dim).transpose(1, 2)

            # Compute attention scores
            scale = head_dim ** -0.5
            attn_weights = torch.matmul(q, k.transpose(-2, -1)) * scale

            # Apply causal mask if needed
            if is_causal:
                mask = torch.triu(torch.ones(seq_len_q, seq_len_k, device=q.device), diagonal=seq_len_k - seq_len_q + 1)
                attn_weights = attn_weights.masked_fill(mask.bool(), float('-inf'))

            attn_weights = F.softmax(attn_weights, dim=-1)

            # Apply attention to values
            attn_output = torch.matmul(attn_weights, v)

            # Reshape back
            attn_output = attn_output.transpose(1, 2).contiguous().view(batch_size, seq_len_q, -1)

            return attn_output, attn_weights

    # Export encoder
    print("\nStep 1: Exporting encoder...")
    encoder_wrapper = AudioEncoderWithCrossKV(hf_model.model.encoder, hf_model.model.decoder)
    encoder_wrapper.eval()

    # Dummy input: (batch=1, n_mels=80, T=3000)
    dummy_mel = torch.randn(1, n_mels, 3000)

    encoder_filename = output_path / "small-encoder.onnx"

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
            dynamo=False,  # Use legacy JIT-based exporter
        )

    # Add metadata
    add_encoder_metadata(str(encoder_filename), config, processor)
    print(f"  Saved: {encoder_filename}")

    # Export decoder
    print("\nStep 2: Exporting decoder...")
    decoder_wrapper = TextDecoderWithKVCache(
        hf_model.model.decoder,
        hf_model.model.decoder.embed_tokens,
        hf_model.proj_out,
        n_text_ctx
    )
    decoder_wrapper.eval()

    # Dummy inputs for decoder
    batch_size = 1
    seq_len = 3
    audio_len = 1500

    dummy_tokens = torch.tensor([[50258, 50259, 50359]], dtype=torch.long)  # SOT tokens
    dummy_self_k_cache = torch.zeros(n_text_layer, batch_size, n_text_ctx, n_text_state)
    dummy_self_v_cache = torch.zeros(n_text_layer, batch_size, n_text_ctx, n_text_state)
    dummy_cross_k = torch.randn(n_text_layer, batch_size, audio_len, n_text_state)
    dummy_cross_v = torch.randn(n_text_layer, batch_size, audio_len, n_text_state)
    dummy_offset = torch.tensor([0], dtype=torch.int64)

    decoder_filename = output_path / "small-decoder.onnx"

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
            dynamo=False,  # Use legacy JIT-based exporter
        )

    print(f"  Saved: {decoder_filename}")

    # Export tokens
    print("\nStep 3: Exporting tokens...")
    tokens_filename = output_path / "small-tokens.txt"
    export_tokens(processor, tokens_filename)
    print(f"  Saved: {tokens_filename}")

    # Quantize
    if quantize:
        print("\nStep 4: Quantizing models to int8...")

        encoder_int8 = output_path / "small-encoder.int8.onnx"
        quantize_dynamic(
            model_input=str(encoder_filename),
            model_output=str(encoder_int8),
            op_types_to_quantize=["MatMul"],
            weight_type=QuantType.QInt8,
        )
        print(f"  Created: {encoder_int8}")
        encoder_filename.unlink()

        decoder_int8 = output_path / "small-decoder.int8.onnx"
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

    # Clear existing metadata
    while len(model.metadata_props):
        model.metadata_props.pop()

    # Get tokenizer info
    tokenizer = processor.tokenizer

    # Get token IDs for special tokens
    sot = tokenizer.convert_tokens_to_ids("<|startoftranscript|>")
    eot = tokenizer.convert_tokens_to_ids("<|endoftext|>")
    transcribe = tokenizer.convert_tokens_to_ids("<|transcribe|>")
    translate = tokenizer.convert_tokens_to_ids("<|translate|>")
    no_timestamps = tokenizer.convert_tokens_to_ids("<|notimestamps|>")
    # no_speech is <|nocaptions|> in HF tokenizer, which is 50362 in OpenAI whisper
    no_speech = tokenizer.convert_tokens_to_ids("<|nocaptions|>")

    # Get blank_id (space token)
    blank_id = tokenizer.encode(" ", add_special_tokens=False)[0]

    # SOT sequence - for multilingual whisper it's just [sot]
    # Language/task tokens are added dynamically at runtime
    sot_sequence = [sot]
    sot_index = 0  # Position of sot in sot_sequence

    # Collect all language tokens and codes
    # Language tokens are in the range 50259-50357 for multilingual Whisper
    lang_tokens = []
    lang_codes = []
    for tid in range(50259, 50358):
        tok = tokenizer.convert_ids_to_tokens(tid)
        if tok and tok.startswith('<|') and tok.endswith('|>'):
            code = tok[2:-2]
            lang_tokens.append(tid)
            lang_codes.append(code)

    # Non-speech tokens - tokens that shouldn't appear in speech output
    # This includes special markers and control tokens
    non_speech_tokens = [eot, sot, translate, transcribe, no_timestamps]
    # Also add language tokens as non-speech
    non_speech_tokens.extend(lang_tokens)

    # Add metadata
    meta_data = {
        "model_type": "whisper-small",
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
        # Token IDs for decoding
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
        # Language support
        "all_language_tokens": ",".join(map(str, lang_tokens)),
        "all_language_codes": ",".join(lang_codes),
        "non_speech_tokens": ",".join(map(str, non_speech_tokens)),
    }

    for key, value in meta_data.items():
        meta = model.metadata_props.add()
        meta.key = key
        meta.value = str(value)

    onnx.save(model, filename)


def export_tokens(processor, output_path: Path):
    """Export tokenizer vocabulary in sherpa-onnx tiktoken format (base64 encoded)."""
    import base64

    tokenizer = processor.tokenizer

    with open(output_path, "w", encoding="utf-8") as f:
        # Get the vocabulary
        vocab = tokenizer.get_vocab()
        # Sort by token ID
        sorted_vocab = sorted(vocab.items(), key=lambda x: x[1])

        for token, idx in sorted_vocab:
            # Base64 encode the token bytes (this is the tiktoken format)
            token_bytes = token.encode("utf-8")
            token_b64 = base64.b64encode(token_bytes).decode("ascii")
            f.write(f"{token_b64} {idx}\n")


def main():
    parser = argparse.ArgumentParser(
        description="Convert Whisper Cantonese model to sherpa-onnx format"
    )
    parser.add_argument(
        "--model-id",
        default="alvanlii/whisper-small-cantonese",
        help="HuggingFace model ID",
    )
    parser.add_argument(
        "--output-dir",
        default="./sherpa-onnx-whisper-small-cantonese",
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
