package com.tajertrack.phone.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.graphics.RectangleShape

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CellTower
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.SystemUpdate
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.tajertrack.phone.data.ManagerSenderDevice
import com.tajertrack.phone.data.SmsMessageLogEntry
import kotlinx.coroutines.delay
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

private enum class PhoneDestination {
    Home,
    Connection,
    Devices,
    SmsLog,
    Sending,
    Guide,
    About
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneHomeScreen(
    connected: Boolean,
    relayRunning: Boolean,
    relayConnectionState: String,
    storeName: String,
    deviceManagerLaunchRequest: Int,
    managerDevice: Boolean,
    invitationUrl: String,
    invitationStatus: String,
    creatingInvite: Boolean,
    senderDevices: List<ManagerSenderDevice>,
    managerDevicesStatus: String,
    loadingSenderDevices: Boolean,
    updatingManagerDeviceId: String,
    smsMessages: List<SmsMessageLogEntry>,
    smsMessagesStatus: String,
    loadingSmsMessages: Boolean,
    hasMoreSmsMessages: Boolean,
    smsMessageFilter: String,
    smsSearchQuery: String,
    smsDeviceFilter: String,
    statusMessage: String,
    pairing: Boolean,
    simSetupUnlocked: Boolean,
    testSmsSending: Boolean,
    simChoices: List<SimChoice>,
    selectedSimId: Int,
    secondarySimId: Int,
    alternateSimsEnabled: Boolean,
    onConnect: () -> Unit,
    onStartRelay: () -> Unit,
    onStopRelay: () -> Unit,
    onSendTestSms: () -> Unit,
    onToggleSim: (Int) -> Unit,
    onReconnect: () -> Unit,
    onExitApp: () -> Unit,
    isCheckingForUpdates: Boolean,
    updateMessage: String,
    availableUpdateVersion: String?,
    onCheckForUpdates: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onCreateInvite: (String) -> Unit,
    onSendInviteBySms: () -> Unit,
    onShareInvite: () -> Unit,
    onCopyInvite: () -> Unit,
    onCopyDownloadLink: () -> Unit,
    onCopyInviteMessage: () -> Unit,
    onRefreshManagerDevices: () -> Unit,
    onUpdateManagerDevice: (String, String) -> Unit,
    onRefreshManagerSmsMessages: () -> Unit,
    onLoadMoreManagerSmsMessages: () -> Unit,
    onFilterManagerSmsMessages: (String) -> Unit,
    onSearchManagerSmsMessages: (String) -> Unit,
    onFilterManagerSmsDevice: (String) -> Unit
) {
    CompositionDirectionRtl {
        val drawerState = androidx.compose.material3.rememberDrawerState(
            initialValue = androidx.compose.material3.DrawerValue.Closed
        )
        val scope = rememberCoroutineScope()
        var destination by remember { mutableStateOf(PhoneDestination.Home) }
        LaunchedEffect(deviceManagerLaunchRequest) {
            if (deviceManagerLaunchRequest > 0) {
                destination = PhoneDestination.Devices
            }
        }
        var showExitConfirmation by remember { mutableStateOf(false) }
        val pageTitle = when (destination) {
            PhoneDestination.Home -> "الرئيسية"
            PhoneDestination.Connection -> "ربط المتجر"
            PhoneDestination.Devices -> "إدارة أجهزة الإرسال"
            PhoneDestination.SmsLog -> "سجل الرسائل"
            PhoneDestination.Sending -> "إرسال الرسائل"
            PhoneDestination.Guide -> "الشروط والأسئلة"
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
                            managerDevice = managerDevice,
                            isCheckingForUpdates = isCheckingForUpdates,
                            updateMessage = updateMessage,
                            availableUpdateVersion = availableUpdateVersion,
                            onCheckForUpdates = onCheckForUpdates,
                            onDownloadUpdate = onDownloadUpdate,
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
                                (expandVertically(
                                    expandFrom = Alignment.CenterVertically,
                                    animationSpec = tween(240)
                                ) + expandHorizontally(
                                    expandFrom = Alignment.End,
                                    animationSpec = tween(240)
                                )) togetherWith
                                    (shrinkVertically(
                                        shrinkTowards = Alignment.CenterVertically,
                                        animationSpec = tween(180)
                                    ) + shrinkHorizontally(
                                        shrinkTowards = Alignment.Start,
                                        animationSpec = tween(180)
                                    ))
                            }
                        ) { target ->
                            when (target) {
                                PhoneDestination.Home -> HomePage(
                                    connected = connected,
                                    managerDevice = managerDevice,
                                    relayRunning = relayRunning,
                                    relayConnectionState = relayConnectionState,
                                    storeName = storeName,
                                    onOpenConnection = { destination = PhoneDestination.Connection },
                                    onOpenDevices = { destination = PhoneDestination.Devices },
                                    onOpenSmsLog = { destination = PhoneDestination.SmsLog },
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
                                PhoneDestination.Devices -> if (managerDevice) DeviceManagementPage(
                                    storeName = storeName,
                                    invitationUrl = invitationUrl,
                                    invitationStatus = invitationStatus,
                                    creatingInvite = creatingInvite,
                                    senderDevices = senderDevices,
                                    managerDevicesStatus = managerDevicesStatus,
                                    loadingSenderDevices = loadingSenderDevices,
                                    updatingManagerDeviceId = updatingManagerDeviceId,
                                    onRefreshManagerDevices = onRefreshManagerDevices,
                                    onUpdateManagerDevice = onUpdateManagerDevice,
                                    onCreateInvite = onCreateInvite,
                                    onSendInviteBySms = onSendInviteBySms,
                                    onShareInvite = onShareInvite,
                                    onCopyInvite = onCopyInvite,
                                    onCopyDownloadLink = onCopyDownloadLink,
                                    onCopyInviteMessage = onCopyInviteMessage
                                ) else ConnectionPage(
                                    connected = connected,
                                    storeName = storeName,
                                    pairing = pairing,
                                    statusMessage = statusMessage,
                                    onConnect = onConnect,
                                    onContinue = { destination = PhoneDestination.Sending }
                                )
                                PhoneDestination.SmsLog -> if (managerDevice) SmsMessageLogPage(
                                    messages = smsMessages,
                                    senderDevices = senderDevices,
                                    statusMessage = smsMessagesStatus,
                                    loading = loadingSmsMessages,
                                    hasMore = hasMoreSmsMessages,
                                    selectedFilter = smsMessageFilter,
                                    searchQuery = smsSearchQuery,
                                    selectedDeviceId = smsDeviceFilter,
                                    onRefresh = onRefreshManagerSmsMessages,
                                    onLoadMore = onLoadMoreManagerSmsMessages,
                                    onFilterChange = onFilterManagerSmsMessages,
                                    onSearch = onSearchManagerSmsMessages,
                                    onDeviceFilterChange = onFilterManagerSmsDevice
                                ) else ConnectionPage(
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
                                    relayConnectionState = relayConnectionState,
                                    storeName = storeName,
                                    statusMessage = statusMessage,
                                    simSetupUnlocked = simSetupUnlocked,
                                    testSmsSending = testSmsSending,
                                    simChoices = simChoices,
                                    selectedSimId = selectedSimId,
                                    secondarySimId = secondarySimId,
                                    alternateSimsEnabled = alternateSimsEnabled,
                                    onToggleSim = onToggleSim,
                                    onStartRelay = onStartRelay,
                                    onStopRelay = onStopRelay,
                                    onSendTestSms = onSendTestSms,
                                    onReconnect = {
                                        destination = PhoneDestination.Connection
                                        onReconnect()
                                    }
                                )
                                PhoneDestination.Guide -> GuidePage()
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
    managerDevice: Boolean,
    isCheckingForUpdates: Boolean,
    updateMessage: String,
    availableUpdateVersion: String?,
    onCheckForUpdates: () -> Unit,
    onDownloadUpdate: () -> Unit,
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
        if (managerDevice) {
            DrawerItem(
                title = "إدارة أجهزة الإرسال",
                icon = Icons.Outlined.Link,
                selected = destination == PhoneDestination.Devices,
                onClick = { onNavigate(PhoneDestination.Devices) }
            )
            DrawerItem(
                title = "سجل الرسائل",
                icon = Icons.Outlined.Sms,
                selected = destination == PhoneDestination.SmsLog,
                onClick = { onNavigate(PhoneDestination.SmsLog) }
            )
        }
        DrawerItem(
            title = "إرسال الرسائل",
            icon = Icons.Outlined.Sms,
            selected = destination == PhoneDestination.Sending,
            onClick = { onNavigate(PhoneDestination.Sending) }
        )
        DrawerItem(
            title = "الشروط والأسئلة",
            icon = Icons.Outlined.HelpOutline,
            selected = destination == PhoneDestination.Guide,
            onClick = { onNavigate(PhoneDestination.Guide) }
        )
        DrawerItem(
            title = "حول التطبيق",
            icon = Icons.Outlined.Info,
            selected = destination == PhoneDestination.About,
            onClick = { onNavigate(PhoneDestination.About) }
        )

        Spacer(Modifier.height(22.dp))
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 22.dp),
            color = TajerBorder
        )
        Spacer(Modifier.height(16.dp))
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp)) {
            Button(
                onClick = onCheckForUpdates,
                enabled = !isCheckingForUpdates,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RectangleShape,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TajerSage,
                    contentColor = TajerForestDeep,
                    disabledContainerColor = TajerSage,
                    disabledContentColor = TajerMuted
                )
            ) {
                if (isCheckingForUpdates) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(17.dp),
                        color = TajerForest,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Outlined.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(9.dp))
                Text(
                    if (isCheckingForUpdates) "جارٍ التحقق من التحديثات..." else "التحقق من التحديثات",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (updateMessage.isNotBlank()) {
                Spacer(Modifier.height(9.dp))
                Text(
                    updateMessage,
                    color = if (availableUpdateVersion != null) TajerForest else TajerMuted,
                    fontSize = 10.sp,
                    lineHeight = 17.sp
                )
            }

            if (availableUpdateVersion != null) {
                Spacer(Modifier.height(11.dp))
                Button(
                    onClick = onDownloadUpdate,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RectangleShape,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TajerForest,
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(9.dp))
                    Text(
                        "تنزيل الإصدار $availableUpdateVersion",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(Modifier.height(26.dp))
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 22.dp),
            color = TajerBorder
        )
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

private fun relayConnectionLabel(state: String, relayRunning: Boolean): String = when (state) {
    "connected" -> "متصل"
    "sending" -> "جارٍ الإرسال"
    "reporting" -> "تأكيد النتيجة"
    "connecting" -> "جارٍ الاتصال"
    "retrying" -> "يعاد الاتصال"
    "offline" -> "لا يوجد إنترنت"
    "paused" -> "متوقف مؤقتاً"
    "needs_repair", "needs_pairing" -> "يلزم إعادة الربط"
    "needs_user_start" -> "يلزم تشغيل التطبيق"
    "disabled" -> "موقوف من الإدارة"
    "attention" -> "يحتاج مراجعة"
    "stopped" -> "متوقف"
    else -> if (relayRunning) "جارٍ الاتصال" else "متوقف"
}

@Composable
private fun HomePage(
    connected: Boolean,
    managerDevice: Boolean,
    relayRunning: Boolean,
    relayConnectionState: String,
    storeName: String,
    onOpenConnection: () -> Unit,
    onOpenSending: () -> Unit,
    onOpenDevices: () -> Unit,
    onOpenSmsLog: () -> Unit
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
                            relayConnectionLabel(relayConnectionState, relayRunning),
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
                    when {
                        !connected -> "اربط حساب متجرك مرة واحدة، ثم استخدم شريحتك لإرسال رابط التتبع للعميل."
                        managerDevice -> "هذا هو الهاتف الرئيسي لمتجرك. يمكنك إدارة هواتف الإرسال وإرسال رسائل التتبع من شريحتك."
                        else -> "هذا الهاتف مرتبط كجهاز إرسال فقط. يمكنه إرسال رسائل التتبع، ولا يملك صلاحية إدارة الدعوات أو الهواتف الأخرى."
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
                    HeroStatus(
                        "اتصال الخادم",
                        relayConnectionLabel(relayConnectionState, relayRunning),
                        relayConnectionState == "connected" || relayConnectionState == "sending"
                    )
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
            if (managerDevice) {
                Spacer(Modifier.height(10.dp))
                NativeActionRow(
                    icon = Icons.Outlined.Link,
                    title = "إدارة أجهزة الإرسال",
                    detail = "إنشاء دعوات وربط هواتف أخرى بخطوط الشبكات",
                    onClick = onOpenDevices
                )
                Spacer(Modifier.height(10.dp))
                NativeActionRow(
                    icon = Icons.Outlined.Sms,
                    title = "سجل الرسائل",
                    detail = "متابعة رسائل التتبع من جميع أجهزة الإرسال",
                    onClick = onOpenSmsLog
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
    relayConnectionState: String,
    storeName: String,
    statusMessage: String,
    simSetupUnlocked: Boolean,
    testSmsSending: Boolean,
    simChoices: List<SimChoice>,
    selectedSimId: Int,
    secondarySimId: Int,
    alternateSimsEnabled: Boolean,
    onToggleSim: (Int) -> Unit,
    onStartRelay: () -> Unit,
    onStopRelay: () -> Unit,
    onSendTestSms: () -> Unit,
    onReconnect: () -> Unit
) {
    val enabledSimIds = buildSet {
        if (selectedSimId >= 0) add(selectedSimId)
        if (alternateSimsEnabled && secondarySimId >= 0) add(secondarySimId)
    }

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
                simSetupUnlocked -> "اختر الشرائح التي تريد استخدامها، ثم شغّل الإرسال."
                else -> "فعّل الإعداد واسمح بالأذونات المطلوبة لإظهار شرائح الاتصال."
            }
        )
        Spacer(Modifier.height(14.dp))
        ServiceStatusPanel(
            relayRunning = relayRunning,
            relayConnectionState = relayConnectionState,
            statusMessage = statusMessage
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
                        if (simSetupUnlocked) "شرائح الإرسال" else "تفعيل إعداد الإرسال",
                        fontSize = 14.sp,
                        color = TajerInk,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (simSetupUnlocked) {
                            "فعّل شريحة واحدة على الأقل. يفضّل التطبيق الخط المطابق لشبكة العميل، ويغطي الهاتف الرئيسي الشبكات التي لا تملك خطاً مخصصاً."
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
                                SimPicker(
                                    sims = simChoices,
                                    enabledSimIds = enabledSimIds,
                                    onToggle = onToggleSim
                                )
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
                                if (testSmsSending) "جارٍ إرسال الرسالة التجريبية..." else if (alternateSimsEnabled) "اختبار الشريحة الأساسية" else "إرسال رسالة SMS تجريبية",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
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
private fun SimPicker(
    sims: List<SimChoice>,
    enabledSimIds: Set<Int>,
    onToggle: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val enabledLabels = sims.filter { it.subscriptionId in enabledSimIds }.map { it.label }
    val summary = if (enabledLabels.isEmpty()) "اختر الشرائح المفعّلة" else enabledLabels.joinToString("، ")

    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth().clickable(enabled = sims.isNotEmpty()) { expanded = !expanded },
            color = TajerSurface,
            border = BorderStroke(1.dp, if (expanded) TajerForest else TajerBorder),
            shape = RectangleShape
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("الشرائح المفعّلة", color = TajerMuted, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        summary,
                        color = if (enabledLabels.isEmpty()) TajerMuted else TajerForestDeep,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        fontWeight = if (enabledLabels.isEmpty()) FontWeight.Medium else FontWeight.SemiBold,
                        maxLines = 2
                    )
                }
                Icon(
                    Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "إغلاق قائمة الشرائح" else "فتح قائمة الشرائح",
                    tint = TajerForest,
                    modifier = Modifier.size(23.dp).graphicsLayer { rotationZ = if (expanded) 180f else 0f }
                )
            }
        }
        AnimatedVisibility(
            visible = expanded && sims.isNotEmpty(),
            enter = fadeIn(tween(140)) + androidx.compose.animation.expandVertically(tween(180)),
            exit = fadeOut(tween(100)) + androidx.compose.animation.shrinkVertically(tween(150))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().background(TajerSurface).border(1.dp, TajerBorder, RectangleShape)
            ) {
                sims.forEachIndexed { index, sim ->
                    val enabled = sim.subscriptionId in enabledSimIds
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onToggle(sim.subscriptionId) }
                            .padding(horizontal = 14.dp, vertical = 15.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            sim.label,
                            modifier = Modifier.weight(1f),
                            fontSize = 13.sp,
                            color = if (enabled) TajerForest else TajerInk,
                            fontWeight = if (enabled) FontWeight.SemiBold else FontWeight.Medium
                        )
                        if (enabled) {
                            Icon(Icons.Outlined.Check, contentDescription = "مفعّلة", tint = TajerForest, modifier = Modifier.size(20.dp))
                        }
                    }
                    if (index < sims.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp), color = TajerBorder)
                    }
                }
            }
        }
    }
}

