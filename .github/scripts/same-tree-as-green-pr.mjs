#!/usr/bin/env node
// Regel 5 (skill ci-budget): en push till master som är en merge-commit med exakt samma träd
// som den mergade PR:ens head, där `ci-ok` var grönt, är redan testad – då kör android.yml bara
// dokumentkontrollerna. Allt annat (annat träd, squash/rebase, rött eller okänt ci-ok) kör allt.
// Skriver `skip=true|false` och `why=…` för $GITHUB_OUTPUT. Kräver git-historik med båda
// föräldrarna (checkout med fetch-depth: 2) och `gh` med GH_TOKEN.
// Kör: node .github/scripts/same-tree-as-green-pr.mjs [commit]  (testas av same-tree-as-green-pr.test.mjs)

import { execFileSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const run = (cmd, args) => execFileSync(cmd, args, { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim();

/** Den mergade PR:ens head (merge-commitens andra förälder), eller null för en vanlig commit. */
export function mergedHead(commit) {
  try {
    return run('git', ['rev-parse', '--verify', '-q', `${commit}^2`]);
  } catch {
    return null;
  }
}

const tree = (commit) => run('git', ['rev-parse', `${commit}^{tree}`]);

/** Slutsatsen för den senaste `ci-ok` från GitHub Actions på commiten ("" om den saknas). */
export function ciOkConclusion(sha, repo) {
  return run('gh', [
    'api',
    `repos/${repo}/commits/${sha}/check-runs?check_name=ci-ok`,
    '--jq',
    '[.check_runs[] | select(.app.slug == "github-actions")] | sort_by(.completed_at) | last | .conclusion // ""',
  ]);
}

/** { skip, why } – skip bara när trädet är samma som en PR-head med grönt ci-ok. */
export function alreadyTested(commit, repo) {
  const head = mergedHead(commit);
  if (!head) return { skip: false, why: 'inte en merge-commit' };
  if (tree(commit) !== tree(head)) return { skip: false, why: `trädet skiljer sig från PR-headen ${head}` };
  let conclusion;
  try {
    conclusion = ciOkConclusion(head, repo);
  } catch {
    return { skip: false, why: `kunde inte läsa ci-ok på PR-headen ${head}` };
  }
  if (conclusion !== 'success') return { skip: false, why: `ci-ok på PR-headen ${head} är ${conclusion || 'okänt'}` };
  return { skip: true, why: `samma träd som PR-headen ${head}, där ci-ok var grönt` };
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const { skip, why } = alreadyTested(process.argv[2] ?? 'HEAD', process.env.GITHUB_REPOSITORY ?? '');
  console.log(`skip=${skip}`);
  console.log(`why=${why}`);
}
