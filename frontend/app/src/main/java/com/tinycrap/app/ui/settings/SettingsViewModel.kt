package com.tinycrap.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tinycrap.app.TinyCrapApp
import com.tinycrap.app.data.ApiException
import com.tinycrap.app.data.SettingsState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ServerCheck {
    data object Idle : ServerCheck
    data object Testing : ServerCheck
    data class Result(val ok: Boolean, val message: String) : ServerCheck
}

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val tc = app as TinyCrapApp

    val settings: StateFlow<SettingsState> = tc.settings.state
    val notifier get() = tc.notifier

    private val _check = MutableStateFlow<ServerCheck>(ServerCheck.Idle)
    val check: StateFlow<ServerCheck> = _check.asStateFlow()

    fun update(transform: (SettingsState) -> SettingsState) = tc.settings.update(transform)

    fun locationLabel(s: SettingsState): String = tc.files.locationLabel(s)
    fun treeLabel(uri: android.net.Uri): String = tc.files.treeLabel(uri)

    fun saveServer(url: String, key: String) {
        update { it.copy(serverUrl = url.trim(), apiKey = key.trim()) }
        _check.value = ServerCheck.Idle
    }

    fun testServer(url: String, key: String) {
        _check.value = ServerCheck.Testing
        viewModelScope.launch {
            val result = try {
                ServerCheck.Result(true, tc.api.health(url, key))
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                ServerCheck.Result(false, e.message)
            } catch (e: Exception) {
                ServerCheck.Result(false, "Could not reach the server.")
            }
            _check.update { result }
        }
    }

    fun clearHistory() = tc.library.clear()
    val historyCount: StateFlow<List<com.tinycrap.app.data.LibraryItem>> get() = tc.library.items
}
