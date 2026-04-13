package com.prumo.core.model

enum class AppRole(val wireValue: String) {
    MASTER("master"),
    GESTOR("gestor"),
    ENGENHEIRO("engenheiro"),
    OPERACIONAL("operacional"),
    ALMOXARIFE("almoxarife");

    companion object {
        fun parse(value: String?): AppRole? = entries.firstOrNull { it.wireValue == value }
    }
}

enum class AppLanguage(val wireValue: String) {
    PT_BR("pt-BR"),
    EN("en"),
    ES("es");

    companion object {
        fun parse(value: String?): AppLanguage = entries.firstOrNull { it.wireValue == value } ?: PT_BR
    }
}

enum class AccessMode(val wireValue: String) {
    TEMPLATE("template"),
    CUSTOM("custom");

    companion object {
        fun parse(value: String?): AccessMode = entries.firstOrNull { it.wireValue == value } ?: TEMPLATE
    }
}

enum class PermissionScopeType(val wireValue: String) {
    TENANT("tenant"),
    ALL_OBRAS("all_obras"),
    SELECTED_OBRAS("selected_obras");

    companion object {
        fun parse(value: String?): PermissionScopeType =
            entries.firstOrNull { it.wireValue == value } ?: TENANT
    }
}

enum class PermissionSource {
    TEMPLATE,
    CUSTOM
}

enum class SignupMode {
    COMPANY_OWNER,
    COMPANY_INTERNAL
}

enum class AccessReviewDecision(val wireValue: String) {
    APPROVE("approve"),
    REJECT("reject"),
    EDIT("edit")
}

data class EffectivePermission(
    val permissionKey: String,
    val scopeType: PermissionScopeType,
    val obraIds: List<String>,
    val source: PermissionSource
)

data class SessionUser(
    val userId: String,
    val email: String,
    val fullName: String?,
    val role: AppRole?,
    val tenantId: String,
    val isActive: Boolean,
    val preferredLanguage: AppLanguage = AppLanguage.PT_BR,
    val accessMode: AccessMode = AccessMode.TEMPLATE,
    val obraScope: List<String> = emptyList(),
    val permissions: List<EffectivePermission> = emptyList(),
    val multiObraEnabled: Boolean = true,
    val defaultObraId: String? = null
)

data class SessionToken(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochSeconds: Long,
    val user: SessionUser,
    val sessionStartedAtEpochSeconds: Long = System.currentTimeMillis() / 1000L,
    val lastRefreshAtEpochSeconds: Long = System.currentTimeMillis() / 1000L,
    val expiresPolicyAtEpochSeconds: Long = (System.currentTimeMillis() / 1000L) + (30L * 24L * 60L * 60L),
    val rememberEnabled: Boolean = true,
    val quickUnlockEnabled: Boolean = true,
)

data class ObraSummary(
    val id: String,
    val name: String,
    val status: String,
    val address: String?,
    val description: String?,
    val deletedAt: String? = null
)

data class MaterialSummary(
    val id: String,
    val nome: String,
    val unidade: String,
    val tempoProducaoPadrao: Int? = null,
    val estoqueMinimo: Double = 0.0,
    val deletedAt: String? = null
)

data class MaterialRecord(
    val id: String,
    val nome: String,
    val unidade: String,
    val tempoProducaoPadrao: Int?,
    val estoqueMinimo: Double,
    val deletedAt: String?
)

data class FornecedorSummary(
    val id: String,
    val nome: String,
    val cnpj: String? = null,
    val contatos: String? = null,
    val entregaPropria: Boolean = false,
    val deletedAt: String? = null
)

data class MaterialFornecedorRecord(
    val id: String,
    val materialId: String,
    val fornecedorId: String,
    val precoAtual: Double,
    val pedidoMinimo: Double,
    val leadTimeDias: Int,
    val validadePreco: String?,
    val materialNome: String?,
    val materialUnidade: String?,
    val fornecedorNome: String?,
    val fornecedorCnpj: String?,
    val deletedAt: String?
)

