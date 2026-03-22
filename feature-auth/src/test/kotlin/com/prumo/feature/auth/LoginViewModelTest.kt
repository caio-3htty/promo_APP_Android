package com.prumo.feature.auth

import com.prumo.core.model.AccessRequestReviewData
import com.prumo.core.model.AccessReviewDecision
import com.prumo.core.model.AccessMode
import com.prumo.core.model.AppLanguage
import com.prumo.core.model.AppRole
import com.prumo.core.model.CompanySuggestion
import com.prumo.core.model.SessionToken
import com.prumo.core.model.SessionUser
import com.prumo.core.model.SignupRequestInput
import com.prumo.core.model.SignupResult
import com.prumo.core.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `login success updates state`() = runTest {
        val vm = LoginViewModel(FakeAuthRepository())
        vm.onEmailChange("a@a.com")
        vm.onPasswordChange("123")
        vm.submit("https://prumo.app")
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.success)
    }
}

private class FakeAuthRepository : AuthRepository {
    override suspend fun login(email: String, password: String): SessionToken = SessionToken(
        accessToken = "token",
        refreshToken = "refresh",
        expiresAtEpochSeconds = 999999,
        user = SessionUser(
            userId = "u",
            email = email,
            fullName = "User",
            role = AppRole.OPERACIONAL,
            tenantId = "tenant",
            isActive = true,
            preferredLanguage = AppLanguage.PT_BR,
            accessMode = AccessMode.TEMPLATE,
            obraScope = emptyList(),
            permissions = emptyList(),
            multiObraEnabled = true,
            defaultObraId = null
        )
    )

    override suspend fun refreshSessionContext(): SessionToken? = null
    override suspend fun searchCompanies(query: String): List<CompanySuggestion> = emptyList()
    override suspend fun signup(input: SignupRequestInput): SignupResult = SignupResult(true, "ok")
    override suspend fun getAccessRequest(token: String): AccessRequestReviewData {
        error("unused")
    }

    override suspend fun reviewAccessRequest(
        token: String,
        decision: AccessReviewDecision,
        reviewedUsername: String,
        reviewedJobTitle: String,
        reviewedRole: String,
        reviewNotes: String
    ): SignupResult = SignupResult(true, "ok")

    override suspend fun logout() {}
    override suspend fun currentSession(): SessionToken? = null
    override suspend fun clearSession() {}
}
