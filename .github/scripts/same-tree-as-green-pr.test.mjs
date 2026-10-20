// Körs med: node --test '.github/scripts/*.test.mjs'
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { chmodSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const script = path.join(path.dirname(fileURLToPath(import.meta.url)), 'same-tree-as-green-pr.mjs');

const env = {
  ...process.env,
  GIT_AUTHOR_NAME: 'test',
  GIT_AUTHOR_EMAIL: 'test@example.com',
  GIT_COMMITTER_NAME: 'test',
  GIT_COMMITTER_EMAIL: 'test@example.com',
  GITHUB_REPOSITORY: 'owner/repo',
};

/** Ett repo där `feature` mergas in i `master` med en merge-commit; `masterMoved` ger master en egen commit först. */
function repoWithMerge({ masterMoved }) {
  const dir = mkdtempSync(path.join(tmpdir(), 'same-tree-'));
  const git = (...args) => execFileSync('git', args, { cwd: dir, env, stdio: 'ignore' });
  git('init', '-q', '-b', 'master');
  writeFileSync(path.join(dir, 'a.txt'), 'a\n');
  git('add', '.');
  git('commit', '-q', '-m', 'bas');
  git('checkout', '-q', '-b', 'feature');
  writeFileSync(path.join(dir, 'b.txt'), 'b\n');
  git('add', '.');
  git('commit', '-q', '-m', 'pr');
  git('checkout', '-q', 'master');
  if (masterMoved) {
    writeFileSync(path.join(dir, 'c.txt'), 'c\n');
    git('add', '.');
    git('commit', '-q', '-m', 'annan pr');
  }
  git('merge', '-q', '--no-ff', '-m', 'merge', 'feature');
  return dir;
}

/** Kör skriptet i repot med ett falskt `gh` som svarar `conclusion` (eller fallerar). */
function decide(dir, conclusion) {
  const bin = mkdtempSync(path.join(tmpdir(), 'fake-gh-'));
  const gh = path.join(bin, 'gh');
  writeFileSync(gh, conclusion === null ? '#!/bin/sh\nexit 1\n' : `#!/bin/sh\necho "${conclusion}"\n`);
  chmodSync(gh, 0o755);
  try {
    const out = execFileSync('node', [script], { cwd: dir, env: { ...env, PATH: `${bin}${path.delimiter}${env.PATH}` }, encoding: 'utf8' });
    return Object.fromEntries(out.trim().split('\n').map((l) => [l.slice(0, l.indexOf('=')), l.slice(l.indexOf('=') + 1)]));
  } finally {
    rmSync(bin, { recursive: true, force: true });
  }
}

test('samma träd som en grön PR-head: hoppa över', () => {
  const dir = repoWithMerge({ masterMoved: false });
  try {
    const { skip, why } = decide(dir, 'success');
    assert.equal(skip, 'true');
    assert.match(why, /samma träd/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test('samma träd men rött, okänt eller oläsbart ci-ok: kör allt', () => {
  const dir = repoWithMerge({ masterMoved: false });
  try {
    assert.equal(decide(dir, 'failure').skip, 'false');
    assert.equal(decide(dir, '').skip, 'false');
    assert.equal(decide(dir, null).skip, 'false');
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test('master har ändrats sedan PR:en: trädet skiljer sig och allt körs', () => {
  const dir = repoWithMerge({ masterMoved: true });
  try {
    const { skip, why } = decide(dir, 'success');
    assert.equal(skip, 'false');
    assert.match(why, /skiljer sig/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test('ingen merge-commit (squash eller direkt push): kör allt', () => {
  const dir = repoWithMerge({ masterMoved: false });
  try {
    execFileSync('git', ['reset', '-q', '--hard', 'HEAD^'], { cwd: dir, env, stdio: 'ignore' });
    assert.equal(decide(dir, 'success').skip, 'false');
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});
