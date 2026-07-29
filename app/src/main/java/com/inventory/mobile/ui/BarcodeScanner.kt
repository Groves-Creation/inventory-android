package com.inventory.mobile.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@androidx.annotation.OptIn(markerClass = [ExperimentalGetImage::class])
@Composable
fun BarcodeScannerDialog(onResult: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }

    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val delivered = remember { AtomicBoolean(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_CODE_39,
                Barcode.FORMAT_CODE_93,
                Barcode.FORMAT_CODE_128,
            ).build(),
        )
    }
    // The permission result changes `granted`, so camera binding must be a separate
    // effect from the lifetime of ML Kit and its executor.  Previously, granting the
    // permission disposed this effect and closed both resources before the listener
    // below could use them.
    DisposableEffect(Unit) {
        onDispose {
            scanner.close()
            executor.shutdown()
        }
    }

    DisposableEffect(granted, lifecycleOwner) {
        if (!granted) {
            onDispose { }
        } else {
            val disposed = AtomicBoolean(false)
            var boundProvider: ProcessCameraProvider? = null
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                if (disposed.get()) return@addListener
                val cameraProvider = runCatching { future.get() }.getOrElse {
                    cameraError = "Camera could not be started on this device."
                    return@addListener
                }
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                analysis.setAnalyzer(executor) { proxy ->
                    val media = proxy.image
                    if (media == null || delivered.get() || disposed.get()) {
                        proxy.close()
                    } else {
                        // Device camera implementations can occasionally provide a malformed
                        // frame. Keep that failure inside the analyzer so it cannot take down
                        // the process, and always release the frame back to CameraX.
                        runCatching {
                            scanner.process(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees))
                        }.onFailure {
                            proxy.close()
                        }.getOrNull()?.let { task ->
                            task
                            .addOnSuccessListener { barcodes ->
                                val value = barcodes.firstNotNullOfOrNull { it.rawValue?.takeIf(String::isNotBlank) }
                                if (value != null && delivered.compareAndSet(false, true)) onResult(value)
                            }
                            .addOnCompleteListener { proxy.close() }
                        }
                    }
                }
                runCatching {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                    boundProvider = cameraProvider
                }.onFailure {
                    cameraError = "Camera could not be opened. Check that it is available and try again."
                }
            }, ContextCompat.getMainExecutor(context))
            onDispose {
                disposed.set(true)
                boundProvider?.unbindAll()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Scan barcode") },
        text = {
            Column {
                if (granted) AndroidView(factory = { previewView }, modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f))
                else Button(onClick = { permission.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
                cameraError?.let { Text(it) }
                Text("Hardware scanners can also type directly into any search field.")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
