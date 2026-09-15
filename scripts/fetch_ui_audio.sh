#!/usr/bin/env bash
set -euo pipefail
OUT="mobile/src/main/res/raw"; mkdir -p "$OUT"
declare -A SFX=(
  [ui_click]="https://sfxmint.com/dl/ui-click-02.wav"
  [ui_hover]="https://sfxmint.com/dl/ui-hover-06.wav"
  [ui_open]="https://sfxmint.com/dl/ui-modal-07.wav"
  [ui_close]="https://sfxmint.com/dl/ui-menu-07.wav"
)
for name in "${!SFX[@]}"; do echo "Baixando $name"; curl -L --fail --silent --show-error "${SFX[$name]}" -o "$OUT/$name.wav"; done
echo "Áudios CC0 instalados em $OUT"
