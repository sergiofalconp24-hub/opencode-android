#!/data/data/com.termux/files/usr/bin/bash
# Levanta el servidor de opencode en el teléfono (Termux).
# La app "opencode" se conecta a http://127.0.0.1:4096

set -e

PORT="4096"
HOST="127.0.0.1"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --host) HOST="$2"; shift 2 ;;
    --port) PORT="$2"; shift 2 ;;
    *) echo "Uso: $0 [--host 127.0.0.1] [--port 4096]"; exit 1 ;;
  esac
done

if ! command -v opencode >/dev/null 2>&1; then
  echo "opencode no está instalado. Ejecuta: npm install -g opencode-ai"
  exit 1
fi

echo ">>> opencode serve en http://$HOST:$PORT"
echo ">>> Mantén esta sesión abierta; pulsa Ctrl-C para detener."
exec opencode serve --hostname "$HOST" --port "$PORT"