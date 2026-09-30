---
name: firebase-auth
description: Use when working with Firebase Authentication, Google Sign-In via Credential Manager, FirebaseUser, auth state listeners, or sign-out/credential clearing in ReseApoteket. Covers the CredentialManager + GetSignInWithGoogleOption pattern (replaces deprecated GoogleSignIn). Also covers the required sign-in gate and household bootstrap (inloggning, logga in, logga ut, hushåll, EnsureHouseholdUseCase, HouseholdSession, SignInScreen).
---

# Firebase Auth — Credential Manager Pattern

This project uses **Firebase Auth + Android Credential Manager** (not the deprecated `GoogleSignIn` API).

**ReseApoteket requires sign-in** (unlike Dagboken): the app shows `SignInScreen` until a user is signed in, and after sign-in `EnsureHouseholdUseCase` selects or creates the user's household (`households` where `memberUids` contains the uid) and sets `HouseholdSession`. Firestore security rules depend on `request.auth.uid`, so no data is readable signed out.

## Architecture

```
AppRoot → AuthViewModel.state.gate: Loading | SignedOut | NeedsHousehold | UpdateRequired | Ready
  SignInScreen → AuthEvent.SignIn(activityContext)
    → AuthRepository.signInWithGoogle(activityContext): Result<AuthUser>   (GoogleAuthRepository)
      → CredentialManager.getCredential() — Google account picker
      → GoogleIdTokenCredential.createFrom() — ID token
      → FirebaseAuth.signInWithCredential() — Firebase session
  authState = user && HouseholdSession.selection = null
    → EnsureHouseholdUseCase(uid) — HouseholdDirectory (server query) → select oldest, or create "Mitt hushåll"
```

## Key Classes

- **`AuthRepository`** (`data/auth/`) — interface; `GoogleAuthRepository` is the only implementation (bound in `di/AuthModule`). Tests use `FakeAuthRepository`.
- **`AuthUser(uid)`** — all the app keeps of the user. Never email, name or photo (skill `data-privacy-security`).
- **`authError`** (`data/auth/AuthErrors.kt`) — the one mapping to `DataError`.
- **`GetSignInWithGoogleOption`** — use this (not `GetGoogleIdOption`) — opens full-page Google account picker
- **`AuthViewModel`** (`ui/auth/`) — decides what `AppRoot` shows; `SettingsViewModel` only handles sign-out (#27)
- **`EnsureHouseholdUseCase`** (`domain/`) + **`HouseholdDirectory`** (`data/household/`, Firestore implementation in `data/firestore/`)
- **`R.string.default_web_client_id`** — auto-generated from `google-services.json` by `google-services` plugin. Requires `client_type: 3` in `google-services.json`. When the file is missing (CI without the secret, sessions), a debug-only stub resource provides the string so the code compiles (skill `android-gradle-logic`).

## `AuthRepository` API

```kotlin
interface AuthRepository {
    val authState: Flow<AuthUser?>                                   // null = signed out
    suspend fun signInWithGoogle(activityContext: Context): Result<AuthUser> // Activity context for the picker
    suspend fun signOut(): Result<Unit> // clears Credential Manager state, Firebase session and HouseholdSession
}
```

## Sign-In Flow (as built)

- One button, one event: `AuthEvent.SignIn(context)`. Signed out → sign in. Already signed in but no household (the household step failed, e.g. offline) → retry `EnsureHouseholdUseCase` without a new sign-in.
- The household step starts by itself once per user when `authState` has a user and `HouseholdSession.selection` is `null` (`selection` emits only after DataStore has loaded, so a stored household never triggers a lookup).
- `busy` covers both steps; the button shows "Loggar in …" (`AppButton(loading = true)`).
- Errors arrive already mapped; `Cancelled` is silent (AUTH-2), anything else is shown once via `AppSnackbarHost` and cleared with `AuthEvent.ErrorShown`.
- The lookup uses `Source.SERVER`: an empty cache must never mean "no household" (HUS-1). Offline → `DataError.Offline`, nothing is created.
- Rules: `households` read is `request.auth.uid in resource.data.memberUids` so the `array-contains` query is provable (a `get()` in the read rule cannot be evaluated for a query).
- `gate = UpdateRequired` when the selected household's `schemaVersion` is newer than the app → `UpdateRequiredScreen` (HUS-3).

## Sign-Out

`AuthRepository.signOut()` (from Settings, behind `ConfirmDialog`, #27) clears the cached credential, the Firebase session and the selected household, so the next user on the device never inherits it.

## google-services.json Requirements

For Google Sign-In to work, `app/google-services.json` must contain:
- `client_type: 1` — Android OAuth client (requires the app's SHA-1 fingerprint registered in Firebase/Google Cloud)
- `client_type: 3` — Web OAuth client (generates `R.string.default_web_client_id`)

The debug and release SHA-1 fingerprints are registered in the Firebase project `reseapoteket` (see README "Firebase-setup"). Never commit `google-services.json` or keystores; CI writes `google-services.json` from the `GOOGLE_SERVICES_JSON` secret.

If `client_type: 1` is missing:
1. Go to Firebase Console → Project Settings → Your Android app
2. Add SHA-1 fingerprint (must not be registered in any other Google Cloud project)
3. Download new `google-services.json` → replace `app/google-services.json`
4. Rebuild

## GetSignInWithGoogleOption vs GetGoogleIdOption

| Option | Behavior | When to use |
|---|---|---|
| `GetSignInWithGoogleOption` | Full-page Google account picker | **Use this** — more reliable, matches old `GoogleSignIn` behavior |
| `GetGoogleIdOption` | Bottom sheet with pre-selected account | Can fail with "Failed to retrieve an ID token" if no cached credential |

Always use `GetSignInWithGoogleOption` for first-time sign-in flows.

## Signed-out state

- `authState` emitting `null` → `AppRoot` shows `SignInScreen`; `signOut()` clears `HouseholdSession`.
- Never read Firestore before `HouseholdSession` has a value – `FirestoreCollection` depends on it.
- Sign-out is offered only in Settings, behind `ConfirmDialog` (rule 4: same confirmation pattern everywhere).

## Error Categories

`AuthRepository` maps every exception to `DataError` once, with `suspendRunCatching` (in
`data/common/`), exactly like `FirestoreCollection` does for data:

| Exception | Meaning | `DataError` | Handle |
|---|---|---|---|
| `GetCredentialCancellationException` | User dismissed picker | `Cancelled` | Silent — not an error |
| `GetCredentialException` | No Google accounts on device, API not available | `Unknown` | Show error |
| `FirebaseAuthException` | Firebase rejected the token | `PermissionDenied` | Show error; log only the exception class, never email/uid (skill `data-privacy-security`) |
| `FirebaseNetworkException`, `IOException` | No network | `Offline` | Show error |

## Dependencies (added in the Gradle skeleton, #11)

```toml
# libs.versions.toml
firebase-auth = { group = "com.google.firebase", name = "firebase-auth" }  # version from Firebase BOM
credentials = { group = "androidx.credentials", name = "credentials", version.ref = "credentialManager" }
credentials-play-services = { group = "androidx.credentials", name = "credentials-play-services-auth", version.ref = "credentialManager" }
googleid = { group = "com.google.android.libraries.identity.googleid", name = "googleid", version.ref = "googleIdentity" }
```
