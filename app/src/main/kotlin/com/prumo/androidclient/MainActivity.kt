package com.prumo.androidclient

import android.os.Bundle
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.fragment.app.FragmentActivity
import com.prumo.core.i18n.LocalI18n
import com.prumo.core.i18n.I18nCatalog
import com.prumo.core.i18n.t
import com.prumo.core.model.AccessPolicy
import com.prumo.core.model.AppLanguage
import com.prumo.core.model.AppRole
import com.prumo.core.model.ObraSummary
import com.prumo.core.model.SessionUser
import com.prumo.data.repository.AppContainer
import com.prumo.feature.auth.LoginScreen
import com.prumo.feature.auth.LoginViewModel
import com.prumo.feature.estoque.EstoqueScreen
import com.prumo.feature.estoque.EstoqueViewModel
import com.prumo.feature.pedidos.PedidosScreen
import com.prumo.feature.pedidos.PedidosViewModel
import com.prumo.core.ui.AppPage
import com.prumo.core.ui.StateMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as PromoAndroidApp).container

        setContent {
            PromoTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PromoApp(container = container)
                }
            }
        }
    }
}

@Composable
private fun PromoApp(container: AppContainer) {
    val navController = rememberNavController()

    val mainViewModel: MainViewModel = viewModel(
        factory = simpleFactory { MainViewModel(container.authRepository) }
    )
    val state by mainViewModel.state.collectAsStateWithLifecycle()

    val loginViewModel: LoginViewModel = viewModel(
        factory = simpleFactory { LoginViewModel(container.authRepository) }
    )

    val pedidosViewModel: PedidosViewModel = viewModel(
        factory = simpleFactory { PedidosViewModel(container.pedidosRepository) }
    )

    val estoqueViewModel: EstoqueViewModel = viewModel(
        factory = simpleFactory { EstoqueViewModel(container.estoqueRepository) }
    )

    LaunchedEffect(Unit) {
        mainViewModel.bootstrap()
    }

    val language = state.session?.user?.preferredLanguage ?: AppLanguage.PT_BR
    val i18n = remember(language) { I18nCatalog(language) }

    CompositionLocalProvider(LocalI18n provides i18n) {
    if (state.session != null && state.sessionLocked) {
        QuickUnlockScreen(
            onUnlocked = { mainViewModel.unlockSession() },
            onRequirePassword = { mainViewModel.requirePasswordLogin() }
        )
        return@CompositionLocalProvider
    }
    NavHost(navController = navController, startDestination = AppRoutes.Splash) {
        composable(AppRoutes.Splash) {
            LaunchedEffect(state.bootDone, state.session?.user?.userId) {
                if (!state.bootDone) return@LaunchedEffect
                if (state.session == null) {
                    navController.navigate(AppRoutes.Login) { popUpTo(0) }
                } else {
                    navController.navigate(AppRoutes.Index) { popUpTo(0) }
                }
            }
            Text(t("app.initializing"), modifier = Modifier.padding(24.dp))
        }

        composable(AppRoutes.Login) {
            LoginScreen(
                viewModel = loginViewModel,
                origin = "https://prumo.app",
                onLoggedIn = {
                    mainViewModel.markJustAuthenticated()
                    mainViewModel.bootstrap()
                    navController.navigate(AppRoutes.Index) {
                        popUpTo(AppRoutes.Login) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = AppRoutes.AccessReviewPattern,
            arguments = listOf(navArgument("token") { type = NavType.StringType })
        ) { backStackEntry ->
            val token = backStackEntry.arguments?.getString("token").orEmpty()
            AccessRequestReviewScreen(
                token = token,
                authRepository = container.authRepository,
                onBackLogin = {
                    navController.navigate(AppRoutes.Login) { popUpTo(0) }
                }
            )
        }

        composable(AppRoutes.Index) {
            RequireSession(state, navController) { user ->
                if (!AccessPolicy.hasOperationalAccess(user)) {
                    LaunchedEffect("no_access") { navController.navigate(AppRoutes.SemAcesso) }
                    Text(t("nav.redirecting"), modifier = Modifier.padding(20.dp))
                } else {
                    LaunchedEffect(user.multiObraEnabled, user.defaultObraId) {
                        val defaultObraId = user.defaultObraId
                        if (!user.multiObraEnabled && !defaultObraId.isNullOrBlank()) {
                            mainViewModel.selectObra(defaultObraId)
                            navController.navigate(AppRoutes.dashboard(defaultObraId))
                        }
                    }
                    IndexScreen(
                        user = user,
                        onOpenObras = { navController.navigate(AppRoutes.Obras) },
                        onOpenCadastros = { navController.navigate(AppRoutes.Fornecedores) },
                        onOpenUsuarios = { navController.navigate(AppRoutes.UsuariosAcessos) },
                        onLogout = {
                            mainViewModel.logout()
                            navController.navigate(AppRoutes.Login) { popUpTo(0) }
                        }
                    )
                }
            }
        }

        composable(AppRoutes.SemAcesso) {
            SemAcessoScreen(
                user = state.session?.user,
                onRefresh = { mainViewModel.refreshAccess() },
                onGoHome = { navController.navigate(AppRoutes.Index) },
                onLogout = {
                    mainViewModel.logout()
                    navController.navigate(AppRoutes.Login) { popUpTo(0) }
                }
            )
        }

        composable(AppRoutes.Obras) {
            RequireSession(state, navController) { user ->
                if (!AccessPolicy.can(user, "obras.view")) {
                    LaunchedEffect("obras_deny") { navController.navigate(AppRoutes.SemAcesso) }
                    Text(t("access.no_permission"), modifier = Modifier.padding(20.dp))
                } else {
                    ObrasManagerScreen(
                        repository = container.obrasRepository,
                        canManage = user.role == AppRole.MASTER || user.role == AppRole.GESTOR,
                        onOpenDashboard = { obra ->
                            mainViewModel.selectObra(obra.id)
                            navController.navigate(AppRoutes.dashboard(obra.id))
                        }
                    )
                }
            }
        }

        composable(
            route = AppRoutes.DashboardPattern,
            arguments = listOf(navArgument("obraId") { type = NavType.StringType })
        ) { backStackEntry ->
            val obraId = backStackEntry.arguments?.getString("obraId").orEmpty()
            RequireSession(state, navController) { user ->
                if (!hasObraReadAccess(user, obraId)) {
                    LaunchedEffect("dashboard_deny_$obraId") { navController.navigate(AppRoutes.SemAcesso) }
                    Text(t("access.no_permission"), modifier = Modifier.padding(20.dp))
                } else {
                    val obra = rememberObra(container = container, obraId = obraId)
                    DashboardScreen(
                        obra = obra,
                        onOpenPedidos = { navController.navigate(AppRoutes.pedidos(obraId)) },
                        onOpenRecebimento = { navController.navigate(AppRoutes.recebimento(obraId)) },
                        onOpenEstoque = { navController.navigate(AppRoutes.estoque(obraId)) },
                        onOpenCadastros = { navController.navigate(AppRoutes.Fornecedores) },
                        onBackObras = { navController.navigate(AppRoutes.Obras) }
                    )
                }
            }
        }

        composable(
            route = AppRoutes.PedidosPattern,
            arguments = listOf(navArgument("obraId") { type = NavType.StringType })
        ) { backStackEntry ->
            val obraId = backStackEntry.arguments?.getString("obraId").orEmpty()
            RequireSession(state, navController) { user ->
                if (!hasObraPermission(user, obraId, "pedidos.view")) {
                    LaunchedEffect("pedidos_deny_$obraId") { navController.navigate(AppRoutes.SemAcesso) }
                    Text(t("access.no_permission"), modifier = Modifier.padding(20.dp))
                } else {
                    mainViewModel.selectObra(obraId)
                    PedidosScreen(
                        obraId = obraId,
                        viewModel = pedidosViewModel,
                        canEditBase = AccessPolicy.canEditPedidosBase(user.role),
                        canApprove = AccessPolicy.canApprovePedidos(user.role),
                        canDelete = user.role == AppRole.MASTER || user.role == AppRole.GESTOR
                    )
                }
            }
        }

        composable(
            route = AppRoutes.RecebimentoPattern,
            arguments = listOf(navArgument("obraId") { type = NavType.StringType })
        ) { backStackEntry ->
            val obraId = backStackEntry.arguments?.getString("obraId").orEmpty()
            RequireSession(state, navController) { user ->
                val roleAllowed = AccessPolicy.canAccessRecebimentoRoute(user.role)
                val permissionAllowed = hasObraPermission(user, obraId, "pedidos.view") || hasObraPermission(user, obraId, "pedidos.receive")
                if (!roleAllowed || !permissionAllowed || !AccessPolicy.hasObraAccess(user.role, user.obraScope, obraId)) {
                    LaunchedEffect("receb_deny_$obraId") { navController.navigate(AppRoutes.SemAcesso) }
                    Text(t("access.no_permission"), modifier = Modifier.padding(20.dp))
                } else {
                    mainViewModel.selectObra(obraId)
                    RecebimentoManagerScreen(
                        obraId = obraId,
                        pedidosRepository = container.pedidosRepository,
                        estoqueRepository = container.estoqueRepository,
                        userId = user.userId
                    )
                }
            }
        }

        composable(
            route = AppRoutes.EstoquePattern,
            arguments = listOf(navArgument("obraId") { type = NavType.StringType })
        ) { backStackEntry ->
            val obraId = backStackEntry.arguments?.getString("obraId").orEmpty()
            RequireSession(state, navController) { user ->
                val roleAllowed = user.role == AppRole.MASTER || user.role == AppRole.GESTOR || user.role == AppRole.ALMOXARIFE || user.role == AppRole.ENGENHEIRO
                if (!roleAllowed || !hasObraPermission(user, obraId, "estoque.view") || !AccessPolicy.hasObraAccess(user.role, user.obraScope, obraId)) {
                    LaunchedEffect("estoque_deny_$obraId") { navController.navigate(AppRoutes.SemAcesso) }
                    Text(t("access.no_permission"), modifier = Modifier.padding(20.dp))
                } else {
                    mainViewModel.selectObra(obraId)
                    EstoqueScreen(obraId = obraId, viewModel = estoqueViewModel)
                }
            }
        }

        composable(AppRoutes.Fornecedores) {
            RequireSession(state, navController) { user ->
                if (!AccessPolicy.can(user, "fornecedores.view")) {
                    LaunchedEffect("forn_deny") { navController.navigate(AppRoutes.SemAcesso) }
                    Text(t("access.no_permission"), modifier = Modifier.padding(20.dp))
                } else {
                    FornecedoresManagerScreen(
                        repository = container.cadastrosRepository,
                        canManage = AccessPolicy.canManageCadastros(user.role),
                        onBack = { navController.navigateUp() }
                    )
                }
            }
        }

        composable(AppRoutes.Materiais) {
            RequireSession(state, navController) { user ->
                if (!AccessPolicy.can(user, "materiais.view")) {
                    LaunchedEffect("mat_deny") { navController.navigate(AppRoutes.SemAcesso) }
                    Text(t("access.no_permission"), modifier = Modifier.padding(20.dp))
                } else {
                    MateriaisManagerScreen(
                        repository = container.cadastrosRepository,
                        canManage = AccessPolicy.canManageCadastros(user.role),
                        onBack = { navController.navigateUp() }
                    )
                }
            }
        }

        composable(AppRoutes.MaterialFornecedor) {
            RequireSession(state, navController) { user ->
                if (!AccessPolicy.can(user, "material_fornecedor.view")) {
                    LaunchedEffect("matforn_deny") { navController.navigate(AppRoutes.SemAcesso) }
                    Text(t("access.no_permission"), modifier = Modifier.padding(20.dp))
                } else {
                    MaterialFornecedorManagerScreen(
                        repository = container.cadastrosRepository,
                        canManage = AccessPolicy.canManageCadastros(user.role),
                        onBack = { navController.navigateUp() }
                    )
                }
            }
        }

        composable(AppRoutes.UsuariosAcessos) {
            RequireSession(state, navController) { user ->
                val allowedRole = user.role == AppRole.MASTER || user.role == AppRole.GESTOR
                val allowedPermission = AccessPolicy.can(user, "users.manage")
                if (!allowedRole || !allowedPermission) {
                    LaunchedEffect("users_deny") { navController.navigate(AppRoutes.SemAcesso) }
                    Text(t("access.no_permission"), modifier = Modifier.padding(20.dp))
                } else {
                    UsuariosAcessosScreen(
                        usuariosRepository = container.usuariosRepository,
                        obrasRepository = container.obrasRepository,
                        currentUserId = user.userId
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun RequireSession(
    state: MainUiState,
    navController: NavHostController,
    content: @Composable (SessionUser) -> Unit
) {
    val session = state.session
    if (session == null) {
        LaunchedEffect("require_login") {
            navController.navigate(AppRoutes.Login) { popUpTo(0) }
        }
        Text(t("nav.redirecting_login"), modifier = Modifier.padding(20.dp))
        return
    }
    content(session.user)
}

@Composable
private fun rememberObra(container: AppContainer, obraId: String): ObraSummary? {
    var obra by remember(obraId) { mutableStateOf<ObraSummary?>(null) }
    LaunchedEffect(obraId) {
        obra = withContext(Dispatchers.IO) {
            runCatching {
                container.obrasRepository.listObras(includeDeleted = false, deletedSinceIso = null)
                    .firstOrNull { it.id == obraId }
            }.getOrNull()
        }
    }
    return obra
}

private fun hasObraReadAccess(user: SessionUser, obraId: String): Boolean {
    if (!AccessPolicy.hasObraAccess(user.role, user.obraScope, obraId)) return false
    return AccessPolicy.can(user, "obras.view", obraId)
}

private fun hasObraPermission(user: SessionUser, obraId: String, permission: String): Boolean {
    if (!AccessPolicy.hasObraAccess(user.role, user.obraScope, obraId)) return false
    return AccessPolicy.can(user, permission, obraId)
}

@Composable
private fun QuickUnlockScreen(
    onUnlocked: () -> Unit,
    onRequirePassword: () -> Unit
) {
    val activity = (LocalContext.current as? FragmentActivity)
    var message by remember { mutableStateOf<String?>(null) }
    var prompted by remember { mutableStateOf(false) }

    fun triggerPrompt() {
        val host = activity
        if (host == null) {
            message = "Nao foi possivel abrir biometria neste dispositivo."
            return
        }
        requestBiometricUnlock(
            activity = host,
            onSuccess = onUnlocked,
            onError = { reason -> message = reason }
        )
    }

    LaunchedEffect(Unit) {
        if (!prompted) {
            prompted = true
            triggerPrompt()
        }
    }

    AppPage(title = "Desbloqueio rapido") {
        StateMessage("Confirme sua identidade para continuar.")
        message?.let { StateMessage(it, isError = true) }
        androidx.compose.material3.Button(
            onClick = { triggerPrompt() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Desbloquear com biometria/PIN")
        }
        androidx.compose.material3.Button(
            onClick = onRequirePassword,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Entrar com senha")
        }
    }
}

private fun requestBiometricUnlock(
    activity: FragmentActivity,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    val biometricManager = BiometricManager.from(activity)
    val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL
    val canAuth = biometricManager.canAuthenticate(authenticators)
    if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
        onError("Biometria/PIN do dispositivo indisponivel. Use entrar com senha.")
        return
    }

    val executor = ContextCompat.getMainExecutor(activity)
    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Desbloqueio rapido")
        .setSubtitle("Confirme para continuar conectado")
        .setAllowedAuthenticators(authenticators)
        .build()

    val prompt = BiometricPrompt(
        activity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onError(errString.toString())
            }
        }
    )
    prompt.authenticate(promptInfo)
}

private fun <T : ViewModel> simpleFactory(create: () -> T): ViewModelProvider.Factory {
    return object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = create() as VM
    }
}
