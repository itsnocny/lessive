package dev.nocny.lessive.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore("lessive")

/** Everything the app remembers, as JSON blobs in one preferences file. */
class Store(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val profile: Flow<MachineProfile> = read(PROFILE, MachineProfile.serializer()) { Profiles.default }
    val favorites: Flow<List<Favorite>> = read(FAVORITES, ListSerializer(Favorite.serializer())) { emptyList() }
    val history: Flow<List<HistoryEntry>> = read(HISTORY, ListSerializer(HistoryEntry.serializer())) { emptyList() }
    val lastSettings: Flow<List<WashSettings>> = read(LAST, ListSerializer(WashSettings.serializer())) { emptyList() }
    val onboarded: Flow<Boolean> = context.dataStore.data.map { it[ONBOARDED] ?: false }
    val writeBlocked: Flow<Boolean> = context.dataStore.data.map { it[WRITE_BLOCKED] ?: false }

    suspend fun saveProfile(profile: MachineProfile) = write(PROFILE, MachineProfile.serializer(), profile)

    suspend fun saveFavorites(list: List<Favorite>) = write(FAVORITES, ListSerializer(Favorite.serializer()), list)

    suspend fun addHistory(entry: HistoryEntry, current: List<HistoryEntry>) =
        write(HISTORY, ListSerializer(HistoryEntry.serializer()), (listOf(entry) + current).take(30))

    /** Keeps the last settings used for each program, so reopening one restores them. */
    suspend fun rememberSettings(settings: WashSettings, current: List<WashSettings>) =
        write(LAST, ListSerializer(WashSettings.serializer()), listOf(settings) + current.filter { it.programId != settings.programId || it.selector != settings.selector })

    suspend fun setOnboarded(value: Boolean) = context.dataStore.edit { it[ONBOARDED] = value }

    suspend fun setWriteBlocked(value: Boolean) = context.dataStore.edit { it[WRITE_BLOCKED] = value }

    private fun <T> read(key: Preferences.Key<String>, serializer: KSerializer<T>, default: () -> T): Flow<T> =
        context.dataStore.data.map { prefs ->
            prefs[key]?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() } ?: default()
        }

    private suspend fun <T> write(key: Preferences.Key<String>, serializer: KSerializer<T>, value: T) {
        context.dataStore.edit { it[key] = json.encodeToString(serializer, value) }
    }

    private companion object {
        val PROFILE = stringPreferencesKey("profile")
        val FAVORITES = stringPreferencesKey("favorites")
        val HISTORY = stringPreferencesKey("history")
        val LAST = stringPreferencesKey("last_settings")
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val WRITE_BLOCKED = booleanPreferencesKey("write_blocked")
    }
}
