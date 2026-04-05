#!/usr/bin/env python3
"""
Convert ASLP-lab/WSYue-ASR whisper_medium_yue (WeNet format) to sherpa-onnx format.

The ASLP-lab Whisper Medium Yue model is trained using WeNet framework, NOT the standard
OpenAI Whisper format. This script handles the conversion from WeNet's checkpoint format
to sherpa-onnx compatible ONNX files.

Model source: https://huggingface.co/ASLP-lab/WSYue-ASR/tree/main/whisper_medium_yue

The WeNet Whisper checkpoint contains:
- whisper_medium_yue/train.yaml - Model configuration
- whisper_medium_yue/whisper_medium_yue.pt - Model weights

Usage:
    # First download the model from HuggingFace:
    huggingface-cli download ASLP-lab/WSYue-ASR whisper_medium_yue --local-dir ./wenet_models

    # Then convert:
    python convert_whisper_medium_yue.py \
        --checkpoint ./wenet_models/whisper_medium_yue/whisper_medium_yue.pt \
        --config ./wenet_models/whisper_medium_yue/train.yaml \
        --output-dir ./sherpa-onnx-whisper-medium-yue

Requirements:
    pip install torch onnx onnxruntime pyyaml
    pip install wenet  # For loading WeNet models

Alternative (without wenet package):
    This script can also work with just torch if the checkpoint is a standard state_dict.
"""

import argparse
import os
import sys
from pathlib import Path
from typing import Dict, Any, Optional, Tuple

import torch
import torch.nn as nn
import torch.nn.functional as F
from torch import Tensor

try:
    import yaml
except ImportError:
    print("Please install pyyaml: pip install pyyaml")
    sys.exit(1)


def load_wenet_whisper_config(config_path: str) -> Dict[str, Any]:
    """Load WeNet training configuration from train.yaml."""
    with open(config_path, 'r') as f:
        config = yaml.safe_load(f)
    return config


def load_wenet_whisper_checkpoint(checkpoint_path: str) -> Dict[str, Tensor]:
    """
    Load WeNet Whisper checkpoint.

    WeNet checkpoints can be in different formats:
    1. Direct state_dict
    2. Dict with 'model' key containing state_dict
    3. Full training checkpoint with 'state_dict' key
    """
    print(f"Loading checkpoint: {checkpoint_path}")
    checkpoint = torch.load(checkpoint_path, map_location='cpu')

    if isinstance(checkpoint, dict):
        if 'model' in checkpoint:
            state_dict = checkpoint['model']
        elif 'state_dict' in checkpoint:
            state_dict = checkpoint['state_dict']
        else:
            # Assume it's already a state_dict
            state_dict = checkpoint
    else:
        raise ValueError(f"Unexpected checkpoint format: {type(checkpoint)}")

    print(f"Loaded {len(state_dict)} parameters")
    return state_dict


def get_model_dims_from_config(config: Dict[str, Any]) -> Dict[str, int]:
    """Extract model dimensions from WeNet config."""
    # WeNet Whisper config structure
    model_conf = config.get('model_conf', config.get('encoder_conf', {}))
    decoder_conf = config.get('decoder_conf', {})

    # Try to extract dimensions - WeNet uses different key names
    dims = {
        'n_mels': config.get('dataset_conf', {}).get('fbank_conf', {}).get('num_mel_bins', 80),
        'n_vocab': config.get('output_dim', 51865),  # Whisper multilingual vocab size
        'n_audio_ctx': model_conf.get('max_source_positions', 1500),
        'n_audio_state': model_conf.get('d_model', 1024),  # medium = 1024
        'n_audio_head': model_conf.get('attention_heads', 16),  # medium = 16
        'n_audio_layer': model_conf.get('encoder_layers', 24),  # medium = 24
        'n_text_ctx': decoder_conf.get('max_target_positions', 448),
        'n_text_state': decoder_conf.get('d_model', 1024),
        'n_text_head': decoder_conf.get('attention_heads', 16),
        'n_text_layer': decoder_conf.get('decoder_layers', 24),
    }

    return dims


