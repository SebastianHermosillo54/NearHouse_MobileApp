package com.equipo4.nearhome.ui.common

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/**
 * Diálogo de confirmación con el estilo de la app.
 * Si [dismissText] es null, solo muestra el botón principal.
 */
@Composable
fun NearHomeDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    dismissText: String? = null,
    onDismiss: () -> Unit = {},
    destructive: Boolean = false
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = message,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                DialogButtons(
                    confirmText = confirmText,
                    onConfirm = onConfirm,
                    dismissText = dismissText,
                    onDismiss = onDismiss,
                    destructive = destructive
                )
            }
        }
    }
}