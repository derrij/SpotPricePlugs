package com.spotpriceplugs

import android.app.Application
import android.content.Context
import androidx.annotation.StringRes

/**
 * Gives non-UI code (planner, network, background work) access to string
 * resources, so error messages and refresh results are in the user's language too.
 */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        private lateinit var instance: App
        val context: Context get() = instance
    }
}

/** Localised string for code outside Compose. */
fun str(@StringRes id: Int, vararg args: Any): String =
    if (args.isEmpty()) App.context.getString(id) else App.context.getString(id, *args)

/*
 * Deferred messages.
 *
 * Errors and refresh results are produced in background code and stored
 * (Plug.lastError, Store.lastRunInfo) or thrown as exception messages. Rendering
 * them there would freeze them in whatever language that process had — the
 * system language for a background job — and keep the old language after the
 * user switches. Instead [msg] stores the string resource name and its
 * arguments, and [resolveMessages] turns it into text when it is shown, with
 * the context of the screen showing it.
 *
 * Format: \u0001{"k":"resource_name","a":[args…]}\u0002, embeddable in longer
 * text. Arguments may themselves be deferred messages (JSON escapes the inner
 * control characters, so markers never nest at the text level).
 */
private const val MSG_START = '\u0001'
private const val MSG_END = '\u0002'
private val msgRe = Regex("\u0001(\\{.*?\\})\u0002")

/** A deferred, language-neutral message: see [resolveMessages]. */
fun msg(@StringRes id: Int, vararg args: Any): String {
    val a = org.json.JSONArray()
    args.forEach { arg ->
        when (arg) {
            is Int, is Long, is Double, is Boolean -> a.put(arg)
            is Float -> a.put(arg.toDouble())
            else -> a.put(arg.toString())
        }
    }
    val o = org.json.JSONObject()
        .put("k", App.context.resources.getResourceEntryName(id))
        .put("a", a)
    return "$MSG_START$o$MSG_END"
}

/** Replaces every deferred message in [text] with its text in [context]'s language. */
fun resolveMessages(text: String, context: Context = App.context): String {
    if (text.indexOf(MSG_START) < 0) return text
    return msgRe.replace(text) { m ->
        try {
            val o = org.json.JSONObject(m.groupValues[1])
            val id = context.resources.getIdentifier(o.getString("k"), "string", context.packageName)
            if (id == 0) return@replace ""
            val a = o.optJSONArray("a") ?: org.json.JSONArray()
            val args = Array<Any>(a.length()) { i ->
                when (val v = a.get(i)) {
                    is String -> resolveMessages(v, context)
                    else -> v
                }
            }
            if (args.isEmpty()) context.getString(id) else context.getString(id, *args)
        } catch (e: Exception) {
            ""
        }
    }
}
