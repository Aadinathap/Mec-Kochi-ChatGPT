package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = "mec_theme_prefs")

class ThemePreferenceStore(private val context: Context) {

    val isDarkTheme: Flow<Boolean> = context.themeDataStore.data.map { prefs ->
        prefs[KEY_IS_DARK_THEME] ?: false
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.themeDataStore.edit { prefs ->
            prefs[KEY_IS_DARK_THEME] = enabled
        }
    }

    suspend fun toggleTheme(currentDarkTheme: Boolean) {
        setDarkTheme(!currentDarkTheme)
    }

    private companion object {
        val KEY_IS_DARK_THEME = booleanPreferencesKey("is_dark_theme")
    }
}
