package com.cardcontrol.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cardcontrol.app.data.local.CreditCardEntity
import com.cardcontrol.app.ui.theme.*
import com.cardcontrol.app.util.MoneyUtils
import com.cardcontrol.app.util.ReportEngine
import kotlinx.coroutines.flow.collect

@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    singleLine: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = GlassPalette.TextSecondary.copy(alpha = 0.5f)) } },
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        singleLine = singleLine,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = GlassPalette.CyanGlow,
            unfocusedBorderColor = Color.White.copy(alpha = 0.22f),
            focusedLabelColor = GlassPalette.CyanGlow,
            unfocusedLabelColor = GlassPalette.TextSecondary,
            focusedTextColor = GlassPalette.TextPrimary,
            unfocusedTextColor = GlassPalette.TextPrimary,
            focusedContainerColor = GlassPalette.ObsidianBg.copy(alpha = 0.7f),
            unfocusedContainerColor = GlassPalette.ObsidianBg.copy(alpha = 0.45f)
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onOpenNotificationSettings: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var expandedHolderId by remember { mutableStateOf<Long?>(null) }
    var cardBeingEdited by remember { mutableStateOf<CreditCardEntity?>(null) }
    var cardPendingDeletion by remember { mutableStateOf<CreditCardEntity?>(null) }

    var showExpenseDialog by remember { mutableStateOf(false) }
    var showPaymentDialog by remember { mutableStateOf(false) }
    var showHolderDialog by remember { mutableStateOf(false) }
    var showCardDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    var showSavedMessage by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.uiMessages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(showSavedMessage) {
        if (showSavedMessage) {
            snackbarHostState.showSnackbar("Credenciais protegidas no Android Keystore com sucesso!")
            showSavedMessage = false
        }
    }

    LiquidAmbientBackground {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .liquidGlassSurface(cornerRadius = 18.dp)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("CardControl", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = GlassPalette.TextPrimary)
                            Text("Motor Offline • Liquid Glass", style = MaterialTheme.typography.labelSmall, color = GlassPalette.CyanGlow)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { viewModel.changeMonth(-1) }) { Icon(Icons.Default.ChevronLeft, null, tint = GlassPalette.TextPrimary) }
                            Text("%02d/%d".format(state.selectedMonth, state.selectedYear), fontWeight = FontWeight.Bold, color = GlassPalette.TextPrimary)
                            IconButton(onClick = { viewModel.changeMonth(1) }) { Icon(Icons.Default.ChevronRight, null, tint = GlassPalette.TextPrimary) }
                        }
                    }
                }
            },
            bottomBar = {
                NavigationBar(containerColor = GlassPalette.DeepSlateBg.copy(alpha = 0.92f), tonalElevation = 0.dp) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.AutoMirrored.Filled.ReceiptLong, null) },
                        label = { Text("Faturas") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.CreditCard, null) },
                        label = { Text("Cartões") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Default.Security, null) },
                        label = { Text("Automação") }
                    )
                }
            },
            floatingActionButton = {
                if (selectedTab == 0 && state.cards.isNotEmpty() && state.holders.isNotEmpty()) {
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SmallFloatingActionButton(
                            onClick = { showPaymentDialog = true },
                            containerColor = GlassPalette.EmeraldAccent.copy(alpha = 0.9f),
                            contentColor = GlassPalette.ObsidianBg
                        ) { Icon(Icons.Default.Payments, contentDescription = "Abater Valor") }
                        ExtendedFloatingActionButton(
                            onClick = { showExpenseDialog = true },
                            containerColor = GlassPalette.CyanGlow,
                            contentColor = GlassPalette.ObsidianBg,
                            icon = { Icon(Icons.Default.Add, null) },
                            text = { Text("Lançar Compra", fontWeight = FontWeight.Bold) }
                        )
                    }
                } else if (selectedTab == 1) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            cardBeingEdited = null
                            showCardDialog = true
                        },
                        containerColor = GlassPalette.CyanGlow,
                        contentColor = GlassPalette.ObsidianBg,
                        icon = { Icon(Icons.Default.AddCard, contentDescription = null) },
                        text = { Text("Adicionar cartão", fontWeight = FontWeight.Bold) }
                    )
                }
            }
        ) { padding ->
            Column(modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp)) {
                when (selectedTab) {
                    0 -> {
                        val remainingInvoice = (state.totalMonthInvoice - state.totalMonthPaid).coerceAtLeast(0L)
                        val paidRatio = if (state.totalMonthInvoice > 0L) (state.totalMonthPaid.toFloat() / state.totalMonthInvoice.toFloat()) else 0f

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .liquidGlassSurface(cornerRadius = 24.dp, accentTint = GlassPalette.CyanGlow)
                                .padding(18.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("FATURA CONSOLIDADA DO MÊS", style = MaterialTheme.typography.labelSmall, color = GlassPalette.TextSecondary)
                                Text(ReportEngine.formatCurrency(remainingInvoice), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = GlassPalette.TextPrimary)
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Bruto: ${ReportEngine.formatCurrency(state.totalMonthInvoice)}", style = MaterialTheme.typography.bodySmall, color = GlassPalette.TextSecondary)
                                    Text("Abatido: ${ReportEngine.formatCurrency(state.totalMonthPaid)}", style = MaterialTheme.typography.bodySmall, color = GlassPalette.EmeraldAccent)
                                }
                                LiquidGlassBar(progress = paidRatio, accentColor = GlassPalette.EmeraldAccent)
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = { showHolderDialog = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = GlassPalette.TextPrimary)
                            ) {
                                Icon(Icons.Default.PersonAdd, null, tint = GlassPalette.CyanGlow)
                                Spacer(Modifier.width(6.dp))
                                Text("Pessoa", fontWeight = FontWeight.SemiBold)
                            }
                            OutlinedButton(
                                onClick = {
                                    cardBeingEdited = null
                                    showCardDialog = true
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = GlassPalette.TextPrimary)
                            ) {
                                Icon(Icons.Default.AddCard, null, tint = GlassPalette.IndigoGlow)
                                Spacer(Modifier.width(6.dp))
                                Text("Cartão", fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Spacer(Modifier.height(12.dp))

                        if (state.holders.isEmpty() || state.cards.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .liquidGlassSurface(cornerRadius = 20.dp, accentTint = GlassPalette.CyanGlow)
                                    .padding(22.dp)
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.CreditCard, null, tint = GlassPalette.CyanGlow, modifier = Modifier.size(42.dp))
                                    Text("Primeiros Passos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = GlassPalette.TextPrimary)
                                    Text(
                                        "Cadastre pelo menos uma Pessoa e um Cartão nos botões acima para começar a lançar gastos, controlar sublimites e visualizar as faturas em tempo real.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = GlassPalette.TextSecondary,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 96.dp)) {
                                items(state.holderSummaries, key = { it.holder.id }) { item ->
                                    val isExpanded = expandedHolderId == item.holder.id
                                    val usageRatio = if (item.holder.subLimit > 0L) (item.globalCommitted.toFloat() / item.holder.subLimit.toFloat()) else 0f
                                    val barColor = when {
                                        usageRatio >= 1f -> GlassPalette.CoralDanger
                                        usageRatio >= 0.8f -> GlassPalette.AmberWarn
                                        else -> GlassPalette.CyanGlow
                                    }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .liquidGlassSurface(cornerRadius = 20.dp, accentTint = barColor)
                                            .clickable { expandedHolderId = if (isExpanded) null else item.holder.id }
                                            .padding(16.dp)
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                Column {
                                                    Text(item.holder.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = GlassPalette.TextPrimary)
                                                    Text("A Pagar no Mês: ${ReportEngine.formatCurrency(item.monthlyNetDue)}", style = MaterialTheme.typography.bodyMedium, color = barColor, fontWeight = FontWeight.SemiBold)
                                                }
                                                Row {
                                                    IconButton(onClick = {
                                                        ReportEngine.sharePdfAndTextViaWhatsApp(
                                                            context = context,
                                                            holder = item.holder,
                                                            month = state.selectedMonth,
                                                            year = state.selectedYear,
                                                            installments = item.monthlyInstallments,
                                                            paidInMonth = item.monthlyPaid,
                                                            globalCommitted = item.globalCommitted
                                                        )
                                                    }) { Icon(Icons.Default.Share, contentDescription = "Exportar PDF/WhatsApp", tint = GlassPalette.CyanGlow) }
                                                    Icon(
                                                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                        contentDescription = null,
                                                        tint = GlassPalette.TextSecondary,
                                                        modifier = Modifier.align(Alignment.CenterVertically)
                                                    )
                                                }
                                            }

                                            if (item.holder.subLimit > 0L) {
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                    Text("Sublimite Livre: ${ReportEngine.formatCurrency(item.availableSubLimit)}", style = MaterialTheme.typography.labelSmall, color = GlassPalette.TextSecondary)
                                                    Text("Teto: ${ReportEngine.formatCurrency(item.holder.subLimit)}", style = MaterialTheme.typography.labelSmall, color = GlassPalette.TextSecondary)
                                                }
                                                LiquidGlassBar(progress = usageRatio, accentColor = barColor)
                                            }

                                            AnimatedVisibility(visible = isExpanded) {
                                                Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                                                    if (item.monthlyInstallments.isEmpty()) {
                                                        Text("Nenhuma parcela neste mês.", style = MaterialTheme.typography.bodySmall, color = GlassPalette.TextSecondary)
                                                    } else {
                                                        item.monthlyInstallments.forEach { inst ->
                                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                                 Text("• ${inst.merchantSnapshot} (${inst.installmentNumber}/${inst.totalInstallments})", style = MaterialTheme.typography.bodySmall, color = GlassPalette.TextPrimary)
                                                                 Text(ReportEngine.formatCurrency(inst.amount), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = GlassPalette.CyanGlow)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(top = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    "Seus cartões",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = GlassPalette.TextPrimary
                                )
                                Text(
                                    if (state.cards.size == 1) "1 cartão cadastrado"
                                    else "${state.cards.size} cartões cadastrados",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = GlassPalette.TextSecondary
                                )
                            }

                            if (state.cards.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .liquidGlassSurface(cornerRadius = 22.dp, accentTint = GlassPalette.IndigoGlow)
                                        .padding(22.dp)
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.fillMaxWidth().align(Alignment.Center)
                                    ) {
                                        Icon(Icons.Default.AddCard, null, tint = GlassPalette.IndigoGlow, modifier = Modifier.size(42.dp))
                                        Text("Nenhum cartão por aqui", fontWeight = FontWeight.Bold, color = GlassPalette.TextPrimary)
                                        Text(
                                            "Cadastre um cartão, escolha a pessoa responsável e acompanhe o limite disponível nesta tela.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = GlassPalette.TextSecondary,
                                            textAlign = TextAlign.Center
                                        )
                                        OutlinedButton(
                                            onClick = {
                                                cardBeingEdited = null
                                                showCardDialog = true
                                            },
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = GlassPalette.TextPrimary)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = null)
                                            Spacer(Modifier.width(6.dp))
                                            Text("Cadastrar cartão")
                                        }
                                    }
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    contentPadding = PaddingValues(top = 2.dp, bottom = 104.dp)
                                ) {
                                    items(state.cardSummaries, key = { it.card.id }) { item ->
                                        val ratio = if (item.card.totalLimit > 0L) {
                                            (item.globalCommitted.toFloat() / item.card.totalLimit.toFloat()).coerceIn(0f, 1f)
                                        } else 0f
                                        val linkedHolder = state.holders.firstOrNull { it.id == item.card.defaultHolderId }

                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .liquidGlassSurface(cornerRadius = 22.dp, accentTint = GlassPalette.IndigoGlow)
                                                .padding(18.dp)
                                        ) {
                                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                                        Text(
                                                            item.card.name,
                                                            style = MaterialTheme.typography.titleMedium,
                                                            fontWeight = FontWeight.Bold,
                                                            color = GlassPalette.TextPrimary
                                                        )
                                                        Text(
                                                            "•••• ${item.card.lastFourDigits}",
                                                            style = MaterialTheme.typography.titleSmall,
                                                            color = GlassPalette.CyanGlow
                                                        )
                                                    }
                                                    IconButton(
                                                        onClick = {
                                                            cardBeingEdited = item.card
                                                            showCardDialog = true
                                                        }
                                                    ) {
                                                        Icon(Icons.Default.Edit, contentDescription = "Editar cartão ${item.card.name}", tint = GlassPalette.CyanGlow)
                                                    }
                                                    IconButton(
                                                        onClick = { cardPendingDeletion = item.card }
                                                    ) {
                                                        Icon(Icons.Default.Delete, contentDescription = "Excluir cartão ${item.card.name}", tint = GlassPalette.CoralDanger)
                                                    }
                                                }

                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Icon(
                                                        Icons.Default.Person,
                                                        contentDescription = null,
                                                        tint = if (linkedHolder == null) GlassPalette.AmberWarn else GlassPalette.CyanGlow,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Text(
                                                        linkedHolder?.name?.let { "Pessoa vinculada: $it" } ?: "Nenhuma pessoa vinculada",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = if (linkedHolder == null) GlassPalette.AmberWarn else GlassPalette.TextSecondary
                                                    )
                                                }

                                                Text(
                                                    "Fecha dia ${item.card.closingDay} • Vence dia ${item.card.dueDay}",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = GlassPalette.TextSecondary
                                                )
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text("Disponível", style = MaterialTheme.typography.labelMedium, color = GlassPalette.TextSecondary)
                                                    Text(
                                                        ReportEngine.formatCurrency(item.availableLimit),
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = GlassPalette.EmeraldAccent
                                                    )
                                                }
                                                LiquidGlassBar(progress = ratio, accentColor = GlassPalette.IndigoGlow)
                                                Text(
                                                    "Limite total: ${ReportEngine.formatCurrency(item.card.totalLimit)}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = GlassPalette.TextSecondary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    2 -> {
                        var email by remember { mutableStateOf(viewModel.credentialStore.getSenderEmail()) }
                        var pass by remember { mutableStateOf(viewModel.credentialStore.getAppPassword()) }
                        var showPassword by remember { mutableStateOf(false) }

                        Box(modifier = Modifier.fillMaxWidth().liquidGlassSurface(cornerRadius = 22.dp).padding(18.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Captura em Tempo Real & Cofre Keystore", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = GlassPalette.TextPrimary)
                                Text(
                                    "O CardControl escuta compras emitidas pelos apps bancários e pode enviar comprovantes automáticos por e-mail com o saldo restante do titular.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = GlassPalette.TextSecondary
                                )
                                Button(
                                    onClick = onOpenNotificationSettings,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = GlassPalette.IndigoGlow)
                                ) {
                                    Icon(Icons.Default.NotificationsActive, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Conceder Permissão às Notificações", fontWeight = FontWeight.Bold)
                                }

                                HorizontalDivider(color = Color.White.copy(alpha = 0.12f), modifier = Modifier.padding(vertical = 4.dp))

                                Text("Configuração de Alerta por E-mail (Opcional)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = GlassPalette.CyanGlow)

                                GlassTextField(
                                    value = email,
                                    onValueChange = { email = it },
                                    label = "Gmail Remetente (SMTP TLS)",
                                    placeholder = "seuemail@gmail.com",
                                    leadingIcon = { Icon(Icons.Default.Email, null, tint = GlassPalette.CyanGlow) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)
                                )

                                GlassTextField(
                                    value = pass,
                                    onValueChange = { pass = it },
                                    label = "Senha de App Google (16 caracteres)",
                                    placeholder = "xxxx xxxx xxxx xxxx",
                                    leadingIcon = { Icon(Icons.Default.Key, null, tint = GlassPalette.AmberWarn) },
                                    trailingIcon = {
                                        IconButton(onClick = { showPassword = !showPassword }) {
                                            Icon(
                                                imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = if (showPassword) "Ocultar senha" else "Ver senha",
                                                tint = GlassPalette.TextSecondary
                                            )
                                        }
                                    },
                                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done)
                                )

                                Button(
                                    onClick = {
                                        viewModel.credentialStore.saveSmtpCredentials(email, pass)
                                        showSavedMessage = true
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = GlassPalette.CyanGlow)
                                ) {
                                    Icon(Icons.Default.Lock, null, tint = GlassPalette.ObsidianBg)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Criptografar no Android Keystore", color = GlassPalette.ObsidianBg, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showExpenseDialog && state.cards.isNotEmpty() && state.holders.isNotEmpty()) {
        var merchant by remember { mutableStateOf("") }
        var amount by remember { mutableStateOf("") }
        var parcels by remember { mutableStateOf("1") }
        var selectedCardId by remember { mutableLongStateOf(state.cards.first().id) }
        var selectedHolderId by remember {
            mutableLongStateOf(
                state.cards.first().defaultHolderId
                    ?.takeIf { holderId -> state.holders.any { it.id == holderId } }
                    ?: state.holders.first().id
            )
        }

        AlertDialog(
            onDismissRequest = { showExpenseDialog = false },
            containerColor = GlassPalette.DeepSlateBg,
            titleContentColor = GlassPalette.TextPrimary,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.border(1.dp, GlassPalette.SpecularHighlight, RoundedCornerShape(24.dp)),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AddShoppingCart, null, tint = GlassPalette.CyanGlow)
                    Spacer(Modifier.width(8.dp))
                    Text("Lançar Compra", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Cartão Utilizado:", style = MaterialTheme.typography.labelMedium, color = GlassPalette.TextSecondary)
                    Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        LiquidPillSelector(
                            options = state.cards.map { it.id to "${it.name} (${it.lastFourDigits})" },
                            selectedId = selectedCardId,
                            onSelect = { cardId ->
                                selectedCardId = cardId
                                selectedHolderId = state.cards.find { it.id == cardId }?.defaultHolderId
                                    ?.takeIf { holderId -> state.holders.any { it.id == holderId } }
                                    ?: state.holders.first().id
                            }
                        )
                    }
                    Text("Responsável pelo Gasto:", style = MaterialTheme.typography.labelMedium, color = GlassPalette.TextSecondary)
                    Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        LiquidPillSelector(
                            options = state.holders.map { it.id to it.name },
                            selectedId = selectedHolderId,
                            onSelect = { selectedHolderId = it }
                        )
                    }
                    GlassTextField(
                        value = merchant,
                        onValueChange = { merchant = it },
                        label = "Estabelecimento",
                        placeholder = "Ex: Mercado Livre, Posto...",
                        leadingIcon = { Icon(Icons.Default.Storefront, null, tint = GlassPalette.CyanGlow) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )
                    GlassTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = "Valor Total (R$)",
                        placeholder = "0,00",
                        leadingIcon = { Icon(Icons.Default.AttachMoney, null, tint = GlassPalette.EmeraldAccent) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)
                    )
                    GlassTextField(
                        value = parcels,
                        onValueChange = { parcels = it },
                        label = "Parcelas (1 a 72)",
                        placeholder = "1",
                        leadingIcon = { Icon(Icons.Default.Layers, null, tint = GlassPalette.IndigoGlow) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done)
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showExpenseDialog = false }) {
                    Text("Cancelar", color = GlassPalette.TextSecondary)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val v = MoneyUtils.parseToCents(amount) ?: 0L
                        if (merchant.isNotBlank() && v > 0L) {
                            viewModel.addExpense(selectedCardId, selectedHolderId, merchant, v, (parcels.toIntOrNull() ?: 1).coerceIn(1, 72))
                            showExpenseDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GlassPalette.CyanGlow),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Confirmar", color = GlassPalette.ObsidianBg, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showPaymentDialog && state.holders.isNotEmpty()) {
        var amount by remember { mutableStateOf("") }
        var notes by remember { mutableStateOf("") }
        var selectedHolderId by remember { mutableLongStateOf(state.holders.first().id) }
        var selectedCardId by remember { mutableStateOf(state.cards.firstOrNull()?.id) }

        AlertDialog(
            onDismissRequest = { showPaymentDialog = false },
            containerColor = GlassPalette.DeepSlateBg,
            titleContentColor = GlassPalette.TextPrimary,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.border(1.dp, GlassPalette.SpecularHighlight, RoundedCornerShape(24.dp)),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Payments, null, tint = GlassPalette.EmeraldAccent)
                    Spacer(Modifier.width(8.dp))
                    Text("Registrar Abatimento / Pix", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Quem Pagou:", style = MaterialTheme.typography.labelMedium, color = GlassPalette.TextSecondary)
                    Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        LiquidPillSelector(options = state.holders.map { it.id to it.name }, selectedId = selectedHolderId, onSelect = { selectedHolderId = it })
                    }
                    if (state.cards.isNotEmpty()) {
                        Text("Liberar Limite do Cartão:", style = MaterialTheme.typography.labelMedium, color = GlassPalette.TextSecondary)
                        Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            LiquidPillSelector(options = state.cards.map { it.id to it.name }, selectedId = selectedCardId, onSelect = { selectedCardId = it })
                        }
                    }
                    GlassTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = "Valor Recebido (R$)",
                        placeholder = "0,00",
                        leadingIcon = { Icon(Icons.Default.AttachMoney, null, tint = GlassPalette.EmeraldAccent) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)
                    )
                    GlassTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = "Observação",
                        placeholder = "Ex: Pix fatura adiantada",
                        leadingIcon = { Icon(Icons.Default.EditNote, null, tint = GlassPalette.CyanGlow) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showPaymentDialog = false }) {
                    Text("Cancelar", color = GlassPalette.TextSecondary)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val v = MoneyUtils.parseToCents(amount) ?: 0L
                        if (v > 0L) {
                            viewModel.addPayment(selectedHolderId, selectedCardId, v, notes)
                            showPaymentDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GlassPalette.EmeraldAccent),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Abater", color = GlassPalette.ObsidianBg, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showHolderDialog) {
        var name by remember { mutableStateOf("") }
        var whats by remember { mutableStateOf("") }
        var email by remember { mutableStateOf("") }
        var subLimit by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showHolderDialog = false },
            containerColor = GlassPalette.DeepSlateBg,
            titleContentColor = GlassPalette.TextPrimary,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.border(1.dp, GlassPalette.SpecularHighlight, RoundedCornerShape(24.dp)),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PersonAdd, null, tint = GlassPalette.CyanGlow)
                    Spacer(Modifier.width(8.dp))
                    Text("Novo Titular / Dependente", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = "Nome Completo",
                        placeholder = "Ex: Carlos Silva",
                        leadingIcon = { Icon(Icons.Default.Person, null, tint = GlassPalette.CyanGlow) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )
                    GlassTextField(
                        value = whats,
                        onValueChange = { whats = it },
                        label = "WhatsApp com DDD",
                        placeholder = "Ex: 5511999998888",
                        leadingIcon = { Icon(Icons.Default.Phone, null, tint = GlassPalette.EmeraldAccent) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next)
                    )
                    GlassTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = "E-mail para Alertas Instantâneos",
                        placeholder = "carlos@gmail.com",
                        leadingIcon = { Icon(Icons.Default.Email, null, tint = GlassPalette.IndigoGlow) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)
                    )
                    GlassTextField(
                        value = subLimit,
                        onValueChange = { subLimit = it },
                        label = "Sublimite Mensal (R$)",
                        placeholder = "0,00 (0 para sem teto)",
                        leadingIcon = { Icon(Icons.Default.AccountBalanceWallet, null, tint = GlassPalette.AmberWarn) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done)
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showHolderDialog = false }) {
                    Text("Cancelar", color = GlassPalette.TextSecondary)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            val subLimitCents = MoneyUtils.parseToCents(subLimit) ?: 0L
                            viewModel.addHolder(name, whats, email, subLimitCents)
                            showHolderDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GlassPalette.CyanGlow),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Salvar", color = GlassPalette.ObsidianBg, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (cardPendingDeletion != null) {
        val pendingCard = cardPendingDeletion!!
        AlertDialog(
            onDismissRequest = { cardPendingDeletion = null },
            containerColor = GlassPalette.DeepSlateBg,
            titleContentColor = GlassPalette.TextPrimary,
            shape = RoundedCornerShape(24.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = GlassPalette.CoralDanger)
                    Spacer(Modifier.width(8.dp))
                    Text("Excluir cartão?", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    "O cartão \"${pendingCard.name}\" só será excluído se não tiver compras, parcelas ou pagamentos vinculados. Assim, o histórico financeiro não será apagado por engano.",
                    color = GlassPalette.TextSecondary
                )
            },
            dismissButton = {
                TextButton(onClick = { cardPendingDeletion = null }) {
                    Text("Cancelar", color = GlassPalette.TextSecondary)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteCard(pendingCard.id)
                        cardPendingDeletion = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GlassPalette.CoralDanger),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Excluir cartão", color = GlassPalette.TextPrimary, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showCardDialog) {
        val cardToEdit = cardBeingEdited
        var name by remember(cardToEdit?.id) { mutableStateOf(cardToEdit?.name ?: "") }
        var lastFour by remember(cardToEdit?.id) { mutableStateOf(cardToEdit?.lastFourDigits ?: "") }
        var limit by remember(cardToEdit?.id) {
            mutableStateOf(cardToEdit?.totalLimit?.let { MoneyUtils.formatDecimal(it) } ?: "")
        }
        var closing by remember(cardToEdit?.id) { mutableStateOf(cardToEdit?.closingDay?.toString() ?: "10") }
        var due by remember(cardToEdit?.id) { mutableStateOf(cardToEdit?.dueDay?.toString() ?: "17") }
        var defaultHolderId by remember(cardToEdit?.id) {
            mutableStateOf(if (cardToEdit != null) cardToEdit.defaultHolderId else state.holders.firstOrNull()?.id)
        }
        var formError by remember(cardToEdit?.id) { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = {
                showCardDialog = false
                cardBeingEdited = null
            },
            containerColor = GlassPalette.DeepSlateBg,
            titleContentColor = GlassPalette.TextPrimary,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.border(1.dp, GlassPalette.SpecularHighlight, RoundedCornerShape(24.dp)),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CreditCard, null, tint = GlassPalette.IndigoGlow)
                    Spacer(Modifier.width(8.dp))
                    Text(if (cardToEdit == null) "Novo cartão" else "Editar cartão", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Banco e identificação",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = GlassPalette.CyanGlow
                    )
                    GlassTextField(
                        value = name,
                        onValueChange = { name = it; formError = null },
                        label = "Banco / apelido do cartão",
                        placeholder = "Ex.: Inter Platinum",
                        leadingIcon = { Icon(Icons.Default.CreditCard, null, tint = GlassPalette.IndigoGlow) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )
                    Text(
                        "Inclua o nome do banco no apelido para ajudar a identificar as notificações de compra.",
                        style = MaterialTheme.typography.bodySmall,
                        color = GlassPalette.TextSecondary
                    )
                    GlassTextField(
                        value = lastFour,
                        onValueChange = { value -> lastFour = value.filter { it.isDigit() }.takeLast(4); formError = null },
                        label = "Últimos 4 dígitos",
                        placeholder = "Ex.: 0359",
                        leadingIcon = { Icon(Icons.Default.Pin, null, tint = GlassPalette.CyanGlow) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
                    )
                    GlassTextField(
                        value = limit,
                        onValueChange = { limit = it; formError = null },
                        label = "Limite total (R$)",
                        placeholder = "Ex.: 5000,00",
                        leadingIcon = { Icon(Icons.Default.AttachMoney, null, tint = GlassPalette.EmeraldAccent) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassTextField(
                            value = closing,
                            onValueChange = { closing = it; formError = null },
                            label = "Fechamento (1–28)",
                            placeholder = "10",
                            leadingIcon = { Icon(Icons.Default.CalendarToday, null, tint = GlassPalette.TextSecondary) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            modifier = Modifier.weight(1f)
                        )
                        GlassTextField(
                            value = due,
                            onValueChange = { due = it; formError = null },
                            label = "Vencimento (1–28)",
                            placeholder = "17",
                            leadingIcon = { Icon(Icons.Default.Event, null, tint = GlassPalette.TextSecondary) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.12f), modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        "Pessoa responsável",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = GlassPalette.CyanGlow
                    )
                    Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        LiquidPillSelector(
                            options = listOf(0L to "Sem vínculo") + state.holders.map { it.id to it.name },
                            selectedId = defaultHolderId ?: 0L,
                            onSelect = { selected -> defaultHolderId = selected.takeIf { it != 0L }; formError = null }
                        )
                    }
                    Text(
                        "Essa pessoa será usada nas compras capturadas automaticamente. Os lançamentos antigos não mudam.",
                        style = MaterialTheme.typography.bodySmall,
                        color = GlassPalette.TextSecondary
                    )
                    if (formError != null) {
                        Text(formError!!, style = MaterialTheme.typography.bodySmall, color = GlassPalette.CoralDanger)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCardDialog = false
                        cardBeingEdited = null
                    }
                ) {
                    Text("Cancelar", color = GlassPalette.TextSecondary)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val digits = lastFour.filter { it.isDigit() }.takeLast(4)
                        val limitCents = MoneyUtils.parseToCents(limit)
                        val closingDay = closing.toIntOrNull()
                        val dueDay = due.toIntOrNull()
                        if (
                            name.isBlank() ||
                            digits.length != 4 ||
                            limitCents == null ||
                            closingDay == null || closingDay !in 1..28 ||
                            dueDay == null || dueDay !in 1..28
                        ) {
                            formError = "Confira o apelido, os quatro dígitos, o limite e as datas."
                        } else {
                            val holderId = defaultHolderId?.takeIf { id -> state.holders.any { it.id == id } }
                            if (cardToEdit == null) {
                                viewModel.addCard(
                                    name = name,
                                    lastFour = digits,
                                    limit = limitCents,
                                    closing = closingDay,
                                    due = dueDay,
                                    defaultHolderId = holderId
                                )
                            } else {
                                viewModel.updateCard(
                                    id = cardToEdit.id,
                                    name = name,
                                    lastFour = digits,
                                    limit = limitCents,
                                    closing = closingDay,
                                    due = dueDay,
                                    defaultHolderId = holderId
                                )
                            }
                            showCardDialog = false
                            cardBeingEdited = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GlassPalette.IndigoGlow),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (cardToEdit == null) "Cadastrar cartão" else "Salvar alterações", color = GlassPalette.TextPrimary, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}
