#!/bin/bash
# ─────────────────────────────────────────────────────────────────────────────
# start.sh  —  Start backend + frontend with a single command.
#
# Usage:
#   bash start.sh          (or: npm start from project root)
#
# Stops both servers cleanly on Ctrl+C.
# ─────────────────────────────────────────────────────────────────────────────

ROOT="$(cd "$(dirname "$0")" && pwd)"

# Load .env if present
if [ -f "$ROOT/.env" ]; then
  export $(grep -v '^#' "$ROOT/.env" | xargs)
fi

# Defaults
export DB_HOST="${DB_HOST:-localhost}"
export DB_USER="${DB_USER:-root}"
export DB_PASSWORD="${DB_PASSWORD:-root12345}"
export DB_NAME="${DB_NAME:-ambulance_optimization}"
export JWT_SECRET="${JWT_SECRET:-ambulance_route_optimization_secret_key_2024_secure}"
export PORT="${PORT:-5001}"

echo ""
echo "╔══════════════════════════════════════════════════════╗"
echo "║   🚑  Ambulance Route Optimization System           ║"
echo "╚══════════════════════════════════════════════════════╝"
echo ""

# ── Start backend ─────────────────────────────────────────────────────────────
echo "▶ Starting Java backend on port $PORT…"
bash "$ROOT/build_and_run.sh" &
BACKEND_PID=$!

# Wait for backend to be ready (up to 20s)
echo -n "  Waiting for backend"
for i in $(seq 1 20); do
  sleep 1
  if curl -s "http://localhost:$PORT/health" > /dev/null 2>&1; then
    echo " ✅"
    break
  fi
  echo -n "."
done

# ── Start frontend ────────────────────────────────────────────────────────────
echo "▶ Starting React frontend on port 3000…"
PORT=3000 npm start --prefix "$ROOT/frontend" &
FRONTEND_PID=$!

echo ""
echo "  Backend  → http://localhost:$PORT"
echo "  Frontend → http://localhost:3000"
echo ""
echo "  Press Ctrl+C to stop both servers."
echo ""

# ── Trap Ctrl+C and kill both ─────────────────────────────────────────────────
cleanup() {
  echo ""
  echo "Stopping servers…"
  kill $BACKEND_PID  2>/dev/null
  kill $FRONTEND_PID 2>/dev/null
  # Kill any child processes of the backend (the java process)
  pkill -P $BACKEND_PID 2>/dev/null
  pkill -P $FRONTEND_PID 2>/dev/null
  echo "Done."
  exit 0
}
trap cleanup INT TERM

# Keep script alive
wait
