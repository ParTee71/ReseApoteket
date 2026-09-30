package se.partee71.reseapoteket.ui.common

import androidx.annotation.StringRes
import se.partee71.reseapoteket.R
import se.partee71.reseapoteket.data.common.DataError

/** Den enda översättningen från fel till svensk text (NFR-5). */
@StringRes
fun DataError.toMessage(): Int = when (this) {
    DataError.Offline -> R.string.error_offline
    DataError.PermissionDenied -> R.string.error_permission_denied
    DataError.Cancelled -> R.string.error_cancelled
    DataError.UpdateRequired -> R.string.error_update_required
    DataError.SignInRejected -> R.string.error_sign_in_rejected
    DataError.NoHousehold -> R.string.error_no_household
    DataError.Unknown -> R.string.error_unknown
}