data class PedidoResumo(
    val id: String,
    val obraId: String,
    val obraNome: String?,
    val materialId: String,
    val materialNome: String?,
    val materialUnidade: String?,
    val fornecedorId: String,
    val fornecedorNome: String?,
    val quantidade: Double,
    val precoUnit: Double,
    val total: Double,
    val status: String,
    val codigoCompra: String?,
    val criadoEm: String? = null,
    val dataRecebimento: String? = null,
    val deletedAt: String? = null
)

data class PedidoInput(
    val obraId: String,
    val materialId: String,
    val fornecedorId: String,
    val quantidade: Double,
    val precoUnit: Double,
    val status: String = "pendente",
    val codigoCompra: String? = null,
    val observacoes: String? = null
)

data class RecebimentoInput(
    val pedidoId: String,
    val obraId: String,
    val materialId: String,
    val quantidade: Double,
    val codigoCompra: String,
    val dataRecebimentoIso: String,
    val recebidoPor: String?
)

data class EstoqueItem(
    val id: String,
    val obraId: String,
    val obraNome: String?,
    val materialId: String,
    val materialNome: String?,
    val unidade: String?,
    val estoqueAtual: Double,
    val atualizadoEm: String
)

data class EstoqueUpdateInput(
    val estoqueAtual: Double
)

data class SignupRequestInput(
    val mode: SignupMode,
    val email: String,
    val password: String,
    val fullName: String,
    val companyName: String,
    val tenantId: String? = null,
    val username: String,
    val jobTitle: String,
    val phone: String? = null,
    val requestedRole: AppRole,
    val origin: String
)

data class SignupResult(
    val ok: Boolean,
    val message: String,
    val emailSent: Boolean = false,
    val code: String? = null
)

data class CompanySuggestion(
    val id: String,
    val name: String,
    val slug: String?
)

data class AccessRequestReviewData(
    val id: String,
    val requestType: String,
    val status: String,
    val applicantEmail: String,
    val applicantFullName: String,
    val companyName: String,
    val requestedUsername: String,
    val requestedJobTitle: String,
    val requestedRole: AppRole
)

data class PermissionCatalogItem(
    val key: String,
    val area: String,
    val labelPt: String,
    val obraScoped: Boolean,
    val isActive: Boolean
)

data class UserPermissionGrantDraft(
    val permissionKey: String,
    val scopeType: PermissionScopeType,
    val obraIds: List<String>
)

data class AccessUserRecord(
    val userId: String,
    val tenantId: String,
    val fullName: String,
    val email: String?,
    val isActive: Boolean,
    val role: AppRole?,
    val accessMode: AccessMode,
    val userTypeId: String?,
    val obraIds: List<String>,
    val grants: List<UserPermissionGrantDraft> = emptyList()
)

data class UserTypeRecord(
    val id: String,
    val name: String,
    val description: String?,
    val baseRole: AppRole,
    val isActive: Boolean
)

data class UserTypeUpsertInput(
    val id: String? = null,
    val name: String,
    val description: String? = null,
    val baseRole: AppRole,
    val isActive: Boolean,
    val createdByUserId: String? = null
)

data class AccessAuditEntry(
    val id: String,
    val entityTable: String,
    val action: String,
    val changedBy: String?,
    val targetUserId: String?,
    val obraId: String?,
    val createdAt: String
)

data class UserAccessUpdateInput(
    val userId: String,
    val tenantId: String,
    val isActive: Boolean,
    val role: AppRole?,
    val accessMode: AccessMode,
    val userTypeId: String?,
    val obraIds: List<String>,
    val grants: List<UserPermissionGrantDraft> = emptyList(),
    val changedByUserId: String? = null
)

data class SupabaseConfig(
    val baseUrl: String,
    val anonKey: String
)
