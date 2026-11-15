package com.equipo4.nearhome.ui.auth.recover

import com.equipo4.nearhome.util.PasswordValidator

enum class RecoverStep {
    REQUEST_CODE,
    VERIFY_CODE,
    NEW_PASSWORD
}

data class RecoverPasswordUiState(
    val step: RecoverStep = RecoverStep.REQUEST_CODE,
    val emailInput: String = "",
    val isSendingCode: Boolean = false,
    val emailError: String? = null,
    val otpCode: List<String> = List(6) { "" },
    val isVerifyingCode: Boolean = false,
    val otpErrorMessage: String? = null,
    val resendCountdown: Int = 0,
    val canResendCode: Boolean = false,
    val newPasswordInput: String = "",
    val confirmPasswordInput: String = "",
    val isSavingPassword: Boolean = false,
    val passwordError: String? = null,
    val proofToken: String? = null
) {
    val isNewPasswordFormValid: Boolean
        get() {
            val isConfirmed = newPasswordInput == confirmPasswordInput
            return PasswordValidator.isValid(newPasswordInput) && isConfirmed && !isSavingPassword
        }
}

sealed interface RecoverPasswordUiEvent {
    data class OnEmailChanged(val text: String) : RecoverPasswordUiEvent
    data object OnRequestCodeSubmitted : RecoverPasswordUiEvent
    data class OnOtpDigitChanged(val index: Int, val digit: String) : RecoverPasswordUiEvent
    data object OnVerifyCodeSubmitted : RecoverPasswordUiEvent
    data object OnResendCodeRequested : RecoverPasswordUiEvent
    data class OnNewPasswordChanged(val text: String) : RecoverPasswordUiEvent
    data class OnConfirmPasswordChanged(val text: String) : RecoverPasswordUiEvent
    data object OnSaveNewPasswordSubmitted : RecoverPasswordUiEvent
    data object OnReturnToEmailStepRequested : RecoverPasswordUiEvent
}