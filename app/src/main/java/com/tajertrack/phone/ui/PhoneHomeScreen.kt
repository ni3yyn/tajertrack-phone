package com.tajertrack.phone.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.graphics.RectangleShape

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CellTower
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tajertrack.phone.SimChoice
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class PhoneDestination {
    Home,
    Connection,
    Sending,
    About
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneHomeScreen(
    connected: Boolean,
    relayRunning: Boolean,
    storeName: String,
    statusMessage: String,
    pairing: Boolean,
    simSetupUnlocked: Boolean,
    testSmsSending: Boolean,
    simChoices: List<SimChoice>,
    selectedSimId: Int,
    onConnect: () -> Unit,
    onStartRelay: () -> Unit,
    onStopRelay: () -> Unit,
    onSendTestSms: () -> Unit,
    onSelectSim: (Int) -> Unit,
    onReconnect: () -> Unit,
    onExitApp: () -> Unit
) {
    CompositionDirectionRtl {
        val drawerState = androidx.compose.material3.rememberDrawerState(
            initialValue = androidx.compose.material3.DrawerValue.Closed
        )
        val scope = rememberCoroutineScope()
        var destination by remember { mutableStateOf(PhoneDestination.Home) }
        var showExitConfirmation by remember { mutableStateOf(false) }
        val pageTitle = when (destination) {
            PhoneDestination.Home -> "الرئيسية"
            PhoneDestination.Connection -> "ربط المتجر"
            PhoneDestination.Sending -> "إرسال الرسائل"
            PhoneDestination.About -> "حول التطبيق"
        }

        Box(Modifier.fillMaxSize()) {
            ModalNavigationDrawer(
                drawerState = drawerState,
                scrimColor = Color(0x990F1D14),
                drawerContent = {
                    ModalDrawerSheet(
                        modifier = Modifier.width(304.dp).fillMaxHeight(),
                        drawerShape = RectangleShape,
                        drawerContainerColor = TajerSurface,
                        drawerContentColor = TajerInk
                    ) {
                        PhoneNavigationDrawer(
                            destination = destination,
                            connected = connected,
                            relayRunning = relayRunning,
                            storeName = storeName,
                            onNavigate = { next ->
                                destination = next
                                scope.launch { drawerState.close() }
                            }
                        )
                    }
                }
            ) {
                Scaffold(
                    containerColor = TajerIvory,
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "تاجر تراك",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TajerInk
                                    )
                                    Text(
                                        pageTitle,
                                        fontSize = 10.sp,
                                        color = TajerMuted,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            },
                            navigationIcon = {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(
                                        Icons.Outlined.Menu,
                                        contentDescription = "فتح القائمة",
                                        tint = TajerInk,
                                        modifier = Modifier.size(23.dp)
                                    )
                                }
                            },
                            actions = {
                                BrandMark(modifier = Modifier.padding(end = 16.dp).size(34.dp))
                            },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = TajerIvory,
                                titleContentColor = TajerInk,
                                navigationIconContentColor = TajerInk,
                                actionIconContentColor = TajerInk
                            )
                        )
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        AnimatedContent(
                            targetState = destination,
                            label = "native-page-transition",
                            transitionSpec = {
                                (fadeIn(tween(220)) + slideInHorizontally(
                                    animationSpec = tween(240),
                                    initialOffsetX = { width -> width / 18 }
                                )) togetherWith
                                    (fadeOut(tween(150)) + slideOutHorizontally(
                                        targetOffsetX = { width -> -width / 28 },
                                        animationSpec = tween(180)
                                    ))
                            }
                        ) { target ->
                            when (target) {
                                PhoneDestination.Home -> HomePage(
                                    connected = connected,
                                    relayRunning = relayRunning,
                                    storeName = storeName,
                                    statusMessage = statusMessage,
                                    onOpenConnection = { destination = PhoneDestination.Connection },
                                    onOpenSending = { destination = PhoneDestination.Sending }
                                )
                                PhoneDestination.Connection -> ConnectionPage(
                                    connected = connected,
                                    storeName = storeName,
                                    pairing = pairing,
                                    statusMessage = statusMessage,
                                    onConnect = onConnect,
                                    onContinue = { destination = PhoneDestination.Sending }
                                )
                                PhoneDestination.Sending -> SendingPage(
                                    connected = connected,
                                    relayRunning = relayRunning,
                                    storeName = storeName,
                                    statusMessage = statusMessage,
                                    simSetupUnlocked = simSetupUnlocked,
                                    testSmsSending = testSmsSending,
                                    simChoices = simChoices,
                                    selectedSimId = selectedSimId,
                                    onSelectSim = onSelectSim,
                                    onStartRelay = onStartRelay,
                                    onStopRelay = onStopRelay,
                                    onSendTestSms = onSendTestSms,
                                    onReconnect = {
                                        destination = PhoneDestination.Connection
                                        onReconnect()
                                    }
                                )
                                PhoneDestination.About -> AboutPage()
                            }
                        }
                    }
                }
            }

            var splashVisible by remember { mutableStateOf(true) }
            BackHandler(enabled = !splashVisible) {
                when {
                    drawerState.isOpen -> scope.launch { drawerState.close() }
                    destination != PhoneDestination.Home -> destination = PhoneDestination.Home
                    else -> showExitConfirmation = true
                }
            }
            if (showExitConfirmation) {
                ExitConfirmationDialog(
                    onDismiss = { showExitConfirmation = false },
                    onConfirm = onExitApp
                )
            }
            LaunchedEffect(Unit) {
                delay(1050)
                splashVisible = false
            }
            AnimatedVisibility(
                visible = splashVisible,
                exit = fadeOut(animationSpec = tween(380)),
                label = "brand-splash"
            ) {
                PhoneSplashScreen()
            }
        }
    }
}

