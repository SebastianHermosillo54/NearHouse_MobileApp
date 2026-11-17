package com.equipo4.nearhome.ui.subscription

import java.util.Calendar

/**
 * Reglas de los campos de la tarjeta en el pago simulado.
 * Solo validan lo que el usuario escribe: los datos de la tarjeta no se mandan a la API
 * ni se guardan en la base de datos.
 */

/** Marcas aceptadas, con el largo del número, del CVV y cómo se agrupan los dígitos. */
enum class MarcaTarjeta(val nombre: String, val longitud: Int, val largoCvv: Int, val grupos: List<Int>) {
    VISA("Visa", 16, 3, listOf(4, 4, 4, 4)),
    MASTERCARD("Mastercard", 16, 3, listOf(4, 4, 4, 4)),
    AMEX("American Express", 15, 4, listOf(4, 6, 5))
}

enum class ErrorNumeroTarjeta { INCOMPLETO, MARCA_NO_ACEPTADA, NUMERO_INVALIDO }
enum class ErrorTitular { VACIO, INCOMPLETO }
enum class ErrorVencimiento { INCOMPLETO, MES_INVALIDO, VENCIDA, ANIO_INVALIDO }
enum class ErrorCvv { INCOMPLETO }

object TarjetaValidacion {

    /** Largo máximo del nombre impreso en una tarjeta. */
    const val MAX_TITULAR = 26

    /** Años hacia adelante que se aceptan como vencimiento. */
    const val MAX_ANIOS = 20

    /** Marca según los primeros dígitos, o null si todavía no se sabe o no se acepta. */
    fun marca(digitos: String): MarcaTarjeta? {
        if (digitos.startsWith("4")) return MarcaTarjeta.VISA
        if (digitos.startsWith("34") || digitos.startsWith("37")) return MarcaTarjeta.AMEX
        val dos = digitos.take(2).toIntOrNull()
        if (digitos.length >= 2 && dos != null && dos in 51..55) return MarcaTarjeta.MASTERCARD
        val cuatro = digitos.take(4).toIntOrNull()
        if (digitos.length >= 4 && cuatro != null && cuatro in 2221..2720) return MarcaTarjeta.MASTERCARD
        return null
    }

    /** Dígitos que admite el campo: 15 para American Express y 16 para las demás. */
    fun longitudMaxima(digitos: String): Int = marca(digitos)?.longitud ?: 16

    /** Algoritmo de Luhn: el mismo que usan los bancos para detectar números mal escritos. */
    fun luhnValido(digitos: String): Boolean {
        if (digitos.isEmpty() || digitos.any { !it.isDigit() }) return false
        var suma = 0
        var doblar = false
        for (i in digitos.indices.reversed()) {
            var d = digitos[i] - '0'
            if (doblar) {
                d *= 2
                if (d > 9) d -= 9
            }
            suma += d
            doblar = !doblar
        }
        return suma % 10 == 0
    }

    /** Número con espacios entre los grupos (4-4-4-4, o 4-6-5 en American Express). */
    fun formatearNumero(digitos: String): String {
        val grupos = marca(digitos)?.grupos ?: listOf(4, 4, 4, 4)
        val sb = StringBuilder()
        var i = 0
        for (g in grupos) {
            if (i >= digitos.length) break
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(digitos, i, minOf(i + g, digitos.length))
            i += g
        }
        return sb.toString()
    }

    /** Posición del cursor en el texto formateado, a partir de cuántos dígitos quedan antes de él. */
    fun cursorEnNumero(digitos: String, digitosAntes: Int): Int {
        val grupos = marca(digitos)?.grupos ?: listOf(4, 4, 4, 4)
        var limite = 0
        var espacios = 0
        for (g in grupos.dropLast(1)) {
            limite += g
            if (limite < digitosAntes && limite < digitos.length) espacios++
        }
        return digitosAntes + espacios
    }

    fun errorNumero(digitos: String): ErrorNumeroTarjeta? {
        val marca = marca(digitos)
        return when {
            digitos.length >= 4 && marca == null -> ErrorNumeroTarjeta.MARCA_NO_ACEPTADA
            digitos.length < (marca?.longitud ?: 16) -> ErrorNumeroTarjeta.INCOMPLETO
            !luhnValido(digitos) -> ErrorNumeroTarjeta.NUMERO_INVALIDO
            else -> null
        }
    }

    /** Deja solo letras, espacios, apóstrofo, guion y punto; sin espacios repetidos y en mayúsculas. */
    fun limpiarTitular(texto: String): String {
        val permitido = texto.filter { it.isLetter() || it == ' ' || it == '\'' || it == '-' || it == '.' }
        return permitido
            .replace(Regex(" {2,}"), " ")
            .trimStart()
            .uppercase()
            .take(MAX_TITULAR)
    }

    /** Pide nombre y apellido: al menos dos palabras de dos letras o más. */
    fun errorTitular(texto: String): ErrorTitular? {
        if (texto.isBlank()) return ErrorTitular.VACIO
        val palabras = texto.trim().split(' ').filter { p -> p.count { it.isLetter() } >= 2 }
        return if (palabras.size < 2) ErrorTitular.INCOMPLETO else null
    }

    /**
     * Dígitos del vencimiento (MMAA). Si el primer dígito del mes es de 2 a 9,
     * se agrega el 0 al inicio ("4" → "04").
     */
    fun limpiarVencimiento(texto: String): String {
        var digitos = texto.filter { it.isDigit() }
        if (digitos.isNotEmpty() && digitos[0] in '2'..'9') digitos = "0$digitos"
        return digitos.take(4)
    }

    /** Vencimiento formateado como "MM / AA". */
    fun formatearVencimiento(digitos: String): String =
        if (digitos.length <= 2) digitos else digitos.substring(0, 2) + " / " + digitos.substring(2)

    /**
     * El mes se revisa en cuanto se escriben sus dos dígitos, para avisar de inmediato.
     * La tarjeta vale hasta el último día del mes de vencimiento.
     */
    fun errorVencimiento(digitos: String, hoy: Calendar = Calendar.getInstance()): ErrorVencimiento? {
        if (digitos.length >= 2) {
            val mes = digitos.substring(0, 2).toIntOrNull() ?: return ErrorVencimiento.MES_INVALIDO
            if (mes !in 1..12) return ErrorVencimiento.MES_INVALIDO
        }
        if (digitos.length < 4) return ErrorVencimiento.INCOMPLETO
        val mes = digitos.substring(0, 2).toInt()
        val anio = 2000 + digitos.substring(2, 4).toInt()
        val anioActual = hoy.get(Calendar.YEAR)
        val mesActual = hoy.get(Calendar.MONTH) + 1
        return when {
            anio * 12 + mes < anioActual * 12 + mesActual -> ErrorVencimiento.VENCIDA
            anio > anioActual + MAX_ANIOS -> ErrorVencimiento.ANIO_INVALIDO
            else -> null
        }
    }

    /** Largo del CVV según la marca: 4 en American Express y 3 en las demás. */
    fun largoCvv(digitosTarjeta: String): Int = marca(digitosTarjeta)?.largoCvv ?: 3

    fun errorCvv(cvv: String, digitosTarjeta: String): ErrorCvv? =
        if (cvv.length != largoCvv(digitosTarjeta) || cvv.any { !it.isDigit() }) ErrorCvv.INCOMPLETO else null
}
