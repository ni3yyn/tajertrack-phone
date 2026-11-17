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
private fun GuidePage() {
    val questions = listOf(
        "ما وظيفة تاجر تراك فون؟" to
            "يرسل رابط تتبع الطلب إلى العميل برسالة SMS واحدة عند إنشاء طلب جديد في متجرك. لا يرسل رسائل متكررة عند تغيّر حالة الطلب.",
        "كيف أربط الهاتف بمتجري؟" to
            "من لوحة تاجر تراك افتح الإعدادات ثم خطوط إرسال SMS، واختر شبكة الهاتف وأنشئ دعوة ربط. افتح الدعوة على الهاتف المقصود ووافق على الربط. لا يحتاج صاحب الهاتف إلى كلمة مرور حسابك.",
        "ما الذي يحتاجه الهاتف حتى يعمل؟" to
            "تحتاج إلى هاتف Android يدعم إرسال SMS، وشريحة فعّالة، واتصال بالإنترنت مع تغطية شبكة الهاتف. اسمح للتطبيق بأذونات إرسال الرسائل والوصول إلى معلومات الشرائح والإشعارات.",
        "كيف أستخدم هاتفاً بشريحتي اتصال؟" to
            "افتح قائمة الشرائح وفعّل الشريحة الأولى أو الشريحتين. ستظهر أسماء الشرائح المفعّلة حتى بعد إغلاق القائمة. يختار التطبيق الشريحة التي تطابق شبكة رقم العميل، ولا يرسل رسالتين للطلب نفسه. يمكن تفعيل شريحتين كحد أقصى على الهاتف.",
        "هل يختار التطبيق أي شريحة عند تعذّر الشبكة المطابقة؟" to
            "لا. لا يحوّل التطبيق الرسالة إلى شبكة مختلفة. إذا لم يكن خط الإرسال أو الشريحة المطابقة متاحاً، ينتظر الطلب حتى تصبح الشبكة الصحيحة متاحة.",
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
        "هل يمكنني إيقاف خط أو تغيير الهاتف؟" to
            "يمكنك إيقاف الإرسال من التطبيق، أو تعطيل خط الإرسال أو إلغاء ربطه من إعدادات تاجر تراك. وعند استخدام هاتف آخر، أنشئ دعوة ربط جديدة للشبكة المطلوبة."
    )
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 21.dp, vertical = 22.dp)
    ) {
        PageIntro(
            number = "03",
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
            number = "04",
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
