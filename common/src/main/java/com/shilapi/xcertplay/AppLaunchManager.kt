// SPDX-License-Identifier: AGPL-3.0-only
package com.shilapi.xcertplay

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log

/** An installed app with a launcher activity that can be added to a startup plan. */
data class AppLaunchCandidate(val packageName: String, val label: String)

/** Executes an ordered launch plan while DiPlay is in the foreground. */
object AppLaunchManager {
    private const val TAG = "AppLaunchManager"
    private const val RETURN_DELAY_MS = 500L

    fun installedApps(context: Context): List<AppLaunchCandidate> {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(launcher, PackageManager.MATCH_DEFAULT_ONLY)
            .mapNotNull { resolved ->
                val pkg = resolved.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == context.packageName) return@mapNotNull null
                val label = runCatching { resolved.loadLabel(pm).toString() }
                    .getOrNull()?.takeIf(String::isNotBlank) ?: pkg
                AppLaunchCandidate(pkg, label)
            }
            .distinctBy(AppLaunchCandidate::packageName)
            .sortedBy { it.label.lowercase() }
    }

    /** Each delay is the wait before launching that entry. Return-to-DiPlay is best effort. */
    fun launchSequence(
        context: Context,
        plan: List<AppLaunchEntry> = AppLaunchPreferences.load(context),
        onAppLaunchFailed: ((AppLaunchEntry) -> Unit)? = null,
        onComplete: (() -> Unit)? = null,
    ) {
        val entries = plan.mapNotNull(AppLaunchEntry::sanitized)
        if (entries.isEmpty()) {
            onComplete?.invoke()
            return
        }
        val appContext = context.applicationContext
        val handler = Handler(Looper.getMainLooper())
        var index = 0
        lateinit var next: Runnable
        next = Runnable {
            if (index >= entries.size) {
                onComplete?.invoke()
                return@Runnable
            }
            val entry = entries[index++]
            val launchIntent = appContext.packageManager.getLaunchIntentForPackage(entry.packageName)
            if (launchIntent == null) {
                Log.w(TAG, "No launcher activity for ${entry.packageName}")
                onAppLaunchFailed?.invoke(entry)
                scheduleNext(handler, next, entries, index)
                return@Runnable
            }
            val launched = runCatching {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                appContext.startActivity(launchIntent)
            }.onFailure {
                Log.w(TAG, "Could not launch ${entry.packageName}", it)
                onAppLaunchFailed?.invoke(entry)
            }.isSuccess
            if (entry.returnToDiPlay && launched) {
                handler.postDelayed({ bringDiPlayForward(appContext) }, RETURN_DELAY_MS)
            }
            scheduleNext(handler, next, entries, index)
        }
        handler.postDelayed(next, entries.first().delaySeconds * 1000L)
    }

    private fun scheduleNext(handler: Handler, next: Runnable, entries: List<AppLaunchEntry>, nextIndex: Int) {
        if (nextIndex < entries.size) {
            handler.postDelayed(next, entries[nextIndex].delaySeconds.coerceIn(0, 3600) * 1000L)
        } else {
            handler.post(next)
        }
    }

    private fun bringDiPlayForward(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        runCatching {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            context.startActivity(intent)
        }.onFailure { Log.w(TAG, "Could not return to DiPlay", it) }
    }
}
