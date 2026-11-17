package com.equipo4.nearhome.ui.subscription

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.equipo4.nearhome.util.AppStrings
import com.equipo4.nearhome.R

class CheckoutViewModel(
    val plan: SubscriptionPlan,
    private val repo: SubscriptionRepository
) : ViewModel() {

    val cardNumber = MutableStateFlow(TextFieldValue(""))
    val cardHolder = MutableStateFlow(TextFieldValue(""))
    val expiry = MutableStateFlow(TextFieldValue(""))
    val cvv = MutableStateFlow(TextFieldValue(""))

    val isLoading = MutableStateFlow(false)
    val successMessage = MutableStateFlow<String?>(null)
    val apiError = MutableStateFlow<String?>(null)

    // Todos los campos pasan sus reglas (ver TarjetaValidacion). Nada de esto se manda a la API.
    val isComplete: StateFlow<Boolean> = combine(
        cardNumber, cardHolder, expiry, cvv
    ) { num, holder, exp, c ->
        val digitos = num.text.filter { it.isDigit() }
        TarjetaValidacion.errorNumero(digitos) == null &&
                TarjetaValidacion.errorTitular(holder.text) == null &&
                TarjetaValidacion.errorVencimiento(exp.text.filter { it.isDigit() }) == null &&
                TarjetaValidacion.errorCvv(c.text, digitos) == null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** Solo dígitos, con el largo de la marca (15 o 16) y separados en grupos. */
    fun onCardNumberChanged(input: TextFieldValue) {
        val texto = input.text
        val cursor = input.selection.start.coerceIn(0, texto.length)
        val soloDigitos = texto.filter { it.isDigit() }
        val digitos = soloDigitos.take(TarjetaValidacion.longitudMaxima(soloDigitos))
        val digitosAntes = texto.take(cursor).count { it.isDigit() }.coerceAtMost(digitos.length)

        val formateado = TarjetaValidacion.formatearNumero(digitos)
        val nuevoCursor = TarjetaValidacion.cursorEnNumero(digitos, digitosAntes)
        cardNumber.value = TextFieldValue(
            text = formateado,
            selection = TextRange(nuevoCursor.coerceIn(0, formateado.length))
        )

        // Si cambió la marca, el CVV no puede ser más largo de lo que pide
        val largo = TarjetaValidacion.largoCvv(digitos)
        if (cvv.value.text.length > largo) {
            cvv.value = TextFieldValue(cvv.value.text.take(largo), TextRange(largo))
        }
    }

    /** Letras, espacios, apóstrofo, guion y punto, en mayúsculas como en la tarjeta (máximo 26). */
    fun onCardHolderChanged(input: TextFieldValue) {
        val limpio = TarjetaValidacion.limpiarTitular(input.text)
        val quitados = input.text.length - limpio.length
        val nuevoCursor = (input.selection.start - maxOf(0, quitados)).coerceIn(0, limpio.length)
        cardHolder.value = TextFieldValue(
            text = limpio,
            selection = TextRange(if (input.selection.start >= input.text.length) limpio.length else nuevoCursor)
        )
    }

    /** Formato MM / AA; si el mes empieza con 2 a 9 se agrega el 0 ("4" se vuelve "04"). */
    fun onExpiryChanged(input: TextFieldValue) {
        val digitos = TarjetaValidacion.limpiarVencimiento(input.text)
        val formateado = TarjetaValidacion.formatearVencimiento(digitos)
        val alFinal = input.selection.start >= input.text.length
        val nuevoCursor = if (alFinal) {
            formateado.length
        } else {
            val antes = input.text.take(input.selection.start).count { it.isDigit() }
            (if (antes > 2) antes + 3 else antes).coerceIn(0, formateado.length)
        }
        expiry.value = TextFieldValue(text = formateado, selection = TextRange(nuevoCursor))
    }

    /** Solo dígitos: 3, o 4 si la tarjeta es American Express. */
    fun onCvvChanged(input: TextFieldValue) {
        val largo = TarjetaValidacion.largoCvv(cardNumber.value.text.filter { it.isDigit() })
        val filtrado = input.text.filter { it.isDigit() }.take(largo)
        val nuevoCursor = input.selection.start.coerceAtMost(filtrado.length)
        cvv.value = TextFieldValue(text = filtrado, selection = TextRange(nuevoCursor))
    }

    fun submit(onSuccessNavigation: (String) -> Unit) { // <-- CAMBIO: recibe String
        if (!isComplete.value || isLoading.value) return

        apiError.value = null
        isLoading.value = true

        viewModelScope.launch {
            when (val r = repo.contratar(plan.id)) {
                is Resultado.Ok -> {
                    isLoading.value = false
                    onSuccessNavigation(plan.name)
                }
                is Resultado.Error -> {
                    isLoading.value = false
                    apiError.value = r.mensaje
                }
            }
        }
    }
}