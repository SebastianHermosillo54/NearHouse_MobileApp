package com.equipo4.nearhome.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Rojo de las acciones que borran o descartan algo (igual en tema claro y oscuro). */
val DialogDangerColor = Color(0xFFDC2626)

/**
 * Acomoda los botones de un diálogo con el mismo ancho y el texto centrado:
 * el de cancelar a la izquierda y el principal a la derecha.
 * Si el texto de alguno no cabe en la mitad del ancho, los pone uno debajo del otro
 * a todo lo ancho, con el principal arriba, para que el texto nunca se corte.
 * Con un solo botón, ocupa todo el ancho.
 */
@Composable
fun DialogButtonsLayout(
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    spacing: Dp = 12.dp
) {
    Layout(
        content = {
            // propagateMinConstraints hace que el botón llene el ancho que le toca
            Box(propagateMinConstraints = true) { confirmButton() }
            if (dismissButton != null) {
                Box(propagateMinConstraints = true) { dismissButton() }
            }
        },
        modifier = modifier.fillMaxWidth()
    ) { measurables, constraints ->
        val confirmar = measurables[0]
        val cancelar = measurables.getOrNull(1)
        val espacio = spacing.roundToPx()
        val ancho = if (constraints.hasBoundedWidth) {
            constraints.maxWidth
        } else {
            measurables.sumOf { it.maxIntrinsicWidth(Constraints.Infinity) } + espacio
        }

        if (cancelar == null) {
            val principal = confirmar.measure(Constraints.fixedWidth(ancho))
            layout(ancho, principal.height) { principal.placeRelative(0, 0) }
        } else {
            val mitad = (ancho - espacio) / 2
            val caben = confirmar.maxIntrinsicWidth(Constraints.Infinity) <= mitad &&
                cancelar.maxIntrinsicWidth(Constraints.Infinity) <= mitad
            if (caben) {
                val secundario = cancelar.measure(Constraints.fixedWidth(mitad))
                val principal = confirmar.measure(Constraints.fixedWidth(mitad))
                val alto = maxOf(secundario.height, principal.height)
                layout(ancho, alto) {
                    secundario.placeRelative(0, (alto - secundario.height) / 2)
                    principal.placeRelative(ancho - mitad, (alto - principal.height) / 2)
                }
            } else {
                val principal = confirmar.measure(Constraints.fixedWidth(ancho))
                val secundario = cancelar.measure(Constraints.fixedWidth(ancho))
                layout(ancho, principal.height + espacio + secundario.height) {
                    principal.placeRelative(0, 0)
                    secundario.placeRelative(0, principal.height + espacio)
                }
            }
        }
    }
}

/**
 * Botones estándar de los diálogos de la app:
 * - Principal: relleno, azul de la app o rojo si la acción borra o descarta algo.
 * - Cancelar: con contorno, para que se distinga en tema claro y oscuro.
 */
@Composable
fun DialogButtons(
    confirmText: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    dismissText: String? = null,
    onDismiss: () -> Unit = {},
    destructive: Boolean = false,
    confirmIcon: ImageVector? = null,
    enabled: Boolean = true
) {
    val cs = MaterialTheme.colorScheme
    val fondo = if (destructive) DialogDangerColor else cs.primary
    val textoPrincipal = if (destructive) Color.White else cs.onPrimary
    val forma = RoundedCornerShape(10.dp)
    val relleno = PaddingValues(horizontal = 16.dp, vertical = 10.dp)

    val botonCancelar: (@Composable () -> Unit)? =
        if (dismissText == null) {
            null
        } else {
            {
                OutlinedButton(
                    onClick = onDismiss,
                    enabled = enabled,
                    modifier = Modifier.heightIn(min = 44.dp),
                    shape = forma,
                    border = BorderStroke(1.dp, cs.outline),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = cs.onSurface),
                    contentPadding = relleno
                ) {
                    Text(dismissText, fontSize = 14.sp, textAlign = TextAlign.Center)
                }
            }
        }

    DialogButtonsLayout(
        modifier = modifier,
        dismissButton = botonCancelar,
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = enabled,
                modifier = Modifier.heightIn(min = 44.dp),
                shape = forma,
                colors = ButtonDefaults.buttonColors(containerColor = fondo, contentColor = textoPrincipal),
                contentPadding = relleno
            ) {
                if (confirmIcon != null) {
                    Icon(confirmIcon, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(confirmText, fontSize = 14.sp, textAlign = TextAlign.Center)
            }
        }
    )
}
