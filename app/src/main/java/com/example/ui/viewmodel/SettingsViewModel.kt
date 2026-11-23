package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.model.AiProvider
import com.example.data.model.GeminiModelOption
import com.example.data.model.OpenAiModelOption
import com.example.data.model.ThemePreference
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.preferences.UserSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val prefsRepo: UserPreferencesRepository
) : ViewModel() {

    val settings: StateFlow<UserSettings> = prefsRepo.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UserSettings()
        )

    fun setTheme(theme: ThemePreference) {
        viewModelScope.launch { prefsRepo.setThemePreference(theme) }
    }

    fun toggleDarkLight() {
        val next = if (settings.value.themePreference == ThemePreference.LIGHT) {
            ThemePreference.DARK
        } else {
            ThemePreference.LIGHT
        }
        setTheme(next)
    }

    fun setProvider(provider: AiProvider) {
        viewModelScope.launch { prefsRepo.setPreferredProvider(provider) }
    }

    fun setGeminiModel(model: GeminiModelOption) {
        viewModelScope.launch { prefsRepo.setGeminiModel(model) }
    }

    fun setOpenAiModel(model: OpenAiModelOption) {
        viewModelScope.launch { prefsRepo.setOpenAiModel(model) }
    }

    fun setAutoSpeakVoice(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setAutoSpeakVoice(enabled) }
    }

    companion object {
        fun provideFactory(prefsRepo: UserPreferencesRepository): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { SettingsViewModel(prefsRepo) }
            }
    }
}
