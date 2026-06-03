#!/usr/bin/env bash
set -euo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
HOST="${HOST:-http://localhost:8000}"
UID_VAL="${UID_VAL:-$(date +%s)$$}"

echo "Running Hurl tests against $HOST with uid=$UID_VAL"

FILES=("$@")
if [ ${#FILES[@]} -eq 0 ]; then
  FILES=()
  for file in "$DIR"/*.hurl "$DIR"/commerce/*.hurl; do
    [ "$file" = "$DIR/commerce/webhook.hurl" ] && continue
    FILES+=("$file")
  done
fi

hurl --test \
  --jobs 1 \
  --variable "host=$HOST" \
  --variable "uid=$UID_VAL" \
  "${FILES[@]}"
