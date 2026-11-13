package com.spotpriceplugs.model

import org.json.JSONObject

/** Transport type: Tasmota (Status0), Shelly Gen2/Gen3 (RPC), Shelly Gen1 or OpenBeken. */
object PlugKind {
    const val TASMOTA = 0
    const val SHELLY_RPC = 1 // Gen2/Gen3: /rpc/Shelly.GetDeviceInfo
    const val SHELLY_GEN1 = 2 // classic API: /shelly, /relay/0, /settings/schedule
    const val OPENBEKEN = 3 // Tasmota's cm?cmnd= endpoint, schedule as clock events

    fun isShelly(kind: Int): Boolean = kind == SHELLY_RPC || kind == SHELLY_GEN1

    fun label(kind: Int): String = when (kind) {
        TASMOTA -> "Tasmota"
        SHELLY_RPC -> "Shelly Gen2/3"
        SHELLY_GEN1 -> "Shelly Gen1"
        OPENBEKEN -> "OpenBeken"
        else -> "?"
    }
}

/**
 * One saved plug. Area, hours, price cap and power are parsed from the device
 * name on the first scan (see [DeviceName]); after that the user's edits win.
 */
data class Plug(
    val ip: String,
    val name: String,
    val region: String,
    val hours: Double,
    val limit: Double,
    /** Load power, W. 0 = unknown, so no savings in euros are computed. */
    val watts: Double = 0.0,
    /** Device type: PlugKind.TASMOTA / SHELLY_RPC / SHELLY_GEN1 / OPENBEKEN. */
    val kind: Int = PlugKind.TASMOTA,
    /** Last known relay state. */
    val power: Boolean = false,
    /** false = the schedule is paused (`Timers 0` on Tasmota). */
    val timersOn: Boolean = true,
    /** Last uploaded plan: blocks and prices, for the chart and summary. */
    val planJson: String = "",
    /** Model label reported by the plug (Shelly PLVM-9A9A0, Tasmota 13.2 …). */
    val model: String = "",
    val lastApplied: Long = 0L,
    val lastSummary: String = "",
    val lastError: String = "",
    /** When this device needs its next recalculation (0 = unknown). */
    val nextAt: Long = 0L,
    /** Hardware address (12 hex digits, "" = not known yet): the plug's identity when its IP changes. */
    val mac: String = "",
    /**
     * false = found by a scan but not controlled: the app never writes timers
     * to it or switches it (a lamp, a relay on something else …).
     */
    val managed: Boolean = true,
    /** Highest load the plug's own meter has reported, W (0 = none); used when [watts] is not set. */
    val autoWatts: Double = 0.0,
    /** The timer blocks last written to the plug (see [com.spotpriceplugs.domain.Planner.encodeBlocks]). */
    val applied: String = "",
    /** When [applied] was written to the plug (0 = unknown); an OpenBeken loses its schedule on reboot. */
    val appliedAt: Long = 0L,
    /** OpenBeken: the schedule is also in the plug's autoexec.bat, so it survives a reboot. */
    val persisted: Boolean = false
) {
    val configured: Boolean get() = region.isNotBlank()

    /** Load power for savings: the user's value, else the highest meter reading. */
    val effectiveWatts: Double get() = if (watts > 0.0) watts else autoWatts
    val hasPlan: Boolean get() = planJson.isNotBlank()

    /**
     * Name for display: the device name without the parameters the card already
     * shows elsewhere (area chip, hours/power/max line). Falls back to the IP when
     * the name consists of parameters only.
     */
    val displayName: String get() = DeviceName.baseName(name).ifBlank { ip }

    fun toJson(): JSONObject = JSONObject().apply {
        put("ip", ip)
        put("name", name)
        put("region", region)
        put("hours", hours)
        put("limit", limit)
        put("watts", watts)
        put("kind", kind)
        put("power", power)
        put("timersOn", timersOn)
        put("planJson", planJson)
        put("model", model)
        put("lastApplied", lastApplied)
        put("lastSummary", lastSummary)
        put("lastError", lastError)
        put("nextAt", nextAt)
        put("mac", mac)
        put("managed", managed)
        put("autoWatts", autoWatts)
        put("applied", applied)
        put("appliedAt", appliedAt)
        put("persisted", persisted)
    }

    companion object {
        fun fromJson(o: JSONObject): Plug = Plug(
            ip = o.optString("ip"),
            name = o.optString("name"),
            region = o.optString("region", ""),
            hours = o.optDouble("hours", 4.0),
            limit = o.optDouble("limit", 0.20),
            watts = o.optDouble("watts", 0.0),
            kind = o.optInt("kind", PlugKind.TASMOTA),
            power = o.optBoolean("power", false),
            timersOn = o.optBoolean("timersOn", true),
            planJson = o.optString("planJson", ""),
            model = o.optString("model", ""),
            lastApplied = o.optLong("lastApplied", 0L),
            lastSummary = o.optString("lastSummary", ""),
            lastError = o.optString("lastError", ""),
            nextAt = o.optLong("nextAt", 0L),
            mac = o.optString("mac", ""),
            managed = o.optBoolean("managed", true),
            autoWatts = o.optDouble("autoWatts", 0.0),
            applied = o.optString("applied", ""),
            appliedAt = o.optLong("appliedAt", 0L),
            persisted = o.optBoolean("persisted", false)
        )
    }
}

