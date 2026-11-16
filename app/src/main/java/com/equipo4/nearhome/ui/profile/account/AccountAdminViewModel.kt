package com.equipo4.nearhome.ui.profile.account

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.equipo4.nearhome.R
import com.equipo4.nearhome.data.local.SessionManager
import com.equipo4.nearhome.data.session.CurrentUserStore
import com.equipo4.nearhome.di.NetworkModule
import com.equipo4.nearhome.network.AuthApi
import com.equipo4.nearhome.network.CambiarContrasenaRequest
import com.equipo4.nearhome.network.CambiarTelefonoRequest
import com.equipo4.nearhome.network.ConfirmarCambioCorreoRequest
import com.equipo4.nearhome.network.DesactivarCuentaRequest
import com.equipo4.nearhome.network.SolicitarCodigoCambioCorreoRequest
import com.equipo4.nearhome.network.UsuarioMeDto
import com.equipo4.nearhome.network.ValidarCambioTelefonoRequest
import com.equipo4.nearhome.network.VerificarCodigoContrasenaRequest
import com.equipo4.nearhome.ui.profile.UserRole
import com.equipo4.nearhome.ui.subscription.PlanDates
import com.equipo4.nearhome.util.PasswordValidator
import com.equipo4.nearhome.util.AppStrings
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.IOException
import com.equipo4.nearhome.data.remote.codigoApi

class AccountAdminViewModel(application: Application) : AndroidViewModel(application) {

    private val sessionManager = SessionManager(application)
    private val authApi: AuthApi = NetworkModule.provideRetrofit(
        NetworkModule.provideOkHttpClient(sessionManager)
    ).create(AuthApi::class.java)

    private val _uiState = MutableStateFlow(AccountAdminUiState(isLoading = true))
    val uiState: StateFlow<AccountAdminUiState> = _uiState.asStateFlow()

    private var resendTimerJob: Job? = null
    private var emailResendTimerJob: Job? = null

    init {
        viewModelScope.launch {
            CurrentUserStore.user.collect { user ->
                _uiState.update {
                    it.copy(isLoading = false, userData = user?.toAccountData())
                }
            }
        }
    }

    private fun UsuarioMeDto.toAccountData(): UserAccountData {
        val p = plan
        return UserAccountData(
            email = correo,
            phone = telefono,
            role = UserRole.fromDb(rol),
            planNombre = p?.nombre,
            planVigente = p?.vigente == true,
            planCancelado = p?.cancelado == true,
            planVence = p?.fechaVencimiento,
            isGoogleUser = !tieneContrasena,
            tieneContrasena = tieneContrasena
        )
    }

    fun onEvent(event: AccountAdminUiEvent) {
        when (event) {
            AccountAdminUiEvent.OnOptionClick -> {
                _uiState.update { it.copy(userMessage = R.string.coming_soon) }
            }

            // --- Cambio de Teléfono ---
            AccountAdminUiEvent.OnPhoneOptionClick -> {
                _uiState.update {
                    it.copy(
                        phoneStep = PhoneFlowStep.NEW_PHONE,
                        countryCodeInput = "+52",
                        newPhoneInput = "",
                        phoneCurrentPasswordInput = "",
                        phoneCurrentPasswordError = null,
                        newPhoneError = null,
                        phoneGeneralError = null,
                        phoneProofToken = null,
                        isPhoneValidating = false,
                        isPhoneSaving = false,
                        phoneSaveError = null
                    )
                }
            }
            is AccountAdminUiEvent.OnCountryCodeChanged -> {
                _uiState.update { it.copy(countryCodeInput = event.text) }
            }
            is AccountAdminUiEvent.OnNewPhoneChanged -> {
                _uiState.update { it.copy(newPhoneInput = event.text, newPhoneError = null, phoneGeneralError = null) }
            }
            is AccountAdminUiEvent.OnPhoneCurrentPasswordChanged -> {
                _uiState.update { it.copy(phoneCurrentPasswordInput = event.text, phoneCurrentPasswordError = null, phoneGeneralError = null) }
            }
            is AccountAdminUiEvent.OnValidatePhoneSubmitted -> {
                validarCambioTelefono(event.googleToken, event.onSuccessValidated)
            }
            AccountAdminUiEvent.OnPhoneFlowDismissed -> {
                limpiarEstadoMemoriaTelefono()
            }
            is AccountAdminUiEvent.OnConfirmPhoneSmsSubmitted -> {
                cambiarTelefono(event.tokenFirebase)
            }
            is AccountAdminUiEvent.OnDeletePasswordChanged -> {
                _uiState.update { it.copy(deletePasswordInput = event.text, deactivateError = null) }
            }

            // --- Cambio de Correo ---
            AccountAdminUiEvent.OnEmailOptionClick -> {
                _uiState.update {
                    it.copy(
                        emailStep = EmailFlowStep.NEW_EMAIL,
                        newEmailInput = "",
                        emailCurrentPasswordInput = "",
                        emailCurrentPasswordError = null,
                        newEmailError = null,
                        emailGeneralError = null,
                        emailOtpCode = List(6) { "" },
                        emailOtpErrorMessage = null,
                        googleTokenForEmailChange = null
                    )
                }
            }
            is AccountAdminUiEvent.OnNewEmailChanged -> {
                _uiState.update { it.copy(newEmailInput = event.text, newEmailError = null, emailGeneralError = null) }
            }
            is AccountAdminUiEvent.OnEmailCurrentPasswordChanged -> {
                _uiState.update { it.copy(emailCurrentPasswordInput = event.text, emailCurrentPasswordError = null, emailGeneralError = null) }
            }
            is AccountAdminUiEvent.OnRequestEmailCodeSubmitted -> {
                solicitarCodigoCambioCorreo(event.googleToken)
            }
            AccountAdminUiEvent.OnEmailFlowDismissed -> {
                limpiarEstadoMemoriaCorreo()
            }
            is AccountAdminUiEvent.OnEmailOtpDigitChanged -> {
                val currentOtp = _uiState.value.emailOtpCode.toMutableList()
                currentOtp[event.index] = event.digit
                _uiState.update { it.copy(emailOtpCode = currentOtp, emailOtpErrorMessage = null) }
            }
            AccountAdminUiEvent.OnConfirmNewEmailSubmitted -> {
                confirmarCambioCorreo()
            }
            AccountAdminUiEvent.OnResendEmailCodeRequested -> {
                if (_uiState.value.canResendEmailCode) {
                    solicitarCodigoCambioCorreo(_uiState.value.googleTokenForEmailChange)
                }
            }
            AccountAdminUiEvent.OnReturnToNewEmailStepRequested -> {
                _uiState.update { it.copy(emailStep = EmailFlowStep.NEW_EMAIL, emailOtpErrorMessage = null) }
            }

            // --- Contraseña ---
            AccountAdminUiEvent.OnPasswordOptionClick -> {
                _uiState.update {
                    it.copy(
                        passwordStep = PasswordFlowStep.CONFIRM_SEND_CODE,
                        otpErrorMessage = null
                    )
                }
            }
            AccountAdminUiEvent.OnRequestCodeConfirmed -> {
                solicitarCodigoContrasena()
            }
            AccountAdminUiEvent.OnPasswordFlowDismissed -> {
                limpiarEstadoMemoriaContrasena()
            }
            is AccountAdminUiEvent.OnOtpDigitChanged -> {
                val currentOtp = _uiState.value.otpCode.toMutableList()
                currentOtp[event.index] = event.digit
                _uiState.update { it.copy(otpCode = currentOtp, otpErrorMessage = null) }
            }
            AccountAdminUiEvent.OnVerifyCodeSubmitted -> {
                verificarCodigoContrasena()
            }
            AccountAdminUiEvent.OnResendCodeRequested -> {
                if (_uiState.value.canResendCode) {
                    solicitarCodigoContrasena()
                }
            }
            is AccountAdminUiEvent.OnCurrentPasswordChanged -> {
                _uiState.update { it.copy(currentPasswordInput = event.text, currentPasswordError = null) }
            }
            is AccountAdminUiEvent.OnNewPasswordChanged -> {
                _uiState.update { it.copy(newPasswordInput = event.text, passwordSaveError = null) }
            }
            is AccountAdminUiEvent.OnConfirmPasswordChanged -> {
                _uiState.update { it.copy(confirmPasswordInput = event.text, passwordSaveError = null) }
            }
            AccountAdminUiEvent.OnSaveNewPasswordSubmitted -> {
                guardarNuevaContrasena()
            }

            // --- Desactivar / Eliminar ---
            AccountAdminUiEvent.OnDeactivateRequested -> {
                _uiState.update {
                    it.copy(
                        showDeactivateDialog = true,
                        deactivatePasswordInput = "",
                        deactivateError = null
                    )
                }
            }
            is AccountAdminUiEvent.OnDeactivatePasswordChanged -> {
                _uiState.update {
                    it.copy(deactivatePasswordInput = event.text, deactivateError = null)
                }
            }
            AccountAdminUiEvent.OnDeactivateDismissed -> {
                _uiState.update {
                    it.copy(
                        showDeactivateDialog = false,
                        deactivatePasswordInput = "",
                        deactivateError = null
                    )
                }
            }
            is AccountAdminUiEvent.OnDeactivateConfirmed -> {
                desactivarCuenta(event.googleToken)
            }
            AccountAdminUiEvent.OnDeleteRequested -> {
                _uiState.update { it.copy(showDeleteDialog = true, deleteInputText = "") }
            }
            AccountAdminUiEvent.OnDeleteDismissed -> {
                _uiState.update { it.copy(showDeleteDialog = false, deleteInputText = "") }
            }
            is AccountAdminUiEvent.OnDeleteInputChanged -> {
                _uiState.update { it.copy(deleteInputText = event.text) }
            }
            is AccountAdminUiEvent.OnDeleteConfirmed -> {
                eliminarCuentaProgramada(event.googleToken)
            }
            AccountAdminUiEvent.OnUserMessageConsumed -> {
                _uiState.update { it.copy(userMessage = null) }
            }
            AccountAdminUiEvent.OnSnackbarMessageConsumed -> {
                _uiState.update { it.copy(snackbarSuccessMessage = null) }
            }

            // Cancelar plan: primero se pide confirmación, luego se llama a la API
            AccountAdminUiEvent.OnCancelPlanRequested -> {
                _uiState.update { it.copy(showCancelPlanDialog = true, cancelPlanError = null) }
            }
            AccountAdminUiEvent.OnCancelPlanDismissed -> {
                _uiState.update { it.copy(showCancelPlanDialog = false, cancelPlanError = null) }
            }
            AccountAdminUiEvent.OnCancelPlanConfirmed -> {
                cancelarPlan()
            }
        }
    }

