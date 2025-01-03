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
import android.widget.ImageView
import android.widget.RelativeLayout
import android.view.ViewGroup
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

class LivelinessActivity : AppCompatActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private val cameraPermissionRequestCode = 1001
    private val audioPermissionRequestCode = 1002

    private lateinit var previewView: PreviewView
    private lateinit var overlayImageView: ImageView
    private lateinit var faceDetector: FaceDetector
    private val handler =
            Handler(Looper.getMainLooper()) // Handler to control image emission interval
    private var lastProcessedTime: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize PreviewView programmatically
        previewView = PreviewView(this)
        overlayImageView = ImageView(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setScaleType(ImageView.ScaleType.FIT_XY)
        }

        // Set up a parent layout (e.g., RelativeLayout or FrameLayout) to contain both views
        val parentLayout = RelativeLayout(this).apply {
            addView(previewView)
            addView(overlayImageView)
        }
        setContentView(parentLayout)

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
                        if (currentTime - lastProcessedTime >= 500) { // Process every 100ms
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
            // Convert the media image to a bitmap
            val bitmap = CameraBitmapUtils.getBitmap(mediaImage)
    
            // Rotate the bitmap by 90 degrees
            val rotatedBitmap = rotateBitmap(bitmap, 270f,flipHorizontally = true)
    
            // Handle the rotated bitmap (if it's non-null)
            rotatedBitmap?.let {
                detectFaces(it) // Call detectFaces with the rotated bitmap
            } ?: Log.e("CameraActivity", "Failed to convert or rotate image")
        } else {
            Log.e("CameraActivity", "Media image is null")
        }
    
        imageProxy.close() // Always close the image proxy
    }
    
    private fun rotateBitmap(bitmap: Bitmap?, degrees: Float, flipHorizontally: Boolean = false): Bitmap? {
        return bitmap?.let {
            val matrix = Matrix()
            matrix.postRotate(degrees)
            if (flipHorizontally) {
                matrix.postScale(-1f, 1f, (it.width / 2).toFloat(), (it.height / 2).toFloat()) // Flip horizontally
            }
            Bitmap.createBitmap(it, 0, 0, it.width, it.height, matrix, true)
        }
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
                            // Clear the bounding box when no face is detected
                            clearBoundingBox()
                        } else {
                            for (face in faces) {
                                drawBoundingBox(face)
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e("CameraActivity", "Face detection failed", e)
                    }
        }
                ?: Log.e("CameraActivity", "Bitmap is null, cannot detect faces")
    }
    

    private fun drawBoundingBox(face: Face) {
        // Check if overlayImageView is initialized
        if (!::overlayImageView.isInitialized) {
            Log.e("CameraActivity", "overlayImageView not initialized")
            return
        }
    
        // Convert the face bounding box to a Bitmap
        val bitmap = Bitmap.createBitmap(previewView.width, previewView.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = android.graphics.Color.RED
            strokeWidth = 8f
            style = Paint.Style.STROKE
        }
    
        // Get the bounding box from the detected face
        val bounds = face.boundingBox
    
        // Scale the bounding box coordinates to match the preview size
        val scaleX = previewView.width.toFloat() / previewView.height.toFloat()
        val scaleY = previewView.height.toFloat() / previewView.width.toFloat()
        
        val scaledLeft = bounds.left * scaleX + 100f
        val scaledTop = bounds.top * scaleY - 100f
        val scaledRight = bounds.right * scaleX + 400f
        val scaledBottom = bounds.bottom * scaleY
    
        // Draw the bounding box around the face
        canvas.drawRect(scaledLeft, scaledTop, scaledRight, scaledBottom, paint)
    
        // Set the bitmap with bounding box to the overlay ImageView
        overlayImageView.setImageBitmap(bitmap) // Display updated bitmap with bounding box
    }
    
    

    private fun clearBoundingBox() {
        // Clear the overlay ImageView by setting a blank bitmap
        val emptyBitmap = Bitmap.createBitmap(previewView.width, previewView.height, Bitmap.Config.ARGB_8888)
        overlayImageView.setImageBitmap(emptyBitmap)
    }
    
}

object CameraBitmapUtils {
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
