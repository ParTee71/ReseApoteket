#!/bin/bash
set -euo pipefail

# Körs bara i fjärrsessioner (Claude på webben/telefonen) – lokala Android Studio-miljöer
# lämnas orörda.
if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

echo '{"async": true, "asyncTimeout": 300000}'

cd "${CLAUDE_PROJECT_DIR:-.}"

# tools/db: installera beroenden om verktyget finns och inte redan är installerat.
if [ -f tools/db/package-lock.json ] && [ ! -d tools/db/node_modules ]; then
  npm ci --prefix tools/db --silent --no-audit --no-fund || echo "tools/db: npm ci misslyckades"
fi

# Databasåtkomst: visa bara om nyckeln finns, aldrig dess innehåll (skill db-access).
if [ -n "${FIREBASE_SERVICE_ACCOUNT:-}" ]; then
  echo "FIREBASE_SERVICE_ACCOUNT: satt"
else
  echo "FIREBASE_SERVICE_ACCOUNT: saknas (databasåtkomst från sessionen är avstängd)"
fi

# Valfritt: kortfattad utdata med "caveman" (används i Dagboken). Avkommentera för att slå på.
# npx -y github:JuliusBrussee/caveman -- --only claude --non-interactive || true
