#!/usr/bin/env bash
set -euo pipefail

data_dir="${BUSNAV_BASEMAP_DATA_DIR:-${XDG_DATA_HOME:-$HOME/.local/share}/busnav/basemap}"
font_stack="Klokantech Noto Sans CJK Regular"
revision="025ff2b2f84cc0fdf11f7b1d74b3a784595fe7a4"
archive_sha256="c8106d0af721bbb6adf55005fc4a9ab4ac2d5ade7b9fd8a26ab21530951b7b2f"
target="$data_dir/fonts/$font_stack"
manifest="$data_dir/fonts/glyphs-$revision.sha256"
if [[ -f "$manifest" ]] && (cd "$data_dir/fonts" && sha256sum --status -c "$(basename "$manifest")"); then
  echo "Verified pinned Japanese glyphs: $revision"
  exit 0
fi

temporary_dir="$(mktemp -d)"
trap 'rm -rf "$temporary_dir"' EXIT
curl -fL --retry 2 "https://codeload.github.com/openmaptiles/fonts/tar.gz/$revision" -o "$temporary_dir/fonts.tar.gz"
echo "$archive_sha256  $temporary_dir/fonts.tar.gz" | sha256sum -c -
tar -xzf "$temporary_dir/fonts.tar.gz" -C "$temporary_dir"
source_dir="$temporary_dir/fonts-$revision/$font_stack"
test -f "$source_dir/12288-12543.pbf"
mkdir -p "$target"
cp "$source_dir/"*.pbf "$target/"
(cd "$data_dir/fonts" && sha256sum "$font_stack/"*.pbf) > "$manifest"
echo "Installed verified OpenMapTiles Noto Sans glyphs ($revision) outside the repository."
