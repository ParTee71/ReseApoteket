package se.partee71.reseapoteket.ui

import se.partee71.reseapoteket.data.common.AppErrors
import se.partee71.reseapoteket.data.common.DataError
import se.partee71.reseapoteket.ui.theme.ReseApoteketTheme
import se.partee71.ui.testing.ContractEnvironment

/**
 * Det ramarnas kontraktstester (android-ui `ui-testing`: `runListScreenContract`, `runEditScreenContract`,
 * `runDetailScreenContract`) behöver av appen: temat, felöversättningen och ett fel där "Försök igen"
 * hjälper (NFR-1, NFR-2, NFR-5). En gång för alla skärmtester.
 */
val contracts = ContractEnvironment(AppErrors, DataError.Offline) { ReseApoteketTheme(content = it) }
