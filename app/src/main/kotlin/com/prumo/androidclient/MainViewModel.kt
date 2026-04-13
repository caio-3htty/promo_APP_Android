package com.prumo.androidclient

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prumo.core.model.SessionToken
import com.prumo.core.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MainUiState(
    val bootDone: Boolean = false,
    val session: SessionToken? = null,
    val selectedObraId: String? = null,
    val sessionLocked: Boolean = false,
    val loadingAccess: Boolean = false,
    val error: String? = null
)

class MainViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {
    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()
    private var skipNextLock = false

    fun markJustAuthenticated() {
        skipNextLock = true
    }

    fun bootstrap() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loadingAccess = true, error = null)
            runCatching {
                val current = authRepository.currentSession()
                if (current != null) {
                    authRepository.refreshSessionContext() ?: current
                } else {
                    null
                }
            }.onSuccess { session ->
                val lockRequired = session?.quickUnlockEnabled == true && !skipNextLock
                skipNextLock = false
                _state.value = _state.value.copy(
                    bootDone = true,
                    session = session,
                    selectedObraId = _state.value.selectedObraId ?: session?.user?.defaultObraId,
                    sessionLocked = lockRequired,
                    loadingAccess = false
                )
            }.onFailure { error ->
                skipNextLock = false
                _state.value = _state.value.copy(
                    bootDone = true,
                    session = null,
                    selectedObraId = null,
                    loadingAccess = false,
                    error = error.message ?: "Falha ao carregar sessao."
                )
            }
        }
    }

    fun onLoggedIn(token: SessionToken) {
        _state.value = _state.value.copy(
            session = token,
            selectedObraId = token.user.defaultObraId,
            sessionLocked = token.quickUnlockEnabled,
            error = null
        )
    }

    fun unlockSession() {
        _state.value = _state.value.copy(sessionLocked = false, error = null)
    }

    fun requirePasswordLogin() {
        viewModelScope.launch {
            authRepository.clearSession()
            _state.value = MainUiState(bootDone = true, session = null, sessionLocked = false)
        }
    }

    fun selectObra(obraId: String) {
        _state.value = _state.value.copy(selectedObraId = obraId)
    }

    fun refreshAccess() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loadingAccess = true)
            runCatching { authRepository.refreshSessionContext() }
                .onSuccess { refreshed ->
                    _state.value = _state.value.copy(
                        loadingAccess = false,
                        session = refreshed,
                        selectedObraId = _state.value.selectedObraId ?: refreshed?.user?.defaultObraId,
                        sessionLocked = refreshed?.quickUnlockEnabled == true && _state.value.sessionLocked,
                        error = null
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        loadingAccess = false,
                        error = it.message ?: "Falha ao atualizar acessos."
                    )
                }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _state.value = MainUiState(bootDone = true, session = null)
        }
    }
}
