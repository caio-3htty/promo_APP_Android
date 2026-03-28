package com.prumo.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prumo.core.model.AppRole
import com.prumo.core.model.CompanySuggestion
import com.prumo.core.model.SignupMode
import com.prumo.core.model.SignupRequestInput
import com.prumo.core.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LoginUiState(
    val isSignUp: Boolean = false,
    val signupMode: SignupMode = SignupMode.COMPANY_OWNER,
    val email: String = "",
    val password: String = "",
    val fullName: String = "",
    val companyName: String = "",
    val tenantId: String? = null,
    val companySuggestions: List<CompanySuggestion> = emptyList(),
    val loadingCompanies: Boolean = false,
    val username: String = "",
    val jobTitle: String = "",
    val phone: String = "",
    val requestedRole: AppRole = AppRole.OPERACIONAL,
    val loading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val success: Boolean = false
)

class LoginViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onEmailChange(value: String) {
        _uiState.value = _uiState.value.copy(email = sanitizeEmail(value), error = null)
    }

    fun onPasswordChange(value: String) {
        _uiState.value = _uiState.value.copy(password = value, error = null)
    }

    fun onFullNameChange(value: String) {
        _uiState.value = _uiState.value.copy(fullName = sanitizeFullName(value), error = null)
    }

    fun onCompanyNameChange(value: String) {
        val current = _uiState.value
        _uiState.value = current.copy(
            companyName = sanitizeCompanyName(value),
            tenantId = if (current.signupMode == SignupMode.COMPANY_INTERNAL) null else current.tenantId,
            companySuggestions = if (current.signupMode == SignupMode.COMPANY_INTERNAL) emptyList() else current.companySuggestions,
            error = null
        )
    }

    fun onUsernameChange(value: String) {
        _uiState.value = _uiState.value.copy(username = sanitizeUsername(value), error = null)
    }

    fun onJobTitleChange(value: String) {
        _uiState.value = _uiState.value.copy(jobTitle = sanitizeJobTitle(value), error = null)
    }

    fun onPhoneChange(value: String) {
        _uiState.value = _uiState.value.copy(phone = sanitizePhone(value), error = null)
    }

    fun onSignupModeChange(mode: SignupMode) {
        _uiState.value = _uiState.value.copy(
            signupMode = mode,
            tenantId = if (mode == SignupMode.COMPANY_INTERNAL) _uiState.value.tenantId else null,
            companySuggestions = emptyList(),
            loadingCompanies = false,
            error = null,
            message = null
        )
    }

    fun onRequestedRoleChange(role: AppRole) {
        _uiState.value = _uiState.value.copy(requestedRole = role, error = null)
    }

    fun toggleSignUpMode() {
        _uiState.value = _uiState.value.copy(
            isSignUp = !_uiState.value.isSignUp,
            companySuggestions = emptyList(),
            loadingCompanies = false,
            error = null,
            message = null
        )
    }

    fun searchCompanies() {
        val snapshot = _uiState.value
        if (!snapshot.isSignUp || snapshot.signupMode != SignupMode.COMPANY_INTERNAL) {
            _uiState.value = snapshot.copy(companySuggestions = emptyList(), loadingCompanies = false)
            return
        }

        if (snapshot.tenantId != null) {
            _uiState.value = snapshot.copy(companySuggestions = emptyList(), loadingCompanies = false)
            return
        }

        val query = snapshot.companyName.trim()
        if (query.length < 3) {
            _uiState.value = snapshot.copy(companySuggestions = emptyList(), loadingCompanies = false)
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loadingCompanies = true)
            runCatching { authRepository.searchCompanies(query) }
                .onSuccess { companies ->
                    _uiState.value = _uiState.value.copy(
                        loadingCompanies = false,
                        companySuggestions = companies
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        loadingCompanies = false,
                        companySuggestions = emptyList()
                    )
                }
        }
    }

    fun selectCompany(company: CompanySuggestion) {
        _uiState.value = _uiState.value.copy(
            companyName = company.name,
            tenantId = company.id,
            companySuggestions = emptyList(),
            loadingCompanies = false,
            error = null
        )
    }

    fun submit(origin: String) {
        if (_uiState.value.isSignUp) {
            signup(origin)
        } else {
            login()
        }
    }

    private fun login() {
        val snapshot = _uiState.value
        if (snapshot.email.isBlank() || snapshot.password.isBlank()) {
            _uiState.value = snapshot.copy(error = "Informe e-mail e senha")
            return
        }

        viewModelScope.launch {
            _uiState.value = snapshot.copy(loading = true, error = null)
            runCatching { authRepository.login(snapshot.email.trim(), snapshot.password) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        success = true,
                        message = null
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(loading = false, error = it.message ?: "Falha no login")
                }
        }
    }

    private fun signup(origin: String) {
        val snapshot = _uiState.value
        val validation = validateSignupInput(
            SignupValidationInput(
                fullName = snapshot.fullName,
                companyName = snapshot.companyName,
                username = snapshot.username,
                jobTitle = snapshot.jobTitle,
                email = snapshot.email,
                phone = snapshot.phone,
                password = snapshot.password,
                signupMode = snapshot.signupMode,
                tenantId = snapshot.tenantId
            )
        )
        if (validation.hasErrors()) {
            _uiState.value = snapshot.copy(error = validation.firstError() ?: "Revise os campos do cadastro.")
            return
        }

        viewModelScope.launch {
            _uiState.value = snapshot.copy(loading = true, error = null, message = null)
            runCatching {
                authRepository.signup(
                    SignupRequestInput(
                        mode = snapshot.signupMode,
                        email = snapshot.email.trim().lowercase(),
                        password = snapshot.password,
                        fullName = snapshot.fullName.trim(),
                        companyName = snapshot.companyName.trim(),
                        tenantId = if (snapshot.signupMode == SignupMode.COMPANY_INTERNAL) snapshot.tenantId else null,
                        username = snapshot.username.trim(),
                        jobTitle = snapshot.jobTitle.trim(),
                        phone = sanitizePhone(snapshot.phone).ifBlank { null },
                        requestedRole = if (snapshot.signupMode == SignupMode.COMPANY_OWNER) AppRole.MASTER else snapshot.requestedRole,
                        origin = origin
                    )
                )
            }.onSuccess { result ->
                if (result.ok) {
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        message = result.message,
                        error = null,
                        isSignUp = true
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        error = mapSignupError(result),
                    )
                }
            }.onFailure {
                _uiState.value = _uiState.value.copy(loading = false, error = it.message ?: "Falha ao enviar solicitacao")
            }
        }
    }

    fun consumeSuccess() {
        _uiState.value = _uiState.value.copy(success = false)
    }

    private fun mapSignupError(result: com.prumo.core.model.SignupResult): String {
        return when (result.code) {
            "tenant_required" -> "Selecione uma empresa valida na lista antes de enviar."
            "tenant_not_found" -> "Empresa nao encontrada. Solicite primeiro a criacao da conta empresa."
            "tenant_name_mismatch" -> "A empresa selecionada nao confere com o nome informado."
            "register_company_exists" -> "Empresa ja cadastrada. Use o fluxo de conta interna."
            "email_delivery_failed" -> "Cadastro nao concluido: o e-mail de boas-vindas nao foi enviado. Tente novamente."
            "invalid_phone_format", "phone_length_invalid" -> "Telefone invalido. Informe apenas numeros entre 10 e 13 digitos."
            "invalid_email_format", "email_length_invalid" -> "E-mail invalido. Revise o formato informado."
            "invalid_full_name_format", "full_name_length_invalid" -> "Nome completo invalido. Revise os caracteres e o tamanho."
            "invalid_company_name_format", "company_name_length_invalid" -> "Nome da empresa invalido."
            "invalid_username_format", "username_length_invalid" -> "Usuario invalido. Use letras, numeros, espaco, ponto, underscore ou hifen."
            "invalid_job_title_format", "job_title_length_invalid" -> "Cargo invalido. Revise os caracteres informados."
            "auth_user_create_failed" -> {
                if (result.message.contains("already", ignoreCase = true)) {
                    "E-mail ja cadastrado. Use outro e-mail ou recupere a senha."
                } else {
                    result.message
                }
            }
            else -> result.message
        }
    }
}
