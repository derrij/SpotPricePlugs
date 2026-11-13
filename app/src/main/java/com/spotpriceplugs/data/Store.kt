package com.spotpriceplugs.data

import android.content.Context
import android.content.SharedPreferences
import com.spotpriceplugs.domain.Vat
import com.spotpriceplugs.model.Plug
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import kotlin.math.roundToLong

class Store(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("betp", Context.MODE_PRIVATE)

    /**
     * Calls [onChange] whenever anything is written, including by the background
     * refresh. Keep the returned listener and pass it to [unobserve]: Android holds
     * these listeners weakly.
     */
    fun observe(onChange: () -> Unit): SharedPreferences.OnSharedPreferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> onChange() }
            .also(sp::registerOnSharedPreferenceChangeListener)

    fun unobserve(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        sp.unregisterOnSharedPreferenceChangeListener(listener)

    var subnet: String
        get() = sp.getString("subnet", "") ?: ""
        set(value) = sp.edit().putString("subnet", value).apply()

    /** VAT %, or [Vat.AUTO] (< 0) = automatic by region. */
    var vat: Double
        get() {
            if (!sp.contains("vatPct")) {
                // Migrate the old key: 21 was its default, so treat it as "auto";
                // any other value was set by the user on purpose.
                val old = sp.getFloat("vat", 21f)
                sp.edit()
                    .putFloat("vatPct", if (old == 21f) Vat.AUTO.toFloat() else old)
                    .remove("vat")
                    .apply()
            }
            return sp.getFloat("vatPct", Vat.AUTO.toFloat()).toDouble()
        }
        set(value) = sp.edit().putFloat("vatPct", value.toFloat()).apply()

    var refreshHour: Int
        get() = sp.getInt("refreshHour", 14)
        set(value) = sp.edit().putInt("refreshHour", value).apply()

    var refreshMinute: Int
        get() = sp.getInt("refreshMinute", 5)
        set(value) = sp.edit().putInt("refreshMinute", value).apply()

    var defaultHours: Double
        get() = sp.getFloat("defaultHours", 4f).toDouble()
        set(value) = sp.edit().putFloat("defaultHours", value.toFloat()).apply()

    var defaultLimit: Double
        get() = sp.getFloat("defaultLimit", 0.20f).toDouble()
        set(value) = sp.edit().putFloat("defaultLimit", value.toFloat()).apply()

    /** Area for devices whose name has none ("" = not set). */
    var defaultRegion: String
        get() = sp.getString("defaultRegion", "") ?: ""
        set(value) = sp.edit().putString("defaultRegion", value).apply()

    /** Default load power, W, for plugs without W in the name (0 = not set). */
    var defaultWatts: Double
        get() = sp.getFloat("defaultWatts", 0f).toDouble()
        set(value) = sp.edit().putFloat("defaultWatts", value.toFloat()).apply()

    var lastRun: Long
        get() = sp.getLong("lastRun", 0L)
        set(value) = sp.edit().putLong("lastRun", value).apply()

    var lastRunInfo: String
        get() = sp.getString("lastRunInfo", "") ?: ""
        set(value) = sp.edit().putString("lastRunInfo", value).apply()

    /** When the next refresh will happen (shown in the UI). */
    var nextRun: Long
        get() = sp.getLong("nextRun", 0L)
        set(value) = sp.edit().putLong("nextRun", value).apply()

    /** Delayed retry: tomorrow's prices are not published yet. */
    var retryAt: Long
        get() = sp.getLong("retryAt", 0L)
        set(value) = sp.edit().putLong("retryAt", value).apply()

    var retryCount: Int
        get() = sp.getInt("retryCount", 0)
        set(value) = sp.edit().putInt("retryCount", value).apply()

    /** Local date (ISO) that [retryCount] counts for; a new day starts from zero. */
    var retryDay: String
        get() = sp.getString("retryDay", "") ?: ""
        set(value) = sp.edit().putString("retryDay", value).apply()

    /** When GitHub was last asked for a new version. */
    var lastUpdateCheck: Long
        get() = sp.getLong("lastUpdateCheck", 0L)
        set(value) = sp.edit().putLong("lastUpdateCheck", value).apply()

    /** Available newer version ("" = none) and its link. */
    var updateVersion: String
        get() = sp.getString("updateVersion", "") ?: ""
        set(value) = sp.edit().putString("updateVersion", value).apply()

    var updateUrl: String
        get() = sp.getString("updateUrl", "") ?: ""
        set(value) = sp.edit().putString("updateUrl", value).apply()

    /** Version already announced by a notification; never repeated. */
    var notifiedVersion: String
        get() = sp.getString("notifiedVersion", "") ?: ""
        set(value) = sp.edit().putString("notifiedVersion", value).apply()

    /** Version whose banner the user dismissed with "Later". */
    var dismissedVersion: String
        get() = sp.getString("dismissedVersion", "") ?: ""
        set(value) = sp.edit().putString("dismissedVersion", value).apply()

    var plugs: List<Plug>
        get() {
            val raw = sp.getString("plugs", "[]") ?: "[]"
            return try {
                val arr = JSONArray(raw)
                (0 until arr.length()).map { Plug.fromJson(arr.getJSONObject(it)) }
            } catch (e: Exception) {
                emptyList()
            }
        }
        set(value) {
            val arr = JSONArray()
            value.forEach { arr.put(it.toJson()) }
            sp.edit().putString("plugs", arr.toString()).apply()
        }

    /**
     * Read-modify-write of the plug list as one step. The UI thread and the
     * background refresh both change the list; without the lock an edit made
     * while the other side was between its read and its write would be lost.
     */
    fun mutatePlugs(transform: (List<Plug>) -> List<Plug>): List<Plug> = synchronized(LOCK) {
        val before = plugs
        val after = transform(before)
        if (after != before) plugs = after
        after
    }

    /** Changes one plug in place; nothing happens when it is no longer in the list. */
    fun updatePlug(ip: String, change: (Plug) -> Plug) {
        mutatePlugs { list -> list.map { if (it.ip == ip) change(it) else it } }
    }

    /**
     * Savings history: one stored estimate per day rather than a running total;
     * every recalculation overwrites "today" with a fresh figure.
     */
    fun recordSavings(ip: String, day: LocalDate, euros: Double) = synchronized(LOCK) {
        val key = "sav_$ip"
        val map = loadSavings(key)
        map[day.toString()] = (euros * 1000).roundToLong() / 1000.0
        while (map.size > 90) {
            map.keys.minOrNull()?.let { map.remove(it) }
        }
        val json = JSONObject().apply { map.forEach { (d, v) -> put(d, v) } }
        sp.edit().putString(key, json.toString()).apply()
    }

    /**
     * Plugs changed address ([moves]: old IP → new IP) or left the list
     * ([dropped]): their savings history follows them. All moves are read
     * before anything is written, so two plugs may swap addresses.
     */
    fun moveSavings(moves: Map<String, String>, dropped: Set<String> = emptySet()) = synchronized(LOCK) {
        if (moves.isEmpty() && dropped.isEmpty()) return@synchronized
        val carried = moves.mapNotNull { (from, to) ->
            sp.getString("sav_$from", null)?.let { to to it }
        }
        val edit = sp.edit()
        (moves.keys + dropped).forEach { edit.remove("sav_$it") }
        carried.forEach { (to, json) -> edit.putString("sav_$to", json) }
        edit.apply()
    }

    /** Savings over the last `days` days, today included. */
    fun savingsSince(ip: String, days: Int): Double {
        val map = loadSavings("sav_$ip")
        val today = LocalDate.now(NordPool.zone).toString()
        val cutoff = LocalDate.now(NordPool.zone)
            .minusDays(((days - 1).coerceAtLeast(0)).toLong()).toString()
        return map.entries
            .filter { it.key >= cutoff && it.key <= today }
            .sumOf { it.value }
    }

    /** Total savings of all plugs over `days` days. */
    fun totalSavings(days: Int): Double = plugs.sumOf { savingsSince(it.ip, days) }

    private fun loadSavings(key: String): MutableMap<String, Double> {
        val raw = sp.getString(key, "{}") ?: "{}"
        val map = mutableMapOf<String, Double>()
        return try {
            val json = JSONObject(raw)
            val names = json.names()
            if (names != null) {
                for (i in 0 until names.length()) {
                    val k = names.getString(i)
                    map[k] = json.getDouble(k)
                }
            }
            map
        } catch (e: Exception) {
            map
        }
    }

    private companion object {
        /** One lock for every Store instance: they all share the same prefs file. */
        val LOCK = Any()
    }
}
