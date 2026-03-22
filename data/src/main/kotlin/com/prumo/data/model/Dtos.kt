package com.prumo.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AuthResponseDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long,
    val user: AuthUserDto
)

@Serializable
data class AuthUserDto(
    val id: String,
    val email: String
)

@Serializable
data class ProfileDto(
    @SerialName("full_name") val fullName: String? = null,
    val email: String? = null,
    @SerialName("tenant_id") val tenantId: String,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("user_type_id") val userTypeId: String? = null,
    @SerialName("preferred_language") val preferredLanguage: String? = null,
    @SerialName("access_mode") val accessMode: String? = null
)

@Serializable
data class UserRoleDto(val role: String)

@Serializable
data class TenantSettingsDto(
    @SerialName("multi_obra_enabled") val multiObraEnabled: Boolean? = null,
    @SerialName("default_obra_id") val defaultObraId: String? = null
)

@Serializable
data class UserPermissionGrantDto(
    val id: String,
    @SerialName("permission_key") val permissionKey: String,
    @SerialName("scope_type") val scopeType: String
)

@Serializable
data class UserPermissionGrantByUserDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("permission_key") val permissionKey: String,
    @SerialName("scope_type") val scopeType: String
)

@Serializable
data class UserPermissionObraDto(
    @SerialName("grant_id") val grantId: String,
    @SerialName("obra_id") val obraId: String
)

@Serializable
data class UserTypePermissionDto(
    @SerialName("permission_key") val permissionKey: String,
    @SerialName("scope_type") val scopeType: String
)

@Serializable
data class ObraDto(
    val id: String,
    val name: String,
    val status: String,
    val address: String? = null,
    val description: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null
)

@Serializable
data class MaterialDto(
    val id: String,
    val nome: String,
    val unidade: String,
    @SerialName("tempo_producao_padrao") val tempoProducaoPadrao: Int? = null,
    @SerialName("estoque_minimo") val estoqueMinimo: Double = 0.0,
    @SerialName("deleted_at") val deletedAt: String? = null
)

@Serializable
data class FornecedorDto(
    val id: String,
    val nome: String,
    val cnpj: String? = null,
    val contatos: String? = null,
    @SerialName("entrega_propria") val entregaPropria: Boolean = false,
    @SerialName("deleted_at") val deletedAt: String? = null
)

@Serializable
data class NestedMaterialDto(
    val nome: String? = null,
    val unidade: String? = null
)

@Serializable
data class NestedFornecedorDto(
    val nome: String? = null,
    val cnpj: String? = null
)

@Serializable
data class NestedObraDto(
    val name: String? = null
)

@Serializable
data class MaterialFornecedorDto(
    val id: String,
    @SerialName("material_id") val materialId: String,
    @SerialName("fornecedor_id") val fornecedorId: String,
    @SerialName("preco_atual") val precoAtual: Double,
    @SerialName("pedido_minimo") val pedidoMinimo: Double,
    @SerialName("lead_time_dias") val leadTimeDias: Int,
    @SerialName("validade_preco") val validadePreco: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
    val materiais: NestedMaterialDto? = null,
    val fornecedores: NestedFornecedorDto? = null
)

@Serializable
data class PedidoDto(
    val id: String,
    @SerialName("obra_id") val obraId: String,
    @SerialName("material_id") val materialId: String,
    @SerialName("fornecedor_id") val fornecedorId: String,
    val quantidade: Double,
    @SerialName("preco_unit") val precoUnit: Double,
    val total: Double,
    val status: String,
    @SerialName("codigo_compra") val codigoCompra: String? = null,
    @SerialName("criado_em") val criadoEm: String? = null,
    @SerialName("data_recebimento") val dataRecebimento: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
    val obras: NestedObraDto? = null,
    val materiais: NestedMaterialDto? = null,
    val fornecedores: NestedFornecedorDto? = null
)

@Serializable
data class EstoqueDto(
    val id: String,
    @SerialName("obra_id") val obraId: String,
    @SerialName("material_id") val materialId: String,
    @SerialName("estoque_atual") val estoqueAtual: Double,
    @SerialName("atualizado_em") val atualizadoEm: String,
    val obras: NestedObraDto? = null,
    val materiais: NestedMaterialDto? = null
)

