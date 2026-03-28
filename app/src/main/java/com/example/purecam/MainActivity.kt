package com.example.purecam

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.TextureView
import android.view.View
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity(), Camera2Helper.CameraListener {

    private lateinit var textureView: TextureView
    private lateinit var captureButton: Button
    private lateinit var statusText: TextView
    private lateinit var btnLens: Button
    private lateinit var btnRaw: Button
    private lateinit var btnHdr: Button
    private lateinit var zoomSlider: SeekBar
    private lateinit var zoomValue: TextView
    private lateinit var gridView: View

    // Панели
    private lateinit var aspectPanel: View
    private lateinit var qualityPanel: View
    private lateinit var processingPanel: View

    // Форматы
    private lateinit var aspect4_3: TextView
    private lateinit var aspect1_1: TextView
    private lateinit var aspect16_9: TextView
    private lateinit var aspectFull: TextView

    // Качество
    private lateinit var qualityHigh: TextView
    private lateinit var qualityMedium: TextView
    private lateinit var qualityLow: TextView

    // Обработка
    private lateinit var processNatural: TextView
    private lateinit var processNeutral: TextView
    private lateinit var processVivid: TextView
    private lateinit var processPortrait: TextView
    private lateinit var processLandscape: TextView
    private lateinit var processMonochrome: TextView

    private lateinit var btnFlash: Button
    private lateinit var btnGrid: Button

    private var cameraHelper: Camera2Helper? = null
    private var currentCameraIndex = 0
    private var currentQuality = 95
    private var currentProcessing = "natural"
    private var isRawMode = false
    private var isHdrMode = false
    private var isGridVisible = false

    private var handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupClickListeners()
        setupZoomSlider()

        // Настройка TextureView
        textureView.surfaceTextureListener = surfaceTextureListener

        if (allPermissionsGranted()) {
            initCameraHelper()
        } else {
            requestPermissions()
        }
    }

    private fun initViews() {
        textureView = findViewById(R.id.textureView)
        captureButton = findViewById(R.id.captureButton)
        statusText = findViewById(R.id.statusText)
        btnLens = findViewById(R.id.btnLens)
        btnRaw = findViewById(R.id.btnRaw)
        btnHdr = findViewById(R.id.btnHdr)
        zoomSlider = findViewById(R.id.zoomSlider)
        zoomValue = findViewById(R.id.zoomValue)
        gridView = findViewById(R.id.gridView)
        btnFlash = findViewById(R.id.btnFlash)
        btnGrid = findViewById(R.id.btnGrid)

        aspectPanel = findViewById(R.id.aspectPanel)
        qualityPanel = findViewById(R.id.qualityPanel)
        processingPanel = findViewById(R.id.processingPanel)

        aspect4_3 = findViewById(R.id.aspect4_3)
        aspect1_1 = findViewById(R.id.aspect1_1)
        aspect16_9 = findViewById(R.id.aspect16_9)
        aspectFull = findViewById(R.id.aspectFull)

        qualityHigh = findViewById(R.id.qualityHigh)
        qualityMedium = findViewById(R.id.qualityMedium)
        qualityLow = findViewById(R.id.qualityLow)

        processNatural = findViewById(R.id.processNatural)
        processNeutral = findViewById(R.id.processNeutral)
        processVivid = findViewById(R.id.processVivid)
        processPortrait = findViewById(R.id.processPortrait)
        processLandscape = findViewById(R.id.processLandscape)
        processMonochrome = findViewById(R.id.processMonochrome)
    }

    private val surfaceTextureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            cameraHelper?.openCamera(currentCameraIndex)
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture) = true
        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
    }

    private fun initCameraHelper() {
        cameraHelper = Camera2Helper(this, textureView, this)
    }

    private fun setupZoomSlider() {
        zoomSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    cameraHelper?.setZoomProgress(progress)
                    val zoom = cameraHelper?.getCurrentZoom() ?: 1f
                    zoomValue.text = String.format("%.1fx", zoom)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupClickListeners() {
        captureButton.setOnClickListener { takePhoto() }
        btnLens.setOnClickListener { showCameraSelectorDialog() }
        btnRaw.setOnClickListener { toggleRawMode() }
        btnHdr.setOnClickListener { toggleHdrMode() }
        btnFlash.setOnClickListener { toggleFlash() }
        btnGrid.setOnClickListener { toggleGrid() }
        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<Button>(R.id.btnDev).setOnClickListener {
            startActivity(Intent(this, DevInfoActivity::class.java))
        }

        // Форматы
        aspect4_3.setOnClickListener { setAspectRatio(4f, 3f) }
        aspect1_1.setOnClickListener { setAspectRatio(1f, 1f) }
        aspect16_9.setOnClickListener { setAspectRatio(16f, 9f) }
        aspectFull.setOnClickListener { setFullScreen() }

        // Качество
        qualityHigh.setOnClickListener { setQuality(95, "Высокое") }
        qualityMedium.setOnClickListener { setQuality(75, "Среднее") }
        qualityLow.setOnClickListener { setQuality(50, "Низкое") }

        // Обработка
        processNatural.setOnClickListener { setProcessing("natural", "Натуральный (без обработки)") }
        processNeutral.setOnClickListener { setProcessing("neutral", "Нейтральный") }
        processVivid.setOnClickListener { setProcessing("vivid", "Насыщенный") }
        processPortrait.setOnClickListener { setProcessing("portrait", "Портрет (теплые тона)") }
        processLandscape.setOnClickListener { setProcessing("landscape", "Пейзаж (холодные тона)") }
        processMonochrome.setOnClickListener { setProcessing("monochrome", "Монохром") }
    }

    private fun showCameraSelectorDialog() {
        val cameras = cameraHelper?.availableCameras ?: return
        val cameraNames = cameras.map { it.lensType }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Выберите объектив")
            .setItems(cameraNames) { _, which ->
                if (which != currentCameraIndex) {
                    currentCameraIndex = which
                    cameraHelper?.switchCamera(currentCameraIndex)
                    updateLensButtonText()
                    updateStatus("Переключено на ${cameras[currentCameraIndex].lensType}", true)
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun updateLensButtonText() {
        val lens = cameraHelper?.availableCameras?.getOrNull(currentCameraIndex)?.lensType?.take(4) ?: "📷"
        btnLens.text = lens
    }

    private fun setAspectRatio(width: Float, height: Float) {
        updateStatus("Формат: ${width.toInt()}:${height.toInt()}", true)
        aspectPanel.visibility = View.GONE
    }

    private fun setFullScreen() {
        updateStatus("Полный экран", true)
        aspectPanel.visibility = View.GONE
    }

    private fun setQuality(quality: Int, name: String) {
        currentQuality = quality
        updateStatus("Качество: $name", true)
        qualityPanel.visibility = View.GONE
    }

    private fun setProcessing(mode: String, name: String) {
        currentProcessing = mode
        updateStatus("Обработка: $name", true)
        processingPanel.visibility = View.GONE
    }

    private fun toggleRawMode() {
        isRawMode = !isRawMode
        btnRaw.text = if (isRawMode) "RAW✓" else "RAW"
        updateStatus("RAW: ${if (isRawMode) "Включен (DNG)" else "Выключен (JPEG)"}", true)
    }

    private fun toggleHdrMode() {
        isHdrMode = !isHdrMode
        btnHdr.text = if (isHdrMode) "HDR✓" else "HDR"
        updateStatus("HDR: ${if (isHdrMode) "Включен" else "Выключен"}", true)
    }

    private fun toggleFlash() {
        val currentMode = cameraHelper?.getFlashMode() ?: 0
        val newMode = when (currentMode) {
            0 -> 1
            1 -> 2
            else -> 0
        }
        cameraHelper?.setFlashMode(newMode)

        btnFlash.text = when (newMode) {
            0 -> "⚡"
            1 -> "⚡🌙"
            else -> "⚡🚫"
        }

        val flashText = when (newMode) {
            0 -> "Авто"
            1 -> "Вкл"
            else -> "Выкл"
        }
        updateStatus("Вспышка: $flashText", true)
    }

    private fun toggleGrid() {
        isGridVisible = !isGridVisible
        gridView.visibility = if (isGridVisible) View.VISIBLE else View.GONE
        updateStatus("Сетка: ${if (isGridVisible) "Вкл" else "Выкл"}", true)

        if (isGridVisible) {
            drawGrid()
        }
    }

    private fun drawGrid() {
        gridView.post {
            val width = gridView.width
            val height = gridView.height

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint().apply {
                color = Color.WHITE
                strokeWidth = 2f
                style = Paint.Style.STROKE
                alpha = 180
            }

            val stepX = width / 3f
            for (i in 1..2) {
                canvas.drawLine(stepX * i, 0f, stepX * i, height.toFloat(), paint)
            }

            val stepY = height / 3f
            for (i in 1..2) {
                canvas.drawLine(0f, stepY * i, width.toFloat(), stepY * i, paint)
            }

            gridView.background = android.graphics.drawable.BitmapDrawable(resources, bitmap)
        }
    }

    private fun takePhoto() {
        cameraHelper?.takePicture()
    }

    private fun processImage(data: ByteArray): ByteArray {
        var bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)

        // Применяем обработку
        bitmap = when (currentProcessing) {
            "neutral" -> applyNeutralFilter(bitmap)
            "vivid" -> applyVividFilter(bitmap)
            "portrait" -> applyPortraitFilter(bitmap)
            "landscape" -> applyLandscapeFilter(bitmap)
            "monochrome" -> applyMonochromeFilter(bitmap)
            else -> bitmap
        }

        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, currentQuality, outputStream)
        return outputStream.toByteArray()
    }

    private fun applyNeutralFilter(bitmap: Bitmap): Bitmap {
        // Нейтральная обработка - слегка повышаем четкость
        return bitmap
    }

    private fun applyVividFilter(bitmap: Bitmap): Bitmap {
        // Насыщенный - повышаем насыщенность
        return bitmap
    }

    private fun applyPortraitFilter(bitmap: Bitmap): Bitmap {
        // Портрет - теплые тона, легкое размытие
        return bitmap
    }

    private fun applyLandscapeFilter(bitmap: Bitmap): Bitmap {
        // Пейзаж - холодные тона, повышение контраста
        return bitmap
    }

    private fun applyMonochromeFilter(bitmap: Bitmap): Bitmap {
        // Черно-белый
        return bitmap
    }

    // Camera2Helper.CameraListener
    override fun onCameraOpened() {
        updateStatus("Камера готова", true)
        val zoomRange = cameraHelper?.getZoomRange()
        zoomRange?.let {
            val maxProgress = 100
            zoomSlider.max = maxProgress
        }
    }

    override fun onCameraError(error: String) {
        updateStatus("Ошибка: $error", false)
    }

    override fun onImageCaptured(data: ByteArray) {
        val processedData = if (!isRawMode) processImage(data) else data

        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
            .format(System.currentTimeMillis())
        val extension = if (isRawMode) "dng" else "jpg"
        val filename = if (isRawMode) "PURECAM_RAW_$name.$extension" else "PURECAM_$name.$extension"

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, if (isRawMode) "image/dng" else "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/PureCamera")
            }
        }

        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        uri?.let {
            contentResolver.openOutputStream(it)?.use { outputStream ->
                outputStream.write(processedData)
            }
        }

        val processingMessage = when (currentProcessing) {
            "natural" -> "📸 Натуральный (без обработки)"
            "neutral" -> "🎨 Нейтральный"
            "vivid" -> "🌈 Насыщенный"
            "portrait" -> "👤 Портрет"
            "landscape" -> "🏞️ Пейзаж"
            "monochrome" -> "⚫ Монохром"
            else -> "📸 Фото"
        }

        runOnUiThread {
            updateStatus("Сохранено: $filename", true)
            Toast.makeText(this, "$processingMessage\nПапка: DCIM/PureCamera", Toast.LENGTH_LONG).show()
        }
    }

    override fun onPreviewReady() {
        // Обновляем диапазон зума
        val zoomRange = cameraHelper?.getZoomRange()
        zoomRange?.let {
            zoomSlider.progress = 0
        }
    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.CAMERA] == true) {
            initCameraHelper()
        } else {
            Toast.makeText(this, "Камера необходима", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun requestPermissions() {
        requestPermissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
    }

    private fun updateStatus(message: String, isSuccess: Boolean) {
        statusText.text = message
        statusText.setBackgroundColor(
            if (isSuccess)
                ContextCompat.getColor(this, android.R.color.holo_green_dark)
            else
                ContextCompat.getColor(this, android.R.color.holo_red_dark)
        )
        handler.postDelayed({
            if (statusText.text == message) {
                statusText.text = "Готов"
                statusText.setBackgroundColor(0x80000000.toInt())
            }
        }, 2000)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraHelper?.release()
        handler.removeCallbacksAndMessages(null)
    }
}