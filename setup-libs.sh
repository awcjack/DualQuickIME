#!/bin/bash
# Download sherpa-onnx AAR for local development
# This script downloads the official k2-fsa sherpa-onnx AAR which includes VAD support

set -e

SHERPA_VERSION="1.12.34"
LIBS_DIR="app/libs"
AAR_FILE="sherpa-onnx-${SHERPA_VERSION}.aar"
AAR_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/v${SHERPA_VERSION}/${AAR_FILE}"

echo "Setting up sherpa-onnx library..."

# Create libs directory if it doesn't exist
mkdir -p "$LIBS_DIR"

# Check if AAR already exists
if [ -f "$LIBS_DIR/$AAR_FILE" ]; then
    echo "AAR already exists at $LIBS_DIR/$AAR_FILE"
    echo "Delete it manually if you want to re-download."
    exit 0
fi

echo "Downloading sherpa-onnx AAR (v${SHERPA_VERSION})..."
echo "URL: $AAR_URL"

curl -L -o "$LIBS_DIR/$AAR_FILE" "$AAR_URL"

echo ""
echo "Download complete!"
ls -lh "$LIBS_DIR/$AAR_FILE"
echo ""
echo "You can now build the project with: ./gradlew assembleDebug"