@Composable
private fun CompositionDirectionRtl(content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl,
        content = content
    )
}

@Composable
private fun PhoneNavigationDrawer(
    destination: PhoneDestination,
    connected: Boolean,
    relayRunning: Boolean,
    storeName: String,
    onNavigate: (PhoneDestination) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(top = 26.dp, bottom = 20.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BrandMark(modifier = Modifier.size(46.dp))
            Spacer(Modifier.width(13.dp))
            Column {
                Text("تاجر تراك", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TajerInk)
                Text("SMS PHONE", fontWeight = FontWeight.Medium, fontSize = 9.sp, letterSpacing = 1.5.sp, color = TajerMuted)
            }
        }
        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = TajerBorder)
        Spacer(Modifier.height(17.dp))
        Text(
            "القائمة الرئيسية",
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 7.dp),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = TajerMuted
        )
        DrawerItem(
            title = "الرئيسية",
            icon = Icons.Outlined.Home,
            selected = destination == PhoneDestination.Home,
            onClick = { onNavigate(PhoneDestination.Home) }
        )
        DrawerItem(
            title = "ربط المتجر",
            icon = Icons.Outlined.Link,
            selected = destination == PhoneDestination.Connection,
            onClick = { onNavigate(PhoneDestination.Connection) }
        )
        DrawerItem(
            title = "إرسال الرسائل",
            icon = Icons.Outlined.Sms,
            selected = destination == PhoneDestination.Sending,
            onClick = { onNavigate(PhoneDestination.Sending) }
        )
        DrawerItem(
            title = "حول التطبيق",
            icon = Icons.Outlined.Info,
            selected = destination == PhoneDestination.About,
            onClick = { onNavigate(PhoneDestination.About) }
        )

        Spacer(Modifier.height(26.dp))
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 22.dp),
            color = TajerBorder
        )
        Spacer(Modifier.height(18.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .background(TajerIvory)
                .padding(14.dp)
        ) {
            Text("حالة الهاتف", fontSize = 11.sp, color = TajerMuted, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(10.dp))
            DrawerStatusLine("المتجر", if (connected) "مرتبط" else "غير مربوط", connected)
            Spacer(Modifier.height(8.dp))
            DrawerStatusLine("الإرسال", if (relayRunning) "يعمل" else "متوقف", relayRunning)
            if (connected && storeName.isNotBlank()) {
                Spacer(Modifier.height(11.dp))
                Text(
                    storeName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TajerInk,
                    maxLines = 1
                )
            }
        }
        Spacer(Modifier.height(26.dp))
        Text(
            "تاجر تراك فون",
            modifier = Modifier.padding(horizontal = 22.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = TajerMuted
        )
        Text(
            "يرسل رابط التتبع مرة واحدة من شريحة هاتفك.",
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp),
            fontSize = 10.sp,
            lineHeight = 18.sp,
            color = TajerMuted
        )
    }
}

@Composable
private fun DrawerItem(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    NavigationDrawerItem(
        label = {
            Text(
                title,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            )
        },
        selected = selected,
        onClick = onClick,
        icon = {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        shape = RectangleShape,
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = TajerSage,
            unselectedContainerColor = Color.Transparent,
            selectedIconColor = TajerForest,
            unselectedIconColor = TajerMuted,
            selectedTextColor = TajerForestDeep,
            unselectedTextColor = TajerInk
        )
    )
}

@Composable
private fun DrawerStatusLine(label: String, value: String, active: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 11.sp, color = TajerMuted)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(if (active) TajerForest else TajerClay)
            )
            Spacer(Modifier.width(7.dp))
            Text(
                value,
                fontSize = 11.sp,
                color = if (active) TajerForest else TajerMuted,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun HomePage(
    connected: Boolean,
    relayRunning: Boolean,
    storeName: String,
    statusMessage: String,
    onOpenConnection: () -> Unit,
    onOpenSending: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = TajerForest,
            shape = RectangleShape
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "حالة الهاتف",
                        color = Color(0xFFDCE7DC),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(7.dp)
                                .background(if (relayRunning) Color(0xFF9ED3A7) else Color(0xFFD8A58E))
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            if (relayRunning) "الإرسال يعمل" else "الإرسال متوقف",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Spacer(Modifier.height(23.dp))
                Text(
                    if (connected) storeName.ifBlank { "متجرك مرتبط" } else "هاتفك جاهز للربط",
                    color = Color.White,
                    fontSize = 23.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (connected) {
                        "هذا الهاتف مرتبط بمتجرك ويمكنه إرسال روابط التتبع عبر شريحة الاتصال."
                    } else {
                        "اربط حساب متجرك مرة واحدة، ثم استخدم شريحتك لإرسال رابط التتبع للعميل."
                    },
                    color = Color(0xFFDCE7DC),
                    fontSize = 12.sp,
                    lineHeight = 21.sp
                )
                Spacer(Modifier.height(22.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.18f))
                Spacer(Modifier.height(15.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    HeroStatus("ربط المتجر", if (connected) "مرتبط" else "بانتظار الربط", connected)
                    HeroStatus("خدمة الرسائل", if (relayRunning) "نشطة" else "متوقفة", relayRunning)
                }
            }
        }

        Spacer(Modifier.height(27.dp))
        SectionHeading("وصول سريع", "إدارة الربط والإرسال من داخل التطبيق")
        Spacer(Modifier.height(12.dp))
        if (connected) {
            Button(
                onClick = onOpenSending,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RectangleShape,
                colors = ButtonDefaults.buttonColors(containerColor = TajerForest)
            ) {
                Text(
                    if (relayRunning) "إدارة إرسال الرسائل" else "الخطوة التالية: إعداد إرسال الرسائل",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(10.dp))
            NativeActionRow(
                icon = Icons.Outlined.Link,
                title = "إدارة ربط المتجر",
                detail = "الحساب المرتبط: ${storeName.ifBlank { "متجرك" }}",
                onClick = onOpenConnection
            )
        } else {
            NativeActionRow(
                icon = Icons.Outlined.Link,
                title = "ربط المتجر",
                detail = "افتح صفحة تاجر تراك لإتمام الربط",
                onClick = onOpenConnection
            )
            Spacer(Modifier.height(10.dp))
            NativeActionRow(
                icon = Icons.Outlined.CellTower,
                title = "إعداد إرسال الرسائل",
                detail = "اختر الشريحة وفعّل الإرسال بعد ربط المتجر",
                onClick = onOpenSending
            )
        }

        Spacer(Modifier.height(28.dp))
        SectionHeading("كيف يعمل التطبيق", "إرسال بسيط دون رسائل متكررة")
        Spacer(Modifier.height(12.dp))
        FeatureRow(
            icon = Icons.Outlined.Sms,
            title = "رابط واحد لكل طلب جديد",
            detail = "يرسل التطبيق رابط التتبع مرة واحدة عند إنشاء الطلب، ولا يرسل تحديثات حالة متكررة."
        )
        HorizontalDivider(
            modifier = Modifier.padding(start = 42.dp, top = 15.dp, bottom = 15.dp),
            color = TajerBorder
        )
        FeatureRow(
            icon = Icons.Outlined.Wifi,
            title = "اتصال الإنترنت مطلوب",
            detail = "اترك الهاتف متصلاً بالإنترنت وبالشاحن عند الحاجة حتى يستقبل الطلبات الجديدة."
        )
        Spacer(Modifier.height(26.dp))
        AnimatedVisibility(
            visible = statusMessage.isNotBlank(),
            enter = fadeIn(tween(180)) + androidx.compose.animation.expandVertically(tween(180)),
            exit = fadeOut(tween(120)) + androidx.compose.animation.shrinkVertically(tween(120))
        ) {
            Column {
                Spacer(Modifier.height(16.dp))
                NoticePanel("حالة التطبيق", statusMessage, NoticeTone.Neutral)
            }
        }
        Spacer(Modifier.height(22.dp))
        Text(
            "تاجر تراك فون · رفيق متجرك لإرسال روابط التتبع",
            modifier = Modifier.fillMaxWidth(),
            color = TajerMuted,
            textAlign = TextAlign.Center,
            fontSize = 10.sp
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun HeroStatus(title: String, value: String, active: Boolean) {
    Column {
        Text(title, color = Color(0xFFC5D4C7), fontSize = 10.sp)
        Spacer(Modifier.height(5.dp))
        Text(
            value,
            color = if (active) Color(0xFFBFE3C2) else Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ConnectionPage(
    connected: Boolean,
    storeName: String,
    pairing: Boolean,
    statusMessage: String,
    onConnect: () -> Unit,
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 22.dp)
    ) {
        PageIntro(
            number = "01",
            title = if (connected) "متجرك مرتبط" else "اربط متجرك",
            subtitle = "اربط هذا الهاتف بحسابك في تاجر تراك مرة واحدة."
        )
        Spacer(Modifier.height(22.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = TajerSurface,
            border = BorderStroke(1.dp, TajerBorder),
            shape = RectangleShape
        ) {
            Column(Modifier.padding(19.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Link, contentDescription = null, tint = TajerForest, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(11.dp))
                    Column {
                        Text(
                            if (connected) "تم ربط الحساب" else "لم يتم ربط المتجر بعد",
                            color = TajerInk,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (connected) "يمكنك إعادة الربط عند الحاجة." else "اتبع الخطوات في المتصفح لإكمال الربط.",
                            color = TajerMuted,
                            fontSize = 11.sp
                        )
                    }
                }
                if (connected && storeName.isNotBlank()) {
                    Spacer(Modifier.height(18.dp))
                    HorizontalDivider(color = TajerBorder)
                    Spacer(Modifier.height(14.dp))
                    Text("المتجر المرتبط", fontSize = 10.sp, color = TajerMuted)
                    Spacer(Modifier.height(5.dp))
                    Text(storeName, fontSize = 16.sp, color = TajerInk, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(17.dp))
        if (connected) {
            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RectangleShape,
                colors = ButtonDefaults.buttonColors(containerColor = TajerForest),
                contentPadding = PaddingValues(horizontal = 18.dp)
            ) {
                Text(
                    "الخطوة التالية: إعداد إرسال الرسائل",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(9.dp))
            OutlinedButton(
                onClick = onConnect,
                enabled = !pairing,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RectangleShape,
                border = BorderStroke(1.dp, TajerBorder),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = TajerForest,
                    disabledContentColor = TajerForest
                )
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (pairing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(17.dp),
                            color = TajerForest,
                            strokeWidth = 2.dp
                        )
                    }
                    Text(
                        if (pairing) "جارٍ إكمال الربط..." else "إعادة ربط الحساب",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        } else {
            Button(
                onClick = onConnect,
                enabled = !pairing,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RectangleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = TajerForest,
                    contentColor = Color.White,
                    disabledContainerColor = TajerForest,
                    disabledContentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 18.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (pairing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    }
                    Text(
                        if (pairing) "جارٍ إكمال الربط..." else "ربط المتجر",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "سيُفتح موقع تاجر تراك في المتصفح. سجّل الدخول ووافق على ربط هذا الهاتف، ثم ارجع إلى التطبيق.",
            color = TajerMuted,
            fontSize = 11.sp,
            lineHeight = 20.sp
        )
        AnimatedVisibility(
            visible = statusMessage.isNotBlank(),
            enter = fadeIn(tween(180)) + androidx.compose.animation.expandVertically(tween(180)),
            exit = fadeOut(tween(120)) + androidx.compose.animation.shrinkVertically(tween(120))
        ) {
            Column {
                Spacer(Modifier.height(16.dp))
                NoticePanel("حالة الربط", statusMessage, NoticeTone.Neutral)
            }
        }
    }
}

@Composable
private fun SendingPage(
    connected: Boolean,
    relayRunning: Boolean,
    storeName: String,
    statusMessage: String,
    simSetupUnlocked: Boolean,
    testSmsSending: Boolean,
    simChoices: List<SimChoice>,
    selectedSimId: Int,
    onSelectSim: (Int) -> Unit,
    onStartRelay: () -> Unit,
    onStopRelay: () -> Unit,
    onSendTestSms: () -> Unit,
    onReconnect: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 22.dp)
    ) {
        PageIntro(
            number = "02",
            title = "إرسال الرسائل",
            subtitle = when {
                !connected -> "اربط الهاتف بمتجرك أولاً لإعداد خدمة الرسائل."
                simSetupUnlocked -> "اختر الشريحة التي سيُرسل منها رابط التتبع."
                else -> "ابدأ إعداد الخدمة واسمح بالأذونات المطلوبة لإظهار شرائح الاتصال."
            }
        )

        if (!connected) {
            Spacer(Modifier.height(20.dp))
            NoticePanel(
                title = "اربط متجرك أولاً",
                detail = "يجب ربط الهاتف بحسابك قبل تفعيل إرسال الرسائل.",
                tone = NoticeTone.Warning
            )
            Spacer(Modifier.height(13.dp))
            Button(
                onClick = onReconnect,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RectangleShape,
                colors = ButtonDefaults.buttonColors(containerColor = TajerForest)
            ) {
                Text("الانتقال إلى ربط المتجر", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        } else {
            Spacer(Modifier.height(20.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = TajerSurface,
                border = BorderStroke(1.dp, TajerBorder),
                shape = RectangleShape
            ) {
                Column(
                    Modifier
                        .animateContentSize(
                            animationSpec = spring(dampingRatio = 0.86f, stiffness = 320f)
                        )
                        .padding(18.dp)
                ) {
                    Text("المتجر المرتبط", fontSize = 10.sp, color = TajerMuted)
                    Spacer(Modifier.height(5.dp))
                    Text(
                        storeName.ifBlank { "متجرك" },
                        fontSize = 15.sp,
                        color = TajerInk,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(19.dp))
                    HorizontalDivider(color = TajerBorder)
                    Spacer(Modifier.height(17.dp))
                    Text(
                        if (simSetupUnlocked) "شريحة الإرسال" else "تفعيل إعداد الإرسال",
                        fontSize = 14.sp,
                        color = TajerInk,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (simSetupUnlocked) {
                            "اختر الشريحة التي سيستخدمها الهاتف لإرسال روابط التتبع."
                        } else {
                            "اضغط على زر التفعيل أولاً واسمح بالأذونات المطلوبة. ستظهر شرائح الاتصال بعد الموافقة."
                        },
                        color = TajerMuted,
                        fontSize = 11.sp,
                        lineHeight = 20.sp
                    )
                    AnimatedVisibility(
                        visible = simSetupUnlocked,
                        enter = fadeIn(tween(220)) + androidx.compose.animation.expandVertically(tween(280)),
                        exit = fadeOut(tween(140)) + androidx.compose.animation.shrinkVertically(tween(220))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateContentSize(
                                    animationSpec = spring(dampingRatio = 0.86f, stiffness = 320f)
                                )
                        ) {
                            Spacer(Modifier.height(14.dp))
                            if (simChoices.isNotEmpty()) {
                                SimPicker(simChoices, selectedSimId, onSelectSim)
                            } else {
                                NoticePanel(
                                    title = "لا توجد شريحة متاحة",
                                    detail = "أدخل شريحة اتصال فعّالة وتأكد من أن الهاتف يدعم إرسال الرسائل النصية.",
                                    tone = NoticeTone.Warning
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = if (relayRunning) TajerSage else TajerSurface,
                border = BorderStroke(1.dp, if (relayRunning) TajerSage else TajerBorder),
                shape = RectangleShape
            ) {
                Row(
                    modifier = Modifier.padding(17.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.CellTower,
                        contentDescription = null,
                        tint = if (relayRunning) TajerForest else TajerMuted,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (relayRunning) "الإرسال يعمل" else "الإرسال متوقف",
                            fontSize = 13.sp,
                            color = TajerInk,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (relayRunning) "الهاتف متصل وينتظر الطلبات الجديدة." else "فعّل الخدمة ليبدأ الهاتف استقبال الطلبات الجديدة.",
                            fontSize = 10.sp,
                            color = TajerMuted,
                            lineHeight = 18.sp
                        )
                    }
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(if (relayRunning) TajerForest else TajerClay)
                    )
                }
            }
            Spacer(Modifier.height(13.dp))
            if (relayRunning) {
                OutlinedButton(
                    onClick = onStopRelay,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RectangleShape,
                    border = BorderStroke(1.dp, TajerForest),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TajerForest)
                ) {
                    Text("إيقاف الإرسال", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            } else {
                Button(
                    onClick = onStartRelay,
                    enabled = !simSetupUnlocked || (simChoices.isNotEmpty() && selectedSimId >= 0),
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RectangleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TajerForest,
                        contentColor = Color.White,
                        disabledContainerColor = TajerForest.copy(alpha = 0.38f),
                        disabledContentColor = TajerMuted
                    )
                ) {
                    Text(
                        when {
                            !simSetupUnlocked -> "تفعيل إعداد الإرسال"
                            simChoices.isEmpty() -> "لا توجد شريحة متاحة"
                            selectedSimId < 0 -> "اختر شريحة للمتابعة"
                            else -> "بدء إرسال الرسائل"
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            AnimatedVisibility(
                visible = simSetupUnlocked && simChoices.isNotEmpty() && selectedSimId >= 0,
                enter = fadeIn(tween(180)) + androidx.compose.animation.expandVertically(tween(240)),
                exit = fadeOut(tween(120)) + androidx.compose.animation.shrinkVertically(tween(180))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(
                            animationSpec = spring(dampingRatio = 0.88f, stiffness = 340f)
                        )
                ) {
                    Spacer(Modifier.height(11.dp))
                    OutlinedButton(
                        onClick = onSendTestSms,
                        enabled = !testSmsSending,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RectangleShape,
                        border = BorderStroke(1.dp, TajerForest),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = TajerForest,
                            disabledContentColor = TajerForest
                        )
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (testSmsSending) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(17.dp),
                                    color = TajerForest,
                                    strokeWidth = 2.dp
                                )
                            }
                            Text(
                                if (testSmsSending) "جارٍ إرسال الرسالة التجريبية..." else "إرسال رسالة SMS تجريبية",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "يرسل رسالة اختبار واحدة إلى رقم الهاتف المسجل في حساب متجرك، باستخدام شريحة الإرسال المختارة. قد تُحتسب رسوم SMS العادية.",
                        fontSize = 10.sp,
                        lineHeight = 18.sp,
                        color = TajerMuted
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "يتطلب التشغيل اتصالاً بالإنترنت وأذونات إرسال الرسائل والوصول إلى شرائح الاتصال والإشعارات.",
                fontSize = 10.sp,
                color = TajerMuted,
                lineHeight = 18.sp
            )
        }

        AnimatedVisibility(
            visible = statusMessage.isNotBlank(),
            enter = fadeIn(tween(180)) + androidx.compose.animation.expandVertically(tween(180)),
            exit = fadeOut(tween(120)) + androidx.compose.animation.shrinkVertically(tween(120))
        ) {
            Column {
                Spacer(Modifier.height(16.dp))
                NoticePanel(
                    title = "حالة التطبيق",
                    detail = statusMessage,
                    tone = NoticeTone.Neutral
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PageIntro(number: String, title: String, subtitle: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            number,
            color = TajerClay,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(top = 5.dp)
        )
        Spacer(Modifier.width(13.dp))
        Column {
            Text(title, color = TajerInk, fontSize = 23.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(7.dp))
            Text(subtitle, color = TajerMuted, fontSize = 12.sp, lineHeight = 21.sp)
        }
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Column {
        Text(title, color = TajerInk, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, color = TajerMuted, fontSize = 10.sp, lineHeight = 17.sp)
    }
}

@Composable
private fun NativeActionRow(
    icon: ImageVector,
    title: String,
    detail: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .animateContentSize(),
        color = TajerSurface,
        border = BorderStroke(1.dp, TajerBorder),
        shape = RectangleShape
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(TajerSage),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = TajerForest, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 12.sp, color = TajerInk, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(detail, fontSize = 10.sp, color = TajerMuted, lineHeight = 16.sp)
            }
            Text("فتح", fontSize = 10.sp, color = TajerMuted, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, title: String, detail: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(TajerSage),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = TajerForest, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 12.sp, color = TajerInk, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(detail, fontSize = 10.sp, lineHeight = 18.sp, color = TajerMuted)
        }
    }
}

@Composable
private fun SimPicker(
    sims: List<SimChoice>,
    selectedSimId: Int,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = sims.firstOrNull { it.subscriptionId == selectedSimId }

    Column {
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                enabled = sims.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RectangleShape,
                border = BorderStroke(1.dp, TajerBorder),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = TajerIvory,
                    contentColor = TajerInk,
                    disabledContentColor = TajerMuted
                ),
                contentPadding = PaddingValues(horizontal = 13.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        selected?.label ?: if (sims.isEmpty()) "لا توجد شريحة متاحة" else "اختر شريحة الإرسال",
                        fontSize = 12.sp,
                        color = if (sims.isEmpty()) TajerMuted else TajerInk,
                        fontWeight = FontWeight.Medium
                    )
                    Text("⌄", fontSize = 18.sp, color = TajerForest)
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(TajerSurface).widthIn(min = 260.dp),
                shape = RectangleShape,
                containerColor = TajerSurface
            ) {
                sims.forEach { sim ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                sim.label,
                                fontSize = 12.sp,
                                color = if (sim.subscriptionId == selectedSimId) TajerForest else TajerInk,
                                fontWeight = if (sim.subscriptionId == selectedSimId) FontWeight.SemiBold else FontWeight.Normal
                            )
                        },
                        onClick = {
                            expanded = false
                            onSelect(sim.subscriptionId)
                        }
                    )
                }
            }
        }
    }
}

private enum class NoticeTone { Neutral, Warning }

@Composable
private fun NoticePanel(title: String, detail: String, tone: NoticeTone) {
    val background = if (tone == NoticeTone.Warning) Color(0xFFF4E9E2) else TajerSage
    val accent = if (tone == NoticeTone.Warning) TajerClay else TajerForest
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = background,
        shape = RectangleShape
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.padding(top = 5.dp).size(6.dp).background(accent))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, color = TajerInk, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(detail, color = TajerMuted, fontSize = 10.sp, lineHeight = 18.sp)
            }
        }
    }
}

@Composable
private fun BrandMark(modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = TajerForest, shape = RectangleShape) {
        Box(contentAlignment = Alignment.Center) {
            Text("T", fontSize = 23.sp, color = Color.White, fontWeight = FontWeight.Bold)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 6.dp, bottom = 6.dp)
                    .size(4.dp)
                    .background(TajerClay)
            )
        }
    }
}

@Composable
private fun ExitConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = TajerSurface,
            shape = RectangleShape,
            border = BorderStroke(1.dp, TajerBorder)
        ) {
            Column(Modifier.padding(22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BrandMark(modifier = Modifier.size(42.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("تاجر تراك فون", color = TajerInk, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("تأكيد الخروج", color = TajerMuted, fontSize = 10.sp)
                    }
                }
                Spacer(Modifier.height(22.dp))
                Text(
                    "هل تريد الخروج من التطبيق؟",
                    color = TajerInk,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    "سيبقى ربط متجرك وإعدادات الهاتف محفوظة، ويمكنك العودة في أي وقت.",
                    color = TajerMuted,
                    fontSize = 11.sp,
                    lineHeight = 19.sp
                )
                Spacer(Modifier.height(22.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RectangleShape,
                        border = BorderStroke(1.dp, TajerBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TajerForest)
                    ) {
                        Text("البقاء", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RectangleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = TajerForest)
                    ) {
                        Text("خروج", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun PhoneSplashScreen() {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val scale by animateFloatAsState(
        targetValue = if (started) 1f else 0.78f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 230f),
        label = "splash-logo-scale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(420),
        label = "splash-logo-alpha"
    )

    Box(
        modifier = Modifier.fillMaxSize().background(TajerIvory),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BrandMark(
                modifier = Modifier
                    .size(82.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    }
            )
            Spacer(Modifier.height(22.dp))
            Text(
                "تاجر تراك",
                color = TajerInk,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(5.dp))
            Text(
                "SMS PHONE",
                color = TajerMuted,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(30.dp))
            LinearProgressIndicator(
                modifier = Modifier.width(66.dp).height(2.dp),
                color = TajerForest,
                trackColor = TajerBorder
            )
        }
    }
}


private const val TAJERTRACK_WEBSITE_URL = "https://tajertrack.vercel.app"

@Composable
private fun AboutPage() {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 22.dp)
    ) {
        PageIntro(
            number = "03",
            title = "حول التطبيق",
            subtitle = "معلومات سريعة عن تاجر تراك فون."
        )
        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BrandMark(modifier = Modifier.size(58.dp))
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    "تاجر تراك فون",
                    color = TajerInk,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "رفيق متجرك لإرسال روابط التتبع",
                    color = TajerMuted,
                    fontSize = 11.sp
                )
            }
        }
        Spacer(Modifier.height(22.dp))
        Text(
            "تطبيق مرافق لتاجر تراك، يستخدم شريحة هاتفك لإرسال رابط تتبع الطلب إلى العميل عند إنشاء طلب جديد. يعمل في الخلفية لاستقبال طلبات الإرسال، دون إرسال رسائل تحديث متكررة.",
            color = TajerMuted,
            fontSize = 12.sp,
            lineHeight = 22.sp
        )
        Spacer(Modifier.height(24.dp))
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(TAJERTRACK_WEBSITE_URL))
                        )
                    }
                },
            color = TajerSage,
            shape = RectangleShape
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 17.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Link,
                    contentDescription = null,
                    tint = TajerForest,
                    modifier = Modifier.size(21.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "موقع تاجر تراك",
                        color = TajerInk,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "tajertrack.vercel.app",
                        color = TajerForest,
                        fontSize = 11.sp
                    )
                }
                Text(
                    "فتح",
                    color = TajerForest,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            "تاجر تراك فون · تطبيق إرسال الرسائل عبر شريحة متجرك",
            modifier = Modifier.fillMaxWidth(),
            color = TajerMuted,
            fontSize = 10.sp,
            textAlign = TextAlign.Center
        )
    }
}
