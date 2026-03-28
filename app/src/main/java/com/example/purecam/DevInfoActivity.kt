package com.example.purecam

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import android.os.Build

class DevInfoActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Применяем тему
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dev_info)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "👨‍💻 Разработчик"

        val devInfo = """
            
            📸 Идея: Создать камеру, которая делает фотографии максимально естественно, без AI-обработки и навязчивых фильтров.
            
            🎯 Философия: Минимум обработки = Максимум реальности
            
            👨‍💻 Разработка: ZeNi Games
                        
            👤 Автор: NecroMagik
            
            💡 Pure Camera - твой взгляд на реальность
        """.trimIndent()

        val infoText = findViewById<TextView>(R.id.devInfoText)
        infoText.text = devInfo

        val telegramButton = findViewById<Button>(R.id.telegramButton)
        telegramButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/NecroMagik"))
            startActivity(intent)
        }

        val aboutButton = findViewById<Button>(R.id.aboutButton)
        aboutButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("О приложении")
                .setMessage("""
                    Pure Camera
                    Версия 1.0.0
                    
                    Приложение создано для тех, кто ценит 
                    естественную красоту фотографий.
                    
                    Минимальная обработка, максимум реальности.
                    
                    Разработчик: ZeNi Games
                    Контакты: @NecroMagik
                """.trimIndent())
                .setPositiveButton("Закрыть", null)
                .show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}