/**
 * Device name parser. Tokens may come in any order, separated by a space,
 * `_` or `-`, case-insensitive (except two-letter areas, which must be in
 * capitals so that words like "at" or "de" are not taken for areas):
 *
 *   Boiler DE 4h 2000W max0.20        ← recommended short form
 *   Scooter_DE_4_hours_1500W_limit_0.20  ← legacy form, still works
 *
 *   area     LV, EE, SE3, NO1 …
 *   hours    4h, 2.5h, 4 hours, 4ч
 *   power    1500W, 2kW, 1.5kW
 *   max      max0.20, limit 0.20, threshold 0.2, <0.20, ≤0,20
 */
object DeviceName {
    /** Example shown in UI hints. */
    const val EXAMPLE = "Boiler DE 4h 2000W max0.20"

    /** Nord Pool areas that can be chosen manually. */
    val REGIONS = listOf(
        "LV", "EE", "LT", "FI",
        "SE1", "SE2", "SE3", "SE4",
        "DK1", "DK2",
        "NO1", "NO2", "NO3", "NO4", "NO5",
        "PL", "DE", "AT", "BE", "NL", "FR"
    )

    fun isRegion(value: String): Boolean = value.uppercase() in REGIONS

    /** "aa:bb:cc:dd:ee:ff", "AABBCCDDEEFF" … → "AABBCCDDEEFF"; "" when it is not a MAC. */
    fun normalizeMac(raw: String?): String {
        val hex = raw.orEmpty().filter { it.isLetterOrDigit() }.uppercase()
        return if (hex.length == 12 && hex.all { it in "0123456789ABCDEF" }) hex else ""
    }

    /**
     * Whether the name carries at least one planner parameter. Such a device
     * was named for this app; anything else found on the network is listed
     * but left alone until the user chooses to control it.
     */
    fun hasParameters(name: String): Boolean =
        region(name) != null || hours(name) != null || limit(name) != null || watts(name) != null

    private const val SEP = """[_\-\s.,;|/]"""
    private const val NUM = """(\d+(?:[.,]\d+)?)"""

