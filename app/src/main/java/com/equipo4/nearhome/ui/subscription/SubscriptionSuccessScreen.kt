package com.equipo4.nearhome.ui.subscription

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.equipo4.nearhome.util.LogroSonidoEffect

// Mismos colores que "Teléfono verificado"
private val SuccessGreenCircle = Color(0xFF52C41A)
private val SuccessBgLight = Color(0xFFEFF7EB)
private val SuccessBgDark = Color(0xFF14261A)

@Composable
fun SubscriptionSuccessScreen(
    planName: String,
    onContinue: () -> Unit
) {
    LogroSonidoEffect()
    // Atrás no regresa a la pantalla de pago: va a Mi perfil, igual que el botón
    BackHandler { onContinue() }

    val cs = MaterialTheme.colorScheme
    // El tema se puede cambiar dentro de la app, por eso se revisa el fondo y no el sistema
    val esOscuro = cs.background.luminance() < 0.5f

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = if (esOscuro) SuccessBgDark else SuccessBgLight
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .background(color = SuccessGreenCircle, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(90.dp)
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            Text(
                text = "¡Plan actualizado!",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = cs.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = planName,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = cs.primary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Continuar", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}