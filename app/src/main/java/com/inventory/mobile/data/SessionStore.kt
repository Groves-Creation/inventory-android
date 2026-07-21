package com.inventory.mobile.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.sessionDataStore by preferencesDataStore("inventory_session")

class SessionStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val userKey = stringPreferencesKey("user")
    private val darkModeKey = booleanPreferencesKey("dark_mode")

    val user: Flow<UserDto?> = context.sessionDataStore.data.map { preferences ->
        preferences[userKey]?.let { runCatching { json.decodeFromString<UserDto>(it) }.getOrNull() }
    }

    val darkMode: Flow<Boolean?> = context.sessionDataStore.data.map { preferences ->
        preferences[darkModeKey]
    }

    suspend fun save(user: UserDto?) {
        context.sessionDataStore.edit { preferences ->
            if (user == null) preferences.remove(userKey) else preferences[userKey] = json.encodeToString(user)
        }
    }

    suspend fun saveDarkMode(enabled: Boolean) {
        context.sessionDataStore.edit { preferences -> preferences[darkModeKey] = enabled }
    }
}
