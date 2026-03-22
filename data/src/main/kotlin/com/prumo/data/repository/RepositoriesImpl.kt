package com.prumo.data.repository

import com.prumo.core.model.AccessMode
import com.prumo.core.model.AccessRequestReviewData
import com.prumo.core.model.AccessReviewDecision
import com.prumo.core.model.AccessAuditEntry
import com.prumo.core.model.AccessUserRecord
import com.prumo.core.model.AppLanguage
import com.prumo.core.model.AppRole
import com.prumo.core.model.CompanySuggestion
import com.prumo.core.model.EstoqueItem
import com.prumo.core.model.EstoqueUpdateInput
import com.prumo.core.model.EffectivePermission
import com.prumo.core.model.FornecedorSummary
import com.prumo.core.model.MaterialFornecedorRecord
import com.prumo.core.model.MaterialRecord
import com.prumo.core.model.MaterialSummary
import com.prumo.core.model.ObraSummary
import com.prumo.core.model.PermissionCatalogItem
import com.prumo.core.model.PermissionScopeType
import com.prumo.core.model.PermissionSource
import com.prumo.core.model.PedidoInput
import com.prumo.core.model.PedidoResumo
import com.prumo.core.model.RecebimentoInput
import com.prumo.core.model.SessionToken
import com.prumo.core.model.SessionUser
import com.prumo.core.model.SignupMode
import com.prumo.core.model.SignupRequestInput
import com.prumo.core.model.SignupResult
import com.prumo.core.model.SupabaseConfig
import com.prumo.core.model.UserPermissionGrantDraft
import com.prumo.core.model.UserAccessUpdateInput
import com.prumo.core.model.UserTypeRecord
import com.prumo.core.model.UserTypeUpsertInput
import com.prumo.core.repository.CadastrosRepository
import com.prumo.core.repository.AuthRepository
import com.prumo.core.repository.EstoqueRepository
import com.prumo.core.repository.ObrasRepository
import com.prumo.core.repository.PedidosRepository
import com.prumo.core.repository.UsuariosRepository
import com.prumo.data.api.SupabaseAuthApi
import com.prumo.data.api.SupabaseRestClient
import com.prumo.data.model.AccessUserProfileDto
import com.prumo.data.model.AuditLogDto
import com.prumo.data.model.AuthResponseDto
import com.prumo.data.model.EstoqueDto
import com.prumo.data.model.EstoquePatchDto
import com.prumo.data.model.FornecedorDto
import com.prumo.data.model.FornecedorUpsertDto
import com.prumo.data.model.MaterialDto
import com.prumo.data.model.MaterialFornecedorDto
import com.prumo.data.model.MaterialFornecedorUpsertDto
import com.prumo.data.model.MaterialUpsertDto
import com.prumo.data.model.ObraDto
import com.prumo.data.model.ObraUpsertDto
import com.prumo.data.model.PedidoDto
import com.prumo.data.model.PedidoUpsertDto
import com.prumo.data.model.PermissionCatalogDto
import com.prumo.data.model.ProfileDto
import com.prumo.data.model.TenantSettingsDto
import com.prumo.data.model.UserObraDto
import com.prumo.data.model.UserPermissionGrantByUserDto
import com.prumo.data.model.UserPermissionGrantDto
import com.prumo.data.model.UserPermissionObraDto
import com.prumo.data.model.UserRoleByUserDto
import com.prumo.data.model.UserRoleDto
import com.prumo.data.model.UserTypeDto
import com.prumo.data.model.UserTypePermissionDto
import com.prumo.data.storage.SessionStore
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject

