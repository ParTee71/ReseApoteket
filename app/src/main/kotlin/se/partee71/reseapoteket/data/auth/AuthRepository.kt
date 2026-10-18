package se.partee71.reseapoteket.data.auth

import android.content.Context
import kotlinx.coroutines.flow.Flow

/**
 * Den inloggade användaren – bara det appen behöver. Namn och e-post visas i Inställningar
 * (SET-4) men loggas eller sparas aldrig.
 */
data class AuthUser(val uid: String, val name: String? = null, val email: String? = null)

/** Inloggning med Google (skill android:firebase-auth + android-projekt). Alla fel kommer som `DataError`, mappade en gång. */
interface AuthRepository {
    /** Aktuell användare vid varje ändring; `null` = utloggad. */
    val authState: Flow<AuthUser?>

    /** Visar Googles kontoväljare – kräver aktivitetens kontext. Avbrott ger `DataError.Cancelled`. */
    suspend fun signInWithGoogle(activityContext: Context): Result<AuthUser>

    /** Rensar Credential Managers sparade inloggning, Firebase-sessionen och det valda hushållet. */
    suspend fun signOut(): Result<Unit>
}
