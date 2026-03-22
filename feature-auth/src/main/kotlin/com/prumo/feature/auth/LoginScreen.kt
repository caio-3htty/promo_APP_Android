package com.prumo.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prumo.core.i18n.t
import com.prumo.core.model.AppRole
import com.prumo.core.model.SignupMode
import kotlinx.coroutines.delay

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    origin: String,
    onLoggedIn: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.success) {
        if (state.success) {
            viewModel.consumeSuccess()
            onLoggedIn()
        }
    }

    LaunchedEffect(state.isSignUp, state.signupMode, state.companyName, state.tenantId) {
        if (!state.isSignUp || state.signupMode != SignupMode.COMPANY_INTERNAL) return@LaunchedEffect
        if (state.tenantId != null) return@LaunchedEffect
        delay(250)
        viewModel.searchCompanies()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(t("app.name"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(
            if (state.isSignUp) t("auth.create_or_request") else t("auth.login"),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp)
        )

        if (state.isSignUp) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.onSignupModeChange(SignupMode.COMPANY_OWNER) },
                    modifier = Modifier.weight(1f),
                    enabled = !state.loading
                ) {
                    Text(if (state.signupMode == SignupMode.COMPANY_OWNER) "${t("auth.company_account")} *" else t("auth.company_account"))
                }
                Button(
                    onClick = { viewModel.onSignupModeChange(SignupMode.COMPANY_INTERNAL) },
                    modifier = Modifier.weight(1f),
                    enabled = !state.loading
                ) {
                    Text(if (state.signupMode == SignupMode.COMPANY_INTERNAL) "${t("auth.internal_account")} *" else t("auth.internal_account"))
                }
            }

            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                value = state.fullName,
                onValueChange = viewModel::onFullNameChange,
                label = { Text(t("auth.full_name")) },
                enabled = !state.loading
            )

            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                value = state.companyName,
                onValueChange = viewModel::onCompanyNameChange,
                label = { Text(t("auth.company_name")) },
                enabled = !state.loading
            )
            if (state.signupMode == SignupMode.COMPANY_INTERNAL) {
                if (state.loadingCompanies) {
                    Text(
                        text = "Buscando empresas...",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    )
                }

                if (!state.loadingCompanies && state.companySuggestions.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        state.companySuggestions.forEach { company ->
                            Button(
                                onClick = { viewModel.selectCompany(company) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !state.loading
                            ) {
                                val suffix = if (company.slug.isNullOrBlank()) "" else " (${company.slug})"
                                Text("${company.name}$suffix")
                            }
                        }
                    }
                }

                if (
                    !state.loadingCompanies &&
                    state.companyName.trim().length >= 3 &&
                    state.companySuggestions.isEmpty() &&
                    state.tenantId == null
                ) {
                    Text(
                        text = "Nenhuma empresa valida encontrada. Ajuste o nome e selecione na lista.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                    )
                }

                if (state.tenantId != null) {
                    Text(
                        text = "Empresa validada para envio da solicitacao.",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                    )
                }
            }

            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                value = state.username,
                onValueChange = viewModel::onUsernameChange,
                label = { Text(t("auth.username")) },
                enabled = !state.loading
            )

            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                value = state.jobTitle,
                onValueChange = viewModel::onJobTitleChange,
                label = { Text(t("auth.job_title")) },
                enabled = !state.loading
            )

            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                value = state.phone,
                onValueChange = viewModel::onPhoneChange,
                label = { Text("Telefone (opcional)") },
                enabled = !state.loading,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }

        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (state.isSignUp) 12.dp else 0.dp),
            value = state.email,
            onValueChange = viewModel::onEmailChange,
            label = { Text(t("auth.corporate_email")) },
            enabled = !state.loading
        )

        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            label = { Text(t("auth.password")) },
            visualTransformation = PasswordVisualTransformation(),
            enabled = !state.loading
        )

        if (state.isSignUp && state.signupMode == SignupMode.COMPANY_INTERNAL) {
            RolePicker(
                selected = state.requestedRole,
                enabled = !state.loading,
                onSelect = viewModel::onRequestedRoleChange
            )
        }

        state.error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
            )
        }

        state.message?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
            )
        }

        Button(
            onClick = { viewModel.submit(origin) },
            enabled = !state.loading,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        ) {
            if (state.loading) {
                CircularProgressIndicator()
            } else {
                Text(if (state.isSignUp) t("auth.send_request") else t("auth.login"))
            }
        }

        Button(
            onClick = viewModel::toggleSignUpMode,
            enabled = !state.loading,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Text(if (state.isSignUp) t("auth.have_account") else t("auth.create_account"))
        }
    }
}

@Composable
private fun RolePicker(
    selected: AppRole,
    enabled: Boolean,
    onSelect: (AppRole) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(t("auth.access_profile"), style = MaterialTheme.typography.labelLarge)
        Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                AppRole.GESTOR,
                AppRole.ENGENHEIRO,
                AppRole.OPERACIONAL,
                AppRole.ALMOXARIFE
            ).forEach { role ->
                Button(
                    onClick = { onSelect(role) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        if (selected == role) "${role.wireValue} *" else role.wireValue,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}
