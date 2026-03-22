package com.prumo.feature.auth

import com.prumo.core.model.SignupMode
import java.util.Locale

private const val FULL_NAME_MIN = 2
private const val FULL_NAME_MAX = 120
private const val COMPANY_MIN = 2
private const val COMPANY_MAX = 120
private const val USERNAME_MIN = 3
private const val USERNAME_MAX = 50
private const val JOB_TITLE_MIN = 2
private const val JOB_TITLE_MAX = 80
private const val EMAIL_MAX = 254
private const val PHONE_MIN = 10
private const val PHONE_MAX = 13
private const val PASSWORD_MIN = 6

private val hasAlphaNumericRegex = Regex("[\\p{L}\\p{N}]")
private val hasLetterRegex = Regex("\\p{L}")
private val emailRegex = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

private fun collapseWhitespace(value: String): String = value.replace(Regex("\\s+"), " ").trimStart()
private fun trimAndCollapse(value: String): String = collapseWhitespace(value).trim()

private fun sanitizeByRegex(value: String, invalidRegex: Regex, max: Int): String =
    collapseWhitespace(value).replace(invalidRegex, "").take(max)

fun sanitizeFullName(value: String): String =
    sanitizeByRegex(value, Regex("[^\\p{L}\\s'\\-]"), FULL_NAME_MAX)

fun sanitizeCompanyName(value: String): String =
    sanitizeByRegex(value, Regex("[^\\p{L}\\p{N}\\s.,&\\-/()]"), COMPANY_MAX)

fun sanitizeUsername(value: String): String =
    sanitizeByRegex(value, Regex("[^\\p{L}\\p{N}\\s._-]"), USERNAME_MAX)

fun sanitizeJobTitle(value: String): String =
    sanitizeByRegex(value, Regex("[^\\p{L}\\p{N}\\s._\\-/()]"), JOB_TITLE_MAX)

fun sanitizeEmail(value: String): String =
    value.replace(Regex("\\s+"), "").lowercase(Locale.ROOT).take(EMAIL_MAX)

fun sanitizePhone(value: String): String =
    value.filter { it.isDigit() }.take(PHONE_MAX)

data class SignupValidationInput(
    val fullName: String,
    val companyName: String,
    val username: String,
    val jobTitle: String,
    val email: String,
    val phone: String,
    val password: String,
    val signupMode: SignupMode,
    val tenantId: String?
)

data class SignupValidationErrors(
    val fullName: String? = null,
    val companyName: String? = null,
    val username: String? = null,
    val jobTitle: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val password: String? = null,
    val tenantId: String? = null
) {
    fun hasErrors(): Boolean =
        listOf(fullName, companyName, username, jobTitle, email, phone, password, tenantId).any { !it.isNullOrBlank() }

    fun firstError(): String? =
        listOf(fullName, companyName, username, jobTitle, email, phone, password, tenantId).firstOrNull { !it.isNullOrBlank() }
}

private fun validateHumanText(
    value: String,
    min: Int,
    max: Int,
    requireLetter: Boolean,
    label: String
): String? {
    val normalized = trimAndCollapse(value)
    if (normalized.isBlank()) return "$label e obrigatorio."
    if (normalized.length < min) return "$label deve ter ao menos $min caracteres."
    if (normalized.length > max) return "$label deve ter no maximo $max caracteres."
    if (requireLetter && !hasLetterRegex.containsMatchIn(normalized)) return "$label deve conter letras."
    if (!requireLetter && !hasAlphaNumericRegex.containsMatchIn(normalized)) return "$label nao pode conter apenas simbolos."
    return null
}

private fun validateEmail(value: String): String? {
    val normalized = sanitizeEmail(value).trim()
    if (normalized.isBlank()) return "E-mail e obrigatorio."
    if (normalized.length > EMAIL_MAX) return "E-mail deve ter no maximo $EMAIL_MAX caracteres."
    if (!emailRegex.matches(normalized)) return "Informe um e-mail valido."
    return null
}

private fun validatePhone(value: String): String? {
    val normalized = sanitizePhone(value)
    if (normalized.isBlank()) return null
    if (normalized.length < PHONE_MIN || normalized.length > PHONE_MAX) {
        return "Telefone deve ter entre $PHONE_MIN e $PHONE_MAX numeros."
    }
    return null
}

private fun validatePassword(value: String): String? {
    if (value.isBlank()) return "Senha e obrigatoria."
    if (value.length < PASSWORD_MIN) return "Senha deve ter ao menos $PASSWORD_MIN caracteres."
    return null
}

fun validateSignupInput(input: SignupValidationInput): SignupValidationErrors {
    return SignupValidationErrors(
        fullName = validateHumanText(input.fullName, FULL_NAME_MIN, FULL_NAME_MAX, requireLetter = true, label = "Nome completo"),
        companyName = validateHumanText(input.companyName, COMPANY_MIN, COMPANY_MAX, requireLetter = false, label = "Nome da empresa"),
        username = validateHumanText(input.username, USERNAME_MIN, USERNAME_MAX, requireLetter = false, label = "Usuario"),
        jobTitle = validateHumanText(input.jobTitle, JOB_TITLE_MIN, JOB_TITLE_MAX, requireLetter = false, label = "Cargo"),
        email = validateEmail(input.email),
        phone = validatePhone(input.phone),
        password = validatePassword(input.password),
        tenantId = if (input.signupMode == SignupMode.COMPANY_INTERNAL && input.tenantId.isNullOrBlank()) {
            "Selecione uma empresa valida na lista antes de enviar."
        } else {
            null
        }
    )
}
