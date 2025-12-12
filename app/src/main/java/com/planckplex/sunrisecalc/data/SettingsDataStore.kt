package com.planckplex.sunrisecalc.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsDataStore(context: Context) {

    private val appContext = context.applicationContext

    companion object {
        val SERVER_URL_KEY = stringPreferencesKey("server_url")
        private const val DEFAULT_SERVER_URL = ""
    }

    val serverUrl: Flow<String> = appContext.dataStore.data
        .map { preferences ->
            preferences[SERVER_URL_KEY] ?: DEFAULT_SERVER_URL
        }

    suspend fun saveServerUrl(url: String) {
        appContext.dataStore.edit { settings ->
            settings[SERVER_URL_KEY] = url
        }
    }
}
