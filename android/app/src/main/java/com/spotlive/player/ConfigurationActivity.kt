package com.spotlive.player

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding

/**
 * Activity di configurazione iniziale
 *
 * Questa schermata viene mostrata:
 * 1. Al primo avvio (se ID_SCHERMO e USER_SCHERMO non sono configurati)
 * 2. Quando l'utente clicca "Impostazioni" dalla MainActivity
 *
 * Permette di inserire/modificare:
 * - ID_SCHERMO
 * - USER_SCHERMO
 */
class ConfigurationActivity : AppCompatActivity() {

    private lateinit var prefsManager: PreferencesManager
    private lateinit var editIdSchermo: EditText
    private lateinit var editUserSchermo: EditText
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button
    private lateinit var txtInfo: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefsManager = PreferencesManager(this)

        // Crea UI programmaticamente (più semplice che con XML per questo caso d'uso)
        createUI()

        // Se già configurato, mostra i valori attuali
        if (prefsManager.isConfigured()) {
            editIdSchermo.setText(prefsManager.getIdSchermo())
            editUserSchermo.setText(prefsManager.getUserSchermo())
            txtInfo.text = "Modifica configurazione schermo"
            btnCancel.visibility = View.VISIBLE
        } else {
            txtInfo.text = "Configurazione iniziale richiesta"
            btnCancel.visibility = View.GONE
        }

        // Handler pulsante salva
        btnSave.setOnClickListener {
            saveConfiguration()
        }

        // Handler pulsante annulla
        btnCancel.setOnClickListener {
            // Se è già configurato, torna alla MainActivity
            if (prefsManager.isConfigured()) {
                finish()
            } else {
                // Se non configurato, non può annullare
                Toast.makeText(this, "Configurazione obbligatoria", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun createUI() {
        // Container principale
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(32))
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xFF1E1E1E.toInt())
        }

        // Titolo
        txtInfo = TextView(this).apply {
            text = "Configurazione SpotLive"
            textSize = 24f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(dpToPx(16))
        }
        layout.addView(txtInfo)

        // Informazioni server (read-only)
        val txtServer = TextView(this).apply {
            text = "Server: ${Config.SERVER_HOST}"
            textSize = 14f
            setTextColor(0xFF888888.toInt())
            gravity = Gravity.CENTER
            setPadding(dpToPx(8))
        }
        layout.addView(txtServer)

        // Spazio
        layout.addView(createSpacer(24))

        // Label ID Schermo
        val lblIdSchermo = TextView(this).apply {
            text = "ID Schermo"
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(dpToPx(8))
        }
        layout.addView(lblIdSchermo)

        // Input ID Schermo
        editIdSchermo = EditText(this).apply {
            hint = "Inserisci ID Schermo (es: 567)"
            textSize = 18f
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF666666.toInt())
            setBackgroundColor(0xFF333333.toInt())
            setPadding(dpToPx(16))
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        layout.addView(editIdSchermo, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        // Spazio
        layout.addView(createSpacer(16))

        // Label User Schermo
        val lblUserSchermo = TextView(this).apply {
            text = "User Schermo"
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(dpToPx(8))
        }
        layout.addView(lblUserSchermo)

        // Input User Schermo
        editUserSchermo = EditText(this).apply {
            hint = "Inserisci User Schermo (es: cucciniello)"
            textSize = 18f
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF666666.toInt())
            setBackgroundColor(0xFF333333.toInt())
            setPadding(dpToPx(16))
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or
                    android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        layout.addView(editUserSchermo, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        // Spazio
        layout.addView(createSpacer(32))

        // Pulsante Salva
        btnSave = Button(this).apply {
            text = "Salva e Connetti"
            textSize = 18f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF4CAF50.toInt())
            setPadding(dpToPx(16))
        }
        layout.addView(btnSave, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dpToPx(56)
        ))

        // Spazio
        layout.addView(createSpacer(16))

        // Pulsante Annulla (visibile solo se già configurato)
        btnCancel = Button(this).apply {
            text = "Annulla"
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF666666.toInt())
            setPadding(dpToPx(16))
        }
        layout.addView(btnCancel, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dpToPx(48)
        ))

        setContentView(layout)
    }

    private fun createSpacer(dp: Int): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(dp)
            )
        }
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }

    private fun saveConfiguration() {
        val idSchermo = editIdSchermo.text.toString().trim()
        val userSchermo = editUserSchermo.text.toString().trim()

        // Validazione input
        if (idSchermo.isEmpty()) {
            Toast.makeText(this, "ID Schermo obbligatorio", Toast.LENGTH_SHORT).show()
            editIdSchermo.requestFocus()
            return
        }

        if (userSchermo.isEmpty()) {
            Toast.makeText(this, "User Schermo obbligatorio", Toast.LENGTH_SHORT).show()
            editUserSchermo.requestFocus()
            return
        }

        // Validazione formato ID (deve essere numerico)
        if (!idSchermo.matches(Regex("^[0-9]+$"))) {
            Toast.makeText(this, "ID Schermo deve essere numerico", Toast.LENGTH_SHORT).show()
            editIdSchermo.requestFocus()
            return
        }

        // Salva configurazione
        prefsManager.saveConfiguration(idSchermo, userSchermo)

        // Log per debug
        android.util.Log.d("SpotLive", "Configurazione salvata:")
        android.util.Log.d("SpotLive", "  ID Schermo: $idSchermo")
        android.util.Log.d("SpotLive", "  User Schermo: $userSchermo")
        android.util.Log.d("SpotLive", "  URL Programmazione: ${prefsManager.getProgrammationUrl()}")

        Toast.makeText(this, "Configurazione salvata!", Toast.LENGTH_SHORT).show()

        // Vai alla MainActivity
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish()
    }

    override fun onBackPressed() {
        // Se già configurato, permetti di tornare indietro
        if (prefsManager.isConfigured()) {
            super.onBackPressed()
        } else {
            // Se non configurato, non può tornare indietro
            Toast.makeText(this, "Configurazione obbligatoria", Toast.LENGTH_SHORT).show()
        }
    }
}