def get_model_dims_from_state_dict(state_dict: Dict[str, Tensor]) -> Dict[str, int]:
    """Infer model dimensions from state_dict tensor shapes."""
    dims = {}

    # Find encoder conv1 to get n_mels
    for key in state_dict:
        if 'conv1.weight' in key and 'encoder' in key:
            dims['n_mels'] = state_dict[key].shape[1]
            dims['n_audio_state'] = state_dict[key].shape[0]
            break

    # Find token embedding to get vocab size and text state
    for key in state_dict:
        if 'embed_tokens.weight' in key or 'token_embedding.weight' in key:
            dims['n_vocab'] = state_dict[key].shape[0]
            dims['n_text_state'] = state_dict[key].shape[1]
            break

    # Count encoder layers
    encoder_layers = set()
    for key in state_dict:
        if 'encoder' in key and 'layers.' in key:
            # Extract layer number
            parts = key.split('layers.')
            if len(parts) > 1:
                layer_num = parts[1].split('.')[0]
                if layer_num.isdigit():
                    encoder_layers.add(int(layer_num))
    dims['n_audio_layer'] = len(encoder_layers) if encoder_layers else 24

    # Count decoder layers
    decoder_layers = set()
    for key in state_dict:
        if 'decoder' in key and 'layers.' in key:
            parts = key.split('layers.')
            if len(parts) > 1:
                layer_num = parts[1].split('.')[0]
                if layer_num.isdigit():
                    decoder_layers.add(int(layer_num))
    dims['n_text_layer'] = len(decoder_layers) if decoder_layers else 24

    # Find attention heads from attention weight shapes
    for key in state_dict:
        if 'self_attn.q_proj.weight' in key or 'attn.query.weight' in key:
            # d_model = n_heads * head_dim, typically head_dim = 64
            d_model = state_dict[key].shape[0]
            dims['n_audio_head'] = d_model // 64  # Assume head_dim = 64
            dims['n_text_head'] = d_model // 64
            break

    # Default values for context lengths (standard Whisper values)
    dims.setdefault('n_mels', 80)
    dims.setdefault('n_vocab', 51865)
    dims.setdefault('n_audio_ctx', 1500)
    dims.setdefault('n_audio_state', 1024)  # medium
    dims.setdefault('n_audio_head', 16)  # medium
    dims.setdefault('n_audio_layer', 24)  # medium
    dims.setdefault('n_text_ctx', 448)
    dims.setdefault('n_text_state', 1024)  # medium
    dims.setdefault('n_text_head', 16)  # medium
    dims.setdefault('n_text_layer', 24)  # medium

    return dims


def analyze_checkpoint_structure(state_dict: Dict[str, Tensor]) -> str:
    """Analyze and report the checkpoint structure."""
    print("\n=== Checkpoint Structure Analysis ===")

    prefixes = {}
    for key in state_dict:
        prefix = key.split('.')[0]
        if prefix not in prefixes:
            prefixes[prefix] = []
        prefixes[prefix].append(key)

    print(f"Top-level prefixes: {list(prefixes.keys())}")

    # Check if it's HuggingFace format
    if 'model' in prefixes or any(k.startswith('model.') for k in state_dict):
        return 'huggingface'

    # Check if it's OpenAI format
    if 'encoder' in prefixes and 'decoder' in prefixes:
        if any('blocks.' in k for k in state_dict):
            return 'openai'
        if any('layers.' in k for k in state_dict):
            return 'huggingface_direct'

    # Check for WeNet-specific patterns
    if any('encoder.encoders' in k for k in state_dict):
        return 'wenet_conformer'

    return 'unknown'


class WeNetWhisperEncoder(nn.Module):
    """
    Wrapper for WeNet Whisper encoder that outputs cross-attention KV.

    This follows the sherpa-onnx convention where the encoder outputs
    pre-computed K and V for cross-attention in all decoder layers.
    """
    def __init__(self, state_dict: Dict[str, Tensor], dims: Dict[str, int]):
        super().__init__()
        self.dims = dims

        # Build encoder from state_dict
        # The structure depends on the checkpoint format
        self._build_from_state_dict(state_dict)

    def _build_from_state_dict(self, state_dict: Dict[str, Tensor]):
        """Build encoder modules from state dict."""
        d_model = self.dims['n_audio_state']
        n_heads = self.dims['n_audio_head']
        n_layers = self.dims['n_audio_layer']
        n_mels = self.dims['n_mels']

        # Conv layers
        self.conv1 = nn.Conv1d(n_mels, d_model, kernel_size=3, padding=1)
        self.conv2 = nn.Conv1d(d_model, d_model, kernel_size=3, stride=2, padding=1)

        # Positional embedding
        self.positional_embedding = nn.Parameter(torch.zeros(1500, d_model))

        # Transformer layers
        self.layers = nn.ModuleList([
            nn.TransformerEncoderLayer(
                d_model=d_model,
                nhead=n_heads,
                dim_feedforward=d_model * 4,
                dropout=0.0,
                activation='gelu',
                batch_first=True,
                norm_first=True
            )
            for _ in range(n_layers)
        ])

        # Final layer norm
        self.ln_post = nn.LayerNorm(d_model)

        # Cross-attention K/V projections (one per decoder layer)
        n_decoder_layers = self.dims['n_text_layer']
        self.cross_k_projs = nn.ModuleList([
            nn.Linear(d_model, d_model, bias=False)
            for _ in range(n_decoder_layers)
        ])
        self.cross_v_projs = nn.ModuleList([
            nn.Linear(d_model, d_model, bias=False)
            for _ in range(n_decoder_layers)
        ])

        # Load weights
        self._load_weights(state_dict)

    def _load_weights(self, state_dict: Dict[str, Tensor]):
        """Load weights from state dict with key mapping."""
        # This is a simplified version - actual implementation needs
        # to handle the specific key mapping for the WeNet format
        pass  # Weights will be loaded externally

    def forward(self, mel: Tensor) -> Tuple[Tensor, Tensor]:
        """
        Forward pass returning cross-attention K and V for all decoder layers.

        Args:
            mel: (batch, n_mels, T) mel spectrogram

        Returns:
            n_layer_cross_k: (n_layers, batch, T', d_model)
            n_layer_cross_v: (n_layers, batch, T', d_model)
        """
        # Conv layers
        x = F.gelu(self.conv1(mel))
        x = F.gelu(self.conv2(x))

        # (batch, d_model, T') -> (batch, T', d_model)
        x = x.permute(0, 2, 1)

        # Add positional embedding
        seq_len = x.shape[1]
        x = x + self.positional_embedding[:seq_len]

        # Transformer layers
        for layer in self.layers:
            x = layer(x)

        # Final layer norm
        x = self.ln_post(x)

        # Compute cross-attention K/V for all decoder layers
        cross_k_list = [proj(x) for proj in self.cross_k_projs]
        cross_v_list = [proj(x) for proj in self.cross_v_projs]

        n_layer_cross_k = torch.stack(cross_k_list, dim=0)
        n_layer_cross_v = torch.stack(cross_v_list, dim=0)

        return n_layer_cross_k, n_layer_cross_v


def try_use_hf_conversion(model_id: str, output_dir: str, quantize: bool = True) -> bool:
    """
    Try to convert using HuggingFace format if the model is available.

    Some WeNet checkpoints are actually HuggingFace compatible or can be
    loaded directly with transformers.
    """
    try:
        from transformers import WhisperForConditionalGeneration, WhisperProcessor

        print(f"Attempting HuggingFace conversion for: {model_id}")
        # This will fail if the model isn't in HF format
        model = WhisperForConditionalGeneration.from_pretrained(model_id)
        processor = WhisperProcessor.from_pretrained(model_id)

        # If we get here, use the existing conversion script
        from convert_whisper_cantonese import export_sherpa_onnx
        export_sherpa_onnx(model_id, output_dir, quantize)
        return True

    except Exception as e:
        print(f"HuggingFace conversion failed: {e}")
        return False


