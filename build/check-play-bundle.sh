#!/usr/bin/env bash
set -euo pipefail

bundle=$1
arm_apk=$2
scratch=$(mktemp -d)
trap 'rm -rf "$scratch"' EXIT
tool="$scratch/bundletool.jar"
curl --fail --silent --show-error --location --retry 3 \
  https://github.com/google/bundletool/releases/download/1.18.3/bundletool-all-1.18.3.jar -o "$tool"
printf '%s  %s\n' a099cfa1543f55593bc2ed16a70a7c67fe54b1747bb7301f37fdfd6d91028e29 "$tool" | sha256sum --check
java -jar "$tool" validate --bundle="$bundle"
java -jar "$tool" dump config --bundle="$bundle" > "$scratch/bundle-config.json"
grep -q PAGE_ALIGNMENT_16K "$scratch/bundle-config.json"

# Verify actual native binaries in the bundle, including transitive ML Kit libraries.
unzip -q "$bundle" 'base/lib/*/*.so' -d "$scratch"
count=0
failed=0
while IFS= read -r -d '' lib; do
  count=$((count + 1))
  while read -r alignment; do
    if (( alignment < 16384 )); then
      echo "16 KB ELF alignment missing: ${lib#"$scratch/"} ($alignment)"
      failed=1
      break
    fi
  done < <(readelf -lW "$lib" | awk '$1=="LOAD" {print $NF}')
done < <(find "$scratch/base/lib" -type f \( -path '*/arm64-v8a/*.so' -o -path '*/x86_64/*.so' \) -print0)
test "$count" -gt 0
test "$failed" -eq 0
echo "Verified $count 64-bit libraries for 16 KB ELF alignment."

java -jar "$tool" build-apks --bundle="$bundle" --output="$scratch/app.apks"
java -jar "$tool" get-size total --apks="$scratch/app.apks" > "$scratch/download.csv"
cat "$scratch/download.csv"
python3 - "$scratch/download.csv" "$arm_apk" <<'PY'
import csv
import os
import sys
import zipfile

with open(sys.argv[1]) as stream:
    sizes = list(csv.DictReader(stream))
maximum = max(int(row["MAX"]) for row in sizes)
print(f"Largest estimated Play download: {maximum:,} bytes")
if maximum >= 200_000_000:
    sys.exit("Bundle exceeds the 200 MB base download limit.")
with zipfile.ZipFile(sys.argv[2]) as apk:
    libraries = {name.split("/")[1] for name in apk.namelist()
                 if name.startswith("lib/") and name.endswith(".so")}
    assert libraries == {"arm64-v8a"}, libraries
    assert "assets/siglip2-vision.onnx" in apk.namelist(), "Offline vision model missing"
print(f"Direct ARM64 APK: {os.path.getsize(sys.argv[2]):,} bytes")
PY
