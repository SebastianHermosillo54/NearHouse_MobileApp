package com.equipo4.nearhome.ui.admin.users_crud.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.equipo4.nearhome.R
import com.equipo4.nearhome.domain.model.UserAccountStatus
import com.equipo4.nearhome.ui.common.DialogDangerColor

/**
 * Barra inferior del detalle de usuario: dos botones del mismo tamaño y forma.
 * Bloquear (o Quitar suspensión) a la izquierda y Eliminar cuenta en rojo a la derecha,
 * con el mismo rojo que los diálogos de eliminar.
 */
@Composable
fun UserActionsBar(
    status: UserAccountStatus,
    onBlock: () -> Unit,
    onUnsuspend: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val tamanoBoton = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)

            if (status == UserAccountStatus.SUSPENDIDO) {
                Button(
                    onClick = onUnsuspend,
                    modifier = tamanoBoton,
                    shape = FormaBoton,
                    contentPadding = RellenoBoton
                ) {
                    TextoBoton(stringResource(R.string.admin_user_action_unsuspend))
                }
            } else {
                OutlinedButton(
                    onClick = onBlock,
                    modifier = tamanoBoton,
                    shape = FormaBoton,
                    contentPadding = RellenoBoton,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                ) {
                    TextoBoton(stringResource(R.string.admin_user_action_block))
                }
            }

            OutlinedButton(
                onClick = onDelete,
                modifier = tamanoBoton,
                shape = FormaBoton,
                contentPadding = RellenoBoton,
                border = BorderStroke(1.dp, DialogDangerColor),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = DialogDangerColor)
            ) {
                TextoBoton(stringResource(R.string.admin_user_action_delete))
            }
        }
    }
}

private val FormaBoton = RoundedCornerShape(12.dp)

// Menos relleno a los lados que el normal (24 dp), para que "Bloquear / Suspender" quepa completo
private val RellenoBoton = PaddingValues(horizontal = 8.dp, vertical = 10.dp)

@Composable
private fun TextoBoton(texto: String) {
    Text(
        text = texto,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        maxLines = 2
    )
}
