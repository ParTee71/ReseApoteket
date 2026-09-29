#!/usr/bin/env node
// PostToolUse-hook (Edit|Write|MultiEdit): snabb kontroll av regel 4 direkt efter en
// filändring, utan Gradle. Mönster och undantag läses från samma filer som
// UiConsistencyTest använder – hooken har inga egna mönster (skill shared-ui-components).
//
// Exit 0 = inget att anmärka (tyst). Exit 2 = träff; meddelandet på stderr går till Claude.

import { readFileSync, existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const PATTERNS_FILE = 'app/src/test/resources/ui-forbidden.txt';
const ALLOWLIST_FILE = 'app/src/test/resources/ui-allowlist.txt';

/** Läser hook-JSON från stdin. */
function readInput() {
  try {
    const raw = readFileSync(0, 'utf8');
    return raw.trim() ? JSON.parse(raw) : {};
  } catch {
    return {};
  }
}

/** Tolkar ui-forbidden.txt till { feature: [...], data: [...] }. */
export function parsePatterns(text) {
  const scopes = {};
  let current = null;
  for (const line of text.split(/\r?\n/)) {
    if (!line.trim() || line.startsWith('#')) continue;
    const section = line.match(/^\[(\w+)\]\s*$/);
    if (section) {
      current = section[1];
      scopes[current] = [];
      continue;
    }
    if (!current) continue;
    const [pattern, replacement = ''] = line.split('\t');
    scopes[current].push({ source: pattern, regex: new RegExp(pattern), replacement });
  }
  return scopes;
}

/** Tolkar ui-allowlist.txt till [{ file, symbol }]. */
export function parseAllowlist(text) {
  return text
    .split(/\r?\n/)
    .filter((l) => l.trim() && !l.startsWith('#'))
    .map((l) => {
      const [location] = l.split(/\s+[–-]\s+/);
      const idx = location.lastIndexOf(':');
      return { file: location.slice(0, idx).trim(), symbol: location.slice(idx + 1).trim() };
    });
}

/** Vilket scope en repo-relativ sökväg hör till, eller null. */
export function scopeOf(relPath) {
  const p = relPath.split(path.sep).join('/');
  if (!p.startsWith('app/src/main/') || !p.endsWith('.kt')) return null;
  const ui = p.match(/\/ui\/([^/]+)\//);
  if (ui) return ['components', 'theme', 'common'].includes(ui[1]) ? null : 'feature';
  const data = p.match(/\/data\/([^/]+)\//);
  if (data) return data[1] === 'firestore' ? null : 'data';
  return null;
}

/** Hittar förbjudna mönster i en fils innehåll. */
export function findViolations(relPath, content, scopes, allowlist) {
  const scope = scopeOf(relPath);
  if (!scope || !scopes[scope]) return [];
  const normalized = relPath.split(path.sep).join('/');
  const hits = [];
  content.split(/\r?\n/).forEach((line, i) => {
    const trimmed = line.trim();
    if (trimmed.startsWith('//') || trimmed.startsWith('*') || trimmed.startsWith('import ')) return;
    for (const rule of scopes[scope]) {
      const m = line.match(rule.regex);
      if (!m) continue;
      const allowed = allowlist.some((a) => a.file === normalized && line.includes(a.symbol));
      if (!allowed) hits.push({ line: i + 1, match: m[0], replacement: rule.replacement, scope });
    }
  });
  return hits;
}

function main() {
  const input = readInput();
  const filePath = input?.tool_input?.file_path;
  if (!filePath) return 0;

  const projectDir = process.env.CLAUDE_PROJECT_DIR || input.cwd || process.cwd();
  const abs = path.isAbsolute(filePath) ? filePath : path.join(projectDir, filePath);
  const rel = path.relative(projectDir, abs);
  if (rel.startsWith('..') || !scopeOf(rel) || !existsSync(abs)) return 0;

  const patternsPath = path.join(projectDir, PATTERNS_FILE);
  if (!existsSync(patternsPath)) return 0;
  const scopes = parsePatterns(readFileSync(patternsPath, 'utf8'));
  const allowPath = path.join(projectDir, ALLOWLIST_FILE);
  const allowlist = existsSync(allowPath) ? parseAllowlist(readFileSync(allowPath, 'utf8')) : [];

  const hits = findViolations(rel, readFileSync(abs, 'utf8'), scopes, allowlist);
  if (hits.length === 0) return 0;

  const where = hits[0].scope === 'feature' ? 'feature-kod (ui/<feature>/)' : 'datalagret utanför data/firestore/';
  const lines = hits.map((h) => `  ${rel}:${h.line}  \`${h.match.trim()}\`  → använd ${h.replacement}`);
  process.stderr.write(
    `Regel 4 (enhetligt och utan dubbelkod): förbjudna mönster i ${where}.\n` +
      lines.join('\n') +
      '\nAnvänd den delade komponenten/ramen/byggstenen (skill shared-ui-components). ' +
      'Behövs en variant: utöka den delade delen med en parameter. ' +
      'Undantag bara via app/src/test/resources/ui-allowlist.txt med motivering och användarens ok.\n',
  );
  return 2;
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  process.exit(main());
}
