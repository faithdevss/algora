#!/usr/bin/env bash
# Renders every Google Ads image asset in this folder from its .html source.
#
#   ./docs/ads/graphics/build.sh          # all assets
#   ./docs/ads/graphics/build.sh square   # only names matching "square"
#
# Needs Google Chrome and a network connection (the sources pull two web fonts).
#
# --allow-file-access-from-files is what lets the pages load the screenshots out
# of ../../store/screenshots. --default-background-color is deliberately not
# passed: the opaque body keeps every export RGB rather than RGBA.
set -euo pipefail

CHROME="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# Sources live here, exports land one level up in images/ — the PNGs are what
# gets uploaded, and keeping them out of the source folder makes that obvious.
OUT="$(cd "$DIR/.." && pwd)/images"
FILTER="${1:-}"

# name:width:height — the sizes Google Ads accepts for each asset slot.
ASSETS=(
  "ad-landscape-1200x628:1200:628"    # 1.91:1 landscape image, variant A
  "ad-square-1200x1200:1200:1200"     # 1:1 square image, variant A
  "ad-landscape-b-1200x628:1200:628"  # 1.91:1 landscape image, variant B
  "ad-square-b-1200x1200:1200:1200"   # 1:1 square image, variant B
  "ad-portrait-960x1200:960:1200"     # 4:5 portrait image
  "logo-square-1200x1200:1200:1200"   # 1:1 logo
  "logo-landscape-1200x300:1200:300"  # 4:1 logo
)

[ -x "$CHROME" ] || { echo "Chrome not found at $CHROME" >&2; exit 1; }
mkdir -p "$OUT"

for entry in "${ASSETS[@]}"; do
  IFS=: read -r name w h <<< "$entry"
  [ -n "$FILTER" ] && [[ "$name" != *"$FILTER"* ]] && continue

  echo "rendering $name (${w}x${h})"
  "$CHROME" \
    --headless --disable-gpu --hide-scrollbars \
    --allow-file-access-from-files \
    --force-device-scale-factor=1 \
    --window-size="$w,$h" \
    --virtual-time-budget=9000 \
    --screenshot="$OUT/$name.png" \
    "file://$DIR/$name.html" 2>/dev/null

  # Chrome exits 0 even when it wrote nothing useful, so check the result.
  [ -s "$OUT/$name.png" ] || { echo "  FAILED: no output" >&2; exit 1; }
  echo "  -> images/$name.png ($(du -h "$OUT/$name.png" | cut -f1))"
done
