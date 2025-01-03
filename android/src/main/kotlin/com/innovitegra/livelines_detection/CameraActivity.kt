package com.innovitegra.livelines_detection

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.YuvImage
import android.os.Bundle
import android.util.Log
import android.view.Surface
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.lifecycle.LifecycleOwner
import android.media.Image
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class CameraActivity : AppCompatActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private val cameraPermissionRequestCode = 1001
    private val audioPermissionRequestCode = 1002

    private lateinit var previewView: PreviewView
    private lateinit var faceDetector: FaceDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize PreviewView programmatically
        previewView = PreviewView(this)
        setContentView(previewView)

        // Set up ML Kit Face Detector
        val options = FaceDetectorOptions.Builder()
    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)  // Use accurate mode
    .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
    .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
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
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            cameraPermissionRequestCode -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Camera permission granted, initialize camera
                    initializeCamera(previewView)
                } else {
                    // Permission denied, handle accordingly
                }
            }
            audioPermissionRequestCode -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Audio permission granted, initialize audio recording (if needed)
                } else {
                    // Permission denied, handle accordingly
                }
            }
        }
    }

    private fun initializeCamera(previewView: PreviewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // Preview use case
            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(previewView.surfaceProvider)

            // Image analysis use case for face detection
            val imageAnalysis = ImageAnalysis.Builder()
    .setTargetResolution(android.util.Size(1280, 720))  // Standard HD resolution
    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
    .build()


            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                processImage(imageProxy)
            }

            // Camera selector for back camera
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            // Bind use cases to lifecycle
            cameraProvider.bindToLifecycle(this as LifecycleOwner, cameraSelector, preview, imageAnalysis)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun processImage(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val bitmap = BitmapUtils.getBitmap(mediaImage)
            // Handle the nullable bitmap safely
            bitmap?.let {
                detectFaces(it)  // Only call detectFaces if bitmap is non-null
            } ?: Log.e("CameraActivity", "Failed to convert image to bitmap")
        } else {
            Log.e("CameraActivity", "Media image is null")
        }
    
        imageProxy.close()
    }
    

    private fun detectFaces(bitmap: Bitmap?) {
        bitmap?.let {
            val image = com.google.mlkit.vision.common.InputImage.fromBitmap(it, 0)
            
            faceDetector.process(image)
    .addOnSuccessListener { faces ->
        Log.d("CameraActivity", "Faces detected: ${faces.size}")  // Log number of faces
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
        } ?: Log.e("CameraActivity", "Bitmap is null, cannot detect faces")
    }
    
    

    private fun drawBoundingBox(face: Face) {
        // Ensure previewView.bitmap is non-null
        previewView.bitmap?.let { bitmap ->
            val canvas = Canvas(bitmap)
            val paint = Paint().apply {
                color = android.graphics.Color.RED
                strokeWidth = 8f
                style = Paint.Style.STROKE
            }
    
            val bounds = face.boundingBox
            canvas.drawRect(bounds, paint)
    
            previewView.invalidate()  // Refresh the PreviewView to show bounding box
        } ?: Log.e("CameraActivity", "Bitmap is null, cannot draw bounding box")
    }    
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
            val yuvImage = YuvImage(bytes, android.graphics.ImageFormat.NV21, image.width, image.height, null)
    
            // Use ByteArrayOutputStream to get the Bitmap
            val outputStream = ByteArrayOutputStream()
            yuvImage.compressToJpeg(android.graphics.Rect(0, 0, image.width, image.height), 100, outputStream)
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