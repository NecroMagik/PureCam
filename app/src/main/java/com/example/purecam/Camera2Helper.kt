package com.example.purecam

import android.Manifest
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import androidx.core.content.ContextCompat
import java.util.*
import kotlin.math.abs

class Camera2Helper(
    private val context: Context,
    private val textureView: TextureView,
    private val listener: CameraListener
) {
    private val cameraManager: CameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var cameraDevice: CameraDevice? = null
    private var cameraCaptureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private var previewSize: Size? = null
    private var currentCameraId: String? = null
    private var currentLensFacing = CameraCharacteristics.LENS_FACING_BACK

    private var isFlashSupported = false
    private var currentFlashMode = 0 // 0=auto, 1=on, 2=off

    private var currentZoom = 1.0f
    private var zoomRange = 1.0f..10.0f

    // Список всех камер
    val availableCameras: MutableList<CameraInfo> = mutableListOf()

    interface CameraListener {
        fun onCameraOpened()
        fun onCameraError(error: String)
        fun onImageCaptured(data: ByteArray)
        fun onPreviewReady()
    }

    data class CameraInfo(
        val id: String,
        val lensFacing: Int,
        val lensType: String,
        val resolutions: List<Size>,
        val hasFlash: Boolean,
        val zoomRange: FloatRange,
        val characteristics: CameraCharacteristics
    )

    data class FloatRange(val min: Float, val max: Float)

    init {
        startBackgroundThread()
        loadAvailableCameras()
    }

    private fun loadAvailableCameras() {
        try {
            val cameraIds = cameraManager.cameraIdList
            availableCameras.clear()

            for (cameraId in cameraIds) {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                val lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING) ?: continue

                val configs = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                val resolutions = configs?.getOutputSizes(ImageFormat.JPEG)?.toList() ?: emptyList()

                val lensType = when (lensFacing) {
                    CameraCharacteristics.LENS_FACING_BACK -> "📷 Задняя"
                    CameraCharacteristics.LENS_FACING_FRONT -> "🤳 Фронтальная"
                    CameraCharacteristics.LENS_FACING_EXTERNAL -> "🔌 Внешняя"
                    else -> "📸 Камера"
                }

                val flashAvailable = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false

                // Получаем диапазон зума
                val zoomRatios = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 10f
                val zoomRangeVal = FloatRange(1.0f, zoomRatios)

                availableCameras.add(
                    CameraInfo(
                        id = cameraId,
                        lensFacing = lensFacing,
                        lensType = lensType,
                        resolutions = resolutions,
                        hasFlash = flashAvailable,
                        zoomRange = zoomRangeVal,
                        characteristics = characteristics
                    )
                )
            }

            // Сортируем: задние камеры сначала
            availableCameras.sortBy { it.lensFacing }

        } catch (e: SecurityException) {
            listener.onCameraError("Нет разрешения на камеру")
        } catch (e: Exception) {
            listener.onCameraError("Ошибка загрузки камер: ${e.message}")
        }
    }

    fun openCamera(cameraIndex: Int) {
        if (cameraIndex >= availableCameras.size) return

        val cameraInfo = availableCameras[cameraIndex]
        currentCameraId = cameraInfo.id
        currentLensFacing = cameraInfo.lensFacing
        isFlashSupported = cameraInfo.hasFlash
        zoomRange = cameraInfo.zoomRange

        try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                cameraManager.openCamera(currentCameraId!!, stateCallback, backgroundHandler)
            } else {
                listener.onCameraError("Нет разрешения на камеру")
            }
        } catch (e: SecurityException) {
            listener.onCameraError("Нет разрешения на камеру")
        } catch (e: Exception) {
            listener.onCameraError("Ошибка открытия камеры: ${e.message}")
        }
    }

    private val stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera
            createCameraPreview()
            listener.onCameraOpened()
        }

        override fun onDisconnected(camera: CameraDevice) {
            camera.close()
            cameraDevice = null
        }

        override fun onError(camera: CameraDevice, error: Int) {
            camera.close()
            cameraDevice = null
            listener.onCameraError("Ошибка камеры: $error")
        }
    }

    private fun createCameraPreview() {
        val texture = textureView.surfaceTexture
        if (texture == null) {
            listener.onCameraError("TextureView не готов")
            return
        }

        previewSize = getOptimalPreviewSize()
        texture.setDefaultBufferSize(previewSize!!.width, previewSize!!.height)

        val surface = Surface(texture)

        imageReader = ImageReader.newInstance(
            previewSize!!.width,
            previewSize!!.height,
            ImageFormat.JPEG,
            2
        )
        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
            image?.let {
                val buffer = it.planes[0].buffer
                val data = ByteArray(buffer.remaining())
                buffer.get(data)
                listener.onImageCaptured(data)
                it.close()
            }
        }, backgroundHandler)

        val captureRequestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        captureRequestBuilder?.addTarget(surface)

        cameraDevice?.createCaptureSession(
            listOf(surface, imageReader!!.surface),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    cameraCaptureSession = session
                    captureRequestBuilder?.set(
                        CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                    )
                    session.setRepeatingRequest(
                        captureRequestBuilder?.build()!!,
                        null,
                        backgroundHandler
                    )
                    listener.onPreviewReady()
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    listener.onCameraError("Не удалось настроить сессию камеры")
                }
            },
            backgroundHandler
        )
    }

    private fun getOptimalPreviewSize(): Size {
        val characteristics = cameraManager.getCameraCharacteristics(currentCameraId!!)
        val configs = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val previewSizes = configs?.getOutputSizes(SurfaceTexture::class.java) ?: emptyArray()

        val targetRatio = 4.0 / 3.0
        return previewSizes
            .filter { it.width.toDouble() / it.height.toDouble() == targetRatio }
            .maxByOrNull { it.width * it.height } ?: previewSizes.maxByOrNull { it.width * it.height }!!
    }

    fun takePicture() {
        val captureRequestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
        captureRequestBuilder?.addTarget(imageReader!!.surface)

        // Настройка вспышки
        if (isFlashSupported) {
            when (currentFlashMode) {
                0 -> captureRequestBuilder?.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
                1 -> captureRequestBuilder?.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH)
                2 -> captureRequestBuilder?.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            }
        }

        // Настройка зума
        captureRequestBuilder?.set(CaptureRequest.SCALER_CROP_REGION, getZoomRegion())

        cameraCaptureSession?.capture(
            captureRequestBuilder?.build()!!,
            object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    super.onCaptureCompleted(session, request, result)
                }
            },
            backgroundHandler
        )
    }

    fun setZoom(zoom: Float) {
        currentZoom = zoom.coerceIn(zoomRange.min, zoomRange.max)
        updateZoom()
    }

    fun setZoomProgress(progress: Int) {
        val zoom = zoomRange.min + (progress / 100f) * (zoomRange.max - zoomRange.min)
        currentZoom = zoom.coerceIn(zoomRange.min, zoomRange.max)
        updateZoom()
    }

    private fun updateZoom() {
        val captureRequestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        captureRequestBuilder?.set(CaptureRequest.SCALER_CROP_REGION, getZoomRegion())

        cameraCaptureSession?.setRepeatingRequest(
            captureRequestBuilder?.build()!!,
            null,
            backgroundHandler
        )
    }

    private fun getZoomRegion(): android.graphics.Rect? {
        val characteristics = cameraManager.getCameraCharacteristics(currentCameraId!!)
        val sensorSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: return null

        val minW = sensorSize.width() / currentZoom
        val minH = sensorSize.height() / currentZoom
        val deltaW = (sensorSize.width() - minW) / 2
        val deltaH = (sensorSize.height() - minH) / 2

        return android.graphics.Rect(
            deltaW.toInt(),
            deltaH.toInt(),
            (deltaW + minW).toInt(),
            (deltaH + minH).toInt()
        )
    }

    fun setFlashMode(mode: Int) {
        currentFlashMode = mode
    }

    fun getFlashMode(): Int = currentFlashMode

    fun getCurrentZoom(): Float = currentZoom

    fun getZoomRange(): FloatRange = zoomRange

    fun switchCamera(index: Int) {
        closeCamera()
        openCamera(index)
    }

    fun closeCamera() {
        cameraCaptureSession?.close()
        cameraCaptureSession = null
        cameraDevice?.close()
        cameraDevice = null
        imageReader?.close()
        imageReader = null
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").apply { start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
        backgroundThread = null
        backgroundHandler = null
    }

    fun release() {
        closeCamera()
        stopBackgroundThread()
    }
}