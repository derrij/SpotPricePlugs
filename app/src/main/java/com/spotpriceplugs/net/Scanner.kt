package com.spotpriceplugs.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.spotpriceplugs.model.DeviceName
import com.spotpriceplugs.model.Plug
import com.spotpriceplugs.model.PlugKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

object Scanner {
    /** First three octets of the local network, e.g. "10.85.228". */
    fun localPrefix(context: Context): String? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return null
        val network = cm.activeNetwork ?: return null
        val link = cm.getLinkProperties(network) ?: return null
        val addr = link.linkAddresses
            .map { it.address }
            .firstOrNull { it is Inet4Address && !it.isLoopbackAddress } as? Inet4Address
            ?: return null
        return addr.address.take(3).joinToString(".") { (it.toInt() and 0xFF).toString() }
    }

    /**
     * Whether the phone is on any network the plugs could be reached through:
     * Wi-Fi, Ethernet or a VPN (which may lead home). On mobile data alone a
     * background refresh would only time out on every plug.
     */
    fun lanAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        return try {
            @Suppress("DEPRECATION")
            cm.allNetworks.any { network ->
                val caps = cm.getNetworkCapabilities(network) ?: return@any false
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            }
        } catch (e: SecurityException) {
            true
        }
    }

    /**
     * Probes hosts 1..254 of the subnet and returns every Tasmota, OpenBeken
     * or Shelly found; devices without an area in their name are listed as unconfigured.
     */
    suspend fun scan(
        prefix: String,
        fallbackHours: Double,
        fallbackLimit: Double,
        fallbackWatts: Double = 0.0,
        onProgress: (Int) -> Unit = {}
    ): List<Plug> = withContext(Dispatchers.IO) {
        val found = Collections.synchronizedList(ArrayList<Plug>())
        val done = AtomicInteger(0)
        val semaphore = Semaphore(64)

        coroutineScope {
            (1..254).map { host ->
                async {
                    semaphore.withPermit {
                        val ip = "$prefix.$host"
                        // Tasmota (or OpenBeken, same endpoint) first; if it does
                        // not answer, Shelly (Gen2/3 → Gen1).
                        val tasmota = Tasmota.status(ip)
                        val scan = if (tasmota != null) {
                            DeviceName.parse(
                                ip, tasmota.name, fallbackHours, fallbackLimit, fallbackWatts,
                                if (tasmota.openBeken) PlugKind.OPENBEKEN else PlugKind.TASMOTA,
                                if (tasmota.openBeken) tasmota.version else "",
                                tasmota.mac
                            )
                        } else {
                            val shelly = Shelly.detect(ip)
                            if (shelly != null) {
                                DeviceName.parse(
                                    ip, shelly.name, fallbackHours, fallbackLimit, fallbackWatts,
                                    shelly.kind, shelly.model, shelly.mac
                                )
                            } else {
                                null
                            }
                        }
                        if (scan != null) found.add(scan)
                        onProgress(done.incrementAndGet())
                    }
                }
            }.awaitAll()
        }

        found.sortedBy { plug ->
            plug.ip.split(".").mapNotNull { it.toIntOrNull() }
                .fold(0L) { acc, part -> acc * 256 + part }
        }
    }

    fun isPrefixValid(value: String): Boolean {
        val parts = value.split(".")
        return parts.size == 3 && parts.all { p ->
            p.isNotEmpty() && p.toIntOrNull()?.let { it in 0..255 } == true
        }
    }
}
