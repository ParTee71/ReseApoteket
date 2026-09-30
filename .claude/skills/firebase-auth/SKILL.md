---
name: firebase-auth
description: Use when working with Firebase Authentication, Google Sign-In via Credential Manager, FirebaseUser, auth state listeners, or sign-out/credential clearing in ReseApoteket. Covers the CredentialManager + GetSignInWithGoogleOption pattern (replaces deprecated GoogleSignIn). Also covers the required sign-in gate and household bootstrap (inloggning, logga in, logga ut, hushåll, EnsureHouseholdUseCase, HouseholdSession, SignInScreen).
---

# Firebase Auth — Credential Manager Pattern

This project uses **Firebase Auth + Android Credential Manager** (not the deprecated `GoogleSignIn` API).

**ReseApoteket requires sign-in** (unlike Dagboken): the app shows `SignInScreen` until a user is signed in, and after sign-in `EnsureHouseholdUseCase` selects or creates the user's household (`households` where `memberUids` contains the uid) and sets `HouseholdSession`. Firestore security rules depend on `request.auth.uid`, so no data is readable signed out.

## Architecture

```
UI (SignInScreen)
  → AuthViewModel.signIn(activityContext)
    → AuthRepository.signInWithGoogle(activityContext): Result<FirebaseUser>
      → CredentialManager.getCredential() — shows Google account picker
      → GoogleIdTokenCredential.createFrom() — extracts ID token
      → FirebaseAuth.signInWithCredential() — exchanges token for Firebase session
  → EnsureHouseholdUseCase() — select/create household, set HouseholdSession
```

## Key Classes

- **`AuthRepository`** (`data/auth/`) — singleton, inject with `@Inject` constructor
- **`CredentialManager`** — created from `ApplicationContext` at repository construction time
- **`GetSignInWithGoogleOption`** — use this (not `GetGoogleIdOption`) — opens full-page Google account picker
- **`AuthViewModel`** (`ui/auth/`) — sign-in state; `SettingsViewModel` only handles sign-out
- **`R.string.default_web_client_id`** — auto-generated from `google-services.json` by `google-services` plugin. Requires `client_type: 3` in `google-services.json`. When the file is missing (CI without the secret, sessions), a debug-only stub resource provides the string so the code compiles (skill `android-gradle-logic`).

## `AuthRepository` API

```kotlin
@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val currentUser: FirebaseUser? get() = auth.currentUser

    // Hot flow — emits on every auth state change (sign in, sign out, token refresh)
    val authState: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    // Needs activityContext (not ApplicationContext) for the Credential Manager UI
    suspend fun signInWithGoogle(activityContext: Context): Result<FirebaseUser>

    fun signOut()
    suspend fun clearCredentialState() // call before signOut() for clean sign-out
}
```

## Sign-In Flow

```kotlin
// In AuthViewModel – actions arrive as sealed events (skill android-dev)
fun onEvent(event: AuthEvent) = when (event) {
    is AuthEvent.SignIn -> signIn(event.activityContext)
}

private fun signIn(activityContext: Context) {
    viewModelScope.launch {
        _state.update { it.copy(isSigningIn = true, error = null) }
        val result = authRepo.signInWithGoogle(activityContext)
        _state.update { it.copy(isSigningIn = false) }
        result
            .onSuccess { ensureHousehold() }
            .onFailure { e ->
                // AuthRepository has already mapped the exception to DataError (once, via
                // suspendRunCatching in data/common). Cancelled = user dismissed the picker → silent.
                val error = e as? DataError ?: DataError.Unknown
                if (error != DataError.Cancelled) _state.update { it.copy(error = error) }
            }
    }
}

// In Composable — pass LocalContext.current (which is the Activity context in Compose)
val context = LocalContext.current
AppButton(onClick = { vm.onEvent(AuthEvent.SignIn(context)) }, text = stringResource(R.string.sign_in_google))
```

## Sign-Out Flow

```kotlin
// In SettingsViewModel, reached via SettingsEvent.SignOut after ConfirmDialog.
// Always clear credential state before signing out.
private fun signOut() {
    viewModelScope.launch {
        authRepo.clearCredentialState() // removes cached credential from Credential Manager
        authRepo.signOut()              // signs out from Firebase
    }
}
```

## Observing Auth State

```kotlin
// In ViewModel init — collect authState to sync UI
viewModelScope.launch {
    authRepo.authState.collectLatest { user ->
        _state.value = _state.value.copy(
            googleAccountEmail = user?.email,
            googleAccountPhotoUrl = user?.photoUrl?.toString(),
            googleDisplayName = user?.displayName,
        )
    }
}

// Null user = signed out; non-null = signed in
```

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

- `authState` emitting `null` → navigation shows `SignInScreen`; `HouseholdSession` is cleared.
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
