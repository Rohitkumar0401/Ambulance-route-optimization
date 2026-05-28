#!/bin/bash
echo "========================================="
echo " Ambulance Route Optimization System"
echo " Team: Visitors"
echo "========================================="

# Check Node
if ! command -v node &> /dev/null; then
  echo "❌ Node.js not found. Install from https://nodejs.org/"; exit 1
fi
echo "✅ Node.js $(node --version)"

# Check MySQL
if ! command -v mysql &> /dev/null; then
  echo "❌ MySQL not found. Install from https://dev.mysql.com/downloads/mysql/"; exit 1
fi
echo "✅ MySQL found"

# Install deps
echo "📦 Installing backend dependencies..."
npm install --silent

echo "📦 Installing frontend dependencies..."
(cd frontend && npm install --silent)

# Init DB
echo "🗄️  Initializing database..."
npm run db:init
if [ $? -ne 0 ]; then
  echo "❌ DB init failed. Check .env credentials (DB_PASSWORD=root12345)"; exit 1
fi

echo ""
echo "========================================="
echo "✅ Setup complete!"
echo ""
echo "Run in TWO terminals:"
echo "  Terminal 1: npm run server   (backend  → port 5001)"
echo "  Terminal 2: npm run dev      (frontend → port 3000)"
echo ""
echo "Login: admin@ambulance.com / admin123"
echo "========================================="
