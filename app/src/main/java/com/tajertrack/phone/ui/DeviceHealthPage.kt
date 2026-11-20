package com.tajertrack.phone.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tajertrack.phone.SimChoice
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun DeviceHealthPage(
    connected: Boolean,
    relayRunning: Boolean,
    relayConnectionState: String,
    relayDiagnosticMessage: String,
    relayStatusUpdatedAt: Long,
    relayLastSuccessfulPollAt: Long,
    consecutiveRelayFailures: Int,
    healthCheckedAt: Long,
    internetValidated: Boolean,
    smsPermissionGranted: Boolean,
    phoneStatePermissionGranted: Boolean,
    notificationsPermissionGranted: Boolean,
    notificationsPermissionRequired: Boolean,
    simChoices: List<SimChoice>,
    selectedSimId: Int,
    secondarySimId: Int,
    alternateSimsEnabled: Boolean,
    storeName: String,
    appVersion: String,
    deviceName: String,
    androidVersion: String,
    onRefresh: () -> Unit,
    onOpenSending: () -> Unit
) {
    val enabledSimIds = buildSet {
        if (selectedSimId >= 0) add(selectedSimId)
        if (alternateSimsEnabled && secondarySimId >= 0 && secondarySimId != selectedSimId) {
            add(secondarySimId)
        }
    }
    val enabledActiveSims = simChoices.filter { it.subscriptionId in enabledSimIds }
    val hasMissingSavedSim = enabledSimIds.any { id -> simChoices.none { it.subscriptionId == id } }
    val corePermissionsReady = smsPermissionGranted && phoneStatePermissionGranted
    val relayNeedsAttention = relayConnectionState in setOf(
        "attention", "needs_repair", "offline", "retrying", "needs_user_start", "needs_pairing"
    )
    val healthy = connected &&
        relayRunning &&
        relayConnectionState in setOf("connected", "sending", "reporting") &&
        internetValidated &&
        corePermissionsReady &&
        enabledActiveSims.isNotEmpty() &&
        !hasMissingSavedSim
    val summaryTitle = when {
        !connected -> "الهاتف غير مربوط بالمتجر"
        !smsPermissionGranted -> "إذن إرسال الرسائل غير متاح"
        !phoneStatePermissionGranted -> "إذن شرائح الاتصال غير متاح"
        relayRunning && !internetValidated -> "الاتصال بالإنترنت غير متاح"
        relayRunning && enabledActiveSims.isEmpty() -> "لا توجد شريحة إرسال مفعّلة"
        hasMissingSavedSim -> "إحدى الشرائح المفعّلة غير موجودة"
        relayConnectionState == "needs_repair" || relayConnectionState == "needs_pairing" -> "يلزم إعادة ربط الهاتف"
        relayConnectionState == "attention" -> "هناك أمر يحتاج انتباهك"
        relayConnectionState == "offline" || relayConnectionState == "retrying" -> "الاتصال يحتاج إلى استعادة"
        relayConnectionState == "needs_user_start" -> "يلزم تشغيل خدمة الإرسال"
        relayConnectionState == "paused" -> "خدمة الإرسال متوقفة مؤقتاً"
        relayConnectionState == "disabled" -> "خط الإرسال موقوف من الإدارة"
        relayRunning -> "خدمة الإرسال تعمل"
        else -> "خدمة الإرسال متوقفة"
    }
    val summaryDetail = when {
        !connected -> "اربط هذا الهاتف بمتجرك قبل تفعيل خدمة إرسال روابط التتبع."
        !smsPermissionGranted -> "اسمح لتاجر تراك فون بإرسال الرسائل النصية من إعدادات Android."
        !phoneStatePermissionGranted -> "اسمح بالوصول إلى شرائح الاتصال حتى يتمكن التطبيق من اختيار شبكة العميل الصحيحة."
        relayRunning && !internetValidated -> "لا يستطيع الهاتف حالياً تأكيد اتصال إنترنت صالح. ستتم استعادة الاتصال عند عودة الشبكة."
        relayRunning && enabledActiveSims.isEmpty() -> "لا توجد شريحة مفعّلة ومتاحة لهذا الهاتف. راجع اختيار الشرائح في إعداد الإرسال."
        relayRunning && hasMissingSavedSim -> "إحدى الشرائح التي كانت مفعّلة لم تعد ظاهرة للهاتف. راجع اختيار الشرائح قبل استئناف الإرسال."
        relayConnectionState == "needs_user_start" -> "الإعداد محفوظ، لكن خدمة الهاتف ليست نشطة حالياً. افتح إعداد الإرسال واضغط بدء الإرسال."
        !notificationsPermissionGranted && notificationsPermissionRequired -> "اسمح بالإشعارات حتى تظهر لك حالة خدمة الإرسال وتنبيهات Android بوضوح."
        relayDiagnosticMessage.isNotBlank() -> relayDiagnosticMessage
        relayRunning -> "الهاتف متصل ويستقبل طلبات الإرسال من تاجر تراك."
        connected -> "الهاتف مرتبط بنجاح. افتح إعداد الإرسال لاختيار الشرائح وتشغيل الخدمة."
        else -> "راجع إعداد الهاتف للتحقق من الاتصال والأذونات."
    }
    val summaryColor = when {
        healthy -> TajerForest
        relayNeedsAttention || !corePermissionsReady || (relayRunning && (!internetValidated || enabledActiveSims.isEmpty())) || hasMissingSavedSim -> Color(0xFF9D4D32)
        else -> TajerForestDeep
    }
    val summaryBackground = when {
        healthy -> TajerSage
        summaryColor == Color(0xFF9D4D32) -> Color(0xFFFFF3EC)
        else -> TajerSurface
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 22.dp)
    ) {
        Text(
            "كل معلومات الاتصال والإرسال والشرائح والأذونات في مكان واحد.",
            color = TajerMuted,
            fontSize = 14.sp,
            lineHeight = 24.sp
        )
        Spacer(Modifier.height(18.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = summaryBackground,
            border = BorderStroke(1.dp, if (healthy) Color(0xFFC9D8C8) else TajerBorder),
            shape = RectangleShape
        ) {
            Column(Modifier.padding(horizontal = 17.dp, vertical = 17.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("التقييم العام", color = TajerMuted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (!healthy) {
                            Text(
                                if (summaryColor == Color(0xFF9D4D32)) "يحتاج مراجعة" else "غير مكتمل",
                                color = summaryColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        StatusMark(passed = healthy, size = 23.dp)
                    }
                }
                Spacer(Modifier.height(11.dp))
                Text(summaryTitle, color = summaryColor, fontSize = 20.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(7.dp))
                Text(summaryDetail, color = TajerInk, fontSize = 14.sp, lineHeight = 24.sp)
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = TajerBorder)
                Spacer(Modifier.height(9.dp))
                Text("آخر فحص: ${healthAge(healthCheckedAt)}", color = TajerMuted, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(22.dp))
        DeviceHealthSection("الاتصال وخدمة الإرسال")
        DeviceHealthRow("خدمة الإرسال", if (relayRunning) "تعمل" else "متوقفة",
            if (relayRunning) "خدمة الهاتف الأمامية نشطة." else "لم يتم تشغيل الخدمة أو أنها تحتاج إلى تشغيل يدوي.", relayRunning)
        DeviceHealthRow("اتصال الخادم", healthRelayConnectionLabel(relayConnectionState, relayRunning),
            relayDiagnosticMessage.ifBlank { "لم يسجل التطبيق رسالة تشخيصية إضافية." },
            relayConnectionState in setOf("connected", "sending", "reporting"))
        DeviceHealthRow("اتصال الإنترنت", if (internetValidated) "متاح" else "غير متاح أو غير مؤكد",
            positive = internetValidated)
        DeviceHealthRow("آخر اتصال ناجح بالخادم", healthAge(relayLastSuccessfulPollAt),
            healthTimestamp(relayLastSuccessfulPollAt), relayLastSuccessfulPollAt > 0L)
        DeviceHealthRow("آخر تحديث لحالة الخدمة", healthAge(relayStatusUpdatedAt),
            healthTimestamp(relayStatusUpdatedAt), relayStatusUpdatedAt > 0L)
        DeviceHealthRow("الإخفاقات المتتالية", consecutiveRelayFailures.toString(),
            positive = consecutiveRelayFailures == 0)

        Spacer(Modifier.height(21.dp))
        DeviceHealthSection("شرائح الاتصال")
        if (simChoices.isEmpty()) {
            DeviceHealthNotice("لم يتمكن التطبيق من عرض الشرائح النشطة.",
                "تحقق من إذن الوصول إلى شرائح الاتصال، ومن وجود شريحة فعّالة في الهاتف.")
        } else {
            simChoices.forEach { sim ->
                val enabled = sim.subscriptionId in enabledSimIds
                val role = when {
                    sim.subscriptionId == selectedSimId -> "الشريحة الأساسية"
                    alternateSimsEnabled && sim.subscriptionId == secondarySimId -> "الشريحة الثانية"
                    else -> "شريحة غير مفعّلة"
                }
                DeviceHealthRow(
                    label = sim.label,
                    value = if (enabled) "مفعّلة" else "غير مفعّلة",
                    detail = role + if (enabled) " — يمكن استخدامها عند مطابقة شبكة العميل." else " — لن يستخدمها التطبيق للإرسال.",
                    positive = enabled
                )
            }
            if (hasMissingSavedSim) {
                DeviceHealthNotice("شريحة محفوظة غير موجودة",
                    "لم يعثر Android على إحدى الشرائح التي سبق تفعيلها. افتح إعداد الإرسال وحدّث اختيار الشرائح.")
            }
        }
        DeviceHealthRow("الشرائح المفعّلة والمتاحة", "${enabledActiveSims.size} من ${simChoices.size}",
            positive = enabledActiveSims.isNotEmpty())

        Spacer(Modifier.height(21.dp))
        DeviceHealthSection("أذونات Android")
        DeviceHealthPermissionRow("إرسال الرسائل النصية", smsPermissionGranted,
            "مطلوب لإرسال رابط تتبع الطلب من شريحة الهاتف.")
        DeviceHealthPermissionRow("الوصول إلى شرائح الاتصال", phoneStatePermissionGranted,
            "مطلوب لاكتشاف الشرائح واختيار الشبكة المطابقة.")
        DeviceHealthPermissionRow("إشعارات التطبيق",
            notificationsPermissionGranted || !notificationsPermissionRequired,
            if (notificationsPermissionRequired) "يساعد على إظهار حالة خدمة الإرسال وتنبيهاتها."
            else "هذا الإذن غير مطلوب على إصدار Android الحالي.")

        Spacer(Modifier.height(21.dp))
        DeviceHealthSection("معلومات الهاتف والتطبيق")
        DeviceHealthRow("المتجر المرتبط", storeName.ifBlank { if (connected) "متجر مرتبط" else "غير مرتبط" })
        DeviceHealthRow("الجهاز", deviceName)
        DeviceHealthRow("إصدار Android", androidVersion)
        DeviceHealthRow("إصدار تاجر تراك فون", appVersion)

        Spacer(Modifier.height(22.dp))
        Button(
            onClick = onOpenSending,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RectangleShape,
            colors = ButtonDefaults.buttonColors(containerColor = TajerForest)
        ) {
            Text("إعداد الشرائح وخدمة الإرسال", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(9.dp))
        OutlinedButton(
            onClick = onRefresh,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RectangleShape,
            border = BorderStroke(1.dp, TajerForest),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TajerForest)
        ) {
            Text("تحديث حالة الجهاز", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(22.dp))
    }
}

@Composable
private fun DeviceHealthSection(title: String) {
    Text(title, color = TajerInk, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(9.dp))
}

@Composable
private fun DeviceHealthRow(label: String, value: String, detail: String? = null, positive: Boolean? = null) {
    Column(Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Text(label, modifier = Modifier.weight(1f), color = TajerMuted, fontSize = 13.sp, lineHeight = 20.sp)
            Spacer(Modifier.size(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    value,
                    color = when (positive) {
                        true -> TajerForest
                        false -> Color(0xFF9D4D32)
                        null -> TajerInk
                    },
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.End
                )
                positive?.let { StatusMark(passed = it, size = 19.dp) }
            }
        }
        if (!detail.isNullOrBlank()) {
            Spacer(Modifier.height(3.dp))
            Text(detail, color = TajerMuted, fontSize = 12.sp, lineHeight = 20.sp)
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = TajerBorder)
    }
}

@Composable
private fun DeviceHealthPermissionRow(label: String, granted: Boolean, detail: String) {
    DeviceHealthRow(label, if (granted) "مسموح" else "غير مسموح", detail, granted)
}

@Composable
private fun DeviceHealthNotice(title: String, detail: String) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        color = Color(0xFFFFF3EC),
        shape = RectangleShape
    ) {
        Column(Modifier.padding(13.dp)) {
            Text(title, color = Color(0xFF9D4D32), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(detail, color = TajerInk, fontSize = 13.sp, lineHeight = 21.sp)
        }
    }
    Spacer(Modifier.height(5.dp))
}

private fun healthRelayConnectionLabel(state: String, relayRunning: Boolean): String = when (state) {
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

private fun healthAge(timestamp: Long): String {
    if (timestamp <= 0L) return "لم يُسجّل بعد"
    val elapsed = (System.currentTimeMillis() - timestamp).coerceAtLeast(0L) / 1_000L
    return when {
        elapsed < 60L -> "الآن"
        elapsed < 3_600L -> "منذ ${elapsed / 60L} دقيقة"
        elapsed < 86_400L -> "منذ ${elapsed / 3_600L} ساعة"
        else -> "منذ ${elapsed / 86_400L} يوم"
    }
}

private fun healthTimestamp(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    return runCatching {
        Instant.ofEpochMilli(timestamp)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy · HH:mm", Locale("ar", "DZ")))
    }.getOrDefault("")
}
