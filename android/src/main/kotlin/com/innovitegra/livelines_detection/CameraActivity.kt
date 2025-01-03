package com.innovitegra.livelines_detection

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.YuvImage
import android.media.Image
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private val cameraPermissionRequestCode = 1001
    private val audioPermissionRequestCode = 1002

    private lateinit var previewView: PreviewView
    private lateinit var faceDetector: FaceDetector
    private val handler =
            Handler(Looper.getMainLooper()) // Handler to control image emission interval
    private var lastProcessedTime: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize PreviewView programmatically
        previewView = PreviewView(this)
        setContentView(previewView)

        // Set up ML Kit Face Detector
        val options =
                FaceDetectorOptions.Builder()
                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                        .setMinFaceSize(0.05f) // Detect smaller faces (reduce from default 0.1f)
                        .build()

        faceDetector = FaceDetection.getClient(options)

        cameraExecutor = Executors.newSingleThreadExecutor()

        // Initialize camera
        initializeCamera(previewView)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    // Handle permission request results
    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<out String>,
            grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            cameraPermissionRequestCode -> {
                if (grantResults.isNotEmpty() &&
                                grantResults[0] == PackageManager.PERMISSION_GRANTED
                ) {
                    // Camera permission granted, initialize camera
                    initializeCamera(previewView)
                } else {
                    // Permission denied, handle accordingly
                }
            }
            audioPermissionRequestCode -> {
                if (grantResults.isNotEmpty() &&
                                grantResults[0] == PackageManager.PERMISSION_GRANTED
                ) {
                    // Audio permission granted, initialize audio recording (if needed)
                } else {
                    // Permission denied, handle accordingly
                }
            }
        }
    }

    private fun initializeCamera(previewView: PreviewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(
                {
                    val cameraProvider = cameraProviderFuture.get()

                    // Preview use case
                    val preview = Preview.Builder().build()
                    preview.setSurfaceProvider(previewView.surfaceProvider)

                    // Image analysis use case for face detection
                    val imageAnalysis =
                            ImageAnalysis.Builder()
                                    .setTargetResolution(
                                            android.util.Size(640, 480)
                                    ) // Standard HD resolution
                                    .setBackpressureStrategy(
                                            ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                                    )
                                    .build()

                    imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        // Check the time passed since the last image was processed
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastProcessedTime >= 100) { // Process every 100ms
                            processImage(imageProxy)
                            lastProcessedTime = currentTime
                        } else {
                            imageProxy.close() // Close image proxy if not yet time to process
                        }
                    }

                    // Camera selector for back camera
                    val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                    // Bind use cases to lifecycle
                    cameraProvider.bindToLifecycle(
                            this as LifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalysis
                    )
                },
                ContextCompat.getMainExecutor(this)
        )
    }

    private fun processImage(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            // Convert the media image to a bitmap (it can be null)
            val bitmap = BitmapUtils.getBitmap(mediaImage)

            // Rotate the bitmap if needed, handle null safely
            val rotatedBitmap = rotateBitmapIfNeeded(bitmap, imageProxy)

            // Handle the rotated bitmap (if it's non-null)
            rotatedBitmap?.let {
                detectFaces(it) // Only call detectFaces if rotatedBitmap is non-null
            }
                    ?: Log.e("CameraActivity", "Failed to convert image to bitmap or rotate image")
        } else {
            Log.e("CameraActivity", "Media image is null")
        }

        imageProxy.close() // Always close the image proxy
    }

    private fun detectFaces(bitmap: Bitmap?) {
        bitmap?.let {
            val image = com.google.mlkit.vision.common.InputImage.fromBitmap(it, 0)

            faceDetector
                    .process(image)
                    .addOnSuccessListener { faces ->
                        Log.d("CameraActivity", "Faces detected: ${faces.size}")
                        if (faces.isEmpty()) {
                            Log.d("CameraActivity", "No faces detected.")
                        }
                        for (face in faces) {
                            drawBoundingBox(face)
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e("CameraActivity", "Face detection failed", e)
                    }
                    .addOnFailureListener { e ->
                        Log.e("CameraActivity", "Face detection failed", e)
                    }
        }
                ?: Log.e("CameraActivity", "Bitmap is null, cannot detect faces")
    }

    private fun drawBoundingBox(face: Face) {
        // Ensure previewView.bitmap is non-null
        previewView.bitmap?.let { bitmap ->
            val canvas = Canvas(bitmap)
            val paint =
                    Paint().apply {
                        color = android.graphics.Color.RED
                        strokeWidth = 8f
                        style = Paint.Style.STROKE
                    }

            val bounds = face.boundingBox
            canvas.drawRect(bounds, paint)

            previewView.invalidate() // Refresh the PreviewView to show bounding box
        }
                ?: Log.e("CameraActivity", "Bitmap is null, cannot draw bounding box")
    }
}

fun rotateBitmapIfNeeded(bitmap: Bitmap?, imageProxy: ImageProxy): Bitmap? {
    if (bitmap == null) return null // Return null if the bitmap is null

    // Create a matrix for rotating the image
    val rotationMatrix = Matrix()

    // Get the rotation degrees from the image proxy
    val rotationDegree =
            when (imageProxy.imageInfo.rotationDegrees) {
                90 -> 90
                180 -> 180
                270 -> 270
                else -> 0 // No rotation (0 degrees)
            }

    // Apply the rotation to the matrix
    rotationMatrix.postRotate(rotationDegree.toFloat())

    // Create a rotated bitmap from the original bitmap using the matrix
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, rotationMatrix, true)
}

object BitmapUtils {
    fun getBitmap(image: Image): Bitmap? {
        val plane = image.planes[0]
        val buffer: ByteBuffer = plane.buffer

        // Log buffer size
        Log.d("BitmapUtils", "Buffer remaining size: ${buffer.remaining()}")

        if (buffer.remaining() > 0) {
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)

            // Attempt YUV to Bitmap conversion
            val yuvImage =
                    YuvImage(
                            bytes,
                            android.graphics.ImageFormat.NV21,
                            image.width,
                            image.height,
                            null
                    )

            // Use ByteArrayOutputStream to get the Bitmap
            val outputStream = ByteArrayOutputStream()
            yuvImage.compressToJpeg(
                    android.graphics.Rect(0, 0, image.width, image.height),
                    100,
                    outputStream
            )
            val jpegBytes = outputStream.toByteArray()

            // Decode the JPEG bytes to Bitmap
            val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
            Log.d("BitmapUtils", "Bitmap created successfully: $bitmap")
            return bitmap
        } else {
            Log.e("BitmapUtils", "Buffer is empty, unable to decode image")
            return null
        }
    }
}
