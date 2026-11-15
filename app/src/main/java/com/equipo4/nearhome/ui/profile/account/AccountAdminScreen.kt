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
import androidx.compose.ui.platform.LocalContext
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
            val msg = "Tu cuenta está desactivada. Para volver, solo inicia sesión"
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            onNavigateToLogin(msg)
        }
    }

    // NUEVO: Navegar al login cuando se elimina la cuenta
    LaunchedEffect(uiState.isDeletedSuccess) {
        if (uiState.isDeletedSuccess) {
            onNavigateToLogin("Tu cuenta se eliminará en 30 días")
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
    var isSendingSms by remember { mutableStateOf(false) }
    var smsLocalError by remember { mutableStateOf<String?>(null) }
    var phoneOtpCode by remember { mutableStateOf(List(6) { "" }) }

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
                                "Entras con Google. Crea una contraseña para entrar también con tu correo."
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

                val planName = userData.planName

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
                                val statusText = if (!userData.planStatus.isNullOrEmpty()) " (${userData.planStatus})" else ""
                                "$planName$statusText"
                            } else {
                                stringResource(id = R.string.buyer_no_plan)
                            },
                            secondLine = when {
                                planName == null -> stringResource(id = R.string.buyer_plan_subtitle)
                                userData.planExpired -> stringResource(id = R.string.plan_expired_format, userData.renewalDate.orEmpty())
                                else -> stringResource(id = R.string.plan_expires_format, userData.renewalDate.orEmpty())
                            },
                            secondLineColor = if (userData.planExpired) {
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

                    // NUEVO: Cancelar suscripción (solo para vendedores con plan activo)
                    if (userData.role == UserRole.VENDEDOR && planName != null) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            OptionRowItem(
                                icon = Icons.Outlined.Cancel,
                                title = "Cancelar suscripción",
                                subtitle = "Tus anuncios se ocultan, pero no se borran. Puedes contratar otro plan después.",
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
            title = { DialogTitle("Cambiar número de teléfono") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Teléfono actual: ${uiState.userData?.phone.orEmpty()}",
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
                            label = { Text("País") },
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
                            label = { Text("Teléfono nuevo") },
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
                            label = { Text("Contraseña actual") },
                            singleLine = true,
                            enabled = !uiState.isPhoneValidating,
                            visualTransformation = if (isPhoneCurrentPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { isPhoneCurrentPasswordVisible = !isPhoneCurrentPasswordVisible }) {
                                    Icon(
                                        imageVector = if (isPhoneCurrentPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = "Mostrar u ocultar contraseña"
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
                                    if (activity != null) {
                                        isSendingSms = true
                                        smsLocalError = null
                                        TelefonoFirebase.enviarCodigo(
                                            activity = activity,
                                            telefono = targetPhone,
                                            tokenReenvio = null,
                                            eventos = object : TelefonoFirebase.Eventos {
                                                override fun codigoEnviado(verificationId: String, tokenReenvio: PhoneAuthProvider.ForceResendingToken) {
                                                    isSendingSms = false
                                                    smsVerificationId = verificationId
                                                    smsResendToken = tokenReenvio
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
                                            }
                                        )
                                    }
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
                                    Toast.makeText(context, exception.message ?: "Error al verificar con Google", Toast.LENGTH_LONG).show()
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
                        Text(if (hasPass) "Continuar" else "Confirmar con Google y continuar")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { onEvent(AccountAdminUiEvent.OnPhoneFlowDismissed) },
                    enabled = !uiState.isPhoneValidating && !isSendingSms
                ) {
                    Text("Cancelar")
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
            title = { DialogTitle("Verificación por SMS") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Escribe el código de 6 dígitos que enviamos por SMS a $targetPhone",
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
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val vid = smsVerificationId
                        if (vid == null) {
                            onEvent(AccountAdminUiEvent.OnConfirmPhoneSmsSubmitted("123456"))
                        } else {
                            coroutineScope.launch {
                                try {
                                    val credencial = TelefonoFirebase.credencialConCodigo(vid, fullCode)
                                    val tokenFirebase = TelefonoFirebase.obtenerToken(credencial)
                                    onEvent(AccountAdminUiEvent.OnConfirmPhoneSmsSubmitted(tokenFirebase))
                                } catch (e: Exception) {
                                    smsLocalError = e.message ?: "El código de SMS es incorrecto"
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
                        Text("Verificar")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { onEvent(AccountAdminUiEvent.OnPhoneFlowDismissed) },
                    enabled = !uiState.isPhoneSaving
                ) {
                    Text("Cancelar")
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
            title = { DialogTitle("Cambiar correo electrónico") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Correo actual: ${uiState.userData?.email.orEmpty()}",
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
                        label = { Text("Correo nuevo") },
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
                            label = { Text("Contraseña actual") },
                            singleLine = true,
                            enabled = !uiState.isEmailCodeSending,
                            visualTransformation = if (isEmailCurrentPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { isEmailCurrentPasswordVisible = !isEmailCurrentPasswordVisible }) {
                                    Icon(
                                        imageVector = if (isEmailCurrentPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = "Mostrar u ocultar contraseña"
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
                                    Toast.makeText(context, exception.message ?: "Error al verificar con Google", Toast.LENGTH_LONG).show()
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
                        Text(if (hasPass) "Enviar código" else "Confirmar con Google y enviar código")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { onEvent(AccountAdminUiEvent.OnEmailFlowDismissed) },
                    enabled = !uiState.isEmailCodeSending
                ) {
                    Text("Cancelar")
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
            title = { DialogTitle("Verifica tu nuevo correo") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Escribe el código que enviamos a ${uiState.newEmailInput.trim()}",
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
                            Text("Cambiar correo")
                        }

                        TextButton(
                            onClick = { onEvent(AccountAdminUiEvent.OnResendEmailCodeRequested) },
                            enabled = uiState.canResendEmailCode && !uiState.isEmailConfirming
                        ) {
                            if (uiState.canResendEmailCode) {
                                Text("Reenviar código")
                            } else {
                                Text("Reenviar en ${uiState.emailResendCountdown}s")
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
                        Text("Confirmar")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { onEvent(AccountAdminUiEvent.OnEmailFlowDismissed) },
                    enabled = !uiState.isEmailConfirming
                ) {
                    Text("Cancelar")
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
                    text = if (hasPass) "¿Quieres cambiar tu contraseña?" else "¿Quieres crear una contraseña?"
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (hasPass) {
                            "Te enviaremos un código a tu correo para confirmar que eres tú."
                        } else {
                            "Tu cuenta entra con Google. Si creas una contraseña, también podrás entrar con tu correo y contraseña; Google seguirá funcionando. Te enviaremos un código a tu correo."
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
                        Text("Enviar código")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { onEvent(AccountAdminUiEvent.OnPasswordFlowDismissed) },
                    enabled = !uiState.isOtpLoading
                ) {
                    Text("Cancelar")
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
            title = { DialogTitle("Verifica tu correo") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Escribe el código que enviamos a ${uiState.maskedEmail.ifBlank { uiState.userData?.email.orEmpty() }}",
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
                            Text("Reenviar código")
                        } else {
                            Text("Reenviar código en ${uiState.resendCountdown}s")
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
                        Text("Verificar")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { onEvent(AccountAdminUiEvent.OnPasswordFlowDismissed) },
                    enabled = !uiState.isOtpLoading
                ) {
                    Text("Cancelar")
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
                    text = if (hasPass) "Nueva contraseña" else "Crear contraseña"
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
                            label = { Text("Contraseña actual") },
                            singleLine = true,
                            enabled = !uiState.isPasswordSaving,
                            visualTransformation = if (isCurrentPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { isCurrentPasswordVisible = !isCurrentPasswordVisible }) {
                                    Icon(
                                        imageVector = if (isCurrentPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = "Mostrar u ocultar contraseña"
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
                        label = { Text("Nueva contraseña") },
                        singleLine = true,
                        enabled = !uiState.isPasswordSaving,
                        visualTransformation = if (isNewPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isNewPasswordVisible = !isNewPasswordVisible }) {
                                Icon(
                                    imageVector = if (isNewPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Mostrar u ocultar contraseña"
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
                        label = { Text("Confirmar nueva contraseña") },
                        singleLine = true,
                        enabled = !uiState.isPasswordSaving,
                        visualTransformation = if (isConfirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isConfirmPasswordVisible = !isConfirmPasswordVisible }) {
                                Icon(
                                    imageVector = if (isConfirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Mostrar u ocultar contraseña"
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
                        Text("Guardar")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { onEvent(AccountAdminUiEvent.OnPasswordFlowDismissed) },
                    enabled = !uiState.isPasswordSaving
                ) {
                    Text("Cancelar")
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
                            label = { Text("Contraseña actual") },
                            singleLine = true,
                            enabled = !uiState.isDeactivating,
                            visualTransformation = if (isCurrentPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { isCurrentPasswordVisible = !isCurrentPasswordVisible }) {
                                    Icon(
                                        imageVector = if (isCurrentPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = "Mostrar u ocultar contraseña"
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
                                        Toast.makeText(context, e.message ?: "Error al verificar con Google", Toast.LENGTH_LONG).show()
                                    }
                            }
                        }
                    }
                ) {
                    if (uiState.isDeactivating) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text(
                            text = if (hasPassCuenta) stringResource(id = R.string.confirm_deactivate) else "Confirmar con Google",
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
                            label = { Text("Contraseña actual") },
                            singleLine = true,
                            enabled = !uiState.isDeleting,
                            visualTransformation = if (isDeletePasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { isDeletePasswordVisible = !isDeletePasswordVisible }) {
                                    Icon(
                                        imageVector = if (isDeletePasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = "Mostrar u ocultar contraseña"
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
                                        Toast.makeText(context, e.message ?: "Error al verificar con Google", Toast.LENGTH_LONG).show()
                                    }
                            }
                        }
                    }
                ) {
                    if (uiState.isDeleting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text(
                            text = if (hasPassCuenta) stringResource(id = R.string.confirm_delete) else "Confirmar con Google",
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
            title = { DialogTitle(text = "¿Cancelar suscripción?", color = dangerColor) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Tus anuncios se ocultarán, pero no se borrarán. Podrás contratar otro plan cuando quieras.",
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
                        Text(text = "Sí, cancelar plan", fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    modifier = Modifier.height(46.dp),
                    enabled = !uiState.isCancellingPlan,
                    onClick = { onEvent(AccountAdminUiEvent.OnCancelPlanDismissed) }
                ) {
                    Text(text = "Volver")
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