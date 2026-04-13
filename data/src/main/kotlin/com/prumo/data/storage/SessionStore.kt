package com.prumo.data.storage

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.prumo.core.model.AccessMode
import com.prumo.core.model.AppLanguage
import com.prumo.core.model.AppRole
import com.prumo.core.model.EffectivePermission
import com.prumo.core.model.PermissionScopeType
import com.prumo.core.model.PermissionSource
import com.prumo.core.model.SessionToken
import com.prumo.core.model.SessionUser
import org.json.JSONObject

interface SessionStore {
    fun read(): SessionToken?
    fun save(token: SessionToken)
    fun clear()
}

class EncryptedSessionStore(context: Context) : SessionStore {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "promo_secure_session",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override fun read(): SessionToken? {
        val raw = prefs.getString(KEY, null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            val now = System.currentTimeMillis() / 1000L
            val startedAt = json.optLong("sessionStartedAtEpochSeconds", now)
            val rememberEnabled = json.optBoolean("rememberEnabled", true)
            SessionToken(
                accessToken = json.getString("accessToken"),
                refreshToken = json.getString("refreshToken"),
                expiresAtEpochSeconds = json.getLong("expiresAtEpochSeconds"),
                sessionStartedAtEpochSeconds = startedAt,
                lastRefreshAtEpochSeconds = json.optLong("lastRefreshAtEpochSeconds", startedAt),
                expiresPolicyAtEpochSeconds = json.optLong(
                    "expiresPolicyAtEpochSeconds",
                    startedAt + if (rememberEnabled) (30L * 24L * 60L * 60L) else (24L * 60L * 60L)
                ),
                rememberEnabled = rememberEnabled,
                quickUnlockEnabled = json.optBoolean("quickUnlockEnabled", true),
                user = SessionUser(
                    userId = json.getString("userId"),
                    email = json.getString("email"),
                    fullName = nullableString(json, "fullName"),
                    role = AppRole.parse(nullableString(json, "role")),
                    tenantId = json.getString("tenantId"),
                    isActive = json.optBoolean("isActive", false),
                    preferredLanguage = AppLanguage.parse(nullableString(json, "preferredLanguage")),
                    accessMode = AccessMode.parse(nullableString(json, "accessMode")),
                    obraScope = readStringArray(json, "obraScope"),
                    permissions = readPermissions(json),
                    multiObraEnabled = json.optBoolean("multiObraEnabled", true),
                    defaultObraId = nullableString(json, "defaultObraId")
                )
            )
        }.getOrNull()
    }

    override fun save(token: SessionToken) {
        val json = JSONObject()
            .put("accessToken", token.accessToken)
            .put("refreshToken", token.refreshToken)
            .put("expiresAtEpochSeconds", token.expiresAtEpochSeconds)
            .put("sessionStartedAtEpochSeconds", token.sessionStartedAtEpochSeconds)
            .put("lastRefreshAtEpochSeconds", token.lastRefreshAtEpochSeconds)
            .put("expiresPolicyAtEpochSeconds", token.expiresPolicyAtEpochSeconds)
            .put("rememberEnabled", token.rememberEnabled)
            .put("quickUnlockEnabled", token.quickUnlockEnabled)
            .put("userId", token.user.userId)
            .put("email", token.user.email)
            .put("fullName", token.user.fullName)
            .put("role", token.user.role?.wireValue)
            .put("tenantId", token.user.tenantId)
            .put("isActive", token.user.isActive)
            .put("preferredLanguage", token.user.preferredLanguage.wireValue)
            .put("accessMode", token.user.accessMode.wireValue)
            .put("obraScope", org.json.JSONArray(token.user.obraScope))
            .put("multiObraEnabled", token.user.multiObraEnabled)
            .put("defaultObraId", token.user.defaultObraId)
            .put("permissions", org.json.JSONArray(token.user.permissions.map { permission ->
                JSONObject()
                    .put("permissionKey", permission.permissionKey)
                    .put("scopeType", permission.scopeType.wireValue)
                    .put("source", permission.source.name)
                    .put("obraIds", org.json.JSONArray(permission.obraIds))
            }))

        prefs.edit().putString(KEY, json.toString()).apply()
    }

    override fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    private companion object {
        const val KEY = "session"

        private fun nullableString(json: JSONObject, key: String): String? {
            if (json.isNull(key)) return null
            return json.optString(key).ifBlank { null }
        }

        private fun readStringArray(json: JSONObject, key: String): List<String> {
            val array = json.optJSONArray(key) ?: return emptyList()
            return buildList {
                for (i in 0 until array.length()) add(array.getString(i))
            }
        }

        private fun readPermissions(json: JSONObject): List<EffectivePermission> {
            val array = json.optJSONArray("permissions") ?: return emptyList()
            return buildList {
                for (i in 0 until array.length()) {
                    val row = array.optJSONObject(i) ?: continue
                    add(
                        EffectivePermission(
                            permissionKey = row.optString("permissionKey"),
                            scopeType = PermissionScopeType.parse(row.optString("scopeType")),
                            obraIds = readStringArray(row, "obraIds"),
                            source = runCatching { PermissionSource.valueOf(row.optString("source")) }
                                .getOrDefault(PermissionSource.CUSTOM)
                        )
                    )
                }
            }
        }
    }
}