@Serializable
data class PedidoUpsertDto(
    @SerialName("obra_id") val obraId: String,
    @SerialName("material_id") val materialId: String,
    @SerialName("fornecedor_id") val fornecedorId: String,
    val quantidade: Double,
    @SerialName("preco_unit") val precoUnit: Double,
    val total: Double,
    val status: String,
    @SerialName("codigo_compra") val codigoCompra: String? = null,
    val observacoes: String? = null
)

@Serializable
data class EstoquePatchDto(
    @SerialName("estoque_atual") val estoqueAtual: Double
)

@Serializable
data class ObraUpsertDto(
    val name: String,
    val status: String,
    val address: String? = null,
    val description: String? = null
)

@Serializable
data class FornecedorUpsertDto(
    val nome: String,
    val cnpj: String?,
    val contatos: String?,
    @SerialName("entrega_propria") val entregaPropria: Boolean,
    @SerialName("ultima_atualizacao") val ultimaAtualizacao: String,
    @SerialName("atualizado_por") val atualizadoPor: String?
)

@Serializable
data class MaterialUpsertDto(
    val nome: String,
    val unidade: String,
    @SerialName("tempo_producao_padrao") val tempoProducaoPadrao: Int?,
    @SerialName("estoque_minimo") val estoqueMinimo: Double
)

@Serializable
data class MaterialFornecedorUpsertDto(
    @SerialName("material_id") val materialId: String,
    @SerialName("fornecedor_id") val fornecedorId: String,
    @SerialName("preco_atual") val precoAtual: Double,
    @SerialName("pedido_minimo") val pedidoMinimo: Double,
    @SerialName("lead_time_dias") val leadTimeDias: Int,
    @SerialName("validade_preco") val validadePreco: String?,
    @SerialName("ultima_atualizacao") val ultimaAtualizacao: String,
    @SerialName("atualizado_por") val atualizadoPor: String?
)

@Serializable
data class AccessSignupResponseDto(
    val ok: Boolean = false,
    val message: String? = null,
    val code: String? = null,
    @SerialName("emailSent") val emailSent: Boolean? = null
)

@Serializable
data class CompanySuggestionDto(
    val id: String,
    val name: String,
    val slug: String? = null
)

@Serializable
data class CompanySearchResponseDto(
    val ok: Boolean = false,
    val message: String? = null,
    val code: String? = null,
    val companies: List<CompanySuggestionDto> = emptyList()
)

@Serializable
data class AccessRequestPayloadDto(
    val id: String,
    @SerialName("requestType") val requestType: String,
    val status: String,
    @SerialName("applicantEmail") val applicantEmail: String,
    @SerialName("applicantFullName") val applicantFullName: String,
    @SerialName("companyName") val companyName: String,
    @SerialName("requestedUsername") val requestedUsername: String,
    @SerialName("requestedJobTitle") val requestedJobTitle: String,
    @SerialName("requestedRole") val requestedRole: String
)

@Serializable
data class AccessRequestGetResponseDto(
    val ok: Boolean = false,
    val message: String? = null,
    val code: String? = null,
    val request: AccessRequestPayloadDto? = null
)

@Serializable
data class AccessUserProfileDto(
    @SerialName("user_id") val userId: String,
    @SerialName("tenant_id") val tenantId: String,
    @SerialName("full_name") val fullName: String? = null,
    val email: String? = null,
    @SerialName("is_active") val isActive: Boolean,
    @SerialName("access_mode") val accessMode: String? = null,
    @SerialName("user_type_id") val userTypeId: String? = null
)

@Serializable
data class UserTypeDto(
    val id: String,
    val name: String,
    val description: String? = null,
    @SerialName("base_role") val baseRole: String,
    @SerialName("is_active") val isActive: Boolean
)

@Serializable
data class AuditLogDto(
    val id: String,
    @SerialName("entity_table") val entityTable: String,
    val action: String,
    @SerialName("changed_by") val changedBy: String? = null,
    @SerialName("target_user_id") val targetUserId: String? = null,
    @SerialName("obra_id") val obraId: String? = null,
    @SerialName("created_at") val createdAt: String
)

@Serializable
data class UserObraDto(
    @SerialName("user_id") val userId: String,
    @SerialName("obra_id") val obraId: String
)

@Serializable
data class UserRoleByUserDto(
    @SerialName("user_id") val userId: String,
    val role: String
)

@Serializable
data class PermissionCatalogDto(
    val key: String,
    val area: String,
    @SerialName("label_pt") val labelPt: String,
    @SerialName("obra_scoped") val obraScoped: Boolean,
    @SerialName("is_active") val isActive: Boolean
)
