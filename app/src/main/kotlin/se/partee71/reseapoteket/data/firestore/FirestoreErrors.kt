package se.partee71.reseapoteket.data.firestore

import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import se.partee71.reseapoteket.data.common.DataError

/** Firestores fel → [DataError], en gång (skill firestore-data-layer). */
fun firestoreError(error: Throwable): DataError = when (error) {
    is DataError -> error
    is FirebaseFirestoreException -> when (error.code) {
        Code.UNAVAILABLE, Code.DEADLINE_EXCEEDED -> DataError.Offline
        Code.PERMISSION_DENIED, Code.UNAUTHENTICATED -> DataError.PermissionDenied
        Code.CANCELLED -> DataError.Cancelled
        else -> DataError.Unknown
    }
    else -> DataError.Unknown
}
