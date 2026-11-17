package com.equipo4.nearhome.ui.subscription

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.equipo4.nearhome.R
import com.equipo4.nearhome.data.session.CurrentUserStore
import com.equipo4.nearhome.ui.notifications.NotificacionesHelper
import com.equipo4.nearhome.ui.profile.UserRole
import com.equipo4.nearhome.util.LogroSonidoEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutScreen(
    planId: Int,
    onBackClick: () -> Unit = {},
    onSuccessFinished: (String) -> Unit = {} // Recibe el nombre del plan
) {
    val context = LocalContext.current
    val plan = remember(planId) {
        SubscriptionPlanCache.find(planId)?.let {
            it.copy(isFreeTrial = it.isFreeTrialFor(CurrentUserStore.user.value?.pruebaDisponible == true))
        }
    }
    if (plan == null) {
        LaunchedEffect(Unit) { onBackClick() }
        return
    }

    val eraComprador = remember { CurrentUserStore.roleOrNull() == UserRole.COMPRADOR }
    val alFinalizar: (String) -> Unit = { planName ->
        if (eraComprador) NotificacionesHelper.mostrarBienvenidaVendedor(context)
        onSuccessFinished(planName)
    }
    val viewModel: CheckoutViewModel = remember(plan) {
        CheckoutViewModel(plan, SubscriptionRepository(context))
    }

    val cardNumber by viewModel.cardNumber.collectAsState()
    val cardHolder by viewModel.cardHolder.collectAsState()
    val expiry by viewModel.expiry.collectAsState()
    val cvv by viewModel.cvv.collectAsState()

    val isComplete by viewModel.isComplete.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val successMessage by viewModel.successMessage.collectAsState()
    val apiError by viewModel.apiError.collectAsState()

    var showErrors by remember { mutableStateOf(false) }

    // ---- PANTALLA DE PAGO ----
    val cardNumberFocusRequester = remember { FocusRequester() }
    val cardHolderFocusRequester = remember { FocusRequester() }
    val expiryFocusRequester = remember { FocusRequester() }
    val cvvFocusRequester = remember { FocusRequester() }

    // Errores de cada campo (ver TarjetaValidacion). Los de "incompleto" solo se muestran
    // cuando el usuario sale del campo o intenta pagar; los demás se muestran al momento.
    val digitosTarjeta = cardNumber.text.filter { it.isDigit() }
    val marca = TarjetaValidacion.marca(digitosTarjeta)
    val errorNumero = TarjetaValidacion.errorNumero(digitosTarjeta)
    val errorTitular = TarjetaValidacion.errorTitular(cardHolder.text)
    val errorVencimiento = TarjetaValidacion.errorVencimiento(expiry.text.filter { it.isDigit() })
    val errorCvv = TarjetaValidacion.errorCvv(cvv.text, digitosTarjeta)
    val largoCvv = TarjetaValidacion.largoCvv(digitosTarjeta)

    var tocadoNumero by remember { mutableStateOf(false) }
    var tocadoTitular by remember { mutableStateOf(false) }
    var tocadoVencimiento by remember { mutableStateOf(false) }
    var tocadoCvv by remember { mutableStateOf(false) }

    val mensajeNumero = when (errorNumero) {
        ErrorNumeroTarjeta.MARCA_NO_ACEPTADA -> stringResource(R.string.checkout_card_brand)
        ErrorNumeroTarjeta.NUMERO_INVALIDO -> stringResource(R.string.checkout_card_luhn)
        ErrorNumeroTarjeta.INCOMPLETO ->
            if (showErrors || tocadoNumero) stringResource(R.string.checkout_card_incomplete, marca?.longitud ?: 16) else null
        null -> null
    }
    val mensajeTitular = when (errorTitular) {
        ErrorTitular.VACIO -> if (showErrors || tocadoTitular) stringResource(R.string.checkout_holder_empty) else null
        ErrorTitular.INCOMPLETO -> if (showErrors || tocadoTitular) stringResource(R.string.checkout_holder_incomplete) else null
        null -> null
    }
    val mensajeVencimiento = when (errorVencimiento) {
        ErrorVencimiento.MES_INVALIDO -> stringResource(R.string.checkout_expiry_month)
        ErrorVencimiento.VENCIDA -> stringResource(R.string.checkout_expiry_past)
        ErrorVencimiento.ANIO_INVALIDO -> stringResource(R.string.checkout_expiry_year)
        ErrorVencimiento.INCOMPLETO ->
            if (showErrors || tocadoVencimiento) stringResource(R.string.checkout_expiry_incomplete) else null
        null -> null
    }
    val mensajeCvv = when (errorCvv) {
        ErrorCvv.INCOMPLETO -> if (showErrors || tocadoCvv) stringResource(R.string.checkout_cvv_incomplete, largoCvv) else null
        null -> null
    }

    LaunchedEffect(digitosTarjeta) {
        if (digitosTarjeta.isNotEmpty() && errorNumero == null) {
            cardHolderFocusRequester.requestFocus()
        }
    }
    LaunchedEffect(expiry.text) {
        if (expiry.text.filter { it.isDigit() }.length == 4 && errorVencimiento == null) {
            cvvFocusRequester.requestFocus()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (plan.isFreeTrial) stringResource(R.string.free_trial_title) else stringResource(R.string.confirm_payment_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = SubscriptionColors.textPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                            tint = SubscriptionColors.textPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SubscriptionColors.surface)
            )
        },
        containerColor = SubscriptionColors.screenBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
                .imePadding()
                .navigationBarsPadding()
        ) {
            // Banner de prueba gratuita
            if (plan.isFreeTrial) {
                Surface(
                    color = SubscriptionColors.successBannerBg,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF16A34A))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Redeem,
                            contentDescription = null,
                            tint = SubscriptionColors.successIcon,
                            modifier = Modifier.size(32.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = stringResource(R.string.free_trial_banner_title),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = SubscriptionColors.textPrimary,
                                lineHeight = 16.sp
                            )
                            Text(
                                text = stringResource(R.string.free_trial_banner),
                                fontSize = 12.sp,
                                color = SubscriptionColors.textBody,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // Resumen del plan
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.checkout_plan_format, plan.name),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = SubscriptionColors.brand
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${plan.description} · ${plan.visibility}",
                        fontSize = 13.sp,
                        color = SubscriptionColors.textBody
                    )
                }
                Spacer(Modifier.width(8.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = plan.priceText,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = SubscriptionColors.brand,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = stringResource(R.string.price_per_month),
                        fontSize = 14.sp,
                        color = SubscriptionColors.textMuted,
                        modifier = Modifier.padding(bottom = 2.dp),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.payment_method_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = SubscriptionColors.textPrimary,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(Modifier.height(12.dp))

            // Número de tarjeta
            Column {
                Text(stringResource(R.string.card_number_label), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = SubscriptionColors.textLabel)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = cardNumber,
                    onValueChange = viewModel::onCardNumberChanged,
                    placeholder = { Text(stringResource(R.string.checkout_card_number_hint)) },
                    trailingIcon = marca?.let { m ->
                        {
                            Text(
                                text = m.nombre,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SubscriptionColors.textMuted,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { cardHolderFocusRequester.requestFocus() }),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    isError = mensajeNumero != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(cardNumberFocusRequester)
                        .onFocusChanged { if (!it.isFocused && cardNumber.text.isNotEmpty()) tocadoNumero = true }
                )
                MensajeErrorCampo(mensajeNumero)
            }

            Spacer(Modifier.height(12.dp))

            // Nombre del titular
            Column {
                Text(stringResource(R.string.card_holder_label), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = SubscriptionColors.textLabel)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = cardHolder,
                    onValueChange = viewModel::onCardHolderChanged,
                    placeholder = { Text(stringResource(R.string.checkout_cardholder_hint)) },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(onNext = { expiryFocusRequester.requestFocus() }),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    isError = mensajeTitular != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(cardHolderFocusRequester)
                        .onFocusChanged { if (!it.isFocused && cardHolder.text.isNotEmpty()) tocadoTitular = true }
                )
                MensajeErrorCampo(mensajeTitular)
            }

            Spacer(Modifier.height(12.dp))

            // Vencimiento y CVV
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.card_expiry_label), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = SubscriptionColors.textLabel)
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = expiry,
                        onValueChange = viewModel::onExpiryChanged,
                        placeholder = { Text(stringResource(R.string.checkout_expiry_hint)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { cvvFocusRequester.requestFocus() }),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        isError = mensajeVencimiento != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(expiryFocusRequester)
                            .onFocusChanged { if (!it.isFocused && expiry.text.isNotEmpty()) tocadoVencimiento = true }
                    )
                    MensajeErrorCampo(mensajeVencimiento)
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.card_cvv_label), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = SubscriptionColors.textLabel)
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = cvv,
                        onValueChange = viewModel::onCvvChanged,
                        placeholder = { Text(if (largoCvv == 4) "1234" else stringResource(R.string.checkout_cvv_hint)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardActions = KeyboardActions(onDone = {
                            if (isComplete) {
                                viewModel.submit { planName -> alFinalizar(planName) }
                            } else {
                                showErrors = true
                            }
                        }),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        isError = mensajeCvv != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(cvvFocusRequester)
                            .onFocusChanged { if (!it.isFocused && cvv.text.isNotEmpty()) tocadoCvv = true }
                    )
                    MensajeErrorCampo(mensajeCvv)
                }
            }

            Spacer(Modifier.height(36.dp))

            // Botón principal
            Button(
                onClick = {
                    if (isComplete) {
                        // Llamada corregida
                        viewModel.submit { planName -> alFinalizar(planName) }
                    } else {
                        showErrors = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SubscriptionColors.brand,
                    disabledContainerColor = SubscriptionColors.brand.copy(alpha = 0.5f)
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Text(
                        text = if (plan.isFreeTrial) stringResource(R.string.activate_free_trial_btn) else stringResource(R.string.confirm_and_pay_btn),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFFFFF)
                    )
                }
            }

            if (showErrors && !isComplete) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.checkout_required_fields),
                    fontSize = 12.sp,
                    color = SubscriptionColors.errorStrong,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            // Error de la API
            apiError?.let { err ->
                Spacer(Modifier.height(10.dp))
                val errorText = when (err) {
                    "NETWORK_ERROR" -> stringResource(R.string.network_error_connection)
                    "UNEXPECTED_ERROR" -> stringResource(R.string.error_unexpected_retry)
                    else -> err
                }
                Text(errorText, fontSize = 13.sp, color = SubscriptionColors.errorStrong)
            }

            Spacer(Modifier.height(16.dp))

            // Texto debajo del botón
            Text(
                text = if (plan.isFreeTrial) {
                    stringResource(R.string.free_trial_footer)
                } else {
                    stringResource(R.string.confirm_payment_footer)
                },
                fontSize = 12.sp,
                color = SubscriptionColors.textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            )

            Spacer(Modifier.height(30.dp))
        }
    }
}

/** Mensaje de error debajo de un campo de la tarjeta (no ocupa espacio si no hay error). */
@Composable
private fun MensajeErrorCampo(mensaje: String?) {
    if (mensaje == null) return
    Spacer(Modifier.height(2.dp))
    Text(
        text = mensaje,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        color = SubscriptionColors.errorStrong,
        modifier = Modifier.padding(start = 4.dp)
    )
}
