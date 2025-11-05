#!/bin/bash

# CDN Platform Stop Script

set -e

echo "🛑 Stopping CDN Platform..."

docker-compose down

echo "✅ All services stopped"
echo ""
echo "💡 To remove all data: docker-compose down -v"
