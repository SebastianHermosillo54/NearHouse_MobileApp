package com.equipo4.nearhome.util

object PasswordValidator {
    private val specialChars = "!@#$%&*?._-".toSet()

    fun hasMinLength(p: String) = p.length >= 8
    fun hasUppercase(p: String) = p.any { it.isUpperCase() }
    fun hasLowercase(p: String) = p.any { it.isLowerCase() }
    fun hasDigit(p: String) = p.any { it.isDigit() }
    fun hasSpecialChar(p: String) = p.any { it in specialChars }

    fun hasNoSequences(p: String): Boolean {
        if (p.length < 3) return true
        for (i in 0..p.length - 3) {
            val c1 = p[i].lowercaseChar()
            val c2 = p[i + 1].lowercaseChar()
            val c3 = p[i + 2].lowercaseChar()

            // Repetidos (aaa, 111)
            if (c1 == c2 && c2 == c3) return false

            // Secuencia (abc, 123, cba, 321)
            val diff1 = c2.code - c1.code
            val diff2 = c3.code - c2.code
            if ((diff1 == 1 && diff2 == 1) || (diff1 == -1 && diff2 == -1)) return false
        }
        return true
    }

    fun isValid(p: String): Boolean {
        return hasMinLength(p) && hasUppercase(p) && hasLowercase(p) &&
                hasDigit(p) && hasSpecialChar(p) && hasNoSequences(p)
    }
}