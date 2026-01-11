package com.punishman.legendmaster

import android.Manifest
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.graphics.*
import android.os.*
import android.util.Log
import android.util.Size
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ScanActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var imageCapture: ImageCapture
    private lateinit var ivDebug: ImageView
    private lateinit var pbLoading: ProgressBar
    private var cameraControl: CameraControl? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan)

        previewView = findViewById(R.id.previewView)
        ivDebug = findViewById(R.id.ivDebug)
        pbLoading = findViewById(R.id.pbLoading)

        findViewById<Button>(R.id.btnCapture).setOnClickListener {
            captureFrame()
        }

        if (hasCameraPermission()) startCamera()
        else ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1001)
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder()
                .setTargetResolution(Size(1080, 1920))
                .build().also { it.setSurfaceProvider(previewView.surfaceProvider) }

            imageCapture = ImageCapture.Builder()
                .setTargetResolution(Size(1080, 1920))
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()

            try {
                provider.unbindAll()
                val camera = provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)

                cameraControl = camera.cameraControl
                cameraControl?.enableTorch(true)


                val handler = Handler(Looper.getMainLooper())
                handler.post(object : Runnable {
                    override fun run() {
                        val factory = previewView.meteringPointFactory
                        val point = factory.createPoint(previewView.width / 2f, previewView.height / 2f)
                        val action = FocusMeteringAction.Builder(point).build()
                        cameraControl?.startFocusAndMetering(action)
                        handler.postDelayed(this, 2500)
                    }
                })
            } catch (e: Exception) { e.printStackTrace() }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun captureFrame() {
        pbLoading.visibility = View.VISIBLE
        val file = File(cacheDir, "scan.jpg")
        val options = ImageCapture.OutputFileOptions.Builder(file).build()

        imageCapture.takePicture(options, ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {

                    cameraControl?.enableTorch(false)

                    val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    if (bitmap != null) {
                        val rotated = rotateBitmapIfNecessary(bitmap, file.absolutePath)
                        recognizeCP(rotated)
                    }
                }
                override fun onError(exc: ImageCaptureException) {
                    cameraControl?.enableTorch(false)
                    pbLoading.visibility = View.GONE
                }
            }
        )
    }

    private fun recognizeCP(bitmap: Bitmap) {

        val processedBitmap = binarize(bitmap, 130)

        ivDebug.setImageBitmap(processedBitmap)
        ivDebug.visibility = View.VISIBLE

        val image = InputImage.fromBitmap(processedBitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        recognizer.process(image)
            .addOnSuccessListener { result ->
                val rawEntries = mutableListOf<RawNumber>()

                result.textBlocks.forEach { block ->
                    block.lines.forEach { line ->
                        val box = line.boundingBox ?: return@forEach
                        Regex("\\d+").findAll(line.text).forEach { match ->
                            rawEntries.add(RawNumber(match.value.toIntOrNull() ?: 0, match.value, box.centerX(), box.centerY()))
                        }
                    }
                }

                val bestX = rawEntries.groupBy { it.x }
                    .maxByOrNull { entry -> rawEntries.count { Math.abs(it.x - entry.key) < 80 } }?.key ?: 0

                val columnNumbers = rawEntries
                    .filter { Math.abs(it.x - bestX) < 80 }
                    .sortedBy { it.y }

                val finalCPs = mutableListOf<Int>()
                var lastOrderNum = 0

                columnNumbers.forEach { entry ->
                    var foundMatch = false
                    val s = entry.rawStr

                    for (orderNum in (lastOrderNum + 1)..99) {
                        val pref = orderNum.toString()
                        if (s.startsWith(pref) && s.length > pref.length) {
                            val cp = s.substring(pref.length).toIntOrNull() ?: -1
                            if (cp in 31..255) {
                                finalCPs.add(cp)
                                lastOrderNum = orderNum
                                foundMatch = true
                                break
                            }
                        }
                    }

                    if (!foundMatch && entry.value in 31..255) {
                        finalCPs.add(entry.value)
                    }
                }


                Handler(Looper.getMainLooper()).postDelayed({
                    if (finalCPs.isEmpty()) {
                        ivDebug.visibility = View.GONE
                        pbLoading.visibility = View.GONE
                        Toast.makeText(this, "КП не найдены", Toast.LENGTH_LONG).show()

                        cameraControl?.enableTorch(true)
                    } else {
                        val jsonArr = JSONArray()
                        finalCPs.distinct().forEach { cp ->
                            jsonArr.put(JSONObject().put("cpNum", cp).put("icons", JSONArray().apply { repeat(6) { put(0) } }))
                        }
                        setResult(Activity.RESULT_OK, Intent().apply { putExtra("SCAN_JSON", jsonArr.toString()) })
                        finish()
                    }
                }, 500)
            }
            .addOnFailureListener {
                pbLoading.visibility = View.GONE
                cameraControl?.enableTorch(true)
            }
    }

    private fun binarize(src: Bitmap, threshold: Int): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.RGB_565)
        val canvas = Canvas(out)
        val paint = Paint()
        val cm = ColorMatrix()
        cm.setSaturation(0f)
        val scale = 20f
        val translate = (-.5f * scale + .5f) * 255f
        cm.postConcat(ColorMatrix(floatArrayOf(
            scale, 0f, 0f, 0f, translate,
            0f, scale, 0f, 0f, translate,
            0f, 0f, scale, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        )))
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return out
    }

    private fun rotateBitmapIfNecessary(bitmap: Bitmap, path: String): Bitmap {
        val exifInterface = android.media.ExifInterface(path)
        val orientation = exifInterface.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)
        val matrix = Matrix()
        when (orientation) {
            6 -> matrix.postRotate(90f)
            3 -> matrix.postRotate(180f)
            8 -> matrix.postRotate(270f)
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    data class RawNumber(val value: Int, val rawStr: String, val x: Int, val y: Int)

    override fun onDestroy() {
        super.onDestroy()
        cameraControl?.enableTorch(false)
    }
}