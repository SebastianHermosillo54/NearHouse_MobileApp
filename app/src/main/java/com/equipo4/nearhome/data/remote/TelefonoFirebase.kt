package com.equipo4.nearhome.data.remote

import android.app.Activity
import android.util.Log
import com.equipo4.nearhome.R
import com.equipo4.nearhome.util.AppStrings
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

/**
 * Verificación del teléfono por SMS con Firebase.
 *
 * 1. [enviarCodigo]: Firebase manda el SMS y avisa con codigoEnviado(verificationId, tokenReenvio).
 * 2. [credencialConCodigo]: junta ese verificationId con el código que escribió el usuario.
 * 3. [obtenerToken]: inicia sesión en Firebase con la credencial, saca el token y cierra la sesión
 *    de Firebase (la sesión que importa es la de nuestra API). Ese token se manda a la API
 *    en el registro, y la API revisa con Google qué número se verificó.
 *
 * Nunca imprimas el token en Logcat.
 */
object TelefonoFirebase {

    private const val TAG = "TelefonoFirebase"

    /**
     * Segundos que se espera el SMS antes de permitir reenviarlo (Firebase acepta hasta 120).
     * A veces el SMS tarda más de un minuto en llegar; si la gente reenvía muy seguido,
     * Firebase bloquea ese número o ese celular por varias horas ("demasiados intentos").
     */
    const val ESPERA_SEGUNDOS = 120

    /** Lo que Firebase avisa mientras manda el SMS. */
    interface Eventos {
        fun codigoEnviado(verificationId: String, tokenReenvio: PhoneAuthProvider.ForceResendingToken)

        /** Algunos celulares leen el SMS solos: entonces ya no hace falta escribir el código. */
        fun verificadoAutomaticamente(credencial: PhoneAuthCredential)

        fun error(mensaje: String)

        /**
         * Firebase bloqueó los envíos por un tiempo (muchos intentos, o se acabaron los SMS del día).
         * Volver a intentar en ese momento solo alarga el bloqueo, así que ya no se ofrece reenviar.
         */
        fun bloqueado(mensaje: String) {
            this.error(mensaje)
        }
    }

    /**
     * Pide a Firebase que mande el SMS.
     * @param telefono en formato internacional, por ejemplo "+523141234567".
     * @param tokenReenvio null la primera vez; para "Reenviar SMS", el que dio codigoEnviado.
     */
    fun enviarCodigo(
        activity: Activity,
        telefono: String,
        tokenReenvio: PhoneAuthProvider.ForceResendingToken?,
        eventos: Eventos,
    ) {
        enviar(activity, telefono, tokenReenvio, eventos, conRecaptcha = usarRecaptcha)
    }

    /**
     * Antes de mandar el SMS, Firebase revisa que la app sea legítima. Primero lo hace con
     * Google Play (Play Integrity); en algunos celulares eso falla aunque la app esté bien
     * configurada. Entonces se reintenta solo, sin mostrar error, con reCAPTCHA (una página
     * que se abre un momento), y desde ahí se usa reCAPTCHA directo mientras la app siga abierta.
     */
    @Volatile
    private var usarRecaptcha = false

    private fun enviar(
        activity: Activity,
        telefono: String,
        tokenReenvio: PhoneAuthProvider.ForceResendingToken?,
        eventos: Eventos,
        conRecaptcha: Boolean,
    ) {
        FirebaseAuth.getInstance().firebaseAuthSettings.forceRecaptchaFlowForTesting(conRecaptcha)

        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken,
            ) {
                eventos.codigoEnviado(verificationId, token)
            }

            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                eventos.verificadoAutomaticamente(credential)
            }

            override fun onVerificationFailed(e: FirebaseException) {
                Log.w(TAG, "No se pudo mandar el SMS: ${codigoDeError(e)} (reCAPTCHA: $conRecaptcha)", e)
                if (!conRecaptcha && codigoDeError(e) in CODIGOS_APP_NO_AUTORIZADA) {
                    // Falló la revisión con Google Play: se intenta otra vez con reCAPTCHA
                    usarRecaptcha = true
                    enviar(activity, telefono, tokenReenvio, eventos, conRecaptcha = true)
                    return
                }
                if (e is FirebaseTooManyRequestsException || codigoDeError(e) == "ERROR_QUOTA_EXCEEDED") {
                    eventos.bloqueado(mensajeAlEnviar(e))
                } else {
                    eventos.error(mensajeAlEnviar(e))
                }
            }
        }

        val opciones = PhoneAuthOptions.newBuilder(FirebaseAuth.getInstance())
            .setPhoneNumber(telefono)
            .setTimeout(ESPERA_SEGUNDOS.toLong(), TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
        if (tokenReenvio != null) {
            opciones.setForceResendingToken(tokenReenvio)
        }
        PhoneAuthProvider.verifyPhoneNumber(opciones.build())
    }

    /** Junta el verificationId del SMS con el código que escribió el usuario. */
    fun credencialConCodigo(verificationId: String, codigo: String): PhoneAuthCredential =
        PhoneAuthProvider.getCredential(verificationId, codigo)

    /**
     * Inicia sesión en Firebase con la credencial, regresa el token para la API y cierra
     * la sesión de Firebase. Si algo falla (por ejemplo, el código está mal), lanza una
     * excepción con un mensaje listo para mostrarle al usuario.
     */
    suspend fun obtenerToken(credencial: PhoneAuthCredential): String {
        val auth = FirebaseAuth.getInstance()
        try {
            val resultado = auth.signInWithCredential(credencial).await()
            return resultado.user?.getIdToken(true)?.await()?.token
                ?: throw IllegalStateException("Firebase no regresó el token")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo verificar el código", e)
            throw Exception(mensajeAlVerificar(e), e)
        } finally {
            auth.signOut()
        }
    }

    /**
     * Mensaje según lo que contestó Firebase. Cada caso se arregla distinto:
     * - muchos intentos: bloqueo temporal de Firebase para ese número o celular (solo queda esperar);
     * - SMS del día agotados: límite del proyecto en Firebase;
     * - app no autorizada: el APK se compiló en una computadora cuya huella (SHA) no está en Firebase.
     * En cualquier otro caso se muestra el código de Firebase para poder buscarlo.
     */
    private fun mensajeAlEnviar(e: FirebaseException): String {
        val codigo = codigoDeError(e)
        return when {
            e is FirebaseTooManyRequestsException ->
                if (e.message.orEmpty().contains("quota", ignoreCase = true)) AppStrings.get(R.string.phone_sms_error_quota)
                else AppStrings.get(R.string.phone_sms_error_blocked)
            codigo == "ERROR_QUOTA_EXCEEDED" -> AppStrings.get(R.string.phone_sms_error_quota)
            codigo in CODIGOS_APP_NO_AUTORIZADA ->
                AppStrings.get(R.string.phone_sms_error_app_not_authorized, detalleDeError(e, codigo))
            codigo == "ERROR_INVALID_PHONE_NUMBER" -> AppStrings.get(R.string.phone_sms_error_invalid_number)
            e is FirebaseNetworkException -> AppStrings.get(R.string.phone_sms_error_network)
            e is FirebaseAuthInvalidCredentialsException -> AppStrings.get(R.string.phone_sms_error_invalid_number)
            else -> AppStrings.get(R.string.phone_sms_error_send_code, codigo)
        }
    }

    /** Código de Firebase (ej. ERROR_INVALID_APP_CREDENTIAL o BILLING_NOT_ENABLED). Nunca trae datos privados. */
    private fun codigoDeError(e: Exception): String =
        (e as? FirebaseAuthException)?.errorCode
            ?: Regex("[A-Z][A-Z_]{5,}").find(e.message.orEmpty())?.value
            ?: e.javaClass.simpleName

    /**
     * Código de Firebase y, si su mensaje lo dice, qué verificación de la app falló.
     * Se muestra en pantalla para diagnosticar con una simple captura, sin conectar el celular.
     */
    private fun detalleDeError(e: Exception, codigo: String): String {
        val texto = e.message.orEmpty().lowercase()
        val verificacion = when {
            "play_integrity" in texto || "play integrity" in texto -> " · Play Integrity"
            "recaptcha" in texto -> " · reCAPTCHA"
            "sha-256" in texto || "sha256" in texto -> " · SHA-256"
            "sha-1" in texto || "sha1" in texto -> " · SHA-1"
            else -> ""
        }
        return codigo + verificacion
    }

    private val CODIGOS_APP_NO_AUTORIZADA = setOf(
        "ERROR_APP_NOT_AUTHORIZED",
        "ERROR_INVALID_APP_CREDENTIAL",
        "ERROR_MISSING_APP_CREDENTIAL",
        "ERROR_MISSING_CLIENT_IDENTIFIER"
    )

    private fun mensajeAlVerificar(e: Exception): String {
        val codigoError = (e as? FirebaseAuthException)?.errorCode
        return when {
            codigoError == "ERROR_SESSION_EXPIRED" -> AppStrings.get(R.string.phone_sms_error_expired)
            e is FirebaseAuthInvalidCredentialsException -> AppStrings.get(R.string.signup_otp_error)
            e is FirebaseTooManyRequestsException -> AppStrings.get(R.string.phone_sms_error_too_many)
            e is FirebaseNetworkException -> AppStrings.get(R.string.phone_sms_error_network)
            else -> AppStrings.get(R.string.phone_sms_error_verify)
        }
    }
}
