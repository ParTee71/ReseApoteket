package se.partee71.reseapoteket.core.model

/**
 * Enhet för det som packas. Namnen lagras i Firestore – ett nytt värde kräver höjd
 * `Schema.CURRENT_VERSION` (en äldre app kan inte tolka det, skill data-safety-backup).
 */
enum class ItemUnit { TABLETT, KAPSEL, ML, DOS, PUFF, ST, TUB }
