// Service-account-nycklar från miljön – enda tolkningen, delad av databasverktygen och
// rules-deployen (skill data-privacy-security). Värdet skrivs aldrig ut, inte ens i fel.

/**
 * Nyckeln i [value] som objekt. Rå JSON (som den laddas ner från Google Cloud) och base64 av
 * den godtas båda, så att en nyckel kan läggas in från telefonen utan kodning.
 */
export function parseServiceAccount(value, name) {
  const trimmed = (value ?? '').trim();
  if (!trimmed) throw new Error(`${name} saknas.`);
  let account;
  try {
    const json = trimmed.startsWith('{') ? trimmed : Buffer.from(trimmed, 'base64').toString('utf8');
    account = JSON.parse(json);
  } catch {
    throw new Error(`${name} är varken JSON eller base64-kodad JSON.`);
  }
  if (account?.type !== 'service_account' || !account.project_id || !account.private_key) {
    throw new Error(`${name} är inte en service-account-nyckel.`);
  }
  return account;
}

/** Som [parseServiceAccount], men stoppar om nyckeln hör till ett annat projekt än [projectId]. */
export function serviceAccountFor(value, name, projectId) {
  const account = parseServiceAccount(value, name);
  if (account.project_id !== projectId) {
    throw new Error(`${name} hör till projektet ${account.project_id}, inte ${projectId} – fel nyckel.`);
  }
  return account;
}
