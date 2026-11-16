package com.equipo4.nearhome.ui.profile.account

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.equipo4.nearhome.ui.subscription.PlanDates
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.equipo4.nearhome.R
import com.equipo4.nearhome.ui.common.DialogIcon
import com.equipo4.nearhome.ui.common.DialogTitle
import com.equipo4.nearhome.ui.common.NearHomeAlertDialog
import com.equipo4.nearhome.data.remote.GoogleAuthManager
import com.equipo4.nearhome.data.remote.TelefonoFirebase
import com.equipo4.nearhome.ui.common.NearHomeBottomBar
import com.equipo4.nearhome.ui.common.PasswordRulesList
import com.equipo4.nearhome.ui.profile.UserRole
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun AccountAdminRoute(
    viewModel: AccountAdminViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToPlans: () -> Unit,
    onNavigateToLogin: (String) -> Unit = {},
    onBottomTabSelected: (Int) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(uiState.isDeactivatedSuccess) {
        if (uiState.isDeactivatedSuccess) {
            val msg = context.getString(R.string.acc_deactivated_msg)
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            onNavigateToLogin(msg)
        }
    }

    // NUEVO: Navegar al login cuando se elimina la cuenta
    LaunchedEffect(uiState.isDeletedSuccess) {
        if (uiState.isDeletedSuccess) {
            onNavigateToLogin(context.getString(R.string.acc_delete_in_30_days))
        }
    }

    LaunchedEffect(uiState.snackbarSuccessMessage) {
        uiState.snackbarSuccessMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            viewModel.onEvent(AccountAdminUiEvent.OnSnackbarMessageConsumed)
        }
    }

    AccountAdminScreen(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onNavigateBack = onNavigateBack,
        onNavigateToPlans = onNavigateToPlans,
        onBottomTabSelected = onBottomTabSelected
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountAdminScreen(
    uiState: AccountAdminUiState,
    onEvent: (AccountAdminUiEvent) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToPlans: () -> Unit,
    onBottomTabSelected: (Int) -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val activity = context.findActivity()
    val coroutineScope = rememberCoroutineScope()
    val googleAuthManager = remember { GoogleAuthManager(context) }

    var isCurrentPasswordVisible by remember { mutableStateOf(false) }
    var isNewPasswordVisible by remember { mutableStateOf(false) }
    var isConfirmPasswordVisible by remember { mutableStateOf(false) }
    var isEmailCurrentPasswordVisible by remember { mutableStateOf(false) }
    var isPhoneCurrentPasswordVisible by remember { mutableStateOf(false) }
    var isDeletePasswordVisible by remember { mutableStateOf(false) }

    // Variables locales para el flujo de SMS de cambio de teléfono
    var smsVerificationId by remember { mutableStateOf<String?>(null) }
    var smsResendToken by remember { mutableStateOf<PhoneAuthProvider.ForceResendingToken?>(null) }
    var smsBloqueado by remember { mutableStateOf(false) }
    var isSendingSms by remember { mutableStateOf(false) }
    var smsLocalError by remember { mutableStateOf<String?>(null) }
    var phoneOtpCode by remember { mutableStateOf(List(6) { "" }) }

    // Cuenta regresiva para poder reenviar el SMS (60 s)
    var smsSegundos by remember { mutableStateOf(0) }
    LaunchedEffect(smsSegundos) {
        if (smsSegundos > 0) {
            delay(1000L)
            smsSegundos -= 1
        }
    }

    // Envía el SMS al teléfono nuevo, o lo reenvía con el token anterior
    fun enviarSmsCuenta(telefono: String, tokenPrevio: PhoneAuthProvider.ForceResendingToken?) {
        val act = activity ?: return
        isSendingSms = true
        smsLocalError = null
        TelefonoFirebase.enviarCodigo(
            activity = act,
            telefono = telefono,
            tokenReenvio = tokenPrevio,
            eventos = object : TelefonoFirebase.Eventos {
                override fun codigoEnviado(verificationId: String, tokenReenvio: PhoneAuthProvider.ForceResendingToken) {
                    isSendingSms = false
                    smsVerificationId = verificationId
                    smsResendToken = tokenReenvio
                    smsSegundos = TelefonoFirebase.ESPERA_SEGUNDOS
                }

                override fun verificadoAutomaticamente(credencial: PhoneAuthCredential) {
                    coroutineScope.launch {
                        try {
                            val tokenFirebase = TelefonoFirebase.obtenerToken(credencial)
                            onEvent(AccountAdminUiEvent.OnConfirmPhoneSmsSubmitted(tokenFirebase))
                        } catch (e: Exception) {
                            smsLocalError = e.message
                        }
                    }
                }

                override fun error(mensaje: String) {
                    isSendingSms = false
                    smsLocalError = mensaje
                }

                override fun bloqueado(mensaje: String) {
                    // Firebase bloqueó los envíos por un tiempo: ya no se ofrece reenviar
                    isSendingSms = false
                    smsBloqueado = true
                    smsLocalError = mensaje
                }
            }
        )
    }

    uiState.userMessage?.let { messageRes ->
        val message = stringResource(id = messageRes)
        LaunchedEffect(messageRes) {
            snackbarHostState.showSnackbar(message)
            onEvent(AccountAdminUiEvent.OnUserMessageConsumed)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.account_admin_title),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                }
            )
        },
        bottomBar = {
            NearHomeBottomBar(
                selectedTab = 3,
                onTabSelected = onBottomTabSelected
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        if (uiState.isLoading || uiState.userData == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            val userData = uiState.userData

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // ==================== DATOS DE ACCESO ====================
                SectionHeader(title = stringResource(id = R.string.section_access_data))

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column {
                        OptionRowItem(
                            icon = Icons.Outlined.Email,
                            title = stringResource(id = R.string.label_email),
                            subtitle = userData.email,
                            useIconContainer = true,
                            iconBackgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                            iconTintColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = { onEvent(AccountAdminUiEvent.OnEmailOptionClick) }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 72.dp, end = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            thickness = 0.8.dp
                        )

                        OptionRowItem(
                            icon = Icons.Outlined.Smartphone,
                            title = stringResource(id = R.string.label_phone),
                            subtitle = userData.phone,
                            useIconContainer = true,
                            iconBackgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                            iconTintColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = { onEvent(AccountAdminUiEvent.OnPhoneOptionClick) }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 72.dp, end = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            thickness = 0.8.dp
                        )

                        OptionRowItem(
                            icon = Icons.Outlined.Lock,
                            title = stringResource(id = R.string.label_password),
                            subtitle = if (userData.tieneContrasena) {
                                stringResource(id = R.string.masked_password)
                            } else {
                                stringResource(R.string.acc_password_google_subtitle)
                            },
                            useIconContainer = true,
                            iconBackgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                            iconTintColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = { onEvent(AccountAdminUiEvent.OnPasswordOptionClick) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ==================== MEMBRESÍA Y PAGOS ====================
                SectionHeader(title = stringResource(id = R.string.section_membership))

                // Textos del plan: se arman aquí para que sigan el idioma elegido
                val locale = LocalConfiguration.current.locales[0]
                val planName = userData.planNombre?.let { stringResource(R.string.plan_full_name_format, it) }
                val planStatus = when {
                    userData.planNombre == null -> null
                    !userData.planVigente -> stringResource(R.string.plan_status_expired)
                    userData.planCancelado -> stringResource(R.string.plan_status_cancelled)
                    else -> stringResource(R.string.plan_status_active)
                }
                val fechaPlan = userData.planVence?.let { PlanDates.formatear(it, locale) }.orEmpty()
                val planExpired = userData.planNombre != null && !userData.planVigente

                Column {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        OptionRowItem(
                            icon = Icons.Outlined.PublishedWithChanges,
                            title = stringResource(id = R.string.label_current_plan),
                            subtitle = if (planName != null) {
                                if (planStatus != null) {
                                    stringResource(R.string.seller_plan_status_format, planName, planStatus)
                                } else {
                                    planName
                                }
                            } else {
                                stringResource(id = R.string.buyer_no_plan)
                            },
                            secondLine = when {
                                planName == null -> stringResource(id = R.string.buyer_plan_subtitle)
                                planExpired -> stringResource(id = R.string.plan_expired_format, fechaPlan)
                                // Cancelado: ya no se renueva; sus anuncios se ven hasta esa fecha
                                userData.planCancelado -> stringResource(id = R.string.plan_cancelled_until_format, fechaPlan)
                                else -> stringResource(id = R.string.plan_expires_format, fechaPlan)
                            },
                            secondLineColor = if (planExpired) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            useIconContainer = true,
                            iconBackgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                            iconTintColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = onNavigateToPlans
                        )
                    }

                    // Cancelar suscripción: solo vendedores con plan vigente que todavía no lo cancelan
                    if (userData.role == UserRole.VENDEDOR && userData.planVigente && !userData.planCancelado) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            OptionRowItem(
                                icon = Icons.Outlined.Cancel,
                                title = stringResource(R.string.acc_cancel_plan_title),
                                subtitle = stringResource(R.string.acc_cancel_plan_subtitle, fechaPlan),
                                titleColor = MaterialTheme.colorScheme.error,
                                useIconContainer = true,
                                iconBackgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                                iconTintColor = MaterialTheme.colorScheme.error,
                                onClick = { onEvent(AccountAdminUiEvent.OnCancelPlanRequested) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ==================== GESTIÓN DE LA CUENTA ====================
                if (userData.role != UserRole.SUPER_ADMIN) {
                    SectionHeader(title = stringResource(id = R.string.section_account_management))

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Column {
                            OptionRowItem(
                                icon = Icons.Outlined.PowerSettingsNew,
                                title = stringResource(id = R.string.deactivate_account_title),
                                subtitle = stringResource(id = R.string.deactivate_account_subtitle),
                                useIconContainer = true,
                                iconBackgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                                iconTintColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                onClick = { onEvent(AccountAdminUiEvent.OnDeactivateRequested) }
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 72.dp, end = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                thickness = 0.8.dp
                            )
                            OptionRowItem(
                                icon = Icons.Outlined.Delete,
                                title = stringResource(id = R.string.delete_account_title),
                                subtitle = stringResource(id = R.string.delete_account_subtitle),
                                titleColor = MaterialTheme.colorScheme.error,
                                useIconContainer = true,
                                iconBackgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                                iconTintColor = MaterialTheme.colorScheme.error,
                                onClick = { onEvent(AccountAdminUiEvent.OnDeleteRequested) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // ==================== FLUJO DE CAMBIO DE TELÉFONO ====================

    // PASO 1: CAMBIAR TELÉFONO (INGRESO DE NÚMERO Y CONTRASEÑA / GOOGLE)
    if (uiState.phoneStep == PhoneFlowStep.NEW_PHONE) {
        val hasPass = uiState.userData?.tieneContrasena == true

        NearHomeAlertDialog(
            onDismissRequest = {
                if (!uiState.isPhoneValidating) onEvent(AccountAdminUiEvent.OnPhoneFlowDismissed)
            },
            title = { DialogTitle(stringResource(R.string.acc_change_phone_title)) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.acc_current_phone_format, uiState.userData?.phone.orEmpty()),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            textStyle = androidx.compose.ui.text.TextStyle(
                                textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                                fontSize = 16.sp
                            ),
                            value = uiState.countryCodeInput,
                            onValueChange = { onEvent(AccountAdminUiEvent.OnCountryCodeChanged(it)) },
                            label = { Text(stringResource(R.string.acc_country_label)) },
                            singleLine = true,
                            enabled = !uiState.isPhoneValidating,
                            modifier = Modifier.width(80.dp)
                        )

                        OutlinedTextField(
                            textStyle = androidx.compose.ui.text.TextStyle(
                                textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                                fontSize = 16.sp
                            ),
                            value = uiState.newPhoneInput,
                            onValueChange = { onEvent(AccountAdminUiEvent.OnNewPhoneChanged(it)) },
                            label = { Text(stringResource(R.string.acc_new_phone_label)) },
                            singleLine = true,
                            enabled = !uiState.isPhoneValidating,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = if (hasPass) ImeAction.Next else ImeAction.Done),
                            isError = uiState.newPhoneError != null,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (uiState.newPhoneError != null) {
                        Text(
                            text = uiState.newPhoneError!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp
                        )
                    }

                    if (hasPass) {
                        OutlinedTextField(
                            textStyle = androidx.compose.ui.text.TextStyle(
                                textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                                fontSize = 16.sp
                            ),
                            value = uiState.phoneCurrentPasswordInput,
                            onValueChange = { onEvent(AccountAdminUiEvent.OnPhoneCurrentPasswordChanged(it)) },
                            label = { Text(stringResource(R.string.acc_current_password)) },
                            singleLine = true,
                            enabled = !uiState.isPhoneValidating,
                            visualTransformation = if (isPhoneCurrentPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { isPhoneCurrentPasswordVisible = !isPhoneCurrentPasswordVisible }) {
                                    Icon(
                                        imageVector = if (isPhoneCurrentPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = stringResource(R.string.acc_toggle_password_cd)
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                            isError = uiState.phoneCurrentPasswordError != null,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (uiState.phoneCurrentPasswordError != null) {
                            Text(
                                text = uiState.phoneCurrentPasswordError!!,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp
                            )
                        }
                    }

                    if (uiState.phoneGeneralError != null) {
                        Text(
                            text = uiState.phoneGeneralError!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val handleValidateAndSendSms = { googleToken: String? ->
                            onEvent(AccountAdminUiEvent.OnValidatePhoneSubmitted(
                                googleToken = googleToken,
                                onSuccessValidated = { targetPhone ->
                                    enviarSmsCuenta(targetPhone, null)
                                }
                            ))
                        }

                        if (hasPass) {
                            handleValidateAndSendSms(null)
                        } else {
                            coroutineScope.launch {
                                val result = googleAuthManager.getFirebaseIdToken()
                                result.onSuccess { token ->
                                    if (token != null) {
                                        handleValidateAndSendSms(token)
                                    }
                                }.onFailure { exception ->
                                    Toast.makeText(context, exception.message ?: context.getString(R.string.acc_google_verify_error), Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    },
                    enabled = uiState.isNewPhoneFormValid && !uiState.isPhoneValidating && !isSendingSms
                ) {
                    if (uiState.isPhoneValidating || isSendingSms) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(if (hasPass) stringResource(R.string.create_listing_continue) else stringResource(R.string.acc_google_confirm_continue))
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { onEvent(AccountAdminUiEvent.OnPhoneFlowDismissed) },
                    enabled = !uiState.isPhoneValidating && !isSendingSms
                ) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // PASO 2: VERIFICACIÓN POR SMS DE TELÉFONO
    if (uiState.phoneStep == PhoneFlowStep.SMS_VERIFICATION) {
        val targetPhone = uiState.countryCodeInput + uiState.newPhoneInput.filter { it.isDigit() }
        val fullCode = phoneOtpCode.joinToString("")
        val isConfirmEnabled = fullCode.length == 6 && !uiState.isPhoneSaving
        val focusRequesters = remember { List(6) { FocusRequester() } }

        NearHomeAlertDialog(
            onDismissRequest = {
                if (!uiState.isPhoneSaving) onEvent(AccountAdminUiEvent.OnPhoneFlowDismissed)
            },
            title = { DialogTitle(stringResource(R.string.acc_sms_title)) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.acc_sms_body_format, targetPhone),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        for (i in 0 until 6) {
                            OutlinedTextField(
                                value = phoneOtpCode.getOrElse(i) { "" },
                                onValueChange = { digit ->
                                    val cleanDigit = digit.filter { it.isDigit() }.takeLast(1)
                                    val mutable = phoneOtpCode.toMutableList()
                                    mutable[i] = cleanDigit
                                    phoneOtpCode = mutable
                                    smsLocalError = null
                                    if (cleanDigit.isNotEmpty() && i < 5) {
                                        focusRequesters[i + 1].requestFocus()
                                    }
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(54.dp)
                                    .focusRequester(focusRequesters[i])
                            )
                        }
                    }

                    val activeError = uiState.phoneSaveError ?: smsLocalError
                    if (activeError != null) {
                        Text(
                            text = activeError,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                    TextButton(
                        enabled = smsSegundos == 0 && !smsBloqueado && !isSendingSms && !uiState.isPhoneSaving,
                        onClick = { enviarSmsCuenta(targetPhone, smsResendToken) }
                    ) {
                        Text(
                            text = when {
                                smsBloqueado -> stringResource(R.string.phone_verify_resend_blocked)
                                smsSegundos > 0 -> stringResource(R.string.acc_resend_code_in_format, smsSegundos)
                                else -> stringResource(R.string.signup_resend_code)
                            }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (uiState.isPhoneSaving) return@Button
                        val vid = smsVerificationId
                        if (vid == null) {
                            smsLocalError = "Todavía no llega el código. Espera un momento o pide uno nuevo."
                        } else {
                            coroutineScope.launch {
                                try {
                                    val credencial = TelefonoFirebase.credencialConCodigo(vid, fullCode)
                                    val tokenFirebase = TelefonoFirebase.obtenerToken(credencial)
                                    onEvent(AccountAdminUiEvent.OnConfirmPhoneSmsSubmitted(tokenFirebase))
                                } catch (e: Exception) {
                                    smsLocalError = e.message ?: context.getString(R.string.acc_sms_code_wrong)
                                }
                            }
                        }
                    },
                    enabled = isConfirmEnabled && !uiState.isPhoneSaving
                ) {
                    if (uiState.isPhoneSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(stringResource(R.string.common_verify))
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { onEvent(AccountAdminUiEvent.OnPhoneFlowDismissed) },
                    enabled = !uiState.isPhoneSaving
                ) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // ==================== FLUJO DE CAMBIO DE CORREO ====================

    // PASO 1: "CAMBIAR CORREO"
    if (uiState.emailStep == EmailFlowStep.NEW_EMAIL) {
        val hasPass = uiState.userData?.tieneContrasena == true

        NearHomeAlertDialog(
            onDismissRequest = {
                if (!uiState.isEmailCodeSending) onEvent(AccountAdminUiEvent.OnEmailFlowDismissed)
            },
            title = { DialogTitle(stringResource(R.string.acc_change_email_title)) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.acc_current_email_format, uiState.userData?.email.orEmpty()),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        textStyle = androidx.compose.ui.text.TextStyle(
                            textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                            fontSize = 16.sp
                        ),
                        value = uiState.newEmailInput,
                        onValueChange = { onEvent(AccountAdminUiEvent.OnNewEmailChanged(it)) },
                        label = { Text(stringResource(R.string.acc_new_email_label)) },
                        singleLine = true,
                        enabled = !uiState.isEmailCodeSending,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = if (hasPass) ImeAction.Next else ImeAction.Done),
                        isError = uiState.newEmailError != null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (uiState.newEmailError != null) {
                        Text(
                            text = uiState.newEmailError!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp
                        )
                    }

                    if (hasPass) {
                        OutlinedTextField(
                            textStyle = androidx.compose.ui.text.TextStyle(
                                textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                                fontSize = 16.sp
                            ),
                            value = uiState.emailCurrentPasswordInput,
                            onValueChange = { onEvent(AccountAdminUiEvent.OnEmailCurrentPasswordChanged(it)) },
                            label = { Text(stringResource(R.string.acc_current_password)) },
                            singleLine = true,
                            enabled = !uiState.isEmailCodeSending,
                            visualTransformation = if (isEmailCurrentPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { isEmailCurrentPasswordVisible = !isEmailCurrentPasswordVisible }) {
                                    Icon(
                                        imageVector = if (isEmailCurrentPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = stringResource(R.string.acc_toggle_password_cd)
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                            isError = uiState.emailCurrentPasswordError != null,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (uiState.emailCurrentPasswordError != null) {
                            Text(
                                text = uiState.emailCurrentPasswordError!!,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp
                            )
                        }
                    }

                    if (uiState.emailGeneralError != null) {
                        Text(
                            text = uiState.emailGeneralError!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (hasPass) {
                            onEvent(AccountAdminUiEvent.OnRequestEmailCodeSubmitted(null))
                        } else {
                            coroutineScope.launch {
                                val result = googleAuthManager.getFirebaseIdToken()
                                result.onSuccess { token ->
                                    if (token != null) {
                                        onEvent(AccountAdminUiEvent.OnRequestEmailCodeSubmitted(token))
                                    }
                                }.onFailure { exception ->
                                    Toast.makeText(context, exception.message ?: context.getString(R.string.acc_google_verify_error), Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    },
                    enabled = uiState.isNewEmailFormValid && !uiState.isEmailCodeSending
                ) {
                    if (uiState.isEmailCodeSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(if (hasPass) stringResource(R.string.common_send_code) else stringResource(R.string.acc_google_confirm_send_code))
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { onEvent(AccountAdminUiEvent.OnEmailFlowDismissed) },
                    enabled = !uiState.isEmailCodeSending
                ) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // PASO 2: "VERIFICA TU NUEVO CORREO"
    if (uiState.emailStep == EmailFlowStep.VERIFY_CODE) {
        val fullCode = uiState.emailOtpCode.joinToString("")
        val isConfirmEnabled = fullCode.length == 6 && !uiState.isEmailConfirming
        val focusRequesters = remember { List(6) { FocusRequester() } }

        NearHomeAlertDialog(
            onDismissRequest = {
                if (!uiState.isEmailConfirming) onEvent(AccountAdminUiEvent.OnEmailFlowDismissed)
            },
            title = { DialogTitle(stringResource(R.string.acc_verify_new_email_title)) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.acc_code_sent_to_format, uiState.newEmailInput.trim()),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        for (i in 0 until 6) {
                            OutlinedTextField(
                                value = uiState.emailOtpCode.getOrElse(i) { "" },
                                onValueChange = { digit ->
                                    val cleanDigit = digit.filter { it.isDigit() }.takeLast(1)
                                    onEvent(AccountAdminUiEvent.OnEmailOtpDigitChanged(i, cleanDigit))
                                    if (cleanDigit.isNotEmpty() && i < 5) {
                                        focusRequesters[i + 1].requestFocus()
                                    }
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(54.dp)
                                    .focusRequester(focusRequesters[i])
                            )
                        }
                    }

                    if (uiState.emailOtpErrorMessage != null) {
                        Text(
                            text = uiState.emailOtpErrorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { onEvent(AccountAdminUiEvent.OnReturnToNewEmailStepRequested) },
                            enabled = !uiState.isEmailConfirming
                        ) {
                            Text(stringResource(R.string.common_change_email))
                        }

                        TextButton(
                            onClick = { onEvent(AccountAdminUiEvent.OnResendEmailCodeRequested) },
                            enabled = uiState.canResendEmailCode && !uiState.isEmailConfirming
                        ) {
                            if (uiState.canResendEmailCode) {
                                Text(stringResource(R.string.signup_resend_code))
                            } else {
                                Text(stringResource(R.string.acc_resend_in_format, uiState.emailResendCountdown))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { onEvent(AccountAdminUiEvent.OnConfirmNewEmailSubmitted) },
                    enabled = isConfirmEnabled
                ) {
                    if (uiState.isEmailConfirming) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(stringResource(R.string.manage_listing_confirm))
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { onEvent(AccountAdminUiEvent.OnEmailFlowDismissed) },
                    enabled = !uiState.isEmailConfirming
                ) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // ==================== DIÁLOGOS DE CONTRASEÑA ====================

    // PASO 1: AVISO AL TOCAR "CONTRASEÑA"
    if (uiState.passwordStep == PasswordFlowStep.CONFIRM_SEND_CODE) {
        val hasPass = uiState.userData?.tieneContrasena == true
        NearHomeAlertDialog(
            onDismissRequest = {
                if (!uiState.isOtpLoading) onEvent(AccountAdminUiEvent.OnPasswordFlowDismissed)
            },
            title = {
                DialogTitle(
                    text = if (hasPass) stringResource(R.string.acc_change_password_question) else stringResource(R.string.acc_create_password_question)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (hasPass) {
                            stringResource(R.string.acc_password_code_info)
                        } else {
                            stringResource(R.string.acc_password_google_info)
                        }
                    )
                    if (uiState.otpErrorMessage != null) {
                        Text(
                            text = uiState.otpErrorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { onEvent(AccountAdminUiEvent.OnRequestCodeConfirmed) },
                    enabled = !uiState.isOtpLoading
                ) {
                    if (uiState.isOtpLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(stringResource(R.string.common_send_code))
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { onEvent(AccountAdminUiEvent.OnPasswordFlowDismissed) },
                    enabled = !uiState.isOtpLoading
                ) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // PASO 2: VERIFICA TU CORREO (CONTRASEÑA)
    if (uiState.passwordStep == PasswordFlowStep.VERIFY_CODE) {
        val fullCode = uiState.otpCode.joinToString("")
        val isVerifyEnabled = fullCode.length == 6 && !uiState.isOtpLoading
        val focusRequesters = remember { List(6) { FocusRequester() } }

        NearHomeAlertDialog(
            onDismissRequest = {
                if (!uiState.isOtpLoading) onEvent(AccountAdminUiEvent.OnPasswordFlowDismissed)
            },
            title = { DialogTitle(stringResource(R.string.acc_verify_email_title)) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.acc_code_sent_to_format, uiState.maskedEmail.ifBlank { uiState.userData?.email.orEmpty() }),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        for (i in 0 until 6) {
                            OutlinedTextField(
                                value = uiState.otpCode.getOrElse(i) { "" },
                                onValueChange = { digit ->
                                    val cleanDigit = digit.filter { it.isDigit() }.takeLast(1)
                                    onEvent(AccountAdminUiEvent.OnOtpDigitChanged(i, cleanDigit))
                                    if (cleanDigit.isNotEmpty() && i < 5) {
                                        focusRequesters[i + 1].requestFocus()
                                    }
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(54.dp)
                                    .focusRequester(focusRequesters[i])
                            )
                        }
                    }

                    if (uiState.otpErrorMessage != null) {
                        Text(
                            text = uiState.otpErrorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }

                    TextButton(
                        onClick = { onEvent(AccountAdminUiEvent.OnResendCodeRequested) },
                        enabled = uiState.canResendCode && !uiState.isOtpLoading
                    ) {
                        if (uiState.canResendCode) {
                            Text(stringResource(R.string.signup_resend_code))
                        } else {
                            Text(stringResource(R.string.acc_resend_code_in_format, uiState.resendCountdown))
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { onEvent(AccountAdminUiEvent.OnVerifyCodeSubmitted) },
                    enabled = isVerifyEnabled
                ) {
                    if (uiState.isOtpLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(stringResource(R.string.common_verify))
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { onEvent(AccountAdminUiEvent.OnPasswordFlowDismissed) },
                    enabled = !uiState.isOtpLoading
                ) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // PASO 3: NUEVA CONTRASEÑA
    if (uiState.passwordStep == PasswordFlowStep.NEW_PASSWORD) {
        val hasPass = uiState.userData?.tieneContrasena == true

        NearHomeAlertDialog(
            onDismissRequest = {
                if (!uiState.isPasswordSaving) onEvent(AccountAdminUiEvent.OnPasswordFlowDismissed)
            },
            title = {
                DialogTitle(
                    text = if (hasPass) stringResource(R.string.acc_new_password) else stringResource(R.string.acc_create_password)
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (hasPass) {
                        OutlinedTextField(
                            textStyle = androidx.compose.ui.text.TextStyle(
                                textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                                fontSize = 16.sp
                            ),
                            value = uiState.currentPasswordInput,
                            onValueChange = { onEvent(AccountAdminUiEvent.OnCurrentPasswordChanged(it)) },
                            label = { Text(stringResource(R.string.acc_current_password)) },
                            singleLine = true,
                            enabled = !uiState.isPasswordSaving,
                            visualTransformation = if (isCurrentPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { isCurrentPasswordVisible = !isCurrentPasswordVisible }) {
                                    Icon(
                                        imageVector = if (isCurrentPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = stringResource(R.string.acc_toggle_password_cd)
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                            isError = uiState.currentPasswordError != null,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (uiState.currentPasswordError != null) {
                            Text(
                                text = uiState.currentPasswordError!!,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp
                            )
                        }
                    }

                    OutlinedTextField(
                        textStyle = androidx.compose.ui.text.TextStyle(
                            textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                            fontSize = 16.sp
                        ),
                        value = uiState.newPasswordInput,
                        onValueChange = { onEvent(AccountAdminUiEvent.OnNewPasswordChanged(it)) },
                        label = { Text(stringResource(R.string.acc_new_password)) },
                        singleLine = true,
                        enabled = !uiState.isPasswordSaving,
                        visualTransformation = if (isNewPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isNewPasswordVisible = !isNewPasswordVisible }) {
                                Icon(
                                    imageVector = if (isNewPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = stringResource(R.string.acc_toggle_password_cd)
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth()
                    )

                    PasswordRulesList(
                        password = uiState.newPasswordInput,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        textStyle = androidx.compose.ui.text.TextStyle(
                            textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                            fontSize = 16.sp
                        ),
                        value = uiState.confirmPasswordInput,
                        onValueChange = { onEvent(AccountAdminUiEvent.OnConfirmPasswordChanged(it)) },
                        label = { Text(stringResource(R.string.acc_confirm_new_password)) },
                        singleLine = true,
                        enabled = !uiState.isPasswordSaving,
                        visualTransformation = if (isConfirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isConfirmPasswordVisible = !isConfirmPasswordVisible }) {
                                Icon(
                                    imageVector = if (isConfirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = stringResource(R.string.acc_toggle_password_cd)
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (uiState.passwordSaveError != null) {
                        Text(
                            text = uiState.passwordSaveError!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 13.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { onEvent(AccountAdminUiEvent.OnSaveNewPasswordSubmitted) },
                    enabled = uiState.isNewPasswordFormValid && !uiState.isPasswordSaving
                ) {
                    if (uiState.isPasswordSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(stringResource(R.string.edit_profile_save))
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { onEvent(AccountAdminUiEvent.OnPasswordFlowDismissed) },
                    enabled = !uiState.isPasswordSaving
                ) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // ==================== DIÁLOGOS DE DESACTIVAR, ELIMINAR Y CANCELAR PLAN ====================
    val hasPassCuenta = uiState.userData?.tieneContrasena == true
    val dangerColor = Color(0xFFDC2626)
    val accentColor = Color(0xFF41688F)
    val textoCentrado = androidx.compose.ui.text.style.TextAlign.Center

    // Desactivar cuenta: con contraseña, o con Google si la cuenta no tiene contraseña
    if (uiState.showDeactivateDialog) {
        NearHomeAlertDialog(
            onDismissRequest = {
                if (!uiState.isDeactivating) onEvent(AccountAdminUiEvent.OnDeactivateDismissed)
            },
            icon = { DialogIcon(Icons.Outlined.PowerSettingsNew, accentColor) },
            title = { DialogTitle(text = stringResource(id = R.string.dialog_deactivate_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(id = R.string.dialog_deactivate_text),
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        textAlign = textoCentrado,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (hasPassCuenta) {
                        OutlinedTextField(
                            textStyle = androidx.compose.ui.text.TextStyle(
                                textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                                fontSize = 16.sp
                            ),
                            value = uiState.deactivatePasswordInput,
                            onValueChange = { onEvent(AccountAdminUiEvent.OnDeactivatePasswordChanged(it)) },
                            label = { Text(stringResource(R.string.acc_current_password)) },
                            singleLine = true,
                            enabled = !uiState.isDeactivating,
                            visualTransformation = if (isCurrentPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { isCurrentPasswordVisible = !isCurrentPasswordVisible }) {
                                    Icon(
                                        imageVector = if (isCurrentPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = stringResource(R.string.acc_toggle_password_cd)
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (uiState.deactivateError != null) {
                        Text(
                            text = uiState.deactivateError,
                            fontSize = 13.sp,
                            textAlign = textoCentrado,
                            color = dangerColor
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    modifier = Modifier.height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = (!hasPassCuenta || uiState.deactivatePasswordInput.isNotBlank()) && !uiState.isDeactivating,
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Color.White),
                    onClick = {
                        if (hasPassCuenta) {
                            onEvent(AccountAdminUiEvent.OnDeactivateConfirmed(null))
                        } else {
                            coroutineScope.launch {
                                googleAuthManager.getFirebaseIdToken()
                                    .onSuccess { token ->
                                        if (token != null) onEvent(AccountAdminUiEvent.OnDeactivateConfirmed(token))
                                    }
                                    .onFailure { e ->
                                        Toast.makeText(context, e.message ?: context.getString(R.string.acc_google_verify_error), Toast.LENGTH_LONG).show()
                                    }
                            }
                        }
                    }
                ) {
                    if (uiState.isDeactivating) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text(
                            text = if (hasPassCuenta) stringResource(id = R.string.confirm_deactivate) else stringResource(R.string.acc_confirm_google),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(
                    modifier = Modifier.height(46.dp),
                    enabled = !uiState.isDeactivating,
                    onClick = { onEvent(AccountAdminUiEvent.OnDeactivateDismissed) }
                ) {
                    Text(text = stringResource(id = R.string.cancel))
                }
            }
        )
    }

    // Eliminar cuenta: con contraseña si la cuenta la tiene; si es de Google, con el token de Google
    if (uiState.showDeleteDialog) {
        val passValid = !hasPassCuenta || uiState.deletePasswordInput.isNotBlank()
        NearHomeAlertDialog(
            onDismissRequest = {
                if (!uiState.isDeleting) onEvent(AccountAdminUiEvent.OnDeleteDismissed)
            },
            icon = { DialogIcon(Icons.Outlined.Delete, dangerColor) },
            title = { DialogTitle(text = stringResource(id = R.string.dialog_delete_title), color = dangerColor) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(id = R.string.dialog_delete_text),
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        textAlign = textoCentrado,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (hasPassCuenta) {
                        OutlinedTextField(
                            textStyle = androidx.compose.ui.text.TextStyle(
                                textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                                fontSize = 16.sp
                            ),
                            value = uiState.deletePasswordInput,
                            onValueChange = { onEvent(AccountAdminUiEvent.OnDeletePasswordChanged(it)) },
                            label = { Text(stringResource(R.string.acc_current_password)) },
                            singleLine = true,
                            enabled = !uiState.isDeleting,
                            visualTransformation = if (isDeletePasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { isDeletePasswordVisible = !isDeletePasswordVisible }) {
                                    Icon(
                                        imageVector = if (isDeletePasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = stringResource(R.string.acc_toggle_password_cd)
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    OutlinedTextField(
                        textStyle = androidx.compose.ui.text.TextStyle(
                            textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                            fontSize = 16.sp
                        ),
                        value = uiState.deleteInputText,
                        onValueChange = { onEvent(AccountAdminUiEvent.OnDeleteInputChanged(it)) },
                        placeholder = { Text(text = stringResource(id = R.string.dialog_delete_placeholder)) },
                        singleLine = true,
                        enabled = !uiState.isDeleting,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (uiState.deleteError != null) {
                        Text(
                            text = uiState.deleteError,
                            fontSize = 13.sp,
                            textAlign = textoCentrado,
                            color = dangerColor
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    modifier = Modifier.height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = uiState.isDeleteButtonEnabled && passValid && !uiState.isDeleting,
                    colors = ButtonDefaults.buttonColors(containerColor = dangerColor, contentColor = Color.White),
                    onClick = {
                        if (hasPassCuenta) {
                            onEvent(AccountAdminUiEvent.OnDeleteConfirmed(null))
                        } else {
                            coroutineScope.launch {
                                googleAuthManager.getFirebaseIdToken()
                                    .onSuccess { token ->
                                        if (token != null) onEvent(AccountAdminUiEvent.OnDeleteConfirmed(token))
                                    }
                                    .onFailure { e ->
                                        Toast.makeText(context, e.message ?: context.getString(R.string.acc_google_verify_error), Toast.LENGTH_LONG).show()
                                    }
                            }
                        }
                    }
                ) {
                    if (uiState.isDeleting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text(
                            text = if (hasPassCuenta) stringResource(id = R.string.confirm_delete) else stringResource(R.string.acc_confirm_google),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(
                    modifier = Modifier.height(46.dp),
                    enabled = !uiState.isDeleting,
                    onClick = { onEvent(AccountAdminUiEvent.OnDeleteDismissed) }
                ) {
                    Text(text = stringResource(id = R.string.cancel))
                }
            }
        )
    }

    // Cancelar plan: pide confirmación y muestra el error dentro del diálogo
    if (uiState.showCancelPlanDialog) {
        NearHomeAlertDialog(
            onDismissRequest = {
                if (!uiState.isCancellingPlan) onEvent(AccountAdminUiEvent.OnCancelPlanDismissed)
            },
            icon = { DialogIcon(Icons.Outlined.Cancel, dangerColor) },
            title = { DialogTitle(text = stringResource(R.string.cancel_subscription_dialog_title), color = dangerColor) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        // El plan sigue vigente hasta la fecha pagada; después se ocultan los anuncios
                        text = uiState.userData?.planVence
                            ?.let { stringResource(R.string.cancel_subscription_dialog_body_date, PlanDates.formatear(it, LocalConfiguration.current.locales[0])) }
                            ?: stringResource(R.string.cancel_subscription_dialog_body),
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        textAlign = textoCentrado,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (uiState.cancelPlanError != null) {
                        Text(
                            text = uiState.cancelPlanError,
                            fontSize = 13.sp,
                            textAlign = textoCentrado,
                            color = dangerColor
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    modifier = Modifier.height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !uiState.isCancellingPlan,
                    colors = ButtonDefaults.buttonColors(containerColor = dangerColor, contentColor = Color.White),
                    onClick = { onEvent(AccountAdminUiEvent.OnCancelPlanConfirmed) }
                ) {
                    if (uiState.isCancellingPlan) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text(text = stringResource(R.string.acc_cancel_plan_confirm), fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    modifier = Modifier.height(46.dp),
                    enabled = !uiState.isCancellingPlan,
                    onClick = { onEvent(AccountAdminUiEvent.OnCancelPlanDismissed) }
                ) {
                    Text(text = stringResource(R.string.forgot_back))
                }
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        ),
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
    )
}

@Composable
private fun OptionRowItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    secondLine: String? = null,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    secondLineColor: Color = subtitleColor,
    useIconContainer: Boolean = false,
    iconBackgroundColor: Color = Color.Transparent,
    iconTintColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (useIconContainer) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(color = iconBackgroundColor, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTintColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
        } else {
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTintColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = titleColor,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                color = subtitleColor
            )
            if (secondLine != null) {
                Text(
                    text = secondLine,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    color = secondLineColor
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(20.dp)
        )
    }
}