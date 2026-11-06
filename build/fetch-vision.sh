#!/usr/bin/env bash
set -euo pipefail
target="app/src/main/assets/siglip2-vision.onnx"
digest="5f2b401c1a4fc095702a5d45348e17ad46c4f87064085365b43c6e8eaa5c0070"
mkdir -p "$(dirname "$target")"
if [ -f "$target" ] && echo "$digest  $target" | shasum -a 256 -c - >/dev/null 2>&1; then exit 0; fi
curl --fail --location --retry 3 'https://huggingface.co/onnx-community/siglip2-base-patch16-224-ONNX/resolve/ba1f3b0843f24bc5417d38e19c37b287d719b2f4/onnx/vision_model_quantized.onnx' -o "$target.download"
echo "$digest  $target.download" | shasum -a 256 -c -
mv "$target.download" "$target"
