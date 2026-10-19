package se.partee71.reseapoteket.data.common

import se.partee71.reseapoteket.ui.common.toMessage
import se.partee71.ui.common.ErrorMapper
import se.partee71.ui.common.UiError

/**
 * Den enda översättningen från datalagrets fel till ramarnas [UiError] (android-ui): texten är
 * `DataError.toMessage()`, och något som inte finns längre ([DataError.NotFound]) erbjuder inte
 * "Försök igen" (NFR-1, NFR-5). Ett oväntat fel blir [DataError.Unknown].
 */
object AppErrors : ErrorMapper {
    override fun map(error: Throwable): UiError {
        val known = error as? DataError ?: DataError.Unknown
        return UiError(known, known.toMessage(), retryable = known != DataError.NotFound)
    }

    override fun notFound(): UiError = map(DataError.NotFound)
}
