package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.AiProvider
import com.example.data.model.GeminiModelOption
import com.example.data.model.OpenAiModelOption
import com.example.data.model.ThemePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.xproDataStore: DataStore<Preferences> by preferencesDataStore(name = "xpro_ai_prefs")

data class UserSettings(
    val themePreference: ThemePreference = ThemePreference.DARK,
    val preferredProvider: AiProvider = AiProvider.GEMINI,
    val geminiModel: GeminiModelOption = GeminiModelOption.FLASH_3_8,
    val openAiModel: OpenAiModelOption = OpenAiModelOption.GPT_4O_MINI,
    val autoSpeakVoice: Boolean = true
)

class UserPreferencesRepository(context: Context) {
    private val dataStore = context.applicationContext.xproDataStore

    private object Keys {
        val THEME = stringPreferencesKey("theme_preference")
        val PROVIDER = stringPreferencesKey("preferred_provider")
        val GEMINI_MODEL = stringPreferencesKey("gemini_model")
        val OPENAI_MODEL = stringPreferencesKey("openai_model")
        val AUTO_SPEAK = booleanPreferencesKey("auto_speak_voice")
    }

    val settingsFlow: Flow<UserSettings> = dataStore.data.map { prefs ->
        UserSettings(
            themePreference = ThemePreference.entries.firstOrNull {
                it.name == prefs[Keys.THEME]
            } ?: ThemePreference.DARK,
            preferredProvider = AiProvider.fromString(prefs[Keys.PROVIDER]),
            geminiModel = GeminiModelOption.fromModelId(prefs[Keys.GEMINI_MODEL]),
            openAiModel = OpenAiModelOption.fromModelId(prefs[Keys.OPENAI_MODEL]),
            autoSpeakVoice = prefs[Keys.AUTO_SPEAK] ?: true
        )
    }

    suspend fun setThemePreference(theme: ThemePreference) {
        dataStore.edit { it[Keys.THEME] = theme.name }
    }

    suspend fun setPreferredProvider(provider: AiProvider) {
        dataStore.edit { it[Keys.PROVIDER] = provider.name }
    }

    suspend fun setGeminiModel(model: GeminiModelOption) {
        dataStore.edit { it[Keys.GEMINI_MODEL] = model.modelId }
    }

    suspend fun setOpenAiModel(model: OpenAiModelOption) {
        dataStore.edit { it[Keys.OPENAI_MODEL] = model.modelId }
    }

    suspend fun setAutoSpeakVoice(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTO_SPEAK] = enabled }
    }
}
