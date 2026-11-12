package com.tajertrack.phone.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CellTower
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tajertrack.phone.SimChoice

@Composable
fun PhoneHomeScreen(
    connected: Boolean,
    relayRunning: Boolean,
    storeName: String,
    statusMessage: String,
    pairing: Boolean,
    simChoices: List<SimChoice>,
    selectedSimId: Int,
    onConnect: () -> Unit,
    onStartRelay: () -> Unit,
    onStopRelay: () -> Unit,
    onSelectSim: (Int) -> Unit,
    onReconnect: () -> Unit
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(38.dp)
                        .height(38.dp)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text("T", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("تاجر تراك", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("SMS PHONE", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, letterSpacing = 1.8.sp)
                }
            }

            Spacer(Modifier.height(42.dp))
            Text(
                "رسائل الطلبات من شريحتك",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                lineHeight = 34.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "يرسل هذا الهاتف رابط التتبع تلقائياً للعميل عند إنشاء طلب جديد في متجرك.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                lineHeight = 23.sp
            )

            Spacer(Modifier.height(30.dp))
            HorizontalDivider(color = Color(0xFFDCE3DD), thickness = 1.dp)
            Spacer(Modifier.height(22.dp))

            when {
                !connected -> {
                    StatusLine(
                        icon = Icons.Outlined.Link,
                        title = "المتجر غير مربوط",
                        detail = "اربط التطبيق بحسابك في تاجر تراك مرة واحدة."
                    )
                    Spacer(Modifier.height(22.dp))
                    Button(
                        onClick = onConnect,
                        enabled = !pairing,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = MaterialTheme.shapes.small,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text(if (pairing) "جارٍ الربط..." else "ربط المتجر", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                    Text(
                        "سيُفتح موقع تاجر تراك لتسجيل الدخول والموافقة على ربط هذا الهاتف.",
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Center
                    )
                }
                else -> {
                    StatusLine(
                        icon = Icons.Outlined.Link,
                        title = storeName.ifBlank { "المتجر مربوط" },
                        detail = "هذا الهاتف مرتبط بحساب متجرك."
                    )
                    Spacer(Modifier.height(24.dp))
                    HorizontalDivider(color = Color(0xFFDCE3DD), thickness = 1.dp)
                    Spacer(Modifier.height(22.dp))

                    Text("شريحة الإرسال", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "سيتم إرسال الرسائل من رقم الشريحة المختارة في هذا الهاتف. عند التفعيل سيطلب Android أذونات الإرسال والوصول إلى الشرائح وإشعار التشغيل.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 19.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    SimPicker(simChoices, selectedSimId, onSelectSim)
                    Spacer(Modifier.height(24.dp))
                    if (relayRunning) {
                        StatusLine(
                            icon = Icons.Outlined.CellTower,
                            title = "الإرسال يعمل",
                            detail = "الهاتف متصل وينتظر الطلبات الجديدة."
                        )
                        Spacer(Modifier.height(20.dp))
                        OutlinedButton(
                            onClick = onStopRelay,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text("إيقاف الإرسال", fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Button(
                            onClick = onStartRelay,
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                            shape = MaterialTheme.shapes.small,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("تفعيل إرسال الرسائل", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        }
                    }
                    TextButton(onClick = onReconnect, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text("إعادة ربط الحساب", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                }
            }

            if (statusMessage.isNotBlank()) {
                Spacer(Modifier.height(18.dp))
                Text(
                    statusMessage,
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 20.sp
                )
            }

            Spacer(Modifier.height(30.dp))
            HorizontalDivider(color = Color(0xFFDCE3DD), thickness = 1.dp)
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Outlined.Sms, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("رسالة واحدة لكل طلب جديد", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "لا يرسل التطبيق تحديثات متكررة. يرسل رابط التتبع مرة واحدة عند إنشاء الطلب.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 20.sp
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Outlined.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("اتصال الإنترنت مطلوب", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "اترك الهاتف متصلاً بالإنترنت وبالشاحن عند الحاجة حتى يستقبل الطلبات الجديدة.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 20.sp
                    )
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun StatusLine(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    detail: String
) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Spacer(Modifier.height(4.dp))
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 20.sp)
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
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = sims.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = MaterialTheme.shapes.small
        ) {
            Text(
                selected?.label ?: if (sims.isEmpty()) "لا توجد شريحة متاحة" else "اختر شريحة الإرسال",
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Start,
                fontSize = 13.sp
            )
            Text("⌄", fontSize = 17.sp)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            sims.forEach { sim ->
                DropdownMenuItem(
                    text = { Text(sim.label, fontSize = 13.sp) },
                    onClick = {
                        expanded = false
                        onSelect(sim.subscriptionId)
                    }
                )
            }
        }
    }
}
