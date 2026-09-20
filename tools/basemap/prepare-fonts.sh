#!/usr/bin/env bash
set -euo pipefail

data_dir="${BUSNAV_BASEMAP_DATA_DIR:-${XDG_DATA_HOME:-$HOME/.local/share}/busnav/basemap}"
font_stack="Klokantech Noto Sans CJK Regular"
target="$data_dir/fonts/$font_stack"
if [[ -f "$target/12288-12543.pbf" ]]; then
  echo "Japanese Noto Sans glyphs already exist: $target"
  exit 0
fi

temporary_dir="$(mktemp -d)"
cleanup() {
  rm -rf "$temporary_dir"
}
trap cleanup EXIT

echo "Downloading open-licensed pre-generated Noto Sans glyphs..."
git clone --depth 1 --branch gh-pages https://github.com/openmaptiles/fonts.git "$temporary_dir/fonts"
source_dir="$temporary_dir/fonts/$font_stack"
if [[ ! -d "$source_dir" ]]; then
  echo "The OpenMapTiles fonts gh-pages layout did not contain $font_stack." >&2
  exit 1
fi
mkdir -p "$(dirname "$target")"
cp -R "$source_dir" "$target"
echo "Installed glyphs outside the repository: $target"
