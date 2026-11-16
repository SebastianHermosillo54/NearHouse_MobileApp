package com.equipo4.nearhome.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.equipo4.nearhome.R
import com.equipo4.nearhome.util.PasswordValidator

/**
 * Requisitos de la contraseña: tarjeta con contador, barra de progreso y una fila por regla.
 * Cada regla se marca en verde cuando se cumple.
 */
@Composable
fun PasswordRulesList(password: String, modifier: Modifier = Modifier) {
    val reglas = listOf(
        stringResource(R.string.pwd_rule_min_length) to PasswordValidator.hasMinLength(password),
        stringResource(R.string.pwd_rule_uppercase) to PasswordValidator.hasUppercase(password),
        stringResource(R.string.pwd_rule_lowercase) to PasswordValidator.hasLowercase(password),
        stringResource(R.string.pwd_rule_digit) to PasswordValidator.hasDigit(password),
        stringResource(R.string.pwd_rule_special) to PasswordValidator.hasSpecialChar(password),
        stringResource(R.string.pwd_rule_no_sequences) to PasswordValidator.hasNoSequences(password)
    )
    val cumplidas = reglas.count { it.second }
    val total = reglas.size
    val todas = cumplidas == total
    val verde = Color(0xFF16A34A)
    val colorAvance = if (todas) verde else MaterialTheme.colorScheme.primary

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.pwd_rules_title),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "$cumplidas/$total",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (todas) verde else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(8.dp))

            // Barra de progreso (Box, para no depender de la versión de Material 3)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(cumplidas / total.toFloat())
                        .fillMaxHeight()
                        .background(colorAvance)
                )
            }

            Spacer(Modifier.height(10.dp))

            reglas.forEach { (texto, cumple) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .background(
                                color = if (cumple) verde else MaterialTheme.colorScheme.outlineVariant,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (cumple) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = texto,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Start,
                        color = if (cumple) verde else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}