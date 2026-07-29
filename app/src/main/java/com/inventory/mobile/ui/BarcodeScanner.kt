package com.inventory.mobile.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

@Composable
fun BarcodeScannerDialog(onResult: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    var scanAttempt by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }

    val options = remember {
        GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_CODE_39,
                Barcode.FORMAT_CODE_93,
                Barcode.FORMAT_CODE_128,
            )
            .enableAutoZoom()
            .build()
    }
    val scanner = remember(context, options) {
        GmsBarcodeScanning.getClient(context, options)
    }

    LaunchedEffect(scanner, scanAttempt) {
        error = null
        scanner.startScan()
            .addOnSuccessListener { barcode ->
                val value = barcode.rawValue?.takeIf(String::isNotBlank)
                if (value == null) {
                    error = "No barcode value was found. Try again."
                } else {
                    currentOnResult(value)
                }
            }
            .addOnCanceledListener { currentOnDismiss() }
            .addOnFailureListener {
                error = "The barcode scanner could not start. Update Google Play services and try again."
            }
    }

    AlertDialog(
        onDismissRequest = currentOnDismiss,
        title = { Text("Scan barcode") },
        text = {
            Text(error ?: "Opening the barcode scanner…")
        },
        confirmButton = {
            if (error != null) {
                TextButton(onClick = { scanAttempt++ }) { Text("Try again") }
            }
        },
        dismissButton = {
            TextButton(onClick = currentOnDismiss) { Text("Cancel") }
        },
    )
}
