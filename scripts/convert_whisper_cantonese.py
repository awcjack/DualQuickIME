#!/usr/bin/env python3
"""
Convert alvanlii/whisper-small-cantonese to sherpa-onnx format.

This script exports the fine-tuned Whisper Cantonese model to ONNX format
compatible with sherpa-onnx for use in DualQuickIME.

Usage:
    python convert_whisper_cantonese.py --output-dir ./sherpa-onnx-whisper-small-cantonese

Requirements:
    pip install torch transformers onnx onnxruntime onnxscript optimum[onnxruntime]
"""

import argparse
import os
import warnings
from pathlib import Path


def export_to_onnx(model_id: str, output_dir: str, quantize: bool = True):
    """Export HuggingFace Whisper model to sherpa-onnx compatible ONNX format."""

    print(f"Loading model: {model_id}")

    # Import here to allow --help without dependencies
    import torch
    from transformers import WhisperForConditionalGeneration, WhisperProcessor

    output_path = Path(output_dir)
    output_path.mkdir(parents=True, exist_ok=True)

    # Load the model and processor
    model = WhisperForConditionalGeneration.from_pretrained(model_id)
    processor = WhisperProcessor.from_pretrained(model_id)

    model.eval()

    # Export encoder
    print("Exporting encoder...")
    export_encoder(model, output_path)

    # Export decoder
    print("Exporting decoder...")
    export_decoder(model, output_path)

    # Export tokens
    print("Exporting tokens...")
    export_tokens(processor, output_path)

    # Quantize if requested
    if quantize:
        print("Quantizing models to int8...")
        quantize_models(output_path)

    print(f"\nConversion complete! Output files in: {output_path}")
    print("\nFiles created:")
    for f in sorted(output_path.glob("*")):
        if f.is_file():
            size_mb = f.stat().st_size / (1024 * 1024)
            print(f"  {f.name}: {size_mb:.1f} MB")


def export_encoder(model, output_path: Path):
    """Export the Whisper encoder to ONNX."""
    import torch

    encoder = model.get_encoder()

    # Create dummy input (batch_size=1, 80 mel bins, 3000 frames = 30 seconds)
    # Note: Whisper encoder expects fixed 3000 frames (30 seconds of audio)
    dummy_input = torch.randn(1, 80, 3000)

    encoder_path = output_path / "small-encoder.onnx"

    # Use legacy exporter to avoid dynamic shape issues with newer PyTorch
    with warnings.catch_warnings():
        warnings.simplefilter("ignore")
        torch.onnx.export(
            encoder,
            dummy_input,
            str(encoder_path),
            input_names=["mel"],
            output_names=["encoder_out"],
            # Only batch_size is dynamic; mel_length is fixed at 3000 for Whisper
            dynamic_axes={
                "mel": {0: "batch_size"},
                "encoder_out": {0: "batch_size"},
            },
            opset_version=14,
            do_constant_folding=True,
            export_params=True,
            dynamo=False,  # Use legacy exporter
        )

    print(f"  Saved: {encoder_path}")


def export_decoder(model, output_path: Path):
    """Export the Whisper decoder to ONNX."""
    import torch

    # For sherpa-onnx, we need to export the decoder with cross-attention
    # This is more complex and requires custom wrapper

    class DecoderWrapper(torch.nn.Module):
        def __init__(self, decoder, proj_out):
            super().__init__()
            self.decoder = decoder
            self.proj_out = proj_out

        def forward(self, input_ids, encoder_hidden_states):
            # Get decoder outputs
            decoder_outputs = self.decoder(
                input_ids=input_ids,
                encoder_hidden_states=encoder_hidden_states,
                return_dict=True,
            )
            # Project to vocabulary
            lm_logits = self.proj_out(decoder_outputs.last_hidden_state)
            return lm_logits

    decoder_wrapper = DecoderWrapper(model.model.decoder, model.proj_out)
    decoder_wrapper.eval()

    # Dummy inputs
    batch_size = 1
    seq_len = 4
    encoder_len = 1500
    d_model = model.config.d_model

    dummy_input_ids = torch.zeros(batch_size, seq_len, dtype=torch.long)
    dummy_encoder_out = torch.randn(batch_size, encoder_len, d_model)

    decoder_path = output_path / "small-decoder.onnx"

    # Use legacy exporter
    with warnings.catch_warnings():
        warnings.simplefilter("ignore")
        torch.onnx.export(
            decoder_wrapper,
            (dummy_input_ids, dummy_encoder_out),
            str(decoder_path),
            input_names=["input_ids", "encoder_out"],
            output_names=["logits"],
            dynamic_axes={
                "input_ids": {0: "batch_size", 1: "seq_len"},
                "encoder_out": {0: "batch_size", 1: "encoder_len"},
                "logits": {0: "batch_size", 1: "seq_len"},
            },
            opset_version=14,
            do_constant_folding=True,
            export_params=True,
            dynamo=False,  # Use legacy exporter
        )

    print(f"  Saved: {decoder_path}")


def export_tokens(processor, output_path: Path):
    """Export the tokenizer vocabulary to tokens.txt format."""

    tokenizer = processor.tokenizer
    tokens_path = output_path / "small-tokens.txt"

    with open(tokens_path, "w", encoding="utf-8") as f:
        for i in range(tokenizer.vocab_size):
            token = tokenizer.convert_ids_to_tokens(i)
            if token is None:
                token = f"<unk_{i}>"
            # Escape special characters
            token = token.replace(" ", "▁")  # Use SentencePiece convention
            f.write(f"{token} {i}\n")

    print(f"  Saved: {tokens_path}")


def quantize_models(output_path: Path):
    """Quantize ONNX models to int8."""
    from onnxruntime.quantization import quantize_dynamic, QuantType

    for name in ["small-encoder", "small-decoder"]:
        input_path = output_path / f"{name}.onnx"
        output_path_q = output_path / f"{name}.int8.onnx"

        if input_path.exists():
            quantize_dynamic(
                str(input_path),
                str(output_path_q),
                weight_type=QuantType.QInt8,
            )
            print(f"  Quantized: {output_path_q}")

            # Remove original to save space
            input_path.unlink()
            print(f"  Removed: {input_path}")


def main():
    parser = argparse.ArgumentParser(
        description="Convert Whisper Cantonese model to sherpa-onnx format"
    )
    parser.add_argument(
        "--model-id",
        default="alvanlii/whisper-small-cantonese",
        help="HuggingFace model ID (default: alvanlii/whisper-small-cantonese)",
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

    export_to_onnx(
        model_id=args.model_id,
        output_dir=args.output_dir,
        quantize=not args.no_quantize,
    )


if __name__ == "__main__":
    main()
