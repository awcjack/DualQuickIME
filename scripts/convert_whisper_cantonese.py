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
import shutil
import subprocess
import sys
from pathlib import Path


def export_to_onnx(model_id: str, output_dir: str, quantize: bool = True):
    """Export HuggingFace Whisper model to sherpa-onnx compatible ONNX format."""

    print(f"Converting model: {model_id}")

    output_path = Path(output_dir)
    output_path.mkdir(parents=True, exist_ok=True)

    # Use optimum-cli for reliable Whisper ONNX export
    temp_dir = output_path / "temp_optimum"

    print("Step 1: Exporting with optimum-cli...")
    cmd = [
        sys.executable, "-m", "optimum.exporters.onnx",
        "--model", model_id,
        "--task", "automatic-speech-recognition",
        str(temp_dir)
    ]

    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        print(f"Error during optimum export:\n{result.stderr}")
        raise RuntimeError("optimum export failed")

    print("  Export complete")

    # List what was exported
    print("\n  Exported files:")
    for f in sorted(temp_dir.glob("*.onnx")):
        size_mb = f.stat().st_size / (1024 * 1024)
        print(f"    {f.name}: {size_mb:.1f} MB")

    # Rename/copy files to sherpa-onnx naming convention
    print("\nStep 2: Preparing sherpa-onnx format...")

    encoder_src = temp_dir / "encoder_model.onnx"
    decoder_src = temp_dir / "decoder_model.onnx"

    # Check for merged decoder (some models have this)
    if not decoder_src.exists():
        decoder_src = temp_dir / "decoder_model_merged.onnx"
    if not decoder_src.exists():
        decoder_src = temp_dir / "decoder_with_past_model.onnx"

    encoder_dst = output_path / "small-encoder.onnx"
    decoder_dst = output_path / "small-decoder.onnx"

    if encoder_src.exists():
        shutil.copy(encoder_src, encoder_dst)
        print(f"  Copied encoder: {encoder_dst}")
    else:
        print(f"  Warning: encoder not found at {encoder_src}")
        # List available files
        print("  Available files:", list(temp_dir.glob("*.onnx")))

    if decoder_src.exists():
        shutil.copy(decoder_src, decoder_dst)
        print(f"  Copied decoder: {decoder_dst}")
    else:
        print(f"  Warning: decoder not found, checking alternatives...")
        for f in temp_dir.glob("*decoder*.onnx"):
            shutil.copy(f, decoder_dst)
            print(f"  Copied {f.name} as decoder: {decoder_dst}")
            break

    # Export tokens
    print("\nStep 3: Exporting tokens...")
    export_tokens(model_id, output_path)

    # Quantize if requested
    if quantize:
        print("\nStep 4: Quantizing models to int8...")
        quantize_models(output_path)

    # Cleanup temp directory
    print("\nStep 5: Cleaning up...")
    shutil.rmtree(temp_dir)
    print("  Removed temporary files")

    print(f"\n✓ Conversion complete! Output files in: {output_path}")
    print("\nFinal files:")
    for f in sorted(output_path.glob("*")):
        if f.is_file():
            size_mb = f.stat().st_size / (1024 * 1024)
            print(f"  {f.name}: {size_mb:.1f} MB")


def export_tokens(model_id: str, output_path: Path):
    """Export the tokenizer vocabulary to tokens.txt format."""
    from transformers import WhisperProcessor

    processor = WhisperProcessor.from_pretrained(model_id)
    tokenizer = processor.tokenizer
    tokens_path = output_path / "small-tokens.txt"

    with open(tokens_path, "w", encoding="utf-8") as f:
        for i in range(tokenizer.vocab_size):
            token = tokenizer.convert_ids_to_tokens(i)
            if token is None:
                token = f"<unk_{i}>"
            # Escape special characters for sherpa-onnx
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
            print(f"  Quantizing {name}...")
            quantize_dynamic(
                str(input_path),
                str(output_path_q),
                weight_type=QuantType.QInt8,
            )
            print(f"    Created: {output_path_q}")

            # Remove original to save space
            input_path.unlink()
            print(f"    Removed: {input_path}")
        else:
            print(f"  Warning: {input_path} not found, skipping quantization")


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
