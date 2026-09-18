#!/usr/bin/env bash
# Sube los cambios a GitHub sin los errores de siempre.
#
#   ./subir.sh "qué cambiaste"
#
# Qué hace, en orden:
#  1. Guarda (commit) TODO lo modificado, CLAUDE.md incluido. Si CLAUDE.md queda
#     sin commitear, el próximo pull lo choca ("would be overwritten by merge").
#  2. Baja lo nuevo de GitHub con rebase: tus commits se acomodan ENCIMA de lo que
#     subieron los demás (los merge de pull requests). Sin esto el push se rechaza
#     con "non-fast-forward".
#  3. Sube.
# Si aparece un conflicto en código (dos personas cambiaron las mismas líneas),
# git se detiene y lo muestra: eso sí hay que resolverlo a mano.
set -euo pipefail
cd "$(dirname "$0")"

mensaje="${1:-}"
rama="$(git rev-parse --abbrev-ref HEAD)"

# Freno de seguridad: archivos que pueden tener claves NO se suben nunca.
# (application.properties es la base sin claves; los demas perfiles van en .gitignore,
# pero un nombre mal escrito como "aplication-jarv.properties" se escaparía).
sospechosos="$(git status --porcelain --untracked-files=all \
  | sed 's/^...//' \
  | grep -E '\.properties$|(^|/)\.env' \
  | grep -vx 'src/main/resources/application.properties' || true)"
if [ -n "$sospechosos" ]; then
  echo "Frené: estos archivos pueden tener claves y no se suben:"
  echo "$sospechosos"
  echo "Revisalos (¿nombre mal escrito? ¿falta en .gitignore?) y volvé a correr."
  exit 1
fi

if [ -n "$(git status --porcelain)" ]; then
  if [ -z "$mensaje" ]; then
    echo 'Hay cambios sin guardar. Uso: ./subir.sh "qué cambiaste"'
    exit 1
  fi
  git add -A
  git commit -m "$mensaje"
fi

git pull --rebase --autostash origin "$rama"
git push origin "$rama"
echo "Listo: '$rama' subida a GitHub."
