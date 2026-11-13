package com.spotpriceplugs.ui

import com.spotpriceplugs.resolveMessages
import android.app.Application
import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.spotpriceplugs.R
import com.spotpriceplugs.data.NordPool
import com.spotpriceplugs.data.Store
import com.spotpriceplugs.data.Updates
import com.spotpriceplugs.domain.Refresher
import com.spotpriceplugs.model.DeviceName
import com.spotpriceplugs.model.Plug
import com.spotpriceplugs.model.PlugKind
import com.spotpriceplugs.net.OpenBeken
import com.spotpriceplugs.net.Scanner
import com.spotpriceplugs.net.Shelly
import com.spotpriceplugs.net.Tasmota
import com.spotpriceplugs.work.Scheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.ZonedDateTime

/**
 * Text resolved against the configuration current at the moment it is shown,
 * so messages kept across an app-language change appear in the new language.
 * Arguments may themselves be [UiText].
 */
sealed interface UiText {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Raw(val text: String) : UiText

    fun resolve(context: Context): String = when (this) {
        is Raw -> resolveMessages(text, context)
        is Res -> {
            val a = args.map {
                when (it) {
                    is UiText -> it.resolve(context)
                    is String -> resolveMessages(it, context)
                    else -> it
                }
            }.toTypedArray()
            if (a.isEmpty()) context.getString(id) else context.getString(id, *a)
        }
    }
}

@Composable
fun UiText.resolve(): String {
    LocalConfiguration.current // re-resolve on configuration changes
    return resolve(LocalContext.current)
}

internal fun text(@StringRes id: Int, vararg args: Any): UiText = UiText.Res(id, args.toList())

/** Snackbar message. */
data class Message(val text: UiText, val long: Boolean = false)

data class Savings(val today: Double, val week: Double)

/**
 * User-facing text for an exception: common network failures get a localised
 * explanation; our own exceptions already carry localised messages.
 */
fun errorText(e: Throwable): UiText {
    generateSequence(e) { it.cause }.take(5).forEach { t ->
        when (t) {
            is SocketTimeoutException -> return text(R.string.e_net_timeout)
            is UnknownHostException -> return text(R.string.e_net_dns)
            is NoRouteToHostException -> return text(R.string.e_net_unreachable)
            is ConnectException -> return text(R.string.e_net_refused)
        }
    }
    if (e is IOException) return text(R.string.e_net_io)
    val message = e.message
    return if (message.isNullOrBlank()) text(R.string.e_unknown) else UiText.Raw(message)
}

/**
 * Folds a scan result into the saved plugs.
 *
 * A saved plug is recognised by its MAC, so it keeps the user's settings when
 * the router gives it another address ([moves]: old IP → new IP); plugs saved
 * before MACs were kept are matched by IP and learn their MAC. Known plugs
 * pick up the current name/firmware from the device (e.g. after "Copy name" +
 * rename). A saved plug that did not answer stays, unless its address now
 * belongs to another device ([dropped]): it can no longer be reached there,
 * and keeping it would send its timers to the wrong device.
 */
internal fun mergeScan(
    saved: List<Plug>,
    found: List<Plug>,
    moves: MutableMap<String, String>,
    dropped: MutableSet<String>
): List<Plug> {
    val byMac = saved.map { old ->
        if (old.mac.isBlank()) null else found.firstOrNull { it.mac == old.mac }
    }
    val claimed = byMac.filterNotNull().map { it.ip }.toSet()
    val matches = saved.mapIndexed { i, old ->
        byMac[i] ?: found.firstOrNull {
            it.ip == old.ip && it.ip !in claimed && (old.mac.isBlank() || it.mac.isBlank())
        }
    }
    val matchedIps = matches.filterNotNull().map { it.ip }.toSet()
    val foundIps = found.map { it.ip }.toSet()

    val known = saved.mapIndexedNotNull { i, old ->
        val fresh = matches[i]
        when {
            fresh != null -> {
                if (fresh.ip != old.ip) moves[old.ip] = fresh.ip
                old.copy(
                    ip = fresh.ip, name = fresh.name, kind = fresh.kind, model = fresh.model,
                    mac = fresh.mac.ifBlank { old.mac }
                )
            }
            old.ip in foundIps -> {
                dropped.add(old.ip)
                null
            }
            else -> old
        }
    }
    return (known + found.filter { it.ip !in matchedIps }).sortedBy { ipOrder(it.ip) }
}

