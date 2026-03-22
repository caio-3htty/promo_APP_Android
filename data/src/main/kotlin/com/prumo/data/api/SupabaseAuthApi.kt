package com.prumo.data.api

import com.prumo.data.model.AuthResponseDto
import com.prumo.data.model.AccessRequestGetResponseDto
import com.prumo.data.model.AccessSignupResponseDto
import com.prumo.data.model.CompanySearchResponseDto
import com.prumo.data.model.CompanySuggestionDto
import com.prumo.core.model.SupabaseConfig
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class AuthApiException(
    val code: String?,
    override val message: String
) : IllegalStateException(message)

class SupabaseAuthApi(
    private val config: SupabaseConfig,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private val client = OkHttpClient()
    private val mediaType = "application/json".toMediaType()

    fun login(email: String, password: String): AuthResponseDto {
        val body = JSONObject()
            .put("email", email)
            .put("password", password)
            .toString()
            .toRequestBody(mediaType)
        val request = Request.Builder()
            .url("${config.baseUrl}/auth/v1/token?grant_type=password")
            .header("apikey", config.anonKey)
            .header("Content-Type", "application/json")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val normalizedPayload = payload.lowercase()
                val friendlyMessage = when {
                    normalizedPayload.contains("invalid login credentials") ||
                        normalizedPayload.contains("invalid_grant") ->
                        "Credenciais invalidas. Verifique e-mail e senha."
                    normalizedPayload.contains("email not confirmed") ->
                        "E-mail nao confirmado. Confirme seu e-mail antes de entrar."
                    normalizedPayload.contains("invalid api key") ->
                        "Falha de ambiente/chave Supabase. Verifique configuracao."
                    else -> "Falha ao autenticar (${response.code})."
                }
                throw AuthApiException(
                    code = extractCode(payload),
                    message = friendlyMessage,
                )
            }
            return json.decodeFromString(AuthResponseDto.serializer(), payload)
        }
    }

    fun refresh(refreshToken: String): AuthResponseDto {
        val body = JSONObject()
            .put("refresh_token", refreshToken)
            .toString()
            .toRequestBody(mediaType)
        val request = Request.Builder()
            .url("${config.baseUrl}/auth/v1/token?grant_type=refresh_token")
            .header("apikey", config.anonKey)
            .header("Content-Type", "application/json")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw AuthApiException(
                    code = extractCode(payload),
                    message = "Falha ao atualizar sessao (${response.code}).",
                )
            }
            return json.decodeFromString(AuthResponseDto.serializer(), payload)
        }
    }

    fun registerCompany(
        email: String,
        password: String,
        fullName: String,
        username: String,
        companyName: String,
        jobTitle: String,
        phone: String?,
        origin: String
    ): AccessSignupResponseDto {
        val body = JSONObject()
            .put("action", "register_company")
            .put("email", email)
            .put("password", password)
            .put("fullName", fullName)
            .put("username", username)
            .put("companyName", companyName)
            .put("jobTitle", jobTitle)
            .put("phone", phone ?: JSONObject.NULL)
            .put("requestedRole", "master")
            .put("origin", origin)
            .toString()
        return postAccessAction(body)
    }

    fun registerInternal(
        email: String,
        password: String,
        fullName: String,
        username: String,
        companyName: String,
        tenantId: String,
        jobTitle: String,
        phone: String?,
        requestedRole: String,
        origin: String
    ): AccessSignupResponseDto {
        val body = JSONObject()
            .put("action", "register_internal")
            .put("email", email)
            .put("password", password)
            .put("fullName", fullName)
            .put("username", username)
            .put("companyName", companyName)
            .put("tenantId", tenantId)
            .put("jobTitle", jobTitle)
            .put("phone", phone ?: JSONObject.NULL)
            .put("requestedRole", requestedRole)
            .put("origin", origin)
            .toString()
        return postAccessAction(body)
    }

    fun searchCompanies(query: String): List<CompanySuggestionDto> {
        if (query.trim().length < 3) return emptyList()
        val body = JSONObject()
            .put("action", "search_companies")
            .put("query", query.trim())
            .toString()

        val request = Request.Builder()
            .url("${config.baseUrl}/functions/v1/account-access-request")
            .header("apikey", config.anonKey)
            .header("Authorization", "Bearer ${config.anonKey}")
            .header("Content-Type", "application/json")
            .post(body.toRequestBody(mediaType))
            .build()

        client.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw AuthApiException(
                    code = extractCode(payload),
                    message = "Falha ao consultar empresas (${response.code}).",
                )
            }

            val decoded = json.decodeFromString(CompanySearchResponseDto.serializer(), payload)
            if (!decoded.ok) {
                throw AuthApiException(
                    code = decoded.code,
                    message = decoded.message ?: "Nao foi possivel consultar empresas.",
                )
            }
            return decoded.companies
        }
    }

    fun getAccessRequest(token: String): AccessRequestGetResponseDto {
        val body = JSONObject()
            .put("action", "get_request")
            .put("token", token)
            .toString()
        val request = Request.Builder()
            .url("${config.baseUrl}/functions/v1/account-access-request")
            .header("apikey", config.anonKey)
            .header("Authorization", "Bearer ${config.anonKey}")
            .header("Content-Type", "application/json")
            .post(body.toRequestBody(mediaType))
            .build()

        client.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = extractMessage(payload, "Falha ao carregar solicitacao (${response.code}).")
                throw AuthApiException(
                    code = extractCode(payload),
                    message = message,
                )
            }
            val decoded = json.decodeFromString(AccessRequestGetResponseDto.serializer(), payload)
            if (!decoded.ok) {
                throw AuthApiException(
                    code = decoded.code,
                    message = decoded.message ?: "Nao foi possivel carregar a solicitacao.",
                )
            }
            return decoded
        }
    }

    fun reviewAccessRequest(
        token: String,
        decision: String,
        reviewedUsername: String,
        reviewedJobTitle: String,
        reviewedRole: String,
        reviewNotes: String
    ): AccessSignupResponseDto {
        val body = JSONObject()
            .put("action", "review_request")
            .put("token", token)
            .put("decision", decision)
            .put("reviewedUsername", reviewedUsername)
            .put("reviewedJobTitle", reviewedJobTitle)
            .put("reviewedRole", reviewedRole)
            .put("reviewNotes", reviewNotes)
            .toString()
        return postAccessAction(body)
    }

    private fun postAccessAction(body: String): AccessSignupResponseDto {
        val request = Request.Builder()
            .url("${config.baseUrl}/functions/v1/account-access-request")
            .header("apikey", config.anonKey)
            .header("Authorization", "Bearer ${config.anonKey}")
            .header("Content-Type", "application/json")
            .post(body.toRequestBody(mediaType))
            .build()

        client.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw AuthApiException(
                    code = extractCode(payload),
                    message = extractMessage(payload, "Falha ao processar solicitacao (${response.code})."),
                )
            }
            return json.decodeFromString(AccessSignupResponseDto.serializer(), payload)
        }
    }

    private fun extractCode(payload: String): String? {
        if (payload.isBlank()) return null
        return runCatching { JSONObject(payload).optString("code").ifBlank { null } }.getOrNull()
    }

    private fun extractMessage(payload: String, fallback: String): String {
        if (payload.isBlank()) return fallback
        return runCatching {
            val jsonPayload = JSONObject(payload)
            jsonPayload.optString("message").ifBlank {
                jsonPayload.optString("error_description").ifBlank {
                    jsonPayload.optString("error").ifBlank { fallback }
                }
            }
        }.getOrElse { fallback }
    }
}