class SupabaseAuthRepository(
    private val restClient: SupabaseRestClient,
    private val authApi: SupabaseAuthApi,
    private val sessionStore: SessionStore
) : AuthRepository {
    override suspend fun login(email: String, password: String): SessionToken {
        return withContext(Dispatchers.IO) {
            val auth = authApi.login(email, password)
            val user = loadUserContext(auth.user.id, auth.user.email)
            val token = auth.toSessionToken(user)
            sessionStore.save(token)
            token
        }
    }

    override suspend fun refreshSessionContext(): SessionToken? {
        return withContext(Dispatchers.IO) {
            val current = sessionStore.read() ?: return@withContext null
            val refreshedUser = loadUserContext(current.user.userId, current.user.email)
            val refreshed = current.copy(user = refreshedUser)
            sessionStore.save(refreshed)
            refreshed
        }
    }

    override suspend fun searchCompanies(query: String): List<CompanySuggestion> {
        return withContext(Dispatchers.IO) {
            authApi.searchCompanies(query.trim()).map { company ->
                CompanySuggestion(
                    id = company.id,
                    name = company.name,
                    slug = company.slug,
                )
            }
        }
    }

    override suspend fun signup(input: SignupRequestInput): SignupResult {
        return withContext(Dispatchers.IO) {
            if (input.mode == SignupMode.COMPANY_INTERNAL && input.tenantId.isNullOrBlank()) {
                return@withContext SignupResult(
                    ok = false,
                    message = "Selecione uma empresa valida na lista antes de enviar.",
                    code = "tenant_required",
                )
            }

            val response = when (input.mode) {
                SignupMode.COMPANY_OWNER -> authApi.registerCompany(
                    email = input.email.trim().lowercase(),
                    password = input.password,
                    fullName = input.fullName.trim(),
                    username = input.username.trim(),
                    companyName = input.companyName.trim(),
                    jobTitle = input.jobTitle.trim(),
                    phone = input.phone?.trim()?.ifBlank { null },
                    origin = input.origin
                )

                SignupMode.COMPANY_INTERNAL -> authApi.registerInternal(
                    email = input.email.trim().lowercase(),
                    password = input.password,
                    fullName = input.fullName.trim(),
                    username = input.username.trim(),
                    companyName = input.companyName.trim(),
                    tenantId = input.tenantId?.trim().orEmpty(),
                    jobTitle = input.jobTitle.trim(),
                    phone = input.phone?.trim()?.ifBlank { null },
                    requestedRole = input.requestedRole.wireValue,
                    origin = input.origin
                )
            }

            SignupResult(
                ok = response.ok,
                message = response.message ?: if (response.ok) "Solicitacao enviada." else "Falha ao processar solicitacao.",
                emailSent = response.emailSent ?: false,
                code = response.code,
            )
        }
    }

    override suspend fun getAccessRequest(token: String): AccessRequestReviewData {
        return withContext(Dispatchers.IO) {
            val response = authApi.getAccessRequest(token.trim())
            if (!response.ok || response.request == null) {
                error(response.message ?: "Solicitacao nao encontrada.")
            }

            val request = response.request
            AccessRequestReviewData(
                id = request.id,
                requestType = request.requestType,
                status = request.status,
                applicantEmail = request.applicantEmail,
                applicantFullName = request.applicantFullName,
                companyName = request.companyName,
                requestedUsername = request.requestedUsername,
                requestedJobTitle = request.requestedJobTitle,
                requestedRole = AppRole.parse(request.requestedRole) ?: AppRole.OPERACIONAL
            )
        }
    }

    override suspend fun reviewAccessRequest(
        token: String,
        decision: AccessReviewDecision,
        reviewedUsername: String,
        reviewedJobTitle: String,
        reviewedRole: String,
        reviewNotes: String
    ): SignupResult {
        return withContext(Dispatchers.IO) {
            val response = authApi.reviewAccessRequest(
                token = token.trim(),
                decision = decision.wireValue,
                reviewedUsername = reviewedUsername.trim(),
                reviewedJobTitle = reviewedJobTitle.trim(),
                reviewedRole = reviewedRole.trim(),
                reviewNotes = reviewNotes.trim()
            )
            SignupResult(
                ok = response.ok,
                message = response.message ?: if (response.ok) "Solicitacao processada." else "Falha ao processar solicitacao.",
                emailSent = response.emailSent ?: false
            )
        }
    }

    override suspend fun currentSession(): SessionToken? = sessionStore.read()

    override suspend fun logout() {
        withContext(Dispatchers.IO) {
            sessionStore.clear()
        }
    }

    override suspend fun clearSession() {
        withContext(Dispatchers.IO) {
            sessionStore.clear()
        }
    }

    private suspend fun loadUserContext(userId: String, email: String): SessionUser {
        val profiles = restClient.getList(
            path = "profiles",
            query = mapOf(
                "select" to "full_name,email,is_active,user_type_id,tenant_id,preferred_language,access_mode",
                "user_id" to "eq.$userId",
                "limit" to "1"
            ),
            deserializer = ProfileDto.serializer()
        )

        val profile = profiles.firstOrNull() ?: error("Perfil nao encontrado para usuario autenticado.")
        if (profile.tenantId.isBlank()) error("Perfil sem tenant vinculado.")

        val roles = restClient.getList(
            path = "user_roles",
            query = mapOf(
                "select" to "role",
                "user_id" to "eq.$userId",
                "tenant_id" to "eq.${profile.tenantId}",
                "limit" to "1"
            ),
            deserializer = UserRoleDto.serializer()
        )

        val obras = restClient.getList(
            path = "obras",
            query = mapOf(
                "select" to "id,name,status,address,description,deleted_at",
                "tenant_id" to "eq.${profile.tenantId}",
                "deleted_at" to "is.null",
                "order" to "name.asc"
            ),
            deserializer = ObraDto.serializer()
        )

        val tenantSettings = runCatching {
            restClient.getList(
                path = "tenant_settings",
                query = mapOf(
                    "select" to "multi_obra_enabled,default_obra_id",
                    "tenant_id" to "eq.${profile.tenantId}",
                    "limit" to "1"
                ),
                deserializer = TenantSettingsDto.serializer()
            ).firstOrNull()
        }.getOrNull()

        val grants = restClient.getList(
            path = "user_permission_grants",
            query = mapOf(
                "select" to "id,permission_key,scope_type",
                "tenant_id" to "eq.${profile.tenantId}",
                "user_id" to "eq.$userId"
            ),
            deserializer = UserPermissionGrantDto.serializer()
        )

        val grantObras = if (grants.isNotEmpty()) {
            val inClause = "in.(${grants.joinToString(",") { it.id }})"
            restClient.getList(
                path = "user_permission_obras",
                query = mapOf(
                    "select" to "grant_id,obra_id",
                    "grant_id" to inClause
                ),
                deserializer = UserPermissionObraDto.serializer()
            )
        } else {
            emptyList()
        }

        val templatePermissions = if (profile.userTypeId != null) {
            restClient.getList(
                path = "user_type_permissions",
                query = mapOf(
                    "select" to "permission_key,scope_type",
                    "tenant_id" to "eq.${profile.tenantId}",
                    "user_type_id" to "eq.${profile.userTypeId}"
                ),
                deserializer = UserTypePermissionDto.serializer()
            )
        } else {
            emptyList()
        }

        val obraIdsByGrant = grantObras.groupBy({ it.grantId }, { it.obraId })
        val customPermissions = grants.map { grant ->
            EffectivePermission(
                permissionKey = grant.permissionKey,
                scopeType = PermissionScopeType.parse(grant.scopeType),
                obraIds = obraIdsByGrant[grant.id].orEmpty(),
                source = PermissionSource.CUSTOM
            )
        }

        val templateEffectivePermissions = templatePermissions.map { permission ->
            EffectivePermission(
                permissionKey = permission.permissionKey,
                scopeType = PermissionScopeType.parse(permission.scopeType),
                obraIds = emptyList(),
                source = PermissionSource.TEMPLATE
            )
        }

        val obraScope = obras.map { it.id }
        val multiObraEnabled = tenantSettings?.multiObraEnabled ?: (obraScope.size > 1)
        val defaultObra = tenantSettings?.defaultObraId ?: if (obraScope.size == 1) obraScope.first() else null

        return SessionUser(
            userId = userId,
            email = profile.email ?: email,
            fullName = profile.fullName,
            role = AppRole.parse(roles.firstOrNull()?.role),
            tenantId = profile.tenantId,
            isActive = profile.isActive,
            preferredLanguage = AppLanguage.parse(profile.preferredLanguage),
            accessMode = AccessMode.parse(profile.accessMode),
            obraScope = obraScope,
            permissions = customPermissions + templateEffectivePermissions,
            multiObraEnabled = multiObraEnabled,
            defaultObraId = defaultObra
        )
    }
}

