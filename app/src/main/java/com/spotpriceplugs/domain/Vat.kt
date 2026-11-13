package com.spotpriceplugs.domain

/**
 * VAT rates for Nord Pool countries (electricity, standard rate).
 * The user can always override the automatic value in Settings,
 * including with 0 (e.g. Norway applies 25% and 0% in different cases).
 */
object Vat {

    /** Stored value meaning "no explicit VAT: pick it by region". */
    const val AUTO = -1.0

    private val BY_COUNTRY = mapOf(
        "LV" to 21.0, // Latvia
        "EE" to 22.0, // Estonia
        "LT" to 21.0, // Lithuania
        "FI" to 25.5, // Finland
        "SE" to 25.0, // Sweden
        "DK" to 25.0, // Denmark
        "NO" to 25.0, // Norway
        "PL" to 23.0, // Poland
        "DE" to 19.0, // Germany
        "AT" to 20.0, // Austria
        "BE" to 21.0, // Belgium
        "NL" to 21.0, // Netherlands
        "FR" to 20.0  // France
    )

    /** Rate for a Nord Pool area (LV, SE3, NO1 …): country = first two letters. */
    fun forRegion(area: String): Double? = BY_COUNTRY[area.take(2).uppercase()]

    /**
     * The rate actually used for pricing: an explicit user value wins,
     * otherwise the rate of the area's country; 0 when the area is unknown.
     */
    fun effective(area: String, stored: Double): Double =
        if (stored >= 0.0) stored else forRegion(area) ?: 0.0
}
