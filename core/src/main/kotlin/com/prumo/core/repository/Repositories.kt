package com.prumo.core.repository

import com.prumo.core.model.EstoqueItem
import com.prumo.core.model.EstoqueUpdateInput
import com.prumo.core.model.FornecedorSummary
import com.prumo.core.model.MaterialFornecedorRecord
import com.prumo.core.model.MaterialRecord
import com.prumo.core.model.MaterialSummary
import com.prumo.core.model.ObraSummary
import com.prumo.core.model.PermissionCatalogItem
import com.prumo.core.model.AccessRequestReviewData
import com.prumo.core.model.AccessReviewDecision
import com.prumo.core.model.AccessAuditEntry
import com.prumo.core.model.AccessUserRecord
import com.prumo.core.model.CompanySuggestion
import com.prumo.core.model.PedidoInput
import com.prumo.core.model.PedidoResumo
import com.prumo.core.model.RecebimentoInput
import com.prumo.core.model.SessionToken
import com.prumo.core.model.SignupRequestInput
import com.prumo.core.model.SignupResult
import com.prumo.core.model.UserAccessUpdateInput
import com.prumo.core.model.UserTypeRecord
import com.prumo.core.model.UserTypeUpsertInput

interface SessionProvider {
    suspend fun currentSession(): SessionToken?
    suspend fun clearSession()
}

interface AuthRepository : SessionProvider {
    suspend fun login(email: String, password: String, rememberEnabled: Boolean = true): SessionToken
    suspend fun refreshSessionContext(): SessionToken?
    suspend fun searchCompanies(query: String): List<CompanySuggestion>
    suspend fun signup(input: SignupRequestInput): SignupResult
    suspend fun getAccessRequest(token: String): AccessRequestReviewData
    suspend fun reviewAccessRequest(
        token: String,
        decision: AccessReviewDecision,
        reviewedUsername: String,
        reviewedJobTitle: String,
        reviewedRole: String,
        reviewNotes: String
    ): SignupResult
    suspend fun logout()
}

interface ObrasRepository {
    suspend fun listObras(includeDeleted: Boolean = false, deletedSinceIso: String? = null): List<ObraSummary>
    suspend fun saveObra(obra: ObraSummary)
    suspend fun softDeleteObra(obraId: String)
    suspend fun restoreObra(obraId: String)
    suspend fun hardDeleteObra(obraId: String)
}

interface PedidosRepository {
    suspend fun listPedidos(obraId: String?, status: String?, search: String?): List<PedidoResumo>
    suspend fun listMateriais(): List<MaterialSummary>
    suspend fun listFornecedores(): List<FornecedorSummary>
    suspend fun listObras(): List<ObraSummary>
    suspend fun createPedido(input: PedidoInput)
    suspend fun updatePedido(id: String, input: PedidoInput)
    suspend fun updatePedidoStatus(
        id: String,
        status: String,
        codigoCompra: String?,
        dataRecebimentoIso: String? = null,
        recebidoPor: String? = null
    )
    suspend fun softDeletePedido(id: String)
}

interface EstoqueRepository {
    suspend fun listEstoque(obraId: String?): List<EstoqueItem>
    suspend fun updateEstoque(itemId: String, input: EstoqueUpdateInput)
    suspend fun upsertFromRecebimento(input: RecebimentoInput)
}

interface CadastrosRepository {
    suspend fun listFornecedores(includeDeleted: Boolean, deletedSinceIso: String?): List<FornecedorSummary>
    suspend fun saveFornecedor(fornecedor: FornecedorSummary)
    suspend fun softDeleteFornecedor(id: String)
    suspend fun restoreFornecedor(id: String)
    suspend fun hardDeleteFornecedor(id: String)

    suspend fun listMateriais(includeDeleted: Boolean, deletedSinceIso: String?): List<MaterialRecord>
    suspend fun saveMaterial(material: MaterialRecord)
    suspend fun softDeleteMaterial(id: String)
    suspend fun restoreMaterial(id: String)
    suspend fun hardDeleteMaterial(id: String)

    suspend fun listMaterialFornecedor(includeDeleted: Boolean, deletedSinceIso: String?): List<MaterialFornecedorRecord>
    suspend fun saveMaterialFornecedor(item: MaterialFornecedorRecord)
    suspend fun softDeleteMaterialFornecedor(id: String)
    suspend fun restoreMaterialFornecedor(id: String)
    suspend fun hardDeleteMaterialFornecedor(id: String)
}

interface UsuariosRepository {
    suspend fun listAccessUsers(): List<AccessUserRecord>
    suspend fun listUserTypes(): List<UserTypeRecord>
    suspend fun listPermissionCatalog(): List<PermissionCatalogItem>
    suspend fun listAccessAuditLog(limit: Int = 50): List<AccessAuditEntry>
    suspend fun saveUserType(input: UserTypeUpsertInput)
    suspend fun saveUserAccess(input: UserAccessUpdateInput)
}
