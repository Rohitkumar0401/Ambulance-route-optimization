#!/bin/bash
# ─────────────────────────────────────────────────────────────────────────────
# build_and_run.sh
# Compiles ALL Java source files and starts the main server.
#
# Usage:
#   ./build_and_run.sh          # compile + run server on port 5001
#   ./build_and_run.sh db-init  # compile + run database initializer only
#
# Requires:
#   - JDK 17+
#   - mysql-connector-j.jar in the project root
#     Download: https://dev.mysql.com/downloads/connector/j/
# ─────────────────────────────────────────────────────────────────────────────

ROOT="$(cd "$(dirname "$0")" && pwd)"
JAR="$ROOT/mysql-connector-j.jar"
SEC_JAR="$ROOT/spring-security-crypto.jar"
LOG_JAR="$ROOT/commons-logging.jar"
OUT="$ROOT/out"

echo "╔══════════════════════════════════════════════════════╗"
echo "║   Ambulance Route Optimization — Java Build v2.0    ║"
echo "╚══════════════════════════════════════════════════════╝"

# ── Check Java ────────────────────────────────────────────────────────────────
if ! command -v javac &>/dev/null; then
  echo "❌ javac not found. Install JDK 17+ from https://adoptium.net"
  exit 1
fi
echo "✅ Java: $(java -version 2>&1 | head -1)"

# ── Check MySQL connector ─────────────────────────────────────────────────────
if [ ! -f "$JAR" ]; then
  echo ""
  echo "⚠️  mysql-connector-j.jar not found at: $JAR"
  echo "   Download from: https://dev.mysql.com/downloads/connector/j/"
  echo "   (Platform Independent → ZIP, then copy the .jar here)"
  exit 1
fi

if [ ! -f "$SEC_JAR" ]; then
  echo ""
  echo "⚠️  spring-security-crypto.jar not found at: $SEC_JAR"
  echo "   Add the jar file in the project root to enable BCrypt password hashing."
  exit 1
fi

if [ ! -f "$LOG_JAR" ]; then
  echo ""
  echo "⚠️  commons-logging.jar not found at: $LOG_JAR"
  echo "   Download: https://repo1.maven.org/maven2/commons-logging/commons-logging/1.2/commons-logging-1.2.jar"
  exit 1
fi

# ── Collect all Java source files ────────────────────────────────────────────
SOURCES=(
  # Config & utils
  "$ROOT/backend/config/DatabaseConfig.java"
  "$ROOT/backend/config/DbInit.java"
  "$ROOT/backend/utils/ErrorHandler.java"

  # Hospital module
  "$ROOT/backend/modules/hospital-management/Hospital.java"
  "$ROOT/backend/modules/hospital-management/HospitalSearch.java"
  "$ROOT/backend/modules/hospital-management/HospitalRepository.java"
  "$ROOT/backend/modules/hospital-management/HospitalController.java"

  # Emergency module
  "$ROOT/backend/modules/emergency-request/models/RequestQueue.java"
  "$ROOT/backend/modules/emergency-request/EmergencyController.java"

  # Traffic module
  "$ROOT/backend/modules/traffic-analysis/models/TrafficAnalyzer.java"
  "$ROOT/backend/modules/traffic-analysis/TrafficController.java"

  # Road scoring module
  "$ROOT/backend/modules/road-scoring/RoadScoringController.java"

  # Alerts module
  "$ROOT/backend/modules/alerts/AlertController.java"

  # Auth module
  "$ROOT/backend/modules/user-authentication/middleware/AuthMiddleware.java"
  "$ROOT/backend/modules/user-authentication/AuthController.java"

  # Route optimization module
  "$ROOT/backend/modules/route-optimization/algorithms/Graph.java"
  "$ROOT/backend/modules/route-optimization/algorithms/Dijkstra.java"
  "$ROOT/backend/modules/route-optimization/algorithms/MapFactory.java"
  "$ROOT/backend/modules/route-optimization/algorithms/DijkstraAlgorithm.java"
  "$ROOT/backend/modules/route-optimization/algorithms/AStarAlgorithm.java"
  "$ROOT/backend/modules/route-optimization/RouteOptimizationController.java"
  "$ROOT/backend/modules/route-optimization/AmbulanceGraph.java"

  # Main server
  "$ROOT/backend/MainServer.java"
)
mkdir -p "$OUT"
echo "📦 Compiling ${#SOURCES[@]} Java files…"
javac -cp "$JAR:$SEC_JAR:$LOG_JAR" -d "$OUT" "${SOURCES[@]}"
if [ $? -ne 0 ]; then
  echo "❌ Compilation failed"
  exit 1
fi
echo "✅ Compiled successfully → $OUT"

# ── Run ───────────────────────────────────────────────────────────────────────
if [ "$1" = "db-init" ]; then
  echo "🗄️  Initializing database…"
  java -cp "$OUT:$JAR:$SEC_JAR:$LOG_JAR" \
    -DDB_HOST="${DB_HOST:-localhost}" \
    -DDB_USER="${DB_USER:-root}" \
    -DDB_PASSWORD="${DB_PASSWORD:-root12345}" \
    -DDB_NAME="${DB_NAME:-ambulance_optimization}" \
    -Dschema.path="$ROOT/backend/database/schema.sql" \
    config.DbInit
elif [ "$1" = "--compile-only" ]; then
  echo "✅ Compile-only mode — skipping server start"
else
  echo "🚀 Starting Main Server on port ${PORT:-5001}…"
  java -cp "$OUT:$JAR:$SEC_JAR:$LOG_JAR" \
    -DDB_HOST="${DB_HOST:-localhost}" \
    -DDB_USER="${DB_USER:-root}" \
    -DDB_PASSWORD="${DB_PASSWORD:-root12345}" \
    -DDB_NAME="${DB_NAME:-ambulance_optimization}" \
    -DJWT_SECRET="${JWT_SECRET:-your_jwt_secret_key}" \
    -DPORT="${PORT:-5001}" \
    backend.MainServer
fi
