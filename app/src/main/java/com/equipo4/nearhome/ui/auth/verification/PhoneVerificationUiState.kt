package com.equipo4.nearhome.ui.auth.verification

import com.equipo4.nearhome.network.LoginResponse
import com.equipo4.nearhome.data.remote.TelefonoFirebase

data class PhoneVerificationUiState(
    val phoneNumber: String = "",
    val otpCode: String = "",
    val isError: Boolean = false,
    val errorMessage: String? = null,
    val isVerified: Boolean = false,
    val isLoading: Boolean = false,
    val isSending: Boolean = false, // mandando el SMS con Firebase
    /** Firebase confirmó que mandó el SMS: hasta entonces no se dice "Hemos enviado". */
    val codigoEnviado: Boolean = false,
    /** El error es del envío del SMS, no de un código mal escrito: los cuadros no se pintan de rojo. */
    val errorDeEnvio: Boolean = false,
    val timerSeconds: Int = TelefonoFirebase.ESPERA_SEGUNDOS,
    val canResend: Boolean = false,
    /** Firebase bloqueó los envíos por un tiempo: ya no se ofrece "Reenviar SMS" en esta pantalla. */
    val reenvioBloqueado: Boolean = false,
    /** Registro con Google: la sesión que regresó la API (entra directo, sin pasar por el login). */
    val sesionNueva: LoginResponse? = null
)
