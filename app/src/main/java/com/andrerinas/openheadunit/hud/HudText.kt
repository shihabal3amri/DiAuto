package com.andrerinas.openheadunit.hud

/** What the windshield HUD can actually show, as observed on a DiLink5.0 Sealion 06. */
internal object HudText {

    /**
     * The HUD renders anything under 20 m as "现在" ("now") in Chinese, whatever the system
     * language (the instrument cluster says "NOW"). Show 20 m instead of an unreadable word.
     */
    fun distance(meters: Int): Int = if (meters in 0 until NOW_THRESHOLD_M) NOW_THRESHOLD_M else meters

    private const val NOW_THRESHOLD_M = 20

    /**
     * The HUD font has no glyphs for accented Latin letters - Romanian ă, â, î, ș, ț came out as
     * blank gaps - so fold them to their base letter. Only letters whose decomposition starts with
     * a plain ASCII letter are touched; Cyrillic, CJK and everything else pass through unchanged.
     */
    fun street(road: String): String = buildString(road.length) {
        for (char in road) {
            val base = java.text.Normalizer.normalize(char.toString(), java.text.Normalizer.Form.NFD).first()
            append(
                when {
                    base != char && (base in 'A'..'Z' || base in 'a'..'z') -> base
                    else -> FOLDED[char] ?: char
                }
            )
        }
    }

    // Latin letters with no canonical decomposition.
    private val FOLDED = mapOf(
        'ß' to 's', 'đ' to 'd', 'Đ' to 'D', 'ł' to 'l', 'Ł' to 'L', 'ø' to 'o', 'Ø' to 'O', 'ı' to 'i',
    )
}