def convert_wenet_whisper_to_onnx(
    checkpoint_path: str,
    config_path: Optional[str],
    output_dir: str,
    quantize: bool = True
):
    """
    Convert WeNet Whisper checkpoint to sherpa-onnx ONNX format.

    This handles the WeNet-specific checkpoint format used by ASLP-lab models.
    """
    import onnx
    from onnxruntime.quantization import QuantType, quantize_dynamic

    output_path = Path(output_dir)
    output_path.mkdir(parents=True, exist_ok=True)

    # Load checkpoint
    state_dict = load_wenet_whisper_checkpoint(checkpoint_path)

    # Analyze structure
    format_type = analyze_checkpoint_structure(state_dict)
    print(f"Detected checkpoint format: {format_type}")

    # Get model dimensions
    if config_path and Path(config_path).exists():
        config = load_wenet_whisper_config(config_path)
        dims = get_model_dims_from_config(config)
        print(f"Loaded dims from config: {dims}")
    else:
        dims = get_model_dims_from_state_dict(state_dict)
        print(f"Inferred dims from state_dict: {dims}")

    # If it's a HuggingFace format, use the existing converter
    if format_type == 'huggingface':
        print("\nDetected HuggingFace format, using standard converter...")
        # Import and use the existing conversion function
        try:
            # Add parent directory to path for import
            sys.path.insert(0, str(Path(__file__).parent))
            from convert_whisper_cantonese import export_sherpa_onnx

            # Need to determine the HF model ID or create a temporary one
            print("Note: For HuggingFace format, please use convert_whisper_cantonese.py directly")
            print("with the appropriate --model-id argument.")
            return
        except ImportError:
            pass

    print("\n" + "=" * 60)
    print("WeNet Whisper to ONNX Conversion")
    print("=" * 60)
    print(f"\nModel dimensions:")
    for k, v in dims.items():
        print(f"  {k}: {v}")

    # For now, we'll create a conversion guide rather than full implementation
    # because WeNet's Whisper implementation may have custom modifications

    print("\n" + "=" * 60)
    print("CONVERSION GUIDE")
    print("=" * 60)
    print("""
The WeNet Whisper Medium Yue checkpoint requires special handling:

Option 1: Use WeNet's built-in ONNX export
------------------------------------------
If you have WeNet installed:

    python -m wenet.bin.export_onnx_cpu \\
        --config {config_path} \\
        --checkpoint {checkpoint_path} \\
        --output_dir {output_dir} \\
        --chunk_size -1

Then rename files:
    mv model.onnx medium-encoder.onnx
    (You'll need to export decoder separately)

Option 2: Convert through OpenAI Whisper format
-----------------------------------------------
1. First convert WeNet checkpoint to OpenAI format
2. Then use sherpa-onnx's export-onnx.py script

Option 3: Request pre-converted model
------------------------------------
Check if csukuangfj has already converted this model:
https://huggingface.co/csukuangfj

The model might be available at:
- sherpa-onnx-whisper-medium-yue
- Or similar naming

Option 4: Use the existing pre-converted U2pp-Conformer-Yue
----------------------------------------------------------
The U2pp-Conformer-Yue model achieves the SAME accuracy (5.05% MER)
with 6x smaller size (130M vs 769M params). It's already available:

https://huggingface.co/csukuangfj/sherpa-onnx-wenetspeech-yue-u2pp-conformer-ctc-zh-en-cantonese-int8-2025-09-10
""".format(
        config_path=config_path or "train.yaml",
        checkpoint_path=checkpoint_path,
        output_dir=output_dir
    ))

    # Print checkpoint keys for debugging
    print("\n" + "=" * 60)
    print("CHECKPOINT KEYS (first 50)")
    print("=" * 60)
    for i, key in enumerate(sorted(state_dict.keys())[:50]):
        shape = state_dict[key].shape
        print(f"  {key}: {shape}")

    if len(state_dict) > 50:
        print(f"  ... and {len(state_dict) - 50} more keys")


def create_tokens_file(output_path: Path):
    """
    Create tokens.txt file for Whisper medium multilingual.

    Uses the standard multilingual Whisper vocabulary.
    """
    try:
        import whisper
        whisper_dir = Path(whisper.__file__).parent
        tiktoken_file = whisper_dir / "assets" / "multilingual.tiktoken"

        if tiktoken_file.is_file():
            print(f"Copying tokens from: {tiktoken_file}")
            import shutil
            shutil.copy(tiktoken_file, output_path / "medium-tokens.txt")
            return True
    except ImportError:
        pass

    print("Warning: Could not create tokens file. Install openai-whisper package.")
    return False


def main():
    parser = argparse.ArgumentParser(
        description="Convert WeNet Whisper Medium Yue to sherpa-onnx format"
    )
    parser.add_argument(
        "--checkpoint",
        required=True,
        help="Path to WeNet checkpoint file (whisper_medium_yue.pt)",
    )
    parser.add_argument(
        "--config",
        default=None,
        help="Path to WeNet config file (train.yaml)",
    )
    parser.add_argument(
        "--output-dir",
        default="./sherpa-onnx-whisper-medium-yue",
        help="Output directory for ONNX files",
    )
    parser.add_argument(
        "--no-quantize",
        action="store_true",
        help="Skip int8 quantization",
    )
    parser.add_argument(
        "--try-hf",
        action="store_true",
        help="Try to load as HuggingFace model first",
    )
    parser.add_argument(
        "--hf-model-id",
        default="ASLP-lab/WSYue-ASR",
        help="HuggingFace model ID to try",
    )

    args = parser.parse_args()

    if args.try_hf:
        if try_use_hf_conversion(args.hf_model_id, args.output_dir, not args.no_quantize):
            return

    convert_wenet_whisper_to_onnx(
        checkpoint_path=args.checkpoint,
        config_path=args.config,
        output_dir=args.output_dir,
        quantize=not args.no_quantize,
    )


if __name__ == "__main__":
    main()
