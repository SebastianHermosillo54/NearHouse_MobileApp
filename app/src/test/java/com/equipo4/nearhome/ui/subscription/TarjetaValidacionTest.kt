package com.equipo4.nearhome.ui.subscription

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/** Pruebas de las reglas de la tarjeta del pago simulado. */
class TarjetaValidacionTest {

    // Fecha fija para que las pruebas no dependan del día: 17 de noviembre de 2026
    private val hoy: Calendar = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 17) }

    @Test
    fun detectaLaMarca() {
        assertEquals(MarcaTarjeta.VISA, TarjetaValidacion.marca("4242"))
        assertEquals(MarcaTarjeta.MASTERCARD, TarjetaValidacion.marca("5555"))
        assertEquals(MarcaTarjeta.MASTERCARD, TarjetaValidacion.marca("2223"))
        assertEquals(MarcaTarjeta.AMEX, TarjetaValidacion.marca("3782"))
        assertNull(TarjetaValidacion.marca("6011"))
        assertNull(TarjetaValidacion.marca("5"))
    }

    @Test
    fun numeroDeTarjeta() {
        assertNull(TarjetaValidacion.errorNumero("4242424242424242"))
        assertNull(TarjetaValidacion.errorNumero("5555555555554444"))
        assertNull(TarjetaValidacion.errorNumero("378282246310005"))
        assertEquals(ErrorNumeroTarjeta.NUMERO_INVALIDO, TarjetaValidacion.errorNumero("4242424242424241"))
        assertEquals(ErrorNumeroTarjeta.INCOMPLETO, TarjetaValidacion.errorNumero("424242"))
        assertEquals(ErrorNumeroTarjeta.MARCA_NO_ACEPTADA, TarjetaValidacion.errorNumero("1234567812345678"))
    }

    @Test
    fun formatoDelNumero() {
        assertEquals("4242 4242 4242 4242", TarjetaValidacion.formatearNumero("4242424242424242"))
        assertEquals("3782 822463 10005", TarjetaValidacion.formatearNumero("378282246310005"))
        assertEquals("4242 4", TarjetaValidacion.formatearNumero("42424"))
        assertEquals(15, TarjetaValidacion.longitudMaxima("3782"))
        assertEquals(6, TarjetaValidacion.cursorEnNumero("42424", 5))
        assertEquals(4, TarjetaValidacion.cursorEnNumero("4242", 4))
    }

    @Test
    fun titular() {
        assertEquals("JUAN PÉREZ", TarjetaValidacion.limpiarTitular("  juan  pérez7"))
        assertEquals(TarjetaValidacion.MAX_TITULAR, TarjetaValidacion.limpiarTitular("A".repeat(40)).length)
        assertEquals(ErrorTitular.VACIO, TarjetaValidacion.errorTitular(" "))
        assertEquals(ErrorTitular.INCOMPLETO, TarjetaValidacion.errorTitular("JUAN"))
        assertNull(TarjetaValidacion.errorTitular("JUAN P PEREZ"))
    }

    @Test
    fun vencimiento() {
        assertEquals("04", TarjetaValidacion.limpiarVencimiento("4"))
        assertEquals("04 / 28", TarjetaValidacion.formatearVencimiento(TarjetaValidacion.limpiarVencimiento("0428")))
        assertEquals(ErrorVencimiento.MES_INVALIDO, TarjetaValidacion.errorVencimiento("13", hoy))
        assertEquals(ErrorVencimiento.MES_INVALIDO, TarjetaValidacion.errorVencimiento("0028", hoy))
        assertEquals(ErrorVencimiento.INCOMPLETO, TarjetaValidacion.errorVencimiento("12", hoy))
        assertEquals(ErrorVencimiento.VENCIDA, TarjetaValidacion.errorVencimiento("1026", hoy))
        assertNull(TarjetaValidacion.errorVencimiento("1126", hoy))
        assertNull(TarjetaValidacion.errorVencimiento("0130", hoy))
        assertEquals(ErrorVencimiento.ANIO_INVALIDO, TarjetaValidacion.errorVencimiento("0199", hoy))
    }

    @Test
    fun cvv() {
        assertNull(TarjetaValidacion.errorCvv("123", "4242424242424242"))
        assertNull(TarjetaValidacion.errorCvv("1234", "378282246310005"))
        assertEquals(ErrorCvv.INCOMPLETO, TarjetaValidacion.errorCvv("12", "4242424242424242"))
        assertEquals(ErrorCvv.INCOMPLETO, TarjetaValidacion.errorCvv("123", "378282246310005"))
        assertTrue(TarjetaValidacion.luhnValido("4242424242424242"))
        assertFalse(TarjetaValidacion.luhnValido(""))
    }
}
