package com.equipo4.nearhome.ui.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.equipo4.nearhome.R
import com.equipo4.nearhome.util.PasswordValidator

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

    Column(modifier = modifier) {
        reglas.forEach { (texto, cumple) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 2.dp)
            ) {
                Icon(
                    imageVector = if (cumple) Icons.Default.Check else Icons.Default.Close,
                    contentDescription = null,
                    tint = if (cumple) androidx.compose.ui.graphics.Color(0xFF16A34A)
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = texto,
                    fontSize = 12.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                    color = if (cumple) androidx.compose.ui.graphics.Color(0xFF16A34A)
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}