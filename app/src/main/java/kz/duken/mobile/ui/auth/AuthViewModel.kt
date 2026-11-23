package kz.duken.mobile.ui.auth

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kz.duken.mobile.data.api.ApiResult
import kz.duken.mobile.data.api.AppError
import kz.duken.mobile.data.repo.AuthRepository

data class AuthState(
    val busy: Boolean = false,
    val emailInvalid: Boolean = false,
    val passwordTooShort: Boolean = false,
    val error: AppError? = null,
)

class AuthViewModel(private val repo: AuthRepository, private val saved: SavedStateHandle) : ViewModel() {
    /** Поля формы — в SavedStateHandle: не теряются при повороте экрана и после гибели процесса */
    val email: StateFlow<String> = saved.getStateFlow(KEY_EMAIL, "")

    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    fun onEmail(value: String) {
        saved[KEY_EMAIL] = value
        _state.update { it.copy(emailInvalid = false, error = null) }
    }

    fun clearErrors() = _state.update { it.copy(passwordTooShort = false, error = null) }

    fun submit(register: Boolean, password: String) {
        if (_state.value.busy) return
        val mail = email.value.trim()
        val emailInvalid = !EMAIL.matches(mail)
        val tooShort = password.length < 8
        if (emailInvalid || tooShort) {
            _state.update { it.copy(emailInvalid = emailInvalid, passwordTooShort = tooShort) }
            return
        }
        _state.update { AuthState(busy = true) }
        viewModelScope.launch {
            val result = if (register) repo.register(mail, password) else repo.login(mail, password)
            _state.update {
                when (result) {
                    is ApiResult.Ok -> AuthState()
                    is ApiResult.Fail -> AuthState(error = result.error)
                }
            }
        }
    }

    private companion object {
        const val KEY_EMAIL = "email"
        val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}