class SupabaseObrasRepository(
    private val restClient: SupabaseRestClient,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : ObrasRepository {
    override suspend fun listObras(includeDeleted: Boolean, deletedSinceIso: String?): List<ObraSummary> {
        return withContext(Dispatchers.IO) {
            val query = mutableMapOf(
                "select" to "id,name,status,address,description,deleted_at",
                "order" to "name.asc"
            )

            if (includeDeleted) {
                query["deleted_at"] = "not.is.null"
                if (!deletedSinceIso.isNullOrBlank()) {
                    query["deleted_at"] = "gte.$deletedSinceIso"
                }
            } else {
                query["deleted_at"] = "is.null"
            }

            restClient.getList(
                path = "obras",
                query = query,
                deserializer = ObraDto.serializer()
            ).map {
                ObraSummary(
                    id = it.id,
                    name = it.name,
                    status = it.status,
                    address = it.address,
                    description = it.description,
                    deletedAt = it.deletedAt
                )
            }
        }
    }

    override suspend fun saveObra(obra: ObraSummary) {
        withContext(Dispatchers.IO) {
            val payload = ObraUpsertDto(
                name = obra.name,
                status = obra.status,
                address = obra.address,
                description = obra.description
            )
            val body = json.encodeToString(ObraUpsertDto.serializer(), payload)
            if (obra.id.isBlank()) {
                restClient.post(path = "obras", bodyJson = body)
            } else {
                restClient.patch(
                    path = "obras",
                    query = mapOf("id" to "eq.${obra.id}"),
                    bodyJson = body
                )
            }
        }
    }

    override suspend fun softDeleteObra(obraId: String) {
        withContext(Dispatchers.IO) {
            restClient.patch(
                path = "obras",
                query = mapOf("id" to "eq.$obraId"),
                bodyJson = """{"deleted_at":"${nowIso()}"}"""
            )
        }
    }

    override suspend fun restoreObra(obraId: String) {
        withContext(Dispatchers.IO) {
            restClient.patch(
                path = "obras",
                query = mapOf("id" to "eq.$obraId"),
                bodyJson = """{"deleted_at":null}"""
            )
        }
    }

    override suspend fun hardDeleteObra(obraId: String) {
        withContext(Dispatchers.IO) {
            restClient.delete(path = "obras", query = mapOf("id" to "eq.$obraId"))
        }
    }
}

class SupabasePedidosRepository(
    private val restClient: SupabaseRestClient,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : PedidosRepository {
    override suspend fun listPedidos(obraId: String?, status: String?, search: String?): List<PedidoResumo> {
        return withContext(Dispatchers.IO) {
            val query = mutableMapOf(
                "select" to "id,obra_id,material_id,fornecedor_id,quantidade,preco_unit,total,status,codigo_compra,criado_em,data_recebimento,deleted_at,obras(name),materiais(nome,unidade),fornecedores(nome)",
                "deleted_at" to "is.null",
                "order" to "criado_em.desc"
            )

            if (!obraId.isNullOrBlank()) query["obra_id"] = "eq.$obraId"
            if (!status.isNullOrBlank()) query["status"] = "eq.$status"

            val rows = restClient.getList(
                path = "pedidos_compra",
                query = query,
                deserializer = PedidoDto.serializer()
            )

            val mapped = rows.map {
                PedidoResumo(
                    id = it.id,
                    obraId = it.obraId,
                    obraNome = it.obras?.name,
                    materialId = it.materialId,
                    materialNome = it.materiais?.nome,
                    materialUnidade = it.materiais?.unidade,
                    fornecedorId = it.fornecedorId,
                    fornecedorNome = it.fornecedores?.nome,
                    quantidade = it.quantidade,
                    precoUnit = it.precoUnit,
                    total = it.total,
                    status = it.status,
                    codigoCompra = it.codigoCompra,
                    criadoEm = it.criadoEm,
                    dataRecebimento = it.dataRecebimento,
                    deletedAt = it.deletedAt
                )
            }

            val normalized = search?.trim()?.lowercase().orEmpty()
            if (normalized.isBlank()) mapped
            else mapped.filter {
                it.id.lowercase().contains(normalized) ||
                    (it.codigoCompra?.lowercase()?.contains(normalized) == true) ||
                    (it.obraNome?.lowercase()?.contains(normalized) == true) ||
                    (it.materialNome?.lowercase()?.contains(normalized) == true) ||
                    (it.fornecedorNome?.lowercase()?.contains(normalized) == true) ||
                    it.status.lowercase().contains(normalized)
            }
        }
    }

    override suspend fun listMateriais(): List<MaterialSummary> {
        return withContext(Dispatchers.IO) {
            restClient.getList(
                path = "materiais",
                query = mapOf(
                    "select" to "id,nome,unidade,tempo_producao_padrao,estoque_minimo,deleted_at",
                    "deleted_at" to "is.null",
                    "order" to "nome.asc"
                ),
                deserializer = MaterialDto.serializer()
            ).map {
                MaterialSummary(
                    id = it.id,
                    nome = it.nome,
                    unidade = it.unidade,
                    tempoProducaoPadrao = it.tempoProducaoPadrao,
                    estoqueMinimo = it.estoqueMinimo,
                    deletedAt = it.deletedAt
                )
            }
        }
    }

    override suspend fun listFornecedores(): List<FornecedorSummary> {
        return withContext(Dispatchers.IO) {
            restClient.getList(
                path = "fornecedores",
                query = mapOf(
                    "select" to "id,nome,cnpj,contatos,entrega_propria,deleted_at",
                    "deleted_at" to "is.null",
                    "order" to "nome.asc"
                ),
                deserializer = FornecedorDto.serializer()
            ).map {
                FornecedorSummary(
                    id = it.id,
                    nome = it.nome,
                    cnpj = it.cnpj,
                    contatos = it.contatos,
                    entregaPropria = it.entregaPropria,
                    deletedAt = it.deletedAt
                )
            }
        }
    }

    override suspend fun listObras(): List<ObraSummary> {
        return withContext(Dispatchers.IO) {
            restClient.getList(
                path = "obras",
                query = mapOf(
                    "select" to "id,name,status,address,description,deleted_at",
                    "deleted_at" to "is.null",
                    "order" to "name.asc"
                ),
                deserializer = ObraDto.serializer()
            ).map {
                ObraSummary(
                    id = it.id,
                    name = it.name,
                    status = it.status,
                    address = it.address,
                    description = it.description,
                    deletedAt = it.deletedAt
                )
            }
        }
    }

    override suspend fun createPedido(input: PedidoInput) {
        withContext(Dispatchers.IO) {
            val payload = PedidoUpsertDto(
                obraId = input.obraId,
                materialId = input.materialId,
                fornecedorId = input.fornecedorId,
                quantidade = input.quantidade,
                precoUnit = input.precoUnit,
                total = input.quantidade * input.precoUnit,
                status = input.status,
                codigoCompra = input.codigoCompra,
                observacoes = input.observacoes
            )
            restClient.post(path = "pedidos_compra", bodyJson = json.encodeToString(PedidoUpsertDto.serializer(), payload))
        }
    }

    override suspend fun updatePedido(id: String, input: PedidoInput) {
        withContext(Dispatchers.IO) {
            val payload = PedidoUpsertDto(
                obraId = input.obraId,
                materialId = input.materialId,
                fornecedorId = input.fornecedorId,
                quantidade = input.quantidade,
                precoUnit = input.precoUnit,
                total = input.quantidade * input.precoUnit,
                status = input.status,
                codigoCompra = input.codigoCompra,
                observacoes = input.observacoes
            )
            restClient.patch(
                path = "pedidos_compra",
                query = mapOf("id" to "eq.$id"),
                bodyJson = json.encodeToString(PedidoUpsertDto.serializer(), payload)
            )
        }
    }

    override suspend fun updatePedidoStatus(
        id: String,
        status: String,
        codigoCompra: String?,
        dataRecebimentoIso: String?,
        recebidoPor: String?
    ) {
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("status", status)
                .put("codigo_compra", codigoCompra)
                .apply {
                    if (!dataRecebimentoIso.isNullOrBlank()) put("data_recebimento", dataRecebimentoIso)
                    if (!recebidoPor.isNullOrBlank()) put("recebido_por", recebidoPor)
                }
                .toString()

            restClient.patch(
                path = "pedidos_compra",
                query = mapOf("id" to "eq.$id"),
                bodyJson = body
            )
        }
    }

    override suspend fun softDeletePedido(id: String) {
        withContext(Dispatchers.IO) {
            restClient.patch(
                path = "pedidos_compra",
                query = mapOf("id" to "eq.$id"),
                bodyJson = """{"deleted_at":"${nowIso()}"}"""
            )
        }
    }
}

class SupabaseEstoqueRepository(
    private val restClient: SupabaseRestClient,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : EstoqueRepository {
    override suspend fun listEstoque(obraId: String?): List<EstoqueItem> {
        return withContext(Dispatchers.IO) {
            val query = mutableMapOf(
                "select" to "id,obra_id,material_id,estoque_atual,atualizado_em,obras(name),materiais(nome,unidade)",
                "order" to "atualizado_em.desc"
            )
            if (!obraId.isNullOrBlank()) query["obra_id"] = "eq.$obraId"

            restClient.getList(
                path = "estoque_obra_material",
                query = query,
                deserializer = EstoqueDto.serializer()
            ).map {
                EstoqueItem(
                    id = it.id,
                    obraId = it.obraId,
                    obraNome = it.obras?.name,
                    materialId = it.materialId,
                    materialNome = it.materiais?.nome,
                    unidade = it.materiais?.unidade,
                    estoqueAtual = it.estoqueAtual,
                    atualizadoEm = it.atualizadoEm
                )
            }
        }
    }

    override suspend fun updateEstoque(itemId: String, input: EstoqueUpdateInput) {
        withContext(Dispatchers.IO) {
            val payload = EstoquePatchDto(input.estoqueAtual)
            restClient.patch(
                path = "estoque_obra_material",
                query = mapOf("id" to "eq.$itemId"),
                bodyJson = json.encodeToString(EstoquePatchDto.serializer(), payload)
            )
        }
    }

    override suspend fun upsertFromRecebimento(input: RecebimentoInput) {
        withContext(Dispatchers.IO) {
            val existing = restClient.getList(
                path = "estoque_obra_material",
                query = mapOf(
                    "select" to "id,obra_id,material_id,estoque_atual,atualizado_em,materiais(nome,unidade)",
                    "obra_id" to "eq.${input.obraId}",
                    "material_id" to "eq.${input.materialId}",
                    "limit" to "1"
                ),
                deserializer = EstoqueDto.serializer()
            ).firstOrNull()

            if (existing != null) {
                val payload = JSONObject()
                    .put("estoque_atual", existing.estoqueAtual + input.quantidade)
                    .put("atualizado_em", nowIso())
                    .put("atualizado_por", input.recebidoPor)
                restClient.patch(
                    path = "estoque_obra_material",
                    query = mapOf("id" to "eq.${existing.id}"),
                    bodyJson = payload.toString()
                )
            } else {
                val payload = JSONObject()
                    .put("obra_id", input.obraId)
                    .put("material_id", input.materialId)
                    .put("estoque_atual", input.quantidade)
                    .put("atualizado_por", input.recebidoPor)
                restClient.post(path = "estoque_obra_material", bodyJson = payload.toString())
            }
        }
    }
}

class SupabaseCadastrosRepository(
    private val restClient: SupabaseRestClient,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : CadastrosRepository {
    override suspend fun listFornecedores(includeDeleted: Boolean, deletedSinceIso: String?): List<FornecedorSummary> {
        return withContext(Dispatchers.IO) {
            val query = mutableMapOf(
                "select" to "id,nome,cnpj,contatos,entrega_propria,deleted_at",
                "order" to "nome.asc"
            )
            if (includeDeleted) {
                query["deleted_at"] = if (deletedSinceIso.isNullOrBlank()) "not.is.null" else "gte.$deletedSinceIso"
            } else {
                query["deleted_at"] = "is.null"
            }

            restClient.getList(
                path = "fornecedores",
                query = query,
                deserializer = FornecedorDto.serializer()
            ).map {
                FornecedorSummary(
                    id = it.id,
                    nome = it.nome,
                    cnpj = it.cnpj,
                    contatos = it.contatos,
                    entregaPropria = it.entregaPropria,
                    deletedAt = it.deletedAt
                )
            }
        }
    }

    override suspend fun saveFornecedor(fornecedor: FornecedorSummary) {
        withContext(Dispatchers.IO) {
            val payload = FornecedorUpsertDto(
                nome = fornecedor.nome,
                cnpj = fornecedor.cnpj,
                contatos = fornecedor.contatos,
                entregaPropria = fornecedor.entregaPropria,
                ultimaAtualizacao = nowIso(),
                atualizadoPor = null
            )
            val body = json.encodeToString(FornecedorUpsertDto.serializer(), payload)
            if (fornecedor.id.isBlank()) {
                restClient.post(path = "fornecedores", bodyJson = body)
            } else {
                restClient.patch(
                    path = "fornecedores",
                    query = mapOf("id" to "eq.${fornecedor.id}"),
                    bodyJson = body
                )
            }
        }
    }

    override suspend fun softDeleteFornecedor(id: String) {
        withContext(Dispatchers.IO) {
            restClient.patch(
                path = "fornecedores",
                query = mapOf("id" to "eq.$id"),
                bodyJson = """{"deleted_at":"${nowIso()}"}"""
            )
        }
    }

    override suspend fun restoreFornecedor(id: String) {
        withContext(Dispatchers.IO) {
            restClient.patch(
                path = "fornecedores",
                query = mapOf("id" to "eq.$id"),
                bodyJson = """{"deleted_at":null}"""
            )
        }
    }

    override suspend fun hardDeleteFornecedor(id: String) {
        withContext(Dispatchers.IO) {
            restClient.delete(path = "fornecedores", query = mapOf("id" to "eq.$id"))
        }
    }

    override suspend fun listMateriais(includeDeleted: Boolean, deletedSinceIso: String?): List<MaterialRecord> {
        return withContext(Dispatchers.IO) {
            val query = mutableMapOf(
                "select" to "id,nome,unidade,tempo_producao_padrao,estoque_minimo,deleted_at",
                "order" to "nome.asc"
            )
            if (includeDeleted) {
                query["deleted_at"] = if (deletedSinceIso.isNullOrBlank()) "not.is.null" else "gte.$deletedSinceIso"
            } else {
                query["deleted_at"] = "is.null"
            }

            restClient.getList(
                path = "materiais",
                query = query,
                deserializer = MaterialDto.serializer()
            ).map {
                MaterialRecord(
                    id = it.id,
                    nome = it.nome,
                    unidade = it.unidade,
                    tempoProducaoPadrao = it.tempoProducaoPadrao,
                    estoqueMinimo = it.estoqueMinimo,
                    deletedAt = it.deletedAt
                )
            }
        }
    }

    override suspend fun saveMaterial(material: MaterialRecord) {
        withContext(Dispatchers.IO) {
            val payload = MaterialUpsertDto(
                nome = material.nome,
                unidade = material.unidade,
                tempoProducaoPadrao = material.tempoProducaoPadrao,
                estoqueMinimo = material.estoqueMinimo
            )
            val body = json.encodeToString(MaterialUpsertDto.serializer(), payload)
            if (material.id.isBlank()) {
                restClient.post(path = "materiais", bodyJson = body)
            } else {
                restClient.patch(
                    path = "materiais",
                    query = mapOf("id" to "eq.${material.id}"),
                    bodyJson = body
                )
            }
        }
    }

    override suspend fun softDeleteMaterial(id: String) {
        withContext(Dispatchers.IO) {
            restClient.patch(
                path = "materiais",
                query = mapOf("id" to "eq.$id"),
                bodyJson = """{"deleted_at":"${nowIso()}"}"""
            )
        }
    }

    override suspend fun restoreMaterial(id: String) {
        withContext(Dispatchers.IO) {
            restClient.patch(
                path = "materiais",
                query = mapOf("id" to "eq.$id"),
                bodyJson = """{"deleted_at":null}"""
            )
        }
    }

    override suspend fun hardDeleteMaterial(id: String) {
        withContext(Dispatchers.IO) {
            restClient.delete(path = "materiais", query = mapOf("id" to "eq.$id"))
        }
    }

    override suspend fun listMaterialFornecedor(includeDeleted: Boolean, deletedSinceIso: String?): List<MaterialFornecedorRecord> {
        return withContext(Dispatchers.IO) {
            val query = mutableMapOf(
                "select" to "id,material_id,fornecedor_id,preco_atual,pedido_minimo,lead_time_dias,validade_preco,deleted_at,materiais(nome,unidade),fornecedores(nome,cnpj)",
                "order" to "ultima_atualizacao.desc"
            )
            if (includeDeleted) {
                query["deleted_at"] = if (deletedSinceIso.isNullOrBlank()) "not.is.null" else "gte.$deletedSinceIso"
            } else {
                query["deleted_at"] = "is.null"
            }

            restClient.getList(
                path = "material_fornecedor",
                query = query,
                deserializer = MaterialFornecedorDto.serializer()
            ).map {
                MaterialFornecedorRecord(
                    id = it.id,
                    materialId = it.materialId,
                    fornecedorId = it.fornecedorId,
                    precoAtual = it.precoAtual,
                    pedidoMinimo = it.pedidoMinimo,
                    leadTimeDias = it.leadTimeDias,
                    validadePreco = it.validadePreco,
                    materialNome = it.materiais?.nome,
                    materialUnidade = it.materiais?.unidade,
                    fornecedorNome = it.fornecedores?.nome,
                    fornecedorCnpj = it.fornecedores?.cnpj,
                    deletedAt = it.deletedAt
                )
            }
        }
    }

    override suspend fun saveMaterialFornecedor(item: MaterialFornecedorRecord) {
        withContext(Dispatchers.IO) {
            val payload = MaterialFornecedorUpsertDto(
                materialId = item.materialId,
                fornecedorId = item.fornecedorId,
                precoAtual = item.precoAtual,
                pedidoMinimo = item.pedidoMinimo,
                leadTimeDias = item.leadTimeDias,
                validadePreco = item.validadePreco,
                ultimaAtualizacao = nowIso(),
                atualizadoPor = null
            )
            val body = json.encodeToString(MaterialFornecedorUpsertDto.serializer(), payload)
            if (item.id.isBlank()) {
                restClient.post(path = "material_fornecedor", bodyJson = body)
            } else {
                restClient.patch(
                    path = "material_fornecedor",
                    query = mapOf("id" to "eq.${item.id}"),
                    bodyJson = body
                )
            }
        }
    }

    override suspend fun softDeleteMaterialFornecedor(id: String) {
        withContext(Dispatchers.IO) {
            restClient.patch(
                path = "material_fornecedor",
                query = mapOf("id" to "eq.$id"),
                bodyJson = """{"deleted_at":"${nowIso()}"}"""
            )
        }
    }

    override suspend fun restoreMaterialFornecedor(id: String) {
        withContext(Dispatchers.IO) {
            restClient.patch(
                path = "material_fornecedor",
                query = mapOf("id" to "eq.$id"),
                bodyJson = """{"deleted_at":null}"""
            )
        }
    }

    override suspend fun hardDeleteMaterialFornecedor(id: String) {
        withContext(Dispatchers.IO) {
            restClient.delete(path = "material_fornecedor", query = mapOf("id" to "eq.$id"))
        }
    }
}

class SupabaseUsuariosRepository(
    private val restClient: SupabaseRestClient
) : UsuariosRepository {
    override suspend fun listAccessUsers(): List<AccessUserRecord> {
        return withContext(Dispatchers.IO) {
            val profiles = restClient.getList(
                path = "profiles",
                query = mapOf(
                    "select" to "user_id,tenant_id,full_name,email,is_active,access_mode,user_type_id",
                    "order" to "full_name.asc"
                ),
                deserializer = AccessUserProfileDto.serializer()
            )

            val roles = restClient.getList(
                path = "user_roles",
                query = mapOf("select" to "user_id,role"),
                deserializer = UserRoleByUserDto.serializer()
            )

            val assignments = restClient.getList(
                path = "user_obras",
                query = mapOf("select" to "user_id,obra_id"),
                deserializer = UserObraDto.serializer()
            )

            val grants = restClient.getList(
                path = "user_permission_grants",
                query = mapOf("select" to "id,user_id,permission_key,scope_type"),
                deserializer = UserPermissionGrantByUserDto.serializer()
            )

            val grantObras = if (grants.isNotEmpty()) {
                val inClause = "in.(${grants.joinToString(",") { it.id }})"
                restClient.getList(
                    path = "user_permission_obras",
                    query = mapOf("select" to "grant_id,obra_id", "grant_id" to inClause),
                    deserializer = UserPermissionObraDto.serializer()
                )
            } else {
                emptyList()
            }

            val roleByUser = roles.associateBy({ it.userId }, { AppRole.parse(it.role) })
            val obraByUser = assignments.groupBy({ it.userId }, { it.obraId })
            val obraIdsByGrant = grantObras.groupBy({ it.grantId }, { it.obraId })
            val grantsByUser = grants.groupBy({ it.userId }, { grant ->
                UserPermissionGrantDraft(
                    permissionKey = grant.permissionKey,
                    scopeType = PermissionScopeType.parse(grant.scopeType),
                    obraIds = obraIdsByGrant[grant.id].orEmpty()
                )
            })

            profiles.map {
                AccessUserRecord(
                    userId = it.userId,
                    tenantId = it.tenantId,
                    fullName = it.fullName ?: (it.email ?: it.userId),
                    email = it.email,
                    isActive = it.isActive,
                    role = roleByUser[it.userId],
                    accessMode = AccessMode.parse(it.accessMode),
                    userTypeId = it.userTypeId,
                    obraIds = obraByUser[it.userId].orEmpty(),
                    grants = grantsByUser[it.userId].orEmpty()
                )
            }
        }
    }

    override suspend fun listUserTypes(): List<UserTypeRecord> {
        return withContext(Dispatchers.IO) {
            restClient.getList(
                path = "user_types",
                query = mapOf(
                    "select" to "id,name,description,base_role,is_active",
                    "order" to "name.asc"
                ),
                deserializer = UserTypeDto.serializer()
            ).map {
                UserTypeRecord(
                    id = it.id,
                    name = it.name,
                    description = it.description,
                    baseRole = AppRole.parse(it.baseRole) ?: AppRole.OPERACIONAL,
                    isActive = it.isActive
                )
            }
        }
    }

    override suspend fun listPermissionCatalog(): List<PermissionCatalogItem> {
        return withContext(Dispatchers.IO) {
            restClient.getList(
                path = "permission_catalog",
                query = mapOf(
                    "select" to "key,area,label_pt,obra_scoped,is_active",
                    "is_active" to "eq.true",
                    "order" to "area.asc,key.asc"
                ),
                deserializer = PermissionCatalogDto.serializer()
            ).map {
                PermissionCatalogItem(
                    key = it.key,
                    area = it.area,
                    labelPt = it.labelPt,
                    obraScoped = it.obraScoped,
                    isActive = it.isActive
                )
            }
        }
    }

    override suspend fun listAccessAuditLog(limit: Int): List<AccessAuditEntry> {
        return withContext(Dispatchers.IO) {
            restClient.getList(
                path = "audit_log",
                query = mapOf(
                    "select" to "id,entity_table,action,changed_by,target_user_id,obra_id,created_at",
                    "entity_table" to "in.(user_roles,user_obras,profiles,user_types)",
                    "order" to "created_at.desc",
                    "limit" to limit.coerceAtLeast(1).coerceAtMost(200).toString()
                ),
                deserializer = AuditLogDto.serializer()
            ).map {
                AccessAuditEntry(
                    id = it.id,
                    entityTable = it.entityTable,
                    action = it.action,
                    changedBy = it.changedBy,
                    targetUserId = it.targetUserId,
                    obraId = it.obraId,
                    createdAt = it.createdAt
                )
            }
        }
    }

    override suspend fun saveUserType(input: UserTypeUpsertInput) {
        withContext(Dispatchers.IO) {
            val description = input.description?.trim()?.ifBlank { null }
            val payload = JSONObject()
                .put("name", input.name.trim())
                .put("base_role", input.baseRole.wireValue)
                .put("is_active", input.isActive)
                .put("created_by", input.createdByUserId)
                .put("description", description ?: JSONObject.NULL)
                .toString()

            if (!input.id.isNullOrBlank()) {
                restClient.patch(
                    path = "user_types",
                    query = mapOf("id" to "eq.${input.id}"),
                    bodyJson = payload
                )
            } else {
                restClient.post(path = "user_types", bodyJson = payload)
            }
        }
    }

    override suspend fun saveUserAccess(input: UserAccessUpdateInput) {
        withContext(Dispatchers.IO) {
            val profilePatch = JSONObject()
                .put("is_active", input.isActive)
                .put("user_type_id", input.userTypeId)
                .put("access_mode", input.accessMode.wireValue)
                .toString()

            restClient.patch(
                path = "profiles",
                query = mapOf("user_id" to "eq.${input.userId}"),
                bodyJson = profilePatch
            )

            val existingRole = restClient.getList(
                path = "user_roles",
                query = mapOf("select" to "user_id,role", "user_id" to "eq.${input.userId}", "limit" to "1"),
                deserializer = UserRoleByUserDto.serializer()
            ).firstOrNull()

            val role = input.role
            if (role != null) {
                if (existingRole != null) {
                    restClient.patch(
                        path = "user_roles",
                        query = mapOf("user_id" to "eq.${input.userId}"),
                        bodyJson = """{"role":"${role.wireValue}"}"""
                    )
                } else {
                    val roleBody = JSONObject()
                        .put("user_id", input.userId)
                        .put("role", role.wireValue)
                        .toString()
                    restClient.post(path = "user_roles?on_conflict=user_id", bodyJson = roleBody)
                }
            } else if (existingRole != null) {
                restClient.delete(path = "user_roles", query = mapOf("user_id" to "eq.${input.userId}"))
            }

            val currentAssignments = restClient.getList(
                path = "user_obras",
                query = mapOf("select" to "user_id,obra_id", "user_id" to "eq.${input.userId}"),
                deserializer = UserObraDto.serializer()
            ).map { it.obraId }

            val toAdd = input.obraIds.filterNot { currentAssignments.contains(it) }
            val toRemove = currentAssignments.filterNot { input.obraIds.contains(it) }

            toRemove.forEach { obraId ->
                restClient.delete(
                    path = "user_obras",
                    query = mapOf("user_id" to "eq.${input.userId}", "obra_id" to "eq.$obraId")
                )
            }

            toAdd.forEach { obraId ->
                val row = JSONObject()
                    .put("user_id", input.userId)
                    .put("obra_id", obraId)
                    .toString()
                restClient.post(path = "user_obras", bodyJson = row)
            }

            val existingGrants = restClient.getList(
                path = "user_permission_grants",
                query = mapOf(
                    "select" to "id,user_id,permission_key,scope_type",
                    "user_id" to "eq.${input.userId}"
                ),
                deserializer = UserPermissionGrantByUserDto.serializer()
            )
            val existingGrantIds = existingGrants.map { it.id }

            if (existingGrantIds.isNotEmpty()) {
                val inClause = "in.(${existingGrantIds.joinToString(",")})"
                restClient.delete(
                    path = "user_permission_obras",
                    query = mapOf("grant_id" to inClause)
                )
            }

            restClient.delete(
                path = "user_permission_grants",
                query = mapOf("user_id" to "eq.${input.userId}")
            )

            if (input.accessMode == AccessMode.CUSTOM && input.grants.isNotEmpty()) {
                input.grants.forEach { grant ->
                    val grantBody = JSONObject()
                        .put("tenant_id", input.tenantId)
                        .put("user_id", input.userId)
                        .put("permission_key", grant.permissionKey)
                        .put("scope_type", grant.scopeType.wireValue)
                        .put("granted_by", input.changedByUserId)
                        .toString()

                    val inserted = restClient.postReturning(
                        path = "user_permission_grants",
                        bodyJson = grantBody
                    )
                    val grantId = JSONArray(inserted).optJSONObject(0)?.optString("id").orEmpty()
                    if (grantId.isBlank()) error("Falha ao criar grant para ${grant.permissionKey}")

                    if (grant.scopeType == PermissionScopeType.SELECTED_OBRAS && grant.obraIds.isNotEmpty()) {
                        grant.obraIds.forEach { obraId ->
                            val row = JSONObject()
                                .put("grant_id", grantId)
                                .put("obra_id", obraId)
                                .toString()
                            restClient.post(path = "user_permission_obras", bodyJson = row)
                        }
                    }
                }
            }
        }
    }
}

class AppContainer(
    config: SupabaseConfig,
    sessionStore: SessionStore
) {
    private val authApi = SupabaseAuthApi(config)
    private val restClient = SupabaseRestClient(config, sessionStore, authApi)

    val authRepository: AuthRepository = SupabaseAuthRepository(restClient, authApi, sessionStore)
    val obrasRepository: ObrasRepository = SupabaseObrasRepository(restClient)
    val pedidosRepository: PedidosRepository = SupabasePedidosRepository(restClient)
    val estoqueRepository: EstoqueRepository = SupabaseEstoqueRepository(restClient)
    val cadastrosRepository: CadastrosRepository = SupabaseCadastrosRepository(restClient)
    val usuariosRepository: UsuariosRepository = SupabaseUsuariosRepository(restClient)
}

private fun AuthResponseDto.toSessionToken(user: SessionUser): SessionToken {
    val now = System.currentTimeMillis() / 1000L
    return SessionToken(
        accessToken = accessToken,
        refreshToken = refreshToken,
        expiresAtEpochSeconds = now + expiresIn,
        user = user
    )
}

private fun nowIso(): String = Instant.now().toString()
