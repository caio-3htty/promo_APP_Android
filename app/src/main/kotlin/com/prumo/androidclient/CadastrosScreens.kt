package com.prumo.androidclient

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.prumo.core.i18n.t
import com.prumo.core.model.FornecedorSummary
import com.prumo.core.model.MaterialFornecedorRecord
import com.prumo.core.model.MaterialRecord
import com.prumo.core.repository.CadastrosRepository
import com.prumo.core.ui.AppPage
import com.prumo.core.ui.SectionCard
import com.prumo.core.ui.StateMessage
import java.time.Instant
import kotlinx.coroutines.launch

@Composable
fun FornecedoresManagerScreen(
    repository: CadastrosRepository,
    canManage: Boolean,
    onBack: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var showTrash by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var items by remember { mutableStateOf<List<FornecedorSummary>>(emptyList()) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var nome by remember { mutableStateOf("") }
    var cnpj by remember { mutableStateOf("") }
    var contatos by remember { mutableStateOf("") }

    fun load() {
        scope.launch {
            loading = true
            runCatching {
                repository.listFornecedores(
                    includeDeleted = showTrash,
                    deletedSinceIso = if (showTrash) Instant.now().minusSeconds(60L * 60L * 24L * 30L).toString() else null
                )
            }.onSuccess {
                items = it
                loading = false
                error = null
            }.onFailure {
                loading = false
                error = it.message
            }
        }
    }

    LaunchedEffect(showTrash) { load() }

    AppPage(
        title = if (showTrash) t("suppliers.trash_title") else t("suppliers.title"),
        actions = {
            if (onBack != null) {
                Button(onClick = onBack) { Text(t("common.back")) }
            }
            if (canManage) {
                Button(onClick = { showTrash = !showTrash }) {
                    Text(if (showTrash) t("common.active") else t("common.trash"))
                }
            }
        }
    ) {
        if (canManage && !showTrash) {
            SectionCard {
                OutlinedTextField(
                    value = nome,
                    onValueChange = { nome = it },
                    label = { Text(t("suppliers.name")) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = cnpj,
                    onValueChange = { cnpj = it },
                    label = { Text(t("suppliers.cnpj")) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = contatos,
                    onValueChange = { contatos = it },
                    label = { Text(t("suppliers.contacts")) },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = {
                    scope.launch {
                        runCatching {
                            repository.saveFornecedor(
                                FornecedorSummary(
                                    id = editingId ?: "",
                                    nome = nome,
                                    cnpj = cnpj.ifBlank { null },
                                    contatos = contatos.ifBlank { null },
                                    entregaPropria = false,
                                    deletedAt = null
                                )
                            )
                        }.onSuccess {
                            editingId = null
                            nome = ""
                            cnpj = ""
                            contatos = ""
                            load()
                        }.onFailure { error = it.message }
                    }
                }) { Text(if (editingId == null) t("common.create") else t("common.save")) }
            }
        }

        if (loading) {
            CircularProgressIndicator()
        }
        error?.let { StateMessage(it, isError = true) }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items) { item ->
                SectionCard {
                    Text(item.nome, fontWeight = FontWeight.SemiBold)
                    item.cnpj?.takeIf { it.isNotBlank() }?.let { StateMessage(it) }
                    item.contatos?.takeIf { it.isNotBlank() }?.let { StateMessage(it) }
                    if (canManage) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (showTrash) {
                                Button(onClick = { scope.launch { repository.restoreFornecedor(item.id); load() } }) {
                                    Text(t("common.restore"))
                                }
                                Button(onClick = { scope.launch { repository.hardDeleteFornecedor(item.id); load() } }) {
                                    Text(t("common.delete"))
                                }
                            } else {
                                Button(onClick = {
                                    editingId = item.id
                                    nome = item.nome
                                    cnpj = item.cnpj.orEmpty()
                                    contatos = item.contatos.orEmpty()
                                }) { Text(t("common.edit")) }
                                Button(onClick = { scope.launch { repository.softDeleteFornecedor(item.id); load() } }) {
                                    Text(t("common.trash"))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MateriaisManagerScreen(
    repository: CadastrosRepository,
    canManage: Boolean,
    onBack: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var showTrash by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var items by remember { mutableStateOf<List<MaterialRecord>>(emptyList()) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var nome by remember { mutableStateOf("") }
    var unidade by remember { mutableStateOf("un") }
    var tempo by remember { mutableStateOf("") }

    fun load() {
        scope.launch {
            loading = true
            runCatching {
                repository.listMateriais(
                    includeDeleted = showTrash,
                    deletedSinceIso = if (showTrash) Instant.now().minusSeconds(60L * 60L * 24L * 30L).toString() else null
                )
            }.onSuccess {
                items = it
                loading = false
                error = null
            }.onFailure {
                loading = false
                error = it.message
            }
        }
    }

    LaunchedEffect(showTrash) { load() }

    AppPage(
        title = if (showTrash) t("materials.trash_title") else t("materials.title"),
        actions = {
            if (onBack != null) {
                Button(onClick = onBack) { Text(t("common.back")) }
            }
            if (canManage) {
                Button(onClick = { showTrash = !showTrash }) {
                    Text(if (showTrash) t("common.active") else t("common.trash"))
                }
            }
        }
    ) {
        if (canManage && !showTrash) {
            SectionCard {
                OutlinedTextField(
                    value = nome,
                    onValueChange = { nome = it },
                    label = { Text(t("materials.name")) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = unidade,
                    onValueChange = { unidade = it },
                    label = { Text(t("materials.unit")) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = tempo,
                    onValueChange = { tempo = it },
                    label = { Text(t("materials.production_time")) },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = {
                    scope.launch {
                        runCatching {
                            repository.saveMaterial(
                                MaterialRecord(
                                    id = editingId ?: "",
                                    nome = nome,
                                    unidade = unidade,
                                    tempoProducaoPadrao = tempo.toIntOrNull(),
                                    estoqueMinimo = 0.0,
                                    deletedAt = null
                                )
                            )
                        }.onSuccess {
                            editingId = null
                            nome = ""
                            unidade = "un"
                            tempo = ""
                            load()
                        }.onFailure { error = it.message }
                    }
                }) { Text(if (editingId == null) t("common.create") else t("common.save")) }
            }
        }

        if (loading) {
            CircularProgressIndicator()
        }
        error?.let { StateMessage(it, isError = true) }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items) { item ->
                SectionCard {
                    Text(item.nome, fontWeight = FontWeight.SemiBold)
                    StateMessage(t("materials.unit_value", "value" to item.unidade))
                    StateMessage(t("materials.time_value", "value" to (item.tempoProducaoPadrao ?: "-")))
                    if (canManage) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (showTrash) {
                                Button(onClick = { scope.launch { repository.restoreMaterial(item.id); load() } }) {
                                    Text(t("common.restore"))
                                }
                                Button(onClick = { scope.launch { repository.hardDeleteMaterial(item.id); load() } }) {
                                    Text(t("common.delete"))
                                }
                            } else {
                                Button(onClick = {
                                    editingId = item.id
                                    nome = item.nome
                                    unidade = item.unidade
                                    tempo = item.tempoProducaoPadrao?.toString() ?: ""
                                }) { Text(t("common.edit")) }
                                Button(onClick = { scope.launch { repository.softDeleteMaterial(item.id); load() } }) {
                                    Text(t("common.trash"))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MaterialFornecedorManagerScreen(
    repository: CadastrosRepository,
    canManage: Boolean,
    onBack: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var items by remember { mutableStateOf<List<MaterialFornecedorRecord>>(emptyList()) }
    var materiais by remember { mutableStateOf<List<MaterialRecord>>(emptyList()) }
    var fornecedores by remember { mutableStateOf<List<FornecedorSummary>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var showTrash by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var materialId by remember { mutableStateOf("") }
    var fornecedorId by remember { mutableStateOf("") }
    var precoAtual by remember { mutableStateOf("") }
    var pedidoMinimo by remember { mutableStateOf("") }
    var leadTime by remember { mutableStateOf("") }
    var validade by remember { mutableStateOf("") }
    var showMaterialDialog by remember { mutableStateOf(false) }
    var showFornecedorDialog by remember { mutableStateOf(false) }

    fun load() {
        scope.launch {
            loading = true
            runCatching {
                Triple(
                    repository.listMaterialFornecedor(
                        includeDeleted = showTrash,
                        deletedSinceIso = if (showTrash) Instant.now().minusSeconds(60L * 60L * 24L * 30L).toString() else null
                    ),
                    repository.listMateriais(includeDeleted = false, deletedSinceIso = null),
                    repository.listFornecedores(includeDeleted = false, deletedSinceIso = null)
                )
            }.onSuccess {
                items = it.first
                materiais = it.second
                fornecedores = it.third
                loading = false
                error = null
            }.onFailure {
                loading = false
                error = it.message
            }
        }
    }

    fun clearForm() {
        editingId = null
        materialId = ""
        fornecedorId = ""
        precoAtual = ""
        pedidoMinimo = ""
        leadTime = ""
        validade = ""
    }

    LaunchedEffect(showTrash) { load() }

    val selectedMaterialName = materiais.firstOrNull { it.id == materialId }?.nome ?: t("material_supplier.none_selected")
    val selectedFornecedorName = fornecedores.firstOrNull { it.id == fornecedorId }?.nome ?: t("material_supplier.none_selected")
    val requiredMaterialMessage = t("material_supplier.required_material")
    val requiredSupplierMessage = t("material_supplier.required_supplier")

    AppPage(
        title = if (showTrash) t("material_supplier.trash_title") else t("material_supplier.title"),
        actions = {
            if (onBack != null) {
                Button(onClick = onBack) { Text(t("common.back")) }
            }
            if (canManage) {
                Button(onClick = { showTrash = !showTrash }) {
                    Text(if (showTrash) t("common.active") else t("common.trash"))
                }
            }
        }
    ) {
        if (canManage && !showTrash) {
            SectionCard {
                StateMessage(t("material_supplier.material_value", "value" to selectedMaterialName))
                Button(onClick = { showMaterialDialog = true }) { Text(t("material_supplier.select_material")) }
                StateMessage(t("material_supplier.supplier_value", "value" to selectedFornecedorName))
                Button(onClick = { showFornecedorDialog = true }) { Text(t("material_supplier.select_supplier")) }

                OutlinedTextField(
                    value = precoAtual,
                    onValueChange = { precoAtual = it },
                    label = { Text(t("material_supplier.price")) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pedidoMinimo,
                    onValueChange = { pedidoMinimo = it },
                    label = { Text(t("material_supplier.min_order")) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = leadTime,
                    onValueChange = { leadTime = it },
                    label = { Text(t("material_supplier.lead_time")) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = validade,
                    onValueChange = { validade = it },
                    label = { Text(t("material_supplier.price_expiry")) },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = {
                    val validationError = validateMaterialFornecedorSelection(
                        materialId = materialId,
                        fornecedorId = fornecedorId,
                        requiredMaterialMessage = requiredMaterialMessage,
                        requiredFornecedorMessage = requiredSupplierMessage
                    )
                    if (validationError != null) {
                        error = validationError
                        return@Button
                    }
                    scope.launch {
                        runCatching {
                            repository.saveMaterialFornecedor(
                                MaterialFornecedorRecord(
                                    id = editingId ?: "",
                                    materialId = materialId,
                                    fornecedorId = fornecedorId,
                                    precoAtual = precoAtual.toDoubleOrNull() ?: 0.0,
                                    pedidoMinimo = pedidoMinimo.toDoubleOrNull() ?: 0.0,
                                    leadTimeDias = leadTime.toIntOrNull() ?: 0,
                                    validadePreco = validade.ifBlank { null },
                                    materialNome = null,
                                    materialUnidade = null,
                                    fornecedorNome = null,
                                    fornecedorCnpj = null,
                                    deletedAt = null
                                )
                            )
                        }.onSuccess {
                            clearForm()
                            load()
                        }.onFailure { error = it.message }
                    }
                }) { Text(if (editingId == null) t("material_supplier.create") else t("material_supplier.save")) }
            }
        }

        if (loading) {
            CircularProgressIndicator()
        }
        error?.let { StateMessage(it, isError = true) }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items) { item ->
                SectionCard {
                    Text("${item.materialNome ?: item.materialId} x ${item.fornecedorNome ?: item.fornecedorId}")
                    StateMessage(
                        t(
                            "material_supplier.summary",
                            "price" to item.precoAtual,
                            "min" to item.pedidoMinimo,
                            "lead" to item.leadTimeDias
                        )
                    )
                    if (canManage) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (showTrash) {
                                Button(onClick = { scope.launch { repository.restoreMaterialFornecedor(item.id); load() } }) {
                                    Text(t("common.restore"))
                                }
                                Button(onClick = { scope.launch { repository.hardDeleteMaterialFornecedor(item.id); load() } }) {
                                    Text(t("common.delete"))
                                }
                            } else {
                                Button(onClick = {
                                    editingId = item.id
                                    materialId = item.materialId
                                    fornecedorId = item.fornecedorId
                                    precoAtual = item.precoAtual.toString()
                                    pedidoMinimo = item.pedidoMinimo.toString()
                                    leadTime = item.leadTimeDias.toString()
                                    validade = item.validadePreco.orEmpty()
                                }) { Text(t("common.edit")) }
                                Button(onClick = { scope.launch { repository.softDeleteMaterialFornecedor(item.id); load() } }) {
                                    Text(t("common.trash"))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showMaterialDialog) {
        SimpleSelectionDialog(
            title = t("material_supplier.select_material"),
            options = materiais.map { it.id to it.nome },
            onSelect = {
                materialId = it
                showMaterialDialog = false
            },
            onDismiss = { showMaterialDialog = false }
        )
    }

    if (showFornecedorDialog) {
        SimpleSelectionDialog(
            title = t("material_supplier.select_supplier"),
            options = fornecedores.map { it.id to it.nome },
            onSelect = {
                fornecedorId = it
                showFornecedorDialog = false
            },
            onDismiss = { showFornecedorDialog = false }
        )
    }
}

internal fun validateMaterialFornecedorSelection(
    materialId: String,
    fornecedorId: String,
    requiredMaterialMessage: String,
    requiredFornecedorMessage: String
): String? {
    if (materialId.isBlank()) return requiredMaterialMessage
    if (fornecedorId.isBlank()) return requiredFornecedorMessage
    return null
}

@Composable
private fun SimpleSelectionDialog(
    title: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(options) { option ->
                    SectionCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option.first) }
                    ) {
                        Text(option.second, modifier = Modifier.padding(4.dp))
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text(t("common.cancel")) }
        }
    )
}
