package com.inventory.mobile.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// The label printer belongs to the counter it sits on, not to whoever is signed in,
// so this is deliberately kept outside the session store and survives sign-out.
private val Context.printerDataStore by preferencesDataStore("inventory_printer")

data class PrinterSettings(
    val ipAddress: String = "",
    val model: String = DEFAULT_MODEL,
    val labelStockId: String = DEFAULT_LABEL_STOCK,
    val autoCut: Boolean = true,
) {
    val isConfigured get() = ipAddress.isNotBlank()

    companion object {
        const val DEFAULT_MODEL = "QL_810W"
        const val DEFAULT_LABEL_STOCK = "dk1221"
    }
}

class PrinterStore(private val context: Context) {
    private val ipKey = stringPreferencesKey("printer_ip")
    private val modelKey = stringPreferencesKey("printer_model")
    private val labelStockKey = stringPreferencesKey("printer_label_stock")
    private val autoCutKey = booleanPreferencesKey("printer_auto_cut")

    val settings: Flow<PrinterSettings> = context.printerDataStore.data.map { preferences ->
        PrinterSettings(
            ipAddress = preferences[ipKey].orEmpty(),
            model = preferences[modelKey] ?: PrinterSettings.DEFAULT_MODEL,
            labelStockId = preferences[labelStockKey] ?: PrinterSettings.DEFAULT_LABEL_STOCK,
            autoCut = preferences[autoCutKey] ?: true,
        )
    }

    suspend fun save(settings: PrinterSettings) {
        context.printerDataStore.edit { preferences ->
            preferences[ipKey] = settings.ipAddress.trim()
            preferences[modelKey] = settings.model
            preferences[labelStockKey] = settings.labelStockId
            preferences[autoCutKey] = settings.autoCut
        }
    }
}