@Composable
private fun ServiceStatusPanel(
    relayRunning: Boolean,
    relayConnectionState: String,
    statusMessage: String
) {
    val stateTitle = if (relayRunning) "خدمة الإرسال تعمل" else "خدمة الإرسال متوقفة"
    val stateColor = if (relayRunning) TajerForest else TajerClay
    val detail = statusMessage.ifBlank {
        if (relayRunning) "الهاتف جاهز لاستقبال طلبات الرسائل الجديدة."
        else "شغّل الخدمة لاستقبال طلبات الإرسال على هذا الهاتف."
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (relayRunning) TajerSage else TajerSurface,
        border = BorderStroke(1.dp, if (relayRunning) Color(0xFFC9D8C8) else TajerBorder),
        shape = RectangleShape
    ) {
        Column(Modifier.padding(horizontal = 17.dp, vertical = 15.dp)) {
            Text("حالة خدمة الإرسال", color = TajerMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stateTitle,
                    modifier = Modifier.weight(1f),
                    color = stateColor,
                    fontSize = 17.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Box(Modifier.size(9.dp).background(if (relayRunning) TajerForest else TajerClay))
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "اتصال الخادم: ${relayConnectionLabel(relayConnectionState, relayRunning)}",
                color = TajerInk,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(detail, color = TajerMuted, fontSize = 12.sp, lineHeight = 21.sp)
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


@Composable
private fun DeviceManagementPage(
    storeName: String,
    invitationUrl: String,
    invitationStatus: String,
    creatingInvite: Boolean,
    senderDevices: List<ManagerSenderDevice>,
    managerDevicesStatus: String,
    loadingSenderDevices: Boolean,
    updatingManagerDeviceId: String,
    onRefreshManagerDevices: () -> Unit,
    onUpdateManagerDevice: (String, String) -> Unit,
    onCreateInvite: (String) -> Unit,
    onSendInviteBySms: () -> Unit,
    onShareInvite: () -> Unit,
    onCopyInvite: () -> Unit,
    onCopyDownloadLink: () -> Unit,
    onCopyInviteMessage: () -> Unit
) {
    var selectedOperator by remember { mutableStateOf("") }
    var pendingRevokeDevice by remember { mutableStateOf<ManagerSenderDevice?>(null) }
    val operators = listOf(
        "djezzy" to "Djezzy",
        "mobilis" to "Mobilis",
        "ooredoo" to "Ooredoo"
    )
    val deviceListScrollState = rememberScrollState()
    var displayedSenderDevices by remember { mutableStateOf(senderDevices) }
    var exitingSenderDeviceIds by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(senderDevices) {
        val latestIds = senderDevices.map { it.deviceId }.toSet()
        val removedDevices = displayedSenderDevices.filterNot { it.deviceId in latestIds }
        if (removedDevices.isNotEmpty()) {
            exitingSenderDeviceIds = removedDevices.map { it.deviceId }.toSet()
            displayedSenderDevices = senderDevices + removedDevices
            delay(420)
        }
        displayedSenderDevices = senderDevices
        exitingSenderDeviceIds = emptySet()
    }
    val sizeEnter = expandVertically(
        expandFrom = Alignment.Top,
        animationSpec = spring(stiffness = 420f)
    ) + expandHorizontally(
        expandFrom = Alignment.End,
        animationSpec = spring(stiffness = 420f)
    )
    val sizeExit = shrinkVertically(
        shrinkTowards = Alignment.Top,
        animationSpec = spring(stiffness = 420f)
    ) + shrinkHorizontally(
        shrinkTowards = Alignment.End,
        animationSpec = spring(stiffness = 420f)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp)
            .animateContentSize(animationSpec = spring(stiffness = 420f))
    ) {
        PageIntro(
            number = "03",
            title = "إدارة أجهزة الإرسال",
            subtitle = "يمكن للهاتف الرئيسي تغطية الشبكات غير المرتبطة بخط مخصص. أضف خطوطاً لكل شبكة للاستفادة من أسعارها حسب باقة المشغّل."
        )
        Spacer(Modifier.height(20.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = TajerForest,
            shape = RectangleShape
        ) {
            Column(Modifier.padding(18.dp)) {
                Text("الهاتف الرئيسي", color = Color(0xFFDCE7DC), fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    storeName.ifBlank { "متجرك" },
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "هذا الهاتف يدير خطوط الإرسال ويمكنه تغطية أي شبكة لا تملك خطاً مخصصاً مفعّلاً. الهواتف المدعوة ترسل عبر الشبكة المحددة لها فقط.",
                    color = Color(0xFFDCE7DC),
                    fontSize = 11.sp,
                    lineHeight = 19.sp
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionHeading("الهواتف المرتبطة", "حالة خطوط الإرسال لهذا المتجر")
            TextButton(
                onClick = onRefreshManagerDevices,
                enabled = !loadingSenderDevices
            ) {
                Text(if (loadingSenderDevices) "جارٍ التحديث..." else "تحديث", color = TajerForest, fontSize = 11.sp)
            }
        }
        AnimatedVisibility(
            visible = managerDevicesStatus.isNotBlank(),
            enter = sizeEnter,
            exit = sizeExit
        ) {
            Text(
                managerDevicesStatus,
                color = TajerMuted,
                fontSize = 10.sp,
                lineHeight = 17.sp,
                modifier = Modifier.fillMaxWidth().animateContentSize()
            )
        }
        AnimatedVisibility(
            visible = loadingSenderDevices,
            enter = sizeEnter,
            exit = sizeExit
        ) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                color = TajerForest,
                trackColor = TajerSage
            )
        }
        AnimatedVisibility(
            visible = senderDevices.isEmpty() && displayedSenderDevices.isEmpty() && !loadingSenderDevices,
            enter = sizeEnter,
            exit = sizeExit
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).animateContentSize(),
                color = TajerSurface,
                border = BorderStroke(1.dp, TajerBorder),
                shape = RectangleShape
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.CellTower,
                        contentDescription = null,
                        tint = TajerMuted,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "لا توجد خطوط إرسال ظاهرة حالياً.",
                        color = TajerMuted,
                        fontSize = 11.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = displayedSenderDevices.isNotEmpty(),
            enter = sizeEnter,
            exit = sizeExit
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 9.dp)
                    .height(286.dp)
                    .verticalScroll(deviceListScrollState)
            ) {
                displayedSenderDevices.forEach { device ->
                    AnimatedVisibility(
                        visible = device.deviceId !in exitingSenderDeviceIds,
                        enter = sizeEnter,
                        exit = sizeExit
                    ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().animateContentSize(
                            animationSpec = spring(stiffness = 420f)
                        ),
                        color = TajerSurface,
                        border = BorderStroke(1.dp, TajerBorder),
                        shape = RectangleShape
                    ) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(13.dp)
                                .animateContentSize(animationSpec = spring(stiffness = 420f))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    Modifier
                                        .weight(1f)
                                        .animateContentSize()
                                ) {
                                    Text(
                                        device.displayName,
                                        color = TajerInk,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        when (device.operatorCode) {
                                            "djezzy" -> "Djezzy"
                                            "mobilis" -> "Mobilis"
                                            "ooredoo" -> "Ooredoo"
                                            else -> "شبكة غير محددة"
                                        } + if (device.isPrimary) " · خط الهاتف الرئيسي" else " · هاتف مدعو",
                                        color = TajerMuted,
                                        fontSize = 10.sp,
                                        lineHeight = 16.sp
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Column(
                                    horizontalAlignment = Alignment.End,
                                    modifier = Modifier.animateContentSize()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            Modifier
                                                .size(7.dp)
                                                .background(
                                                    when {
                                                        device.revoked -> TajerMuted
                                                        !device.enabled -> Color(0xFFB66B4F)
                                                        device.isOnline -> Color(0xFF287A45)
                                                        else -> TajerMuted.copy(alpha = 0.55f)
                                                    },
                                                    RectangleShape
                                                )
                                        )
                                        Text(
                                            when {
                                                device.revoked -> "ملغى"
                                                !device.enabled -> "موقوف"
                                                device.isOnline -> "متصل الآن"
                                                else -> "غير متصل"
                                            },
                                            color = when {
                                                device.revoked -> TajerMuted
                                                !device.enabled -> Color(0xFF9B563F)
                                                device.isOnline -> TajerForest
                                                else -> TajerMuted
                                            },
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                            AnimatedVisibility(
                                visible = !device.revoked,
                                enter = sizeEnter,
                                exit = sizeExit
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            onUpdateManagerDevice(
                                                device.deviceId,
                                                if (device.enabled) "pause" else "resume"
                                            )
                                        },
                                        enabled = updatingManagerDeviceId != device.deviceId,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(40.dp)
                                            .animateContentSize(),
                                        shape = RectangleShape,
                                        border = BorderStroke(1.dp, TajerBorder),
                                        contentPadding = PaddingValues(horizontal = 6.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = TajerForest
                                        )
                                    ) {
                                        Text(
                                            when {
                                                updatingManagerDeviceId == device.deviceId -> "جارٍ التحديث..."
                                                device.enabled -> "إيقاف الخط"
                                                else -> "استئناف الخط"
                                            },
                                            fontSize = 10.sp,
                                            maxLines = 1
                                        )
                                    }
                                    TextButton(
                                        onClick = { pendingRevokeDevice = device },
                                        enabled = updatingManagerDeviceId != device.deviceId,
                                        modifier = Modifier.height(40.dp).animateContentSize()
                                    ) {
                                        Text(
                                            "إلغاء الربط",
                                            color = Color(0xFF9B563F),
                                            fontSize = 10.sp,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(9.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(26.dp))
        HorizontalDivider(color = TajerBorder)
        Spacer(Modifier.height(18.dp))
        SectionHeading("إنشاء دعوة جديدة", "اختر شبكة الشريحة الموجودة في الهاتف الذي تريد ربطه.")
        Spacer(Modifier.height(7.dp))
        Text(
            "لأفضل تكلفة محتملة، اربط خطاً من كل شبكة: Djezzy وMobilis وOoredoo. يستخدم النظام الخط المخصص والمفعّل لشبكة العميل، ويغطي الهاتف الرئيسي الشبكات التي لا تملك خطاً مخصصاً. قد تختلف الأسعار حسب عرض شركة الاتصالات.",
            color = TajerMuted,
            fontSize = 11.sp,
            lineHeight = 19.sp
        )
        Spacer(Modifier.height(11.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            operators.forEach { (code, label) ->
                val selected = selectedOperator == code
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .animateContentSize(animationSpec = spring(stiffness = 420f))
                        .clickable { selectedOperator = code },
                    color = if (selected) TajerForest else TajerSurface,
                    border = BorderStroke(1.dp, if (selected) TajerForest else TajerBorder),
                    shape = RectangleShape
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 5.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            label,
                            color = if (selected) Color.White else TajerInk,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        AnimatedVisibility(
                            visible = selected,
                            enter = sizeEnter,
                            exit = sizeExit
                        ) {
                            Row {
                                Spacer(Modifier.width(5.dp))
                                Icon(
                                    Icons.Outlined.Check,
                                    contentDescription = "محدد",
                                    tint = Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(13.dp))
        Button(
            onClick = { onCreateInvite(selectedOperator) },
            enabled = !creatingInvite && selectedOperator.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(54.dp).animateContentSize(),
            shape = RectangleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = TajerForest,
                contentColor = Color.White,
                disabledContainerColor = TajerForest,
                disabledContentColor = Color.White.copy(alpha = 0.7f)
            )
        ) {
            if (creatingInvite) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(10.dp))
            }
            Text(
                when {
                    creatingInvite -> "جارٍ إنشاء الدعوة..."
                    selectedOperator.isBlank() -> "اختر الشبكة أولاً"
                    else -> "إنشاء رابط الدعوة"
                },
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        AnimatedVisibility(
            visible = invitationStatus.isNotBlank(),
            enter = sizeEnter,
            exit = sizeExit
        ) {
            Column(Modifier.fillMaxWidth().padding(top = 12.dp).animateContentSize()) {
                Text(
                    invitationStatus,
                    color = if (invitationUrl.isNotBlank()) TajerForest else TajerMuted,
                    fontSize = 11.sp,
                    lineHeight = 19.sp
                )
            }
        }

        AnimatedVisibility(
            visible = invitationUrl.isNotBlank(),
            enter = sizeEnter,
            exit = sizeExit
        ) {
            Spacer(Modifier.height(22.dp))
            HorizontalDivider(color = TajerBorder)
            Spacer(Modifier.height(17.dp))
            SectionHeading("الدعوة جاهزة للمشاركة", "تنتهي صلاحيتها بعد 30 دقيقة، وتُستخدم مرة واحدة فقط.")
            Spacer(Modifier.height(10.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = TajerSurface,
                border = BorderStroke(1.dp, TajerBorder),
                shape = RectangleShape
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        invitationUrl,
                        color = TajerForest,
                        fontSize = 11.sp,
                        lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(9.dp))
                    Text(
                        "تشمل الرسالة رابط تحميل التطبيق ورابط الدعوة الخاص بهذه الشبكة.",
                        color = TajerMuted,
                        fontSize = 10.sp,
                        lineHeight = 17.sp
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onSendInviteBySms,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RectangleShape,
                colors = ButtonDefaults.buttonColors(containerColor = TajerForest)
            ) {
                Icon(Icons.Outlined.Sms, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(9.dp))
                Text("إرسال عبر SMS", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onShareInvite,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RectangleShape,
                border = BorderStroke(1.dp, TajerBorder),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TajerForest)
            ) {
                Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(9.dp))
                Text("مشاركة عبر Telegram أو التطبيقات الأخرى", fontSize = 11.sp)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onCopyInviteMessage,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RectangleShape,
                border = BorderStroke(1.dp, TajerBorder),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TajerForest)
            ) {
                Text("نسخ الرسالة الكاملة", fontSize = 11.sp)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCopyInvite,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RectangleShape,
                    border = BorderStroke(1.dp, TajerBorder),
                    contentPadding = PaddingValues(horizontal = 7.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TajerForest)
                ) {
                    Text("نسخ رابط الدعوة", fontSize = 10.sp)
                }
                OutlinedButton(
                    onClick = onCopyDownloadLink,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RectangleShape,
                    border = BorderStroke(1.dp, TajerBorder),
                    contentPadding = PaddingValues(horizontal = 7.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TajerForest)
                ) {
                    Text("نسخ رابط التحميل", fontSize = 10.sp)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = TajerBorder)
        Spacer(Modifier.height(13.dp))
        Text(
            "كل هاتف مدعو يرتبط بشبكة واحدة فقط. لإضافة شبكة أخرى على هاتف مزدوج الشريحة، أنشئ دعوة ثانية للشبكة الأخرى وافتحها على الهاتف نفسه.",
            color = TajerMuted,
            fontSize = 10.sp,
            lineHeight = 18.sp
        )
        Spacer(Modifier.height(18.dp))
    }

    pendingRevokeDevice?.let { device ->
        Dialog(onDismissRequest = { pendingRevokeDevice = null }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(animationSpec = spring(stiffness = 420f)),
                color = TajerSurface,
                border = BorderStroke(1.dp, TajerBorder),
                shape = RectangleShape
            ) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text(
                        "إلغاء ربط الهاتف",
                        color = TajerInk,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "لن يستطيع هذا الهاتف إرسال رسائل التتبع بعد الإلغاء. يمكنك ربط هاتف آخر بالشبكة نفسها بدعوة جديدة.",
                        color = TajerMuted,
                        fontSize = 12.sp,
                        lineHeight = 20.sp
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { pendingRevokeDevice = null },
                            modifier = Modifier.weight(1f).height(44.dp).animateContentSize()
                        ) {
                            Text("إبقاء الهاتف", color = TajerForest, fontSize = 12.sp)
                        }
                        Button(
                            onClick = {
                                pendingRevokeDevice = null
                                onUpdateManagerDevice(device.deviceId, "revoke")
                            },
                            modifier = Modifier.weight(1f).height(44.dp).animateContentSize(),
                            shape = RectangleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF9B563F),
                                contentColor = Color.White
                            )
                        ) {
                            Text("إلغاء الربط", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GuidePage() {
    val questions = listOf(
        "ما وظيفة تاجر تراك فون؟" to
            "يرسل رابط تتبع الطلب إلى العميل برسالة SMS واحدة عند إنشاء طلب جديد في متجرك. لا يرسل رسائل متكررة عند تغيّر حالة الطلب.",
        "كيف أربط الهاتف الرئيسي بمتجري؟" to
            "افتح تاجر تراك فون واضغط ربط المتجر. سجّل الدخول بحساب التاجر في المتصفح ووافق على جعل الهاتف رئيسياً. لا يُربط الهاتف الرئيسي بشبكة واحدة؛ يمكنه تغطية الشبكات التي لا تملك خطاً مخصصاً مفعّلاً.",
        "ما الفرق بين الهاتف الرئيسي والهاتف المدعو؟" to
            "الهاتف الرئيسي يُربط بعد تسجيل الدخول بحساب التاجر، ويمكنه إرسال الرسائل وإنشاء الدعوات وإدارة خطوط الإرسال. الهاتف المدعو يُربط برابط مؤقت ولا يحتاج إلى تسجيل الدخول؛ يمكنه إرسال الرسائل للشبكة المحددة فقط، ولا يستطيع إنشاء دعوات أو إدارة المتجر.",
        "ما شبكات الهاتف المدعومة؟" to
            "يدعم التطبيق Djezzy وMobilis وOoredoo. يفضّل النظام خطاً مخصصاً ومفعّلاً لشبكة العميل. إذا لم يوجد، يمكن للهاتف الرئيسي استخدام شريحته المفعّلة لإرسال الرسالة إلى تلك الشبكة؛ وقد تختلف التكلفة حسب باقة الشريحة.",
        "هل يرسل التطبيق إلى كل أنواع الأرقام؟" to
            "يدعم أرقام الهواتف المحمولة الجزائرية التابعة للشبكات المدعومة. الأرقام غير الصالحة أو التي لا تنتمي إلى شبكة مدعومة لن تُرسل.",
        "كيف أستخدم هاتفاً بشريحتي اتصال؟" to
            "إذا كان الهاتف الرئيسي يحتوي على شريحة واحدة، يمكنه إرسال الرسائل إلى الشبكات الثلاث عند عدم وجود خط مخصص للشبكة المطلوبة. وإذا احتوى على شريحتين، فعّل واحدة أو الاثنتين؛ يحاول التطبيق أولاً استخدام الشريحة المطابقة لشبكة العميل، ثم يستخدم الشريحة الأساسية للشبكات غير المغطاة. لا يرسل رسالتين للطلب نفسه.",
        "ماذا يحدث إذا لم أربط إلا رقماً واحداً؟" to
            "يستطيع الهاتف الرئيسي إرسال الرسائل إلى Djezzy وMobilis وOoredoo من شريحته المفعّلة. عند إضافة خط مخصص ومفعّل لشبكة معينة، يستخدمه النظام لهذه الشبكة ويترك للهاتف الرئيسي تغطية الشبكات الأخرى غير المربوطة.",
        "هل يمكنني اختبار الإرسال؟" to
            "زر الاختبار يرسل رسالة واحدة إلى رقم الهاتف المسجّل في حساب المتجر باستخدام الشريحة الأساسية المحددة. لا يختبر الشريحة الثانية تلقائياً، وقد تُحتسب رسوم SMS عادية.",
        "هل الرسائل مجانية؟" to
            "تُرسل الرسائل من رصيد شريحة الهاتف أو باقتها. قد يخصم مشغّل الشبكة تكلفة SMS حسب عرضك؛ لا يستطيع التطبيق معرفة رصيدك أو ضمان أن الرسائل مجانية.",
        "هل تأكيد Android يعني أن العميل استلم الرسالة؟" to
            "لا. يعني ذلك أن Android قبل طلب الإرسال فقط، وليس تأكيداً لوصول الرسالة إلى هاتف العميل. عندما تكون النتيجة غير مؤكدة، لا يعيد النظام الإرسال تلقائياً حتى لا تصل رسالة مكررة.",
        "هل يعمل التطبيق عندما تكون الشاشة مغلقة؟" to
            "يمكنه العمل في الخلفية أثناء تشغيل خدمة الإرسال، وستظهر إشعارات الخدمة من Android. اترك الهاتف متصلاً بالإنترنت وبشبكة الهاتف. قد يستهلك التشغيل المستمر البطارية، لذلك يُفضّل إبقاء الهاتف على الشاحن عند الحاجة.",
        "ماذا أفعل بعد إعادة تشغيل الهاتف؟" to
            "بعد إطفاء الهاتف أو إعادة تشغيله بالكامل، افتح تاجر تراك فون واضغط بدء إرسال الرسائل من جديد. لا يعيد التطبيق تشغيل الخدمة تلقائياً بعد إعادة تشغيل الهاتف.",
        "كيف أرسل دعوة إلى هاتف آخر؟" to
            "من الهاتف الرئيسي افتح إدارة أجهزة الإرسال، واختر شبكة الهاتف الآخر، ثم أنشئ الدعوة. يمكنك إرسالها برسالة SMS أو مشاركتها عبر Telegram والتطبيقات الأخرى أو نسخ رابط الدعوة والرسالة كاملة. تتضمن الرسالة رابط تحميل التطبيق ورابط الدعوة المؤقت.",
        "هل يمكن للهاتف المدعو إنشاء دعوات؟" to
            "لا. صلاحية الهاتف المدعو مقتصرة على إرسال رسائل التتبع من خطه. إنشاء الدعوات وإيقاف خطوط الهواتف الأخرى وإلغاء ربطها متاح للهاتف الرئيسي فقط.",
        "هل يمكنني إيقاف خط أو تغيير الهاتف؟" to
            "من الهاتف الرئيسي افتح إدارة أجهزة الإرسال لتحديث حالة كل خط أو إيقافه أو إلغاء ربطه. عند فقدان الهاتف الرئيسي، سجّل الدخول بحساب التاجر من هاتف جديد وأكّد استبدال الهاتف الرئيسي السابق."
    )
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 21.dp, vertical = 22.dp)
    ) {
        PageIntro(
            number = "04",
            title = "الشروط والأسئلة",
            subtitle = "كل ما تحتاج معرفته عن الربط والشرائح والإرسال، بلغة واضحة."
        )
        Spacer(Modifier.height(16.dp))
        questions.forEachIndexed { index, item ->
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
                Text(
                    "سؤال ${(index + 1).toString().padStart(2, '0')}",
                    color = TajerClay,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.7.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(item.first, color = TajerForestDeep, fontSize = 15.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text("الإجابة", color = TajerMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(3.dp))
                Text(item.second, color = TajerInk, fontSize = 13.sp, lineHeight = 24.sp)
            }
            if (index < questions.lastIndex) HorizontalDivider(color = TajerBorder)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "تاجر تراك فون · دليل الاستخدام",
            modifier = Modifier.fillMaxWidth(),
            color = TajerMuted,
            textAlign = TextAlign.Center,
            fontSize = 10.sp
        )
        Spacer(Modifier.height(12.dp))
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
            number = "05",
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

private val smsLogFilters = listOf(
    "all" to "الكل",
    "pending" to "قيد الانتظار",
    "submitted" to "تم الإرسال",
    "delivered" to "تم التسليم",
    "awaiting_delivery" to "بانتظار التسليم",
    "delivery_failed" to "تعذر التسليم",
    "delivery_unknown" to "تسليم غير مؤكد",
    "failed" to "فشل / لم يُرسل",
    "unknown" to "نتيجة الإرسال غير مؤكدة"
)

@Composable
private fun SmsMessageLogPage(
    messages: List<SmsMessageLogEntry>,
    senderDevices: List<ManagerSenderDevice>,
    statusMessage: String,
    loading: Boolean,
    hasMore: Boolean,
    selectedFilter: String,
    searchQuery: String,
    selectedDeviceId: String,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onFilterChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onDeviceFilterChange: (String) -> Unit
) {
    var searchText by remember { mutableStateOf(searchQuery) }
    var deviceMenuExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(searchQuery) { searchText = searchQuery }
    val selectedDevice = senderDevices.firstOrNull { it.deviceId == selectedDeviceId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        PageIntro(
            number = "04",
            title = "سجل الرسائل",
            subtitle = "سجل موحّد لرسائل روابط التتبع من جميع أجهزة الإرسال المرتبطة بمتجرك."
        )
        Spacer(Modifier.height(16.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = TajerSage,
            shape = RectangleShape
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = null,
                    tint = TajerForest,
                    modifier = Modifier.size(19.dp)
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    "تظهر «تم التسليم» فقط عند استلام تقرير إيجابي من شبكة الهاتف. قد لا توفر بعض الشبكات تقريراً لكل رسالة.",
                    color = TajerForestDeep,
                    fontSize = 10.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SectionHeading("الرسائل", "تظهر أحدث النتائج أولاً")
            OutlinedButton(
                onClick = onRefresh,
                enabled = !loading,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier.height(38.dp),
                shape = RectangleShape,
                border = BorderStroke(1.dp, TajerBorder),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = TajerForest,
                    disabledContentColor = TajerMuted
                )
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(15.dp),
                        color = TajerForest,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text("تحديث", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            smsLogFilters.chunked(3).forEach { filterRow ->
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    filterRow.forEach { (key, label) ->
                        SmsLogFilterButton(
                            label = label,
                            selected = selectedFilter == key,
                            modifier = Modifier.weight(1f),
                            onClick = { onFilterChange(key) }
                        )
                    }
                    repeat(3 - filterRow.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it.take(80) },
                modifier = Modifier.weight(1f).height(54.dp),
                singleLine = true,
                shape = RectangleShape,
                placeholder = {
                    Text("اسم العميل أو رقم الهاتف", fontSize = 11.sp, color = TajerMuted)
                },
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 12.sp,
                    color = TajerInk
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TajerForest,
                    unfocusedBorderColor = TajerBorder,
                    focusedContainerColor = TajerSurface,
                    unfocusedContainerColor = TajerSurface,
                    focusedTextColor = TajerInk,
                    unfocusedTextColor = TajerInk,
                    cursorColor = TajerForest
                )
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { onSearch(searchText) },
                enabled = !loading,
                modifier = Modifier.height(54.dp),
                shape = RectangleShape,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TajerForest,
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(5.dp))
                Text("بحث", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(9.dp))
        Box(modifier = Modifier.fillMaxWidth()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !loading) { deviceMenuExpanded = true },
                color = TajerSurface,
                border = BorderStroke(1.dp, if (deviceMenuExpanded) TajerForest else TajerBorder),
                shape = RectangleShape
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("جهاز الإرسال", color = TajerMuted, fontSize = 9.sp)
                        Text(
                            selectedDevice?.let { smsDeviceDisplayLabel(it) } ?: "كل أجهزة الإرسال",
                            color = TajerInk,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                    Icon(
                        Icons.Outlined.ExpandMore,
                        contentDescription = "اختيار جهاز الإرسال",
                        tint = TajerForest,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
            DropdownMenu(
                expanded = deviceMenuExpanded,
                onDismissRequest = { deviceMenuExpanded = false },
                modifier = Modifier.fillMaxWidth(0.86f)
            ) {
                DropdownMenuItem(
                    text = { Text("كل أجهزة الإرسال", fontSize = 12.sp) },
                    onClick = {
                        deviceMenuExpanded = false
                        onDeviceFilterChange("")
                    }
                )
                senderDevices.forEach { device ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(smsDeviceDisplayLabel(device), fontSize = 12.sp)
                                if (device.revoked) {
                                    Text("تم إلغاء الربط", fontSize = 9.sp, color = TajerMuted)
                                }
                            }
                        },
                        onClick = {
                            deviceMenuExpanded = false
                            onDeviceFilterChange(device.deviceId)
                        }
                    )
                }
            }
        }
        Spacer(Modifier.height(15.dp))

        if (loading && messages.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 42.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = TajerForest, strokeWidth = 2.dp)
                    Spacer(Modifier.height(12.dp))
                    Text("جارٍ تحميل سجل الرسائل...", color = TajerMuted, fontSize = 11.sp)
                }
            }
        } else if (messages.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = TajerSurface,
                border = BorderStroke(1.dp, TajerBorder),
                shape = RectangleShape
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 25.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Outlined.Sms,
                        contentDescription = null,
                        tint = TajerMuted,
                        modifier = Modifier.size(27.dp)
                    )
                    Spacer(Modifier.height(9.dp))
                    Text(
                        if (statusMessage.isNotBlank()) statusMessage else "لا توجد رسائل في هذا التصنيف.",
                        color = TajerInk,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "ستظهر هنا رسائل روابط التتبع التي تعالجها هواتف الإرسال.",
                        color = TajerMuted,
                        fontSize = 10.sp,
                        lineHeight = 17.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            if (statusMessage.isNotBlank()) {
                Text(
                    statusMessage,
                    color = TajerClay,
                    fontSize = 10.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(bottom = 9.dp)
                )
            }
            messages.forEach { message ->
                SmsMessageLogCard(message)
                Spacer(Modifier.height(9.dp))
            }
            if (hasMore) {
                Button(
                    onClick = onLoadMore,
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RectangleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TajerSage,
                        contentColor = TajerForestDeep,
                        disabledContainerColor = TajerSage,
                        disabledContentColor = TajerMuted
                    )
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = TajerForest,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("عرض رسائل أقدم", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        Spacer(Modifier.height(22.dp))
    }
}

@Composable
private fun SmsLogFilterButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.height(38.dp).clickable(onClick = onClick),
        color = if (selected) TajerForest else TajerSurface,
        border = BorderStroke(1.dp, if (selected) TajerForest else TajerBorder),
        shape = RectangleShape
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                color = if (selected) Color.White else TajerInk,
                fontSize = 10.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SmsMessageLogCard(message: SmsMessageLogEntry) {
    val sendStatusLabel = when (message.status) {
        "pending", "retry" -> "قيد الانتظار"
        "processing" -> "جارٍ الإرسال"
        "submitted" -> "تم الإرسال"
        "unknown" -> "غير مؤكدة"
        "expired" -> "انتهت المهلة"
        "skipped" -> "لم يُرسل"
        "failed", "dead_letter" -> "فشل الإرسال"
        else -> "حالة غير معروفة"
    }
    val statusLabel = when (message.deliveryStatus) {
        "delivered" -> "تم التسليم"
        "pending" -> "بانتظار تقرير التسليم"
        "failed" -> "تعذر التسليم"
        "unknown" -> "تسليم غير مؤكد"
        else -> sendStatusLabel
    }
    val statusColor = when (message.deliveryStatus) {
        "delivered" -> Color(0xFF287A45)
        "pending" -> Color(0xFF9A6A18)
        "unknown" -> Color(0xFFB56A35)
        "failed" -> Color(0xFFB34B43)
        else -> when (message.status) {
            "submitted" -> Color(0xFF287A45)
            "pending", "retry", "processing" -> Color(0xFF9A6A18)
            "unknown" -> Color(0xFFB56A35)
            else -> Color(0xFFB34B43)
        }
    }
    val statusBackground = when (message.deliveryStatus) {
        "delivered" -> Color(0xFFE5F0E6)
        "pending" -> Color(0xFFF5EEDC)
        "unknown" -> Color(0xFFF7EADF)
        "failed" -> Color(0xFFF7E7E5)
        else -> when (message.status) {
            "submitted" -> Color(0xFFE5F0E6)
            "pending", "retry", "processing" -> Color(0xFFF5EEDC)
            "unknown" -> Color(0xFFF7EADF)
            else -> Color(0xFFF7E7E5)
        }
    }
    val detailMessage = listOfNotNull(
        message.deliveryError?.takeIf(String::isNotBlank),
        message.errorMessage?.takeIf(String::isNotBlank)
    ).distinct().joinToString("\n")

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = TajerSurface,
        border = BorderStroke(1.dp, TajerBorder),
        shape = RectangleShape
    ) {
        Column(Modifier.padding(15.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        message.customerName.ifBlank { "عميل" },
                        color = TajerInk,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        message.customerPhone.ifBlank { "رقم العميل غير متاح" },
                        color = TajerMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(Modifier.width(8.dp))
                Surface(color = statusBackground, shape = RectangleShape) {
                    Text(
                        statusLabel,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                        color = statusColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Spacer(Modifier.height(11.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "رابط تتبع الطلب",
                    color = TajerForest,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (message.orderReference.isNotBlank()) {
                    Text(
                        "الطلب #${message.orderReference}",
                        color = TajerMuted,
                        fontSize = 10.sp,
                        maxLines = 1
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "حالة الإرسال: $sendStatusLabel",
                color = TajerMuted,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(9.dp))
            HorizontalDivider(color = TajerBorder)
            Spacer(Modifier.height(11.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(Modifier.weight(1f)) {
                    Text(message.eventLabel, color = TajerMuted, fontSize = 9.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        formatSmsLogTimestamp(message.eventAt ?: message.queuedAt),
                        color = TajerInk,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text("جهاز الإرسال", color = TajerMuted, fontSize = 9.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        message.senderDeviceName ?: "بانتظار جهاز الإرسال",
                        color = TajerInk,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.End,
                        maxLines = 1
                    )
                    val operatorLabel = smsOperatorLabel(message.operatorCode)
                    if (operatorLabel.isNotBlank()) {
                        Text(operatorLabel, color = TajerMuted, fontSize = 9.sp)
                    }
                }
            }
            if (message.attemptCount > 0) {
                Spacer(Modifier.height(9.dp))
                Text(
                    "عدد محاولات المعالجة: ${message.attemptCount}",
                    color = TajerMuted,
                    fontSize = 9.sp
                )
            }
            if (detailMessage.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFFFFF5EF),
                    shape = RectangleShape
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text("تفاصيل النتيجة", color = Color(0xFF9D4D32), fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            detailMessage,
                            color = Color(0xFF78452F),
                            fontSize = 10.sp,
                            lineHeight = 17.sp
                        )
                    }
                }
            }
        }
    }
}

private fun smsDeviceDisplayLabel(device: ManagerSenderDevice): String {
    val operator = smsOperatorLabel(device.operatorCode)
    val state = when {
        device.revoked -> "ملغي"
        !device.enabled -> "متوقف"
        else -> ""
    }
    return listOf(device.displayName, operator, state)
        .filter(String::isNotBlank)
        .joinToString(" · ")
}

private fun smsOperatorLabel(operatorCode: String?): String = when (operatorCode) {
    "djezzy" -> "Djezzy"
    "mobilis" -> "Mobilis"
    "ooredoo" -> "Ooredoo"
    else -> ""
}

private fun formatSmsLogTimestamp(value: String?): String {
    if (value.isNullOrBlank()) return "لم تُسجّل محاولة بعد"
    return runCatching {
        OffsetDateTime.parse(value)
            .atZoneSameInstant(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy · HH:mm", Locale("ar", "DZ")))
    }.getOrDefault(value)
}
