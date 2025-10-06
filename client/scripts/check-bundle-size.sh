#!/bin/bash

# Bundle size budget: 250 KB gzipped
BUDGET_KB=250
BUILD_DIR="dist/assets"

echo "🔍 Checking bundle size..."
echo "Budget: ${BUDGET_KB} KB gzipped"
echo ""

# Build the project
npm run build > /dev/null 2>&1

# Calculate gzipped size of main JS bundle
MAIN_JS=$(find $BUILD_DIR -name 'index-*.js' | head -1)

if [ ! -f "$MAIN_JS" ]; then
  echo "❌ Error: Could not find main JS bundle"
  exit 1
fi

GZIP_SIZE=$(gzip -c "$MAIN_JS" | wc -c)
GZIP_KB=$((GZIP_SIZE / 1024))

echo "📦 Main bundle: ${GZIP_KB} KB gzipped"

# Calculate total gzipped size
TOTAL_SIZE=0
for file in $BUILD_DIR/*.js; do
  SIZE=$(gzip -c "$file" | wc -c)
  TOTAL_SIZE=$((TOTAL_SIZE + SIZE))
done

TOTAL_KB=$((TOTAL_SIZE / 1024))
echo "📊 Total JS: ${TOTAL_KB} KB gzipped"
echo ""

# Check against budget
if [ $TOTAL_KB -gt $BUDGET_KB ]; then
  echo "❌ FAIL: Bundle exceeds budget by $((TOTAL_KB - BUDGET_KB)) KB"
  exit 1
else
  UNDER=$((BUDGET_KB - TOTAL_KB))
  PERCENT=$((UNDER * 100 / BUDGET_KB))
  echo "✅ PASS: ${PERCENT}% under budget (${UNDER} KB remaining)"
fi






