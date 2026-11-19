package com.reader.oghma.settings

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.reader.oghma.LoginActivity
import com.reader.oghma.reader.EasterEgg
import com.reader.oghma.reader.MlKitTranslationEngine
import com.reader.oghma.reader.TranslationLanguages
import com.reader.oghma.reader.TranslationPrefs
import java.util.Locale

/**
 * The Settings screen. Sections:
 *
 *  - Account: the signed-in email and Log out.
 *  - Translation:
 *
 *  - which languages text is translated INTO (the ones shown when you tap "Translate"),
 *  - the language your books are in (used when it cannot be detected, e.g. for a single word),
 *  - language packs: download them to translate offline, or delete them to free space.
 *
 * Built from plain views so it needs no layout file.
 */
class SettingsScreen(
    context: Context,
    private val listener: Listener
) : LinearLayout(context) {

    interface Listener {

        fun onBack()
    }

    private val background = Color.parseColor("#1E1E2E")
    private val headerColor = Color.parseColor("#181825")
    private val surface = Color.parseColor("#313244")
    private val textPrimary = Color.parseColor("#CDD6F4")
    private val textSecondary = Color.parseColor("#A6ADC8")
    private val accent = Color.parseColor("#CBA6F7")
    private val good = Color.parseColor("#A6E3A1")
    private val link = Color.parseColor("#89B4FA")

    private val engine = MlKitTranslationEngine()

    private val supported = TranslationLanguages.sortedByName(engine.supportedLanguages())

    private val targets = TranslationPrefs.getTargets(context).toMutableList()

    private var downloaded: Set<String> = emptySet()

    private val busy = HashSet<String>()

    private class Row(val check: CheckBox, val action: TextView)

    private val rows = LinkedHashMap<String, Row>()

    // True while we set checkboxes from code, so the listener does not treat it as a tap.
    private var syncing = false

    private val sourceValue = TextView(context)

    private val summary = TextView(context)


    init {

        orientation = VERTICAL

        setBackgroundColor(background)

        // Keep clear of the status bar and navigation bar.
        fitsSystemWindows = true

        addView(header())

        val scroll = ScrollView(context)

        val body = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(32))
        }

        buildAccountSection(body)

        buildTranslationSection(body)

        scroll.addView(body)

        addView(scroll, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        updateSummary()

        refreshPacks()
    }


    override fun onDetachedFromWindow() {

        engine.close()

        super.onDetachedFromWindow()
    }


    // ---------------------------------------------------------------------

    private fun header(): View =
        LinearLayout(context).apply {

            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(headerColor)
            setPadding(dp(4), dp(8), dp(16), dp(8))

            addView(
                TextView(context).apply {
                    text = "\u2190"
                    setTextColor(textSecondary)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                    gravity = Gravity.CENTER
                    contentDescription = "Back"
                    setOnClickListener { listener.onBack() }
                },
                LayoutParams(dp(48), dp(48))
            )

            addView(
                TextView(context).apply {
                    text = "Settings"
                    setTextColor(textPrimary)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                    setTypeface(typeface, Typeface.BOLD)
                },
                LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            )
        }


    private fun buildAccountSection(body: LinearLayout) {

        body.addView(sectionTitle("Account"))

        val user = FirebaseAuth.getInstance().currentUser

        val email = user?.email

        val emailText =
            when {
                user == null -> "Not signed in"
                !email.isNullOrEmpty() -> email
                else -> "Signed in"
            }

        val card = card()

        card.addView(
            TextView(context).apply {
                text = emailText
                setTextColor(textPrimary)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            }
        )

        body.addView(card)

        body.addView(
            TextView(context).apply {
                text = "Log out"
                setTextColor(textPrimary)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                setTypeface(typeface, Typeface.BOLD)
                setPadding(dp(14), dp(16), dp(14), dp(16))
                isClickable = true
                isFocusable = true
                setOnClickListener { confirmLogout() }
            },
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(8) }
        )
    }


    private fun confirmLogout() {

        AlertDialog.Builder(context)
            .setTitle("Log out?")
            .setMessage("Are you sure you want to log out of Oghma?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Log out") { _, _ -> logout() }
            .show()
    }


    private fun logout() {

        FirebaseAuth.getInstance().signOut()

        val intent = Intent(context, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        context.startActivity(intent)

        (context as? android.app.Activity)?.finish()
    }


    private fun buildTranslationSection(body: LinearLayout) {

        body.addView(sectionTitle("Translation"))

        body.addView(
            note(
                "Pick the languages you want selected text translated into. " +
                        "Download a language pack to translate without internet " +
                        "(about 30 MB each, it runs on your phone)."
            )
        )

        // ---- language of my books ----
        body.addView(
            card().apply {

                addView(
                    TextView(context).apply {
                        text = "Language of my books"
                        setTextColor(textPrimary)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    }
                )

                addView(
                    small("Used when the language of a selection cannot be detected, such as a single word.")
                )

                sourceValue.apply {
                    setTextColor(link)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    setTypeface(typeface, Typeface.BOLD)
                    setPadding(0, dp(8), 0, 0)
                    setOnClickListener { pickSourceFallback() }
                }

                addView(sourceValue)
            }
        )

        updateSourceValue()

        // ---- targets + packs ----
        body.addView(subTitle("Translate into"))

        body.addView(summary)

        supported.forEach { code -> body.addView(languageRow(code)) }

        body.addView(
            small(
                "Translation by Google ML Kit, running on your phone. " +
                        "Selected text is not sent anywhere once a pack is downloaded."
            ).apply { setPadding(0, dp(20), 0, 0) }
        )
    }


    private fun languageRow(code: String): View {

        val check = CheckBox(context).apply {
            text = TranslationLanguages.label(code)
            setTextColor(textPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            // Not usable until the pack is downloaded; updatePackButtons() enables it.
            isChecked = false
            isEnabled = false
            alpha = 0.4f
            buttonTintList = android.content.res.ColorStateList.valueOf(accent)
            setOnCheckedChangeListener { _, checked ->

                if (syncing) return@setOnCheckedChangeListener

                if (checked) {
                    if (code !in targets) targets.add(code)
                } else {
                    targets.remove(code)
                }

                TranslationPrefs.setTargets(context, targets)

                updateSummary()
            }
        }

        val action = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(8), dp(4), dp(8))
            setOnClickListener { onPackAction(code) }
        }

        rows[code] = Row(check, action)

        return LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(2))
            addView(check, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(action)
        }
    }


    // ---------------------------------------------------------------------
    // Language packs
    // ---------------------------------------------------------------------

    private fun refreshPacks() {

        engine.downloadedLanguages { set ->

            downloaded = set ?: emptySet()

            updatePackButtons()
        }
    }

    private fun updatePackButtons() {

        syncing = true

        rows.forEach { (code, row) ->

            val ready = code in downloaded && code !in busy

            // Only a downloaded pack can be ticked; the others stay greyed out.
            row.check.isEnabled = ready
            row.check.alpha = if (ready) 1f else 0.4f
            row.check.isChecked = ready && code in targets

            when {

                code in busy -> {
                    row.action.text = "Working\u2026"
                    row.action.setTextColor(textSecondary)
                }

                code in downloaded -> {
                    row.action.text = "Remove"
                    row.action.setTextColor(link)
                }

                else -> {
                    row.action.text = "Download"
                    row.action.setTextColor(link)
                }
            }
        }

        syncing = false

        updateSummary()
    }

    private fun onPackAction(code: String) {

        if (code in busy) return

        // Easter egg: this pack shows a message instead of downloading.
        if (EasterEgg.blocks(code) && code !in downloaded) {

            AlertDialog.Builder(context)
                .setMessage(EasterEgg.MESSAGE)
                .setPositiveButton("OK", null)
                .show()

            return
        }

        val name = TranslationLanguages.displayName(code)

        if (code in downloaded) {

            AlertDialog.Builder(context)
                .setTitle("Delete the $name pack?")
                .setMessage("You can download it again later. Translating $name will need internet until you do.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete") { _, _ ->

                    busy.add(code)

                    updatePackButtons()

                    engine.delete(code) { ok ->

                        busy.remove(code)

                        if (ok) {

                            targets.remove(code)

                            TranslationPrefs.setTargets(context, targets)

                        } else {

                            toast("Couldn't delete the $name pack.")
                        }

                        refreshPacks()
                    }
                }
                .show()

            return
        }

        busy.add(code)

        updatePackButtons()

        engine.download(code) { ok, error ->

            busy.remove(code)

            if (ok) {
                toast("$name pack downloaded.")
            } else {
                toast("Couldn't download the $name pack: ${error ?: "check your connection"}")
            }

            refreshPacks()
        }
    }


    // ---------------------------------------------------------------------
    // Language of my books
    // ---------------------------------------------------------------------

    private fun pickSourceFallback() {

        val current = TranslationPrefs.getSourceFallback(context)

        val labels = ArrayList<String>()

        labels.add("Automatic (${TranslationLanguages.displayName(automaticSource())})")

        supported.forEach { labels.add(TranslationLanguages.label(it)) }

        val checked = if (current == null) 0 else supported.indexOf(current) + 1

        AlertDialog.Builder(context)
            .setTitle("Language of my books")
            .setSingleChoiceItems(labels.toTypedArray(), checked) { dialog, which ->

                TranslationPrefs.setSourceFallback(
                    context,
                    if (which == 0) null else supported[which - 1]
                )

                dialog.dismiss()

                updateSourceValue()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun automaticSource() =
        TranslationLanguages.defaultSource(Locale.getDefault().language, supported)

    private fun updateSourceValue() {

        val chosen = TranslationPrefs.getSourceFallback(context)

        sourceValue.text =
            if (chosen == null) {
                "Automatic (${TranslationLanguages.displayName(automaticSource())}) \u25BE"
            } else {
                "${TranslationLanguages.label(chosen)} \u25BE"
            }
    }

    private fun updateSummary() {

        // Only downloaded packs can be in use.
        val active = targets.filter { it in downloaded }

        summary.apply {
            setTextColor(if (active.isEmpty()) accent else textSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, 0, 0, dp(6))
            text =
                if (active.isEmpty()) {
                    "Nothing chosen yet. Download a language pack, then tick it to use Translate."
                } else {
                    "Chosen: " + active.joinToString(", ") { TranslationLanguages.displayName(it) }
                }
        }
    }


    // ---------------------------------------------------------------------
    // Small view helpers
    // ---------------------------------------------------------------------

    private fun sectionTitle(text: String) =
        TextView(context).apply {
            this.text = text
            setTextColor(accent)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(12), 0, dp(4))
        }

    private fun subTitle(text: String) =
        TextView(context).apply {
            this.text = text
            setTextColor(textPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(18), 0, dp(2))
        }

    private fun note(text: String) =
        TextView(context).apply {
            this.text = text
            setTextColor(textSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setLineSpacing(0f, 1.1f)
            setPadding(0, 0, 0, dp(12))
        }

    private fun small(text: String) =
        TextView(context).apply {
            this.text = text
            setTextColor(textSecondary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(2), 0, 0)
        }

    private fun card() =
        LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply {
                setColor(surface)
                cornerRadius = dp(14).toFloat()
            }
        }

    private fun toast(message: String) =
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()

    private fun dp(value: Int): Int =
        Math.round(value * context.resources.displayMetrics.density)
}