/**
 * Screen state and long-running plug operations. Lives across activity
 * recreation (rotation, app-language change), so a refresh, scan or relay
 * command started by the user is not cancelled midway.
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val context: Context get() = getApplication()
    val store = Store(app)

    var plugs by mutableStateOf(store.plugs)
        private set
    /** Savings per plug ip; computed on reload, not on every recomposition. */
    var savings by mutableStateOf<Map<String, Savings>>(emptyMap())
        private set
    var lastRun by mutableLongStateOf(0L)
        private set
    var lastRunInfo by mutableStateOf("")
        private set
    var nextRun by mutableLongStateOf(0L)
        private set
    var savedMonth by mutableDoubleStateOf(0.0)
        private set
    /** Saved default region (the settings field may hold unsaved input). */
    var defaultRegion by mutableStateOf("")
        private set
    /** Version to offer in the banner; "" when none or dismissed. */
    var updateVersion by mutableStateOf("")
        private set

    var scanning by mutableStateOf(false)
        private set
    var scanned by mutableIntStateOf(0)
        private set
    var busy by mutableStateOf(false)
        private set
    var progress by mutableStateOf("")
        private set

    var checkingUpdate by mutableStateOf(false)
        private set
    var updateStatus by mutableStateOf<UiText?>(null)
        private set

    private val messageChannel = Channel<Message>(Channel.BUFFERED)
    val messages: Flow<Message> = messageChannel.receiveAsFlow()

    // The background refresh can run while the screen is open: follow every
    // write to the store. Held in a field: Android keeps these listeners weakly.
    private val listener = store.observe { reload() }

    init {
        reload()
        // Automatic check on open, at most every 12 hours.
        if (Updates.isDue(context)) checkUpdates(manual = false)
    }

    override fun onCleared() {
        store.unobserve(listener)
    }

    fun say(text: UiText, long: Boolean = false) {
        messageChannel.trySend(Message(text, long))
    }

    private fun say(@StringRes id: Int, vararg args: Any) = say(UiText.Res(id, args.toList()))

    fun reload() {
        val current = store.plugs
        plugs = current
        savings = current.associate {
            it.ip to Savings(store.savingsSince(it.ip, 1), store.savingsSince(it.ip, 7))
        }
        lastRun = store.lastRun
        lastRunInfo = store.lastRunInfo
        nextRun = store.nextRun
        savedMonth = store.totalSavings(30)
        defaultRegion = store.defaultRegion
        val version = store.updateVersion
        updateVersion = if (version.isNotBlank() && version != store.dismissedVersion) version else ""
    }

    /**
     * The relay may have been switched by timers or by the plug's own button,
     * so ask for the real state to keep the ON/OFF badge and button honest.
     */
    fun pollPower() {
        if (busy || scanning || plugs.isEmpty()) return
        viewModelScope.launch {
            try {
                val states = coroutineScope {
                    store.plugs.filter { it.managed }.map { plug ->
                        async {
                            plug.ip to if (PlugKind.isShelly(plug.kind)) {
                                Shelly.readState(plug.ip, plug.kind).first
                            } else {
                                Tasmota.readPower(plug.ip)
                            }
                        }
                    }.awaitAll()
                }.toMap()
                if (busy || scanning) return@launch
                store.mutatePlugs { current ->
                    current.map { p ->
                        val on = states[p.ip]
                        if (on != null && on != p.power) p.copy(power = on) else p
                    }
                }
                reload()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Best effort: the badge stays as it was.
            }
        }
    }

    fun checkUpdates(manual: Boolean) {
        if (checkingUpdate) return
        checkingUpdate = true
        if (manual) updateStatus = text(R.string.update_checking)
        viewModelScope.launch {
            try {
                val release = Updates.check(context)
                if (manual && release != null) store.dismissedVersion = ""
                reload()
                updateStatus = if (release != null) {
                    text(R.string.update_available, release.version)
                } else {
                    text(R.string.update_latest)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (manual) updateStatus = text(R.string.update_failed, errorText(e))
            } finally {
                checkingUpdate = false
            }
        }
    }

    fun dismissUpdate() {
        store.dismissedVersion = store.updateVersion
        reload()
    }

    fun startScan(prefix: String) {
        if (scanning || busy) return
        if (!Scanner.isPrefixValid(prefix)) {
            say(R.string.msg_subnet_invalid)
            return
        }
        store.subnet = prefix
        scanning = true
        scanned = 0
        viewModelScope.launch {
            try {
                val found = Scanner.scan(
                    prefix,
                    store.defaultHours,
                    store.defaultLimit,
                    store.defaultWatts
                ) { done -> scanned = done }
                val moves = LinkedHashMap<String, String>()
                val dropped = HashSet<String>()
                store.mutatePlugs { saved ->
                    moves.clear()
                    dropped.clear()
                    mergeScan(saved, found, moves, dropped)
                }
                store.moveSavings(moves, dropped)
                reload()
                if (found.isEmpty()) {
                    say(text(R.string.msg_none_found), long = true)
                } else {
                    say(R.string.msg_found, found.size)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                say(text(R.string.msg_scan_error, errorText(e)), long = true)
            } finally {
                scanning = false
            }
        }
    }

    fun refresh(only: Set<String>? = null) {
        if (busy || scanning) return
        if (plugs.isEmpty()) {
            say(R.string.msg_scan_first)
            return
        }
        busy = true
        viewModelScope.launch {
            try {
                val outcome = Refresher.run(context, only) { text -> progress = text }
                Scheduler.schedule(context)
                say(UiText.Raw(outcome.message), long = outcome.failCount > 0)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                say(text(R.string.msg_error, errorText(e)), long = true)
            } finally {
                reload()
                busy = false
                progress = ""
            }
        }
    }

    /** Validates and saves the settings card; false when the input is rejected. */
    fun saveSettings(
        vat: String,
        refreshAt: String,
        hours: String,
        limit: String,
        region: String,
        watts: String
    ): Boolean {
        val clock = parseClock(refreshAt)
        if (clock == null) {
            say(R.string.msg_time_invalid)
            return false
        }
        val (hour, minute) = clock
        val code = region.trim().uppercase()
        if (code.isNotBlank() && !DeviceName.isRegion(code)) {
            say(R.string.msg_region_unknown, code)
            return false
        }
        store.vat = parseVat(vat)
        store.refreshHour = hour
        store.refreshMinute = minute
        store.defaultHours = parseNum(hours, 4.0)
        store.defaultLimit = parseNum(limit, 0.20)
        store.defaultWatts = parseWatts(watts, store.defaultWatts)
        store.defaultRegion = code

        // The new publication time may come before the plan coverage ends,
        // so pull each device's "next" label forward.
        val daily = Refresher.nextDaily(
            ZonedDateTime.now(NordPool.zone), hour, minute
        ).toInstant().toEpochMilli()
        store.mutatePlugs { plugs ->
            plugs.map { plug -> if (plug.nextAt > daily) plug.copy(nextAt = daily) else plug }
        }

        Scheduler.schedule(context)
        reload()
        say(R.string.msg_settings_saved)
        return true
    }

    fun savePlug(plug: Plug, region: String, hours: Double, limit: Double, watts: Double) {
        store.updatePlug(plug.ip) {
            it.copy(region = region, hours = hours, limit = limit, watts = watts)
        }
        reload()
        say(R.string.msg_saved_plug, plug.displayName)
    }

    fun deletePlug(plug: Plug) {
        store.mutatePlugs { plugs -> plugs.filterNot { it.ip == plug.ip } }
        Scheduler.schedule(context)
        reload()
        say(R.string.msg_deleted_plug, plug.displayName)
    }

    /**
     * Whether the app controls this device. Nothing is sent to it here: a
     * newly controlled plug gets its schedule with the next update, and one
     * that is let go keeps the timers it has.
     */
    fun setManaged(plug: Plug, on: Boolean) {
        store.updatePlug(plug.ip) { it.copy(managed = on, nextAt = 0L, lastError = "") }
        Scheduler.schedule(context)
        reload()
    }

    /** Common wrapper for relay commands: button lock, message, errors. */
    private fun plugAction(plug: Plug, action: suspend () -> ((Plug) -> Plug), done: (Plug) -> Int) {
        if (busy || scanning) return
        busy = true
        viewModelScope.launch {
            try {
                // The command returns the change to make, applied to the plug
                // as it is stored now (a background refresh may have touched it).
                val change = action()
                store.updatePlug(plug.ip, change)
                reload()
                say(done(change(plug)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                say(text(R.string.msg_failed, errorText(e)), long = true)
            } finally {
                busy = false
            }
        }
    }

    /** Relay button in the card: switches the plug immediately. */
    fun setPower(plug: Plug, on: Boolean) = plugAction(
        plug,
        action = {
            val current = if (PlugKind.isShelly(plug.kind)) {
                Shelly.setPower(plug.ip, plug.kind, on)
            } else {
                Tasmota.setPower(plug.ip, on)
            }
            { it.copy(power = current) }
        },
        done = { if (it.power) R.string.msg_power_on else R.string.msg_power_off }
    )

    /** Schedule pause: the plug stops following the plan. */
    fun setTimersEnabled(plug: Plug, on: Boolean) = plugAction(
        plug,
        action = {
            when {
                PlugKind.isShelly(plug.kind) -> Shelly.setTimersEnabled(plug.ip, plug.kind, on)
                plug.kind != PlugKind.OPENBEKEN -> Tasmota.setTimersEnabled(plug.ip, on)
                !on -> OpenBeken.clear(plug.ip)
                else -> {
                    // Pausing removed the plug's clock events: write the plan again.
                    store.updatePlug(plug.ip) { it.copy(timersOn = true, appliedAt = 0L) }
                    val outcome = Refresher.run(context, setOf(plug.ip))
                    Scheduler.schedule(context)
                    if (outcome.failCount > 0) {
                        val error = store.plugs.firstOrNull { it.ip == plug.ip }?.lastError
                        throw IllegalStateException(error?.ifBlank { null } ?: outcome.message)
                    }
                }
            }
            { it.copy(timersOn = on) }
        },
        done = { if (on) R.string.msg_plan_on else R.string.msg_plan_off }
    )
}
