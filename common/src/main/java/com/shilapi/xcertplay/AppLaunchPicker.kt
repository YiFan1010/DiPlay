// SPDX-License-Identifier: AGPL-3.0-only
package com.shilapi.xcertplay

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** UI for choosing apps and editing their order, delay, and return preference. */
object AppLaunchPicker {
    fun show(context: Context, onSaved: (() -> Unit)? = null) {
        val entries = AppLaunchPreferences.load(context).toMutableList()
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 16), dp(context, 8), dp(context, 16), dp(context, 8))
        }
        val scroll = ScrollView(context).apply { addView(body) }
        val dialog = AlertDialog.Builder(context)
            .setTitle("App launch plan")
            .setView(scroll)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()

        fun render() {
            body.removeAllViews()
            body.addView(Button(context).apply {
                text = "Add app"
                setOnClickListener { chooseApp(context, entries) { render() } }
            }, fullWidth())
            if (entries.isEmpty()) {
                body.addView(TextView(context).apply {
                    text = "No apps selected."
                    setPadding(0, dp(context, 16), 0, dp(context, 12))
                })
                return
            }
            entries.forEachIndexed { index, entry ->
                val card = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(context, 10), dp(context, 8), dp(context, 10), dp(context, 8))
                    setBackgroundColor(0x18202020)
                }
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                row.addView(ImageView(context).apply {
                    runCatching { setImageDrawable(context.packageManager.getApplicationIcon(entry.packageName)) }
                    contentDescription = entry.label
                }, LinearLayout.LayoutParams(dp(context, 36), dp(context, 36)))
                row.addView(TextView(context).apply {
                    text = "${index + 1}. ${entry.label}"
                    textSize = 16f
                    setPadding(dp(context, 8), 0, dp(context, 4), 0)
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(actionButton(context, "↑") {
                    if (index > 0) entries.add(index - 1, entries.removeAt(index))
                    render()
                })
                row.addView(actionButton(context, "↓") {
                    if (index < entries.lastIndex) entries.add(index + 1, entries.removeAt(index))
                    render()
                })
                row.addView(actionButton(context, "×") {
                    entries.removeAt(index)
                    render()
                })
                card.addView(row)
                card.addView(Button(context).apply {
                    text = "Delay before next app: ${entry.delaySeconds}s"
                    setOnClickListener { editDelay(context, entry) { seconds ->
                        entries[index] = entries[index].copy(delaySeconds = seconds)
                        render()
                    } }
                }, fullWidth())
                card.addView(CheckBox(context).apply {
                    text = "Return to DiPlay after this app"
                    isChecked = entry.returnToDiPlay
                    setOnCheckedChangeListener { _, checked ->
                        entries[index] = entries[index].copy(returnToDiPlay = checked)
                    }
                })
                body.addView(card, fullWidth().apply { topMargin = dp(context, 8) })
            }
        }

        render()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                AppLaunchPreferences.save(context, entries)
                Toast.makeText(context, "App launch plan saved", Toast.LENGTH_SHORT).show()
                onSaved?.invoke()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun chooseApp(context: Context, entries: MutableList<AppLaunchEntry>, changed: () -> Unit) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val choices = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .mapNotNull { resolved ->
                val info = resolved.activityInfo ?: return@mapNotNull null
                if (info.packageName == context.packageName || entries.any { it.packageName == info.packageName }) {
                    return@mapNotNull null
                }
                val label = runCatching { resolved.loadLabel(context.packageManager).toString() }
                    .getOrNull()?.takeIf(String::isNotBlank) ?: info.packageName
                AppLaunchEntry(info.packageName, label)
            }
            .distinctBy(AppLaunchEntry::packageName)
            .sortedBy { it.label.lowercase() }
        if (choices.isEmpty()) {
            AlertDialog.Builder(context).setMessage("No additional launchable apps found.")
                .setPositiveButton("OK", null).show()
            return
        }
        AlertDialog.Builder(context).setTitle("Choose an app")
            .setItems(choices.map(AppLaunchEntry::label).toTypedArray()) { _, which ->
                entries.add(choices[which])
                changed()
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun editDelay(context: Context, entry: AppLaunchEntry, onChanged: (Int) -> Unit) {
        val input = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
            setText(entry.delaySeconds.toString())
            hint = "0"
        }
        AlertDialog.Builder(context).setTitle("Delay in seconds")
            .setMessage("Wait before launching the next app.")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                onChanged(input.text.toString().toIntOrNull()?.coerceIn(0, 3600) ?: 0)
            }.show()
    }

    private fun actionButton(context: Context, title: String, onClick: () -> Unit) = Button(context).apply {
        text = title
        minWidth = dp(context, 40)
        setOnClickListener { onClick() }
    }

    private fun fullWidth() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )

    private fun dp(context: Context, value: Int) =
        (value * context.resources.displayMetrics.density).toInt()
}
