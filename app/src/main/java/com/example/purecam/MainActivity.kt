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
import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
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

    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraInfoList = mutableListOf<CameraInfo>()
    private var currentCameraIndex = 0
    private var currentZoom = 1.0f
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

        if (allPermissionsGranted()) {
            loadAvailableCameras()
        } else {
            requestPermissions()
        }
    }

    private fun initViews() {
        previewView = findViewById(R.id.previewView)
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

    private fun setupZoomSlider() {
        zoomSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    currentZoom = 1f + (progress / 100f) * 9f
                    zoomValue.text = String.format("%.1fx", currentZoom)
                    updateZoom()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun updateZoom() {
        cameraProvider?.let { provider ->
            try {
                val lensFacing = cameraInfoList[currentCameraIndex].characteristics.get(CameraCharacteristics.LENS_FACING)
                val cameraSelector = when (lensFacing) {
                    CameraCharacteristics.LENS_FACING_BACK -> CameraSelector.DEFAULT_BACK_CAMERA
                    CameraCharacteristics.LENS_FACING_FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
                    CameraCharacteristics.LENS_FACING_EXTERNAL -> CameraSelector.DEFAULT_BACK_CAMERA
                    else -> CameraSelector.DEFAULT_BACK_CAMERA
                }

                val preview = Preview.Builder().build()

                val camera = provider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageCapture
                )
                camera.cameraControl.setZoomRatio(currentZoom)
            } catch (e: Exception) {
                // Игнорируем ошибки зума
            }
        }
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
        processNatural.setOnClickListener { setProcessing("natural", "Натуральный") }
        processNeutral.setOnClickListener { setProcessing("neutral", "Нейтральный") }
        processVivid.setOnClickListener { setProcessing("vivid", "Насыщенный") }
        processPortrait.setOnClickListener { setProcessing("portrait", "Портрет") }
        processLandscape.setOnClickListener { setProcessing("landscape", "Пейзаж") }
        processMonochrome.setOnClickListener { setProcessing("monochrome", "Монохром") }
    }

    private fun loadAvailableCameras() {
        val cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager

        try {
            val cameraIds = cameraManager.cameraIdList
            cameraInfoList.clear()

            for (cameraId in cameraIds) {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                val lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING)

                val lensType = when (lensFacing) {
                    CameraCharacteristics.LENS_FACING_BACK -> "📷 Задняя"
                    CameraCharacteristics.LENS_FACING_FRONT -> "🤳 Фронтальная"
                    CameraCharacteristics.LENS_FACING_EXTERNAL -> "🔌 Внешняя"
                    else -> "📸 Камера"
                }

                val configs = characteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
                )
                val resolutions = configs?.getOutputSizes(android.graphics.ImageFormat.JPEG)
                val maxResolution = resolutions?.maxByOrNull { it.width * it.height }
                val resolutionInfo = if (maxResolution != null) {
                    " ${maxResolution.width}×${maxResolution.height}"
                } else ""

                cameraInfoList.add(CameraInfo(
                    id = cameraId,
                    lensType = lensType,
                    resolution = resolutionInfo,
                    characteristics = characteristics
                ))
            }

            if (cameraInfoList.isNotEmpty()) {
                updateLensButtonText()
                startCamera(0)
            } else {
                updateStatus("Камеры не найдены", false)
            }

        } catch (e: Exception) {
            updateStatus("Ошибка: ${e.message}", false)
        }
    }

    private fun startCamera(index: Int) {
        if (index >= cameraInfoList.size) return

        val cameraInfo = cameraInfoList[index]
        val lensFacing = cameraInfo.characteristics.get(CameraCharacteristics.LENS_FACING)

        // Выбираем правильный селектор
        val cameraSelector = when (lensFacing) {
            CameraCharacteristics.LENS_FACING_BACK -> CameraSelector.DEFAULT_BACK_CAMERA
            CameraCharacteristics.LENS_FACING_FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
            CameraCharacteristics.LENS_FACING_EXTERNAL -> CameraSelector.DEFAULT_BACK_CAMERA
            else -> CameraSelector.DEFAULT_BACK_CAMERA
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setJpegQuality(currentQuality)
                .build()

            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(this, cameraSelector, preview, imageCapture)
                updateStatus("Готово: ${cameraInfo.lensType}", true)
            } catch (exc: Exception) {
                updateStatus("Ошибка: ${exc.message}", false)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun showCameraSelectorDialog() {
        val cameraNames = cameraInfoList.map { info ->
            "${info.lensType}${info.resolution}"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Выберите объектив")
            .setItems(cameraNames) { _, which ->
                if (which != currentCameraIndex) {
                    currentCameraIndex = which
                    updateLensButtonText()
                    startCamera(currentCameraIndex)
                    updateStatus("Переключено на ${cameraInfoList[currentCameraIndex].lensType}", true)
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun updateLensButtonText() {
        btnLens.text = cameraInfoList.getOrNull(currentCameraIndex)?.lensType?.take(4) ?: "📷"
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
        imageCapture?.let {
            // Обновляем качество
            val newCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setJpegQuality(quality)
                .build()
            imageCapture = newCapture
        }
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
        updateStatus("RAW: ${if (isRawMode) "Включен" else "Выключен"}", true)
    }

    private fun toggleHdrMode() {
        isHdrMode = !isHdrMode
        btnHdr.text = if (isHdrMode) "HDR✓" else "HDR"
        updateStatus("HDR: ${if (isHdrMode) "Включен" else "Выключен"}", true)
    }

    private fun toggleFlash() {
        btnFlash.text = if (btnFlash.text == "⚡") "⚡🌙" else "⚡"
        updateStatus("Вспышка: ${if (btnFlash.text == "⚡🌙") "Вкл" else "Авто"}", true)
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

            // Вертикальные линии
            val stepX = width / 3f
            for (i in 1..2) {
                canvas.drawLine(stepX * i, 0f, stepX * i, height.toFloat(), paint)
            }

            // Горизонтальные линии
            val stepY = height / 3f
            for (i in 1..2) {
                canvas.drawLine(0f, stepY * i, width.toFloat(), stepY * i, paint)
            }

            gridView.background = android.graphics.drawable.BitmapDrawable(resources, bitmap)
        }
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
            .format(System.currentTimeMillis())
        val filename = if (isRawMode) "PURECAM_RAW_$name.dng" else "PURECAM_$name.jpg"

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, if (isRawMode) "image/dng" else "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/PureCamera")
            }
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()

        val processingMessage = when (currentProcessing) {
            "natural" -> "📸 Натуральный (без обработки)"
            "neutral" -> "🎨 Нейтральный"
            "vivid" -> "🌈 Насыщенный"
            "portrait" -> "👤 Портрет"
            "landscape" -> "🏞️ Пейзаж"
            "monochrome" -> "⚫ Монохром"
            else -> "📸 Фото"
        }

        updateStatus("$processingMessage...", true)

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    updateStatus("Сохранено: $filename", true)
                    Toast.makeText(this@MainActivity,
                        "$processingMessage\nПапка: DCIM/PureCamera",
                        Toast.LENGTH_LONG).show()
                }

                override fun onError(exc: ImageCaptureException) {
                    updateStatus("Ошибка: ${exc.message}", false)
                }
            }
        )
    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.CAMERA] == true) {
            loadAvailableCameras()
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
        handler.removeCallbacksAndMessages(null)
    }

    data class CameraInfo(
        val id: String,
        val lensType: String,
        val resolution: String,
        val characteristics: CameraCharacteristics
    )
}