// SPDX-License-Identifier: AGPL-3.0-only
package com.shilapi.xcertplay

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** A single app in the ordered startup plan. */
data class AppLaunchEntry(
    val packageName: String,
    val label: String,
    val delaySeconds: Int = 0,
    val returnToDiPlay: Boolean = false,
) {
    fun sanitized(): AppLaunchEntry? = if (packageName.isBlank()) null else {
        copy(
            packageName = packageName.trim(),
            label = label.ifBlank { packageName },
            delaySeconds = delaySeconds.coerceIn(0, 3600),
        )
    }
}

object AppLaunchPreferences {
    private const val PREFS = "xcertplay_airplay"
    private const val KEY_PLAN = "app_launch_plan"

    fun load(context: Context): List<AppLaunchEntry> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PLAN, null) ?: return emptyList()
        return runCatching {
            val json = JSONArray(raw)
            buildList {
                for (i in 0 until json.length()) {
                    val item = json.optJSONObject(i) ?: continue
                    AppLaunchEntry(
                        packageName = item.optString("package"),
                        label = item.optString("label"),
                        delaySeconds = item.optInt("delay", 0),
                        returnToDiPlay = item.optBoolean("return", false),
                    ).sanitized()?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }

    fun save(context: Context, entries: List<AppLaunchEntry>) {
        val json = JSONArray()
        entries.mapNotNull(AppLaunchEntry::sanitized).forEach { entry ->
            json.put(JSONObject().apply {
                put("package", entry.packageName)
                put("label", entry.label)
                put("delay", entry.delaySeconds)
                put("return", entry.returnToDiPlay)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_PLAN, json.toString())
            .apply()
    }
}
