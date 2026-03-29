package com.adriano.demoman.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

data class LoginUiState(
    val isLoginMode: Boolean = true,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSuccess: Boolean = false
)

class LoginViewModel(
    private val authService: AuthApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun toggleMode() {
        _uiState.value = _uiState.value.copy(
            isLoginMode = !_uiState.value.isLoginMode,
            error = null
        )
    }

    fun submit(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Username and password cannot be empty")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val isLoginMode = _uiState.value.isLoginMode
            try {
                val request = UsernamePasswordRequest(username, password)
                val response = if (isLoginMode) authService.login(request) else authService.register(request)
                
                if (response.isSuccessful) {
                    val token = response.body()?.token
                    if (token != null) {
                        // In a real application, you would save this token in EncryptedSharedPreferences or DataStore
                        _uiState.value = _uiState.value.copy(isLoading = false, isSuccess = true)
                    } else {
                        _uiState.value = _uiState.value.copy(isLoading = false, error = "Invalid response from server")
                    }
                } else {
                    val defaultMsg = if (isLoginMode) "Invalid username or password" else "Registration failed"
                    val errorMessage = response.errorBody()?.string() ?: defaultMsg
                    _uiState.value = _uiState.value.copy(isLoading = false, error = errorMessage)
                }
            } catch (e: IOException) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Network error. Check your connection.")
            } catch (e: HttpException) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = "Unexpected error occurred.")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.localizedMessage ?: "Unknown error")
            }
        }
    }
}