    // ... (validarCambioTelefono, cambiarTelefono, solicitarCodigoCambioCorreo, confirmarCambioCorreo,
    //      solicitarCodigoContrasena, verificarCodigoContrasena, guardarNuevaContrasena, startResendTimer,
    //      startEmailResendTimer, limpiarEstadoMemoriaTelefono, limpiarEstadoMemoriaCorreo,
    //      limpiarEstadoMemoriaContrasena, parseErrorMessage, onCleared) -- SE QUEDAN IGUAL.

    // Vamos a pegarlos completos abajo:
    private fun validarCambioTelefono(googleToken: String?, onSuccessValidated: (String) -> Unit) {
        val currentState = _uiState.value
        val digits = currentState.newPhoneInput.filter { it.isDigit() }
        val fullPhone = currentState.countryCodeInput + digits

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isPhoneValidating = true,
                    phoneCurrentPasswordError = null,
                    newPhoneError = null,
                    phoneGeneralError = null
                )
            }
            try {
                val hasPass = currentState.userData?.tieneContrasena == true
                val request = ValidarCambioTelefonoRequest(
                    telefonoNuevo = fullPhone,
                    contrasena = if (hasPass) currentState.phoneCurrentPasswordInput else null,
                    googleToken = googleToken
                )
                val response = authApi.validarCambioTelefono(request)
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    _uiState.update {
                        it.copy(
                            isPhoneValidating = false,
                            phoneProofToken = body.comprobante,
                            phoneStep = PhoneFlowStep.SMS_VERIFICATION,
                            phoneGeneralError = null
                        )
                    }
                    onSuccessValidated(fullPhone)
                } else {
                    val rawError = response.errorBody()?.string()
                    val errorMsg = parseErrorMessage(rawError, AppStrings.get(R.string.acc_err_validate_phone))
                    when (response.code()) {
                        403 -> {
                            if (hasPass) {
                                _uiState.update { it.copy(isPhoneValidating = false, phoneCurrentPasswordError = AppStrings.get(R.string.acc_err_current_password)) }
                            } else {
                                _uiState.update { it.copy(isPhoneValidating = false, phoneGeneralError = AppStrings.get(R.string.acc_err_wrong_google)) }
                            }
                        }
                        409 -> {
                            _uiState.update { it.copy(isPhoneValidating = false, newPhoneError = AppStrings.get(R.string.acc_err_phone_taken)) }
                        }
                        429 -> {
                            _uiState.update { it.copy(isPhoneValidating = false, phoneGeneralError = AppStrings.get(R.string.acc_err_phone_too_many)) }
                        }
                        else -> {
                            _uiState.update { it.copy(isPhoneValidating = false, phoneGeneralError = errorMsg) }
                        }
                    }
                }
            } catch (_: IOException) {
                _uiState.update {
                    it.copy(isPhoneValidating = false, phoneGeneralError = AppStrings.get(R.string.network_error_connection))
                }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(isPhoneValidating = false, phoneGeneralError = AppStrings.get(R.string.error_unexpected_retry))
                }
            }
        }
    }

    private fun cambiarTelefono(tokenFirebase: String) {
        val estado = _uiState.value
        // Evita dos peticiones: el SMS automático y el botón Verificar pueden llegar juntos
        if (estado.isPhoneSaving) return
        val proof = estado.phoneProofToken ?: return
        // Se marca antes de lanzar la corrutina, así la segunda llamada ya ve que está guardando
        _uiState.update { it.copy(isPhoneSaving = true, phoneSaveError = null) }
        viewModelScope.launch {
            try {
                val request = CambiarTelefonoRequest(comprobante = proof, tokenFirebase = tokenFirebase)
                val response = authApi.cambiarTelefono(request)
                if (response.isSuccessful) {
                    CurrentUserStore.refresh(authApi)
                    limpiarEstadoMemoriaTelefono()
                    _uiState.update { it.copy(snackbarSuccessMessage = AppStrings.get(R.string.acc_phone_updated)) }
                } else {
                    val rawError = response.errorBody()?.string()
                    val errorMsg = parseErrorMessage(rawError, AppStrings.get(R.string.acc_err_phone_expired))
                    if (response.code() == 409) {
                        _uiState.update {
                            it.copy(
                                isPhoneSaving = false,
                                phoneStep = PhoneFlowStep.NEW_PHONE,
                                phoneGeneralError = AppStrings.get(R.string.acc_err_phone_expired)
                            )
                        }
                    } else {
                        _uiState.update { it.copy(isPhoneSaving = false, phoneSaveError = errorMsg) }
                    }
                }
            } catch (_: IOException) {
                _uiState.update {
                    it.copy(isPhoneSaving = false, phoneSaveError = AppStrings.get(R.string.network_error_connection))
                }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(isPhoneSaving = false, phoneSaveError = AppStrings.get(R.string.error_unexpected_retry))
                }
            }
        }
    }

    private fun limpiarEstadoMemoriaTelefono() {
        _uiState.update {
            it.copy(
                phoneStep = PhoneFlowStep.NONE,
                countryCodeInput = "+52",
                newPhoneInput = "",
                phoneCurrentPasswordInput = "",
                phoneCurrentPasswordError = null,
                newPhoneError = null,
                isPhoneValidating = false,
                phoneGeneralError = null,
                phoneProofToken = null,
                isPhoneSaving = false,
                phoneSaveError = null
            )
        }
    }

    private fun solicitarCodigoCambioCorreo(googleToken: String?) {
        val currentState = _uiState.value
        if (!currentState.isNewEmailFormValid && googleToken == null) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isEmailCodeSending = true,
                    emailCurrentPasswordError = null,
                    newEmailError = null,
                    emailGeneralError = null
                )
            }
            try {
                val hasPass = currentState.userData?.tieneContrasena == true
                val request = SolicitarCodigoCambioCorreoRequest(
                    correoNuevo = currentState.newEmailInput.trim(),
                    contrasena = if (hasPass) currentState.emailCurrentPasswordInput else null,
                    googleToken = googleToken
                )
                val response = authApi.solicitarCodigoCambioCorreo(request)
                if (response.isSuccessful) {
                    _uiState.update {
                        it.copy(
                            isEmailCodeSending = false,
                            googleTokenForEmailChange = googleToken,
                            emailStep = EmailFlowStep.VERIFY_CODE,
                            emailOtpCode = List(6) { "" },
                            emailOtpErrorMessage = null
                        )
                    }
                    startEmailResendTimer()
                } else {
                    val rawError = response.errorBody()?.string()
                    val errorMsg = parseErrorMessage(rawError, AppStrings.get(R.string.acc_err_request_code))
                    when (response.code()) {
                        403 -> {
                            if (hasPass) {
                                _uiState.update { it.copy(isEmailCodeSending = false, emailCurrentPasswordError = AppStrings.get(R.string.acc_err_current_password)) }
                            } else {
                                _uiState.update { it.copy(isEmailCodeSending = false, emailGeneralError = AppStrings.get(R.string.acc_err_wrong_google)) }
                            }
                        }
                        409 -> {
                            _uiState.update { it.copy(isEmailCodeSending = false, newEmailError = AppStrings.get(R.string.acc_err_email_taken)) }
                        }
                        400 -> {
                            if (errorMsg.contains("Confirma con Google", ignoreCase = true)) {
                                _uiState.update { it.copy(isEmailCodeSending = false, emailStep = EmailFlowStep.NEW_EMAIL, emailGeneralError = AppStrings.get(R.string.acc_err_confirm_google_again)) }
                            } else {
                                _uiState.update { it.copy(isEmailCodeSending = false, emailGeneralError = errorMsg) }
                            }
                        }
                        else -> {
                            _uiState.update { it.copy(isEmailCodeSending = false, emailGeneralError = errorMsg) }
                        }
                    }
                }
            } catch (_: IOException) {
                _uiState.update {
                    it.copy(isEmailCodeSending = false, emailGeneralError = AppStrings.get(R.string.network_error_connection))
                }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(isEmailCodeSending = false, emailGeneralError = AppStrings.get(R.string.error_unexpected_retry))
                }
            }
        }
    }

    private fun confirmarCambioCorreo() {
        val currentState = _uiState.value
        val code = currentState.emailOtpCode.joinToString("")
        if (code.length != 6) return

        viewModelScope.launch {
            _uiState.update { it.copy(isEmailConfirming = true, emailOtpErrorMessage = null) }
            try {
                val request = ConfirmarCambioCorreoRequest(
                    correoNuevo = currentState.newEmailInput.trim(),
                    codigo = code
                )
                val response = authApi.confirmarCambioCorreo(request)
                if (response.isSuccessful) {
                    CurrentUserStore.refresh(authApi)
                    limpiarEstadoMemoriaCorreo()
                    _uiState.update { it.copy(snackbarSuccessMessage = AppStrings.get(R.string.acc_email_updated)) }
                } else {
                    val rawError = response.errorBody()?.string()
                    val errorMsg = parseErrorMessage(rawError, AppStrings.get(R.string.acc_err_code_wrong))
                    if (response.code() == 409) {
                        _uiState.update {
                            it.copy(
                                isEmailConfirming = false,
                                emailStep = EmailFlowStep.NEW_EMAIL,
                                newEmailError = AppStrings.get(R.string.acc_err_email_taken)
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(isEmailConfirming = false, emailOtpErrorMessage = errorMsg)
                        }
                    }
                }
            } catch (_: IOException) {
                _uiState.update {
                    it.copy(isEmailConfirming = false, emailOtpErrorMessage = AppStrings.get(R.string.network_error_connection))
                }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(isEmailConfirming = false, emailOtpErrorMessage = AppStrings.get(R.string.error_unexpected_retry))
                }
            }
        }
    }

    private fun startEmailResendTimer() {
        emailResendTimerJob?.cancel()
        _uiState.update { it.copy(canResendEmailCode = false, emailResendCountdown = 60) }
        emailResendTimerJob = viewModelScope.launch {
            for (i in 60 downTo 1) {
                _uiState.update { it.copy(emailResendCountdown = i) }
                delay(1000)
            }
            _uiState.update { it.copy(canResendEmailCode = true, emailResendCountdown = 0) }
        }
    }

    private fun limpiarEstadoMemoriaCorreo() {
        emailResendTimerJob?.cancel()
        _uiState.update {
            it.copy(
                emailStep = EmailFlowStep.NONE,
                newEmailInput = "",
                emailCurrentPasswordInput = "",
                emailCurrentPasswordError = null,
                newEmailError = null,
                isEmailCodeSending = false,
                emailGeneralError = null,
                emailOtpCode = List(6) { "" },
                isEmailConfirming = false,
                emailOtpErrorMessage = null,
                emailResendCountdown = 0,
                canResendEmailCode = false,
                googleTokenForEmailChange = null
            )
        }
    }

    private fun solicitarCodigoContrasena() {
        viewModelScope.launch {
            _uiState.update { it.copy(isOtpLoading = true, otpErrorMessage = null) }
            try {
                val response = authApi.solicitarCodigoContrasena()
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    _uiState.update {
                        it.copy(
                            isOtpLoading = false,
                            maskedEmail = body.correoOculto,
                            passwordStep = PasswordFlowStep.VERIFY_CODE,
                            otpCode = List(6) { "" },
                            otpErrorMessage = null
                        )
                    }
                    startResendTimer()
                } else {
                    val rawError = response.errorBody()?.string()
                    val errorMsg = parseErrorMessage(rawError, AppStrings.get(R.string.acc_err_send_email))
                    _uiState.update { it.copy(isOtpLoading = false, otpErrorMessage = errorMsg) }
                }
            } catch (_: IOException) {
                _uiState.update {
                    it.copy(isOtpLoading = false, otpErrorMessage = AppStrings.get(R.string.network_error_connection))
                }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(isOtpLoading = false, otpErrorMessage = AppStrings.get(R.string.error_unexpected_retry))
                }
            }
        }
    }

    private fun verificarCodigoContrasena() {
        val currentState = _uiState.value
        val code = currentState.otpCode.joinToString("")
        if (code.length != 6) return

        viewModelScope.launch {
            _uiState.update { it.copy(isOtpLoading = true, otpErrorMessage = null) }
            try {
                val response = authApi.verificarCodigoContrasena(VerificarCodigoContrasenaRequest(code))
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    _uiState.update {
                        it.copy(
                            isOtpLoading = false,
                            proofToken = body.comprobante,
                            passwordStep = PasswordFlowStep.NEW_PASSWORD,
                            otpErrorMessage = null
                        )
                    }
                } else {
                    val rawError = response.errorBody()?.string()
                    val errorMsg = parseErrorMessage(rawError, AppStrings.get(R.string.acc_err_code_wrong))
                    _uiState.update { it.copy(isOtpLoading = false, otpErrorMessage = errorMsg) }
                }
            } catch (_: IOException) {
                _uiState.update {
                    it.copy(isOtpLoading = false, otpErrorMessage = AppStrings.get(R.string.network_error_connection))
                }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(isOtpLoading = false, otpErrorMessage = AppStrings.get(R.string.error_unexpected_retry))
                }
            }
        }
    }

    private fun guardarNuevaContrasena() {
        val currentState = _uiState.value
        val proof = currentState.proofToken ?: return
        if (!currentState.isNewPasswordFormValid || currentState.isPasswordSaving) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isPasswordSaving = true,
                    currentPasswordError = null,
                    passwordSaveError = null
                )
            }
            try {
                val hasCurrentPassword = currentState.userData?.tieneContrasena == true
                val request = CambiarContrasenaRequest(
                    comprobante = proof,
                    contrasenaActual = if (hasCurrentPassword) currentState.currentPasswordInput else null,
                    contrasenaNueva = currentState.newPasswordInput
                )
                val response = authApi.cambiarOcrearContrasena(request)
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    sessionManager.saveToken(body.token)
                    CurrentUserStore.refresh(authApi)

                    val successMsg = if (hasCurrentPassword) AppStrings.get(R.string.acc_password_updated) else AppStrings.get(R.string.acc_password_created)

                    limpiarEstadoMemoriaContrasena()
                    _uiState.update {
                        it.copy(snackbarSuccessMessage = successMsg)
                    }
                } else {
                    val rawError = response.errorBody()?.string()
                    val errorMsg = parseErrorMessage(rawError, AppStrings.get(R.string.acc_err_save_password))
                    if (response.code() == 403) {
                        _uiState.update {
                            it.copy(isPasswordSaving = false, currentPasswordError = AppStrings.get(R.string.acc_err_current_password))
                        }
                    } else if (response.code() == 409) {
                        limpiarEstadoMemoriaContrasena()
                        _uiState.update {
                            it.copy(snackbarSuccessMessage = AppStrings.get(R.string.acc_err_password_expired))
                        }
                    } else {
                        _uiState.update {
                            it.copy(isPasswordSaving = false, passwordSaveError = errorMsg)
                        }
                    }
                }
            } catch (_: IOException) {
                _uiState.update {
                    it.copy(
                        isPasswordSaving = false,
                        passwordSaveError = AppStrings.get(R.string.network_error_connection)
                    )
                }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        isPasswordSaving = false,
                        passwordSaveError = AppStrings.get(R.string.error_unexpected_retry)
                    )
                }
            }
        }
    }

    private fun startResendTimer() {
        resendTimerJob?.cancel()
        _uiState.update { it.copy(canResendCode = false, resendCountdown = 60) }
        resendTimerJob = viewModelScope.launch {
            for (i in 60 downTo 1) {
                _uiState.update { it.copy(resendCountdown = i) }
                delay(1000)
            }
            _uiState.update { it.copy(canResendCode = true, resendCountdown = 0) }
        }
    }

    private fun limpiarEstadoMemoriaContrasena() {
        resendTimerJob?.cancel()
        _uiState.update {
            it.copy(
                passwordStep = PasswordFlowStep.NONE,
                maskedEmail = "",
                otpCode = List(6) { "" },
                isOtpLoading = false,
                otpErrorMessage = null,
                resendCountdown = 0,
                canResendCode = false,
                currentPasswordInput = "",
                currentPasswordError = null,
                newPasswordInput = "",
                confirmPasswordInput = "",
                isPasswordSaving = false,
                passwordSaveError = null,
                proofToken = null
            )
        }
    }

    private fun desactivarCuenta(googleToken: String?) {
        val currentState = _uiState.value
        if (currentState.isDeactivating) return

        viewModelScope.launch {
            _uiState.update { it.copy(isDeactivating = true, deactivateError = null) }
            try {
                val request = DesactivarCuentaRequest(
                    contrasena = currentState.deactivatePasswordInput.takeIf { it.isNotBlank() },
                    googleToken = googleToken
                )
                val response = authApi.desactivarCuenta(request)
                if (response.isSuccessful || response.code() == 204) {
                    sessionManager.clearSession()
                    _uiState.update {
                        it.copy(
                            isDeactivating = false,
                            showDeactivateDialog = false,
                            isDeactivatedSuccess = true
                        )
                    }
                } else {
                    val rawError = response.errorBody()?.string()
                    val errorMsg = parseErrorMessage(rawError, AppStrings.get(R.string.acc_err_current_password))
                    _uiState.update {
                        it.copy(isDeactivating = false, deactivateError = errorMsg)
                    }
                }
            } catch (_: IOException) {
                _uiState.update {
                    it.copy(
                        isDeactivating = false,
                        deactivateError = AppStrings.get(R.string.network_error_connection)
                    )
                }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        isDeactivating = false,
                        deactivateError = AppStrings.get(R.string.error_unexpected_retry)
                    )
                }
            }
        }
    }

    // Eliminar cuenta: con contraseña si la cuenta la tiene; si es de Google, con el token de Google
    private fun eliminarCuentaProgramada(googleToken: String?) {
        if (_uiState.value.isDeleting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true, deleteError = null) }
            try {
                val request = DesactivarCuentaRequest(
                    contrasena = _uiState.value.deletePasswordInput.takeIf { it.isNotBlank() },
                    googleToken = googleToken
                )
                val response = authApi.eliminarCuenta(request)
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    sessionManager.clearSession()
                    CurrentUserStore.clear()
                    _uiState.update {
                        it.copy(
                            isDeleting = false,
                            showDeleteDialog = false,
                            deleteInputText = "",
                            deletePasswordInput = "",
                            isDeletedSuccess = true,
                            snackbarSuccessMessage = AppStrings.get(R.string.acc_delete_scheduled_msg, PlanDates.formatear(body.fechaEliminacion, AppStrings.locale()))
                        )
                    }
                } else {
                    val errorMsg = parseErrorMessage(response.errorBody()?.string(), AppStrings.get(R.string.acc_err_delete_account))
                    _uiState.update { it.copy(isDeleting = false, deleteError = errorMsg) }
                }
            } catch (_: IOException) {
                _uiState.update {
                    it.copy(
                        isDeleting = false,
                        deleteError = AppStrings.get(R.string.network_error_connection)
                    )
                }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(isDeleting = false, deleteError = AppStrings.get(R.string.error_unexpected_retry))
                }
            }
        }
    }

    // Cancelar plan: el error se muestra dentro del diálogo de confirmación
    private fun cancelarPlan() {
        if (_uiState.value.isCancellingPlan) return
        viewModelScope.launch {
            _uiState.update { it.copy(isCancellingPlan = true, cancelPlanError = null) }
            try {
                val response = authApi.cancelarPlan()
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    // El plan sigue vigente hasta su fecha: el usuario regresa con "cancelado" y esa fecha
                    sessionManager.saveToken(body.token)
                    sessionManager.saveUser(name = body.usuario.nombre, role = body.usuario.rol)
                    CurrentUserStore.set(body.usuario)
                    val hasta = body.usuario.plan?.fechaVencimiento?.let { PlanDates.formatear(it, AppStrings.locale()) }.orEmpty()
                    _uiState.update {
                        it.copy(
                            isCancellingPlan = false,
                            showCancelPlanDialog = false,
                            snackbarSuccessMessage = AppStrings.get(R.string.plan_cancel_success_body, hasta)
                        )
                    }
                } else {
                    val raw = response.errorBody()?.string()
                    val errorMsg = when (codigoApi(raw)) {
                        "SIN_PLAN_ACTIVO" -> AppStrings.get(R.string.plan_cancel_error_no_plan)
                        "PLAN_YA_CANCELADO" -> AppStrings.get(R.string.plan_cancel_error_already)
                        else -> parseErrorMessage(raw, AppStrings.get(R.string.acc_err_cancel_plan))
                    }
                    _uiState.update { it.copy(isCancellingPlan = false, cancelPlanError = errorMsg) }
                }
            } catch (_: IOException) {
                _uiState.update {
                    it.copy(
                        isCancellingPlan = false,
                        cancelPlanError = AppStrings.get(R.string.network_error_connection)
                    )
                }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(isCancellingPlan = false, cancelPlanError = AppStrings.get(R.string.error_unexpected_retry))
                }
            }
        }
    }

    // La API manda un error general ("Los datos enviados no son válidos") y el motivo real en "detalles"
    private fun parseErrorMessage(rawError: String?, defaultMsg: String): String {
        if (rawError.isNullOrBlank()) return defaultMsg
        return try {
            val json = JSONObject(rawError)
            val detalles = json.optJSONArray("detalles")
            if (detalles != null && detalles.length() > 0) {
                detalles.optString(0, defaultMsg)
            } else {
                json.optString("error", defaultMsg)
            }
        } catch (_: Exception) {
            defaultMsg
        }
    }

    override fun onCleared() {
        super.onCleared()
        resendTimerJob?.cancel()
        emailResendTimerJob?.cancel()
        limpiarEstadoMemoriaContrasena()
        limpiarEstadoMemoriaCorreo()
        limpiarEstadoMemoriaTelefono()
    }
}