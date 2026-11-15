package com.equipo4.nearhome.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import android.view.WindowManager

/**
 * Diálogo con el estilo de la app: tarjeta de 24 dp, ícono opcional en círculo,
 * título, texto y botones todo centrado. Mismos slots que AlertDialog.
 */
@Composable
fun NearHomeAlertDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    // Sin ancho fijo de plataforma: el diálogo ocupa el ancho de la pantalla menos los márgenes
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
) {
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        val vista = LocalView.current
        SideEffect {
            (vista.parent as? DialogWindowProvider)?.window
                ?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
        }
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (icon != null) {
                    icon()
                    Spacer(Modifier.height(16.dp))
                }
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { title() }
                Spacer(Modifier.height(12.dp))
                // El texto del cuerpo va centrado, con un tamaño cómodo para leer
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    MaterialTheme(
                        colorScheme = MaterialTheme.colorScheme,
                        shapes = MaterialTheme.shapes,
                        typography = MaterialTheme.typography.centrada()
                    ) { text() }
                }
                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (dismissButton != null) dismissButton()
                    confirmButton()
                }
            }
        }
    }
}

private fun Typography.centrada(): Typography = copy(
    bodyLarge = bodyLarge.copy(textAlign = TextAlign.Center, fontSize = 15.sp, lineHeight = 21.sp),
    bodyMedium = bodyMedium.copy(textAlign = TextAlign.Center)
)

/** Título de diálogo: 20 sp, negrita y centrado. */
@Composable
fun DialogTitle(
    text: String,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight = FontWeight.Bold
) {
    Text(
        text = text,
        fontSize = 20.sp,
        fontWeight = fontWeight,
        textAlign = TextAlign.Center,
        color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color
    )
}

/** Ícono dentro de un círculo suave, arriba del título. */
@Composable
fun DialogIcon(icon: ImageVector, accent: Color) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .background(accent.copy(alpha = 0.12f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(28.dp)
        )
    }
}