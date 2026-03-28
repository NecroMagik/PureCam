package com.example.purecam

import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Настройки"

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)

            // Устанавливаем версию из BuildConfig
            val versionPref = findPreference<Preference>("version")
            versionPref?.summary = "Pure Camera ${BuildConfig.VERSION_NAME}"

            // Обновляем информацию о теме
            val themePref = findPreference<Preference>("theme_info")
            val isDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            themePref?.summary = if (isDark) {
                "🌙 Темная тема (следует за системой)"
            } else {
                "☀️ Светлая тема (следует за системой)"
            }

            // Обработчик для HDR режима
            val hdrPref = findPreference<SwitchPreferenceCompat>("hdr_mode")
            hdrPref?.setOnPreferenceChangeListener { preference, newValue ->
                val isEnabled = newValue as Boolean
                if (isEnabled) {
                    AlertDialog.Builder(requireContext())
                        .setTitle("HDR режим")
                        .setMessage("HDR режим может увеличить время обработки фотографий. Продолжить?")
                        .setPositiveButton("Да") { _, _ ->
                            preference.sharedPreferences?.edit()?.putBoolean("hdr_mode", true)?.apply()
                        }
                        .setNegativeButton("Нет", null)
                        .show()
                }
                true
            }

            // Обработчик для качества JPEG
            val jpegPref = findPreference<EditTextPreference>("jpeg_quality")
            jpegPref?.setOnPreferenceChangeListener { _, newValue ->
                val newValueStr = newValue as? String ?: return@setOnPreferenceChangeListener false
                val quality = newValueStr.toIntOrNull()
                when {
                    quality == null -> {
                        Toast.makeText(requireContext(), "Введите число", Toast.LENGTH_SHORT).show()
                        false
                    }
                    quality < 1 || quality > 100 -> {
                        Toast.makeText(requireContext(), "Качество должно быть от 1 до 100", Toast.LENGTH_SHORT).show()
                        false
                    }
                    else -> true
                }
            }
        }
    }
}