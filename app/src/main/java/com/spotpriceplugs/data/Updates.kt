package com.spotpriceplugs.data

import com.spotpriceplugs.R
import com.spotpriceplugs.msg
import android.content.Context
import android.os.Build
import com.spotpriceplugs.domain.Support
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Update check via GitHub Releases (`releases/latest`).
 *
 * The release tag is the version number, e.g. `v1.3` or `1.3.1`, compared with
 * the installed versionName. Downloading happens in the browser: installing
 * the APK from inside the app would need an extra, risky permission.
 */
object Updates {
    private const val API = "https://api.github.com/repos/${Support.GITHUB_REPO}/releases/latest"

    /** Automatic checks at most every 12 hours (GitHub API limit: 60/hour). */
    const val CHECK_INTERVAL = 12 * 60 * 60_000L

    data class Release(
        val version: String,
        val pageUrl: String,
        /** Direct link to the .apk among the release assets, if present. */
        val apkUrl: String,
        val notes: String
    )

    fun currentVersion(context: Context): String = try {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, 0)
        }
        info.versionName ?: "0"
    } catch (e: Exception) {
        "0"
    }

    /** Latest release; null if there are none yet. Network errors throw. */
    suspend fun fetchLatest(): Release? = withContext(Dispatchers.IO) {
        val conn = URL(API).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "SpotPricePlugs-Android")
        try {
            when (conn.responseCode) {
                404 -> return@withContext null
                in 200..299 -> Unit
                else -> throw IllegalStateException(msg(R.string.e_http, "GitHub", conn.responseCode))
            }
            val root = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val tag = root.optString("tag_name").ifBlank { return@withContext null }
            val assets = root.optJSONArray("assets")
            var apk = ""
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.optJSONObject(i) ?: continue
                    if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                        apk = a.optString("browser_download_url")
                        break
                    }
                }
            }
            Release(
                version = tag.trimStart('v', 'V'),
                pageUrl = root.optString("html_url").ifBlank { Support.RELEASES_URL },
                apkUrl = apk,
                notes = root.optString("body").trim()
            )
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Checks releases and stores the result in Store. Returns the release
     * if it is newer than the installed version, otherwise null.
     */
    suspend fun check(context: Context): Release? {
        val store = Store(context)
        val latest = fetchLatest()
        store.lastUpdateCheck = System.currentTimeMillis()
        val current = currentVersion(context)
        return if (latest != null && isNewer(latest.version, current)) {
            store.updateVersion = latest.version
            store.updateUrl = latest.apkUrl.ifBlank { latest.pageUrl }
            latest
        } else {
            store.updateVersion = ""
            store.updateUrl = ""
            null
        }
    }

    fun isDue(context: Context): Boolean =
        System.currentTimeMillis() - Store(context).lastUpdateCheck > CHECK_INTERVAL

    /** "1.10" > "1.9", "1.3" == "1.3.0"; suffixes such as "-beta" are ignored. */
    fun isNewer(remote: String, local: String): Boolean {
        fun parts(v: String) = v.trim().trimStart('v', 'V')
            .split('.', '-', '+')
            .map { p -> p.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
        val r = parts(remote)
        val l = parts(local)
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}
