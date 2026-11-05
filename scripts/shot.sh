#!/usr/bin/env bash
# Emulator screenshot downscaled to 720 px on the long side (about a third of the image tokens of full size).
# Prefer scripts/ui.sh when checking text. Usage: scripts/shot.sh [out.png] (default: build/shot.png)
source "$(dirname "$0")/_common.sh"
out="${1:-build/shot.png}"; mkdir -p "$(dirname "$out")"
"${ADB[@]}" exec-out screencap -p > "$out.full" || exit 1
win() { cygpath -w "$1" 2>/dev/null || echo "$1"; }
powershell.exe -NoProfile -Command "
Add-Type -AssemblyName System.Drawing
\$src = [System.Drawing.Image]::FromFile('$(win "$out.full")')
\$s = 720 / [Math]::Max(\$src.Width, \$src.Height)
\$dst = New-Object System.Drawing.Bitmap([int](\$src.Width*\$s), [int](\$src.Height*\$s))
\$g = [System.Drawing.Graphics]::FromImage(\$dst)
\$g.InterpolationMode = 'HighQualityBicubic'
\$g.DrawImage(\$src, 0, 0, \$dst.Width, \$dst.Height)
\$src.Dispose(); \$dst.Save('$(win "$out")'); \$dst.Dispose()" && rm -f "$out.full" && echo "$out"