    /**
     * Area codes. Plain two-letter codes must be written in capitals: lower-case
     * "at", "be", "fr", "de", "nl", "pl" are ordinary words ("Pump at garage").
     * Codes with a digit (se3, no1, dk2) cannot be words, so any case is fine.
     */
    private val regionRe = run {
        val (numbered, plain) = REGIONS.partition { code -> code.any { it.isDigit() } }
        Regex(
            """(?:^|$SEP)((?i:${numbered.joinToString("|")})|${plain.joinToString("|")})(?=$|$SEP)"""
        )
    }
    private val hoursRe = Regex(
        """(?<![\d.,])$NUM[_\-\s]*(?:hours?|hrs?|h|часа|часов|час|ч)(?!\p{L})""",
        RegexOption.IGNORE_CASE
    )
    private val limitRe = Regex(
        """(?:limit|threshold|max|≤|<=|<)[_\-\s:=]*$NUM""",
        RegexOption.IGNORE_CASE
    )
    private val wattsRe = Regex(
        """(?<![\d.,])(\d{2,5})[_\-\s]*w(?:atts?)?(?!\p{L})""",
        RegexOption.IGNORE_CASE
    )
    private val kiloWattsRe = Regex(
        """(?<![\d.,])$NUM[_\-\s]*kw(?!\p{L})""",
        RegexOption.IGNORE_CASE
    )

    private fun num(text: String): Double? = text.replace(',', '.').toDoubleOrNull()

    fun region(name: String): String? =
        regionRe.find(name)?.groupValues?.get(1)?.uppercase()

    fun hours(name: String): Double? =
        hoursRe.find(name)?.groupValues?.get(1)?.let(::num)
            ?.takeIf { it in 0.25..24.0 }

    fun limit(name: String): Double? =
        limitRe.find(name)?.groupValues?.get(1)?.let(::num)
            ?.takeIf { it > 0.0 && it < 100.0 }

    fun watts(name: String): Double? {
        val w = wattsRe.find(name)?.groupValues?.get(1)?.let(::num)
            ?: kiloWattsRe.find(name)?.groupValues?.get(1)?.let(::num)?.times(1000.0)
        return w?.takeIf { it in 10.0..50_000.0 }
    }

    /** Filler words from older naming schemes ("…_on_limit_0.20") that carry no meaning. */
    private val fillerRe = Regex("""(?:^|(?<=$SEP))on(?=$|$SEP)""", RegexOption.IGNORE_CASE)

    /**
     * The human part of a device name with every recognised parameter removed:
     * "Scooter_LV_4_hours_on_limit_0.99" → "Scooter".
     */
    fun baseName(name: String): String {
        var rest = name
        for (re in listOf(limitRe, kiloWattsRe, wattsRe, hoursRe, regionRe, fillerRe)) {
            rest = rest.replace(re, " ")
        }
        return rest.split(Regex("""[_\-\s]+""")).filter { it.isNotBlank() }.joinToString(" ")
    }

    /**
     * Builds a device name in the recommended short form, e.g.
     * "Boiler DE 4h 2000W max0.20". Numbers always use a dot, regardless of
     * the phone's locale, so the parser reads them back unchanged.
     */
    fun compose(base: String, region: String, hours: Double, watts: Double, limit: Double): String {
        fun plain(v: Double) =
            if (v % 1.0 == 0.0) v.toLong().toString()
            else String.format(java.util.Locale.US, "%.2f", v).trimEnd('0')
        return buildList {
            add(base.ifBlank { "Plug" })
            if (region.isNotBlank()) add(region.uppercase())
            if (hours > 0.0) add("${plain(hours)}h")
            if (watts > 0.0) add("${watts.toLong()}W")
            if (limit > 0.0) add("max" + String.format(java.util.Locale.US, "%.2f", limit))
        }.joinToString(" ")
    }

    fun parse(
        ip: String,
        name: String,
        fallbackHours: Double,
        fallbackLimit: Double,
        fallbackWatts: Double = 0.0,
        kind: Int = PlugKind.TASMOTA,
        model: String = "",
        mac: String = ""
    ): Plug {
        val region = region(name)
        return Plug(
            ip = ip,
            name = name,
            region = region ?: "",
            hours = hours(name) ?: fallbackHours,
            limit = limit(name) ?: fallbackLimit,
            watts = watts(name) ?: fallbackWatts,
            kind = kind,
            model = model,
            mac = normalizeMac(mac),
            managed = hasParameters(name)
        )
    }
}
