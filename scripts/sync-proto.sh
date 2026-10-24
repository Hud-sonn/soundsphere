#!/bin/bash
# Copy the canonical Listen Together schema from the server repo.
#
# The app vendors proto/listentogether.proto so a build never needs a
# submodule or a third-party checkout. soundsphere-listen owns the schema;
# run this after changing the protocol there.
set -euo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="${1:-$ROOT/../soundsphere-listen/proto/listentogether.proto}"
DST="$ROOT/proto/listentogether.proto"

if [ ! -f "$SRC" ]; then
    echo "Missing proto at $SRC" >&2
    echo "Pass the path as the first argument if the server repo lives elsewhere." >&2
    exit 1
fi

cp "$SRC" "$DST"
echo "Synced $DST -> $SRC"
