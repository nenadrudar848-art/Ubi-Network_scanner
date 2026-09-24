package com.ubi.scanner

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val Bg = Color(0xFF05070F)
private val Accent = Color(0xFF34D399)
private val Cyan = Color(0xFF22D3EE)
private val Violet = Color(0xFFA78BFA)
private val Glass = Color(0x14FFFFFF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
            .launch(perms.toTypedArray())
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Bg)) {
                ScannerScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen() {
    val ctx = LocalContextSafe()
    val scope = rememberCoroutineScope()

    var segment by remember { mutableStateOf("") }
    var ssid by remember { mutableStateOf<String?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var currentIp by remember { mutableStateOf("") }
    var deepScan by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf<String?>(null) }
    val hosts = remember { mutableStateListOf<HostResult>() }
    var job by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(Unit) {
        segment = NetworkScanner.detectSubnet(ctx) ?: "192.168.1.0/24"
        ssid = NetworkScanner.wifiSsid(ctx)
    }

    fun start() {
        val targets = NetworkScanner.parseTargets(segment)
        if (targets.isEmpty()) return
        hosts.clear(); progress = 0f; scanning = true; expanded = null
        val gw = targets.firstOrNull()
        job = scope.launch {
            NetworkScanner.scan(
                targets = targets,
                gateway = gw,
                scanPorts = deepScan,
                onProgress = { done, total, ip ->
                    progress = done.toFloat() / total
                    currentIp = ip
                },
                onHost = { h ->
                    scope.launch {
                        if (hosts.none { it.ip == h.ip }) {
                            hosts.add(h)
                            hosts.sortBy { it.ip.split(".").last().toIntOrNull() ?: 0 }
                        }
                    }
                }
            )
            scanning = false
        }
    }

    val filtered = hosts.filter {
        query.isBlank() || it.ip.contains(query) || it.hostname.contains(query, true) ||
            it.vendor.contains(query, true) || it.ports.any { p -> p.port.toString().contains(query) }
    }

    Box(Modifier.fillMaxSize().background(Bg)) {
        // pozadinski glow blobovi + grid
        Box(Modifier.size(320.dp).offset((-90).dp, (-110).dp).blur(90.dp)
            .background(Brush.radialGradient(listOf(Accent.copy(alpha = .35f), Color.Transparent))))
        Box(Modifier.size(300.dp).align(Alignment.CenterEnd).offset(110.dp, (-60).dp).blur(90.dp)
            .background(Brush.radialGradient(listOf(Cyan.copy(alpha = .30f), Color.Transparent))))
        Box(Modifier.size(320.dp).align(Alignment.BottomStart).offset(20.dp, 90.dp).blur(100.dp)
            .background(Brush.radialGradient(listOf(Violet.copy(alpha = .30f), Color.Transparent))))
        Canvas(Modifier.fillMaxSize()) {
            val step = 110f
            var x = 0f
            while (x < size.width) { drawLine(Color(0x11FFFFFF), Offset(x, 0f), Offset(x, size.height)); x += step }
            var y = 0f
            while (y < size.height) { drawLine(Color(0x11FFFFFF), Offset(0f, y), Offset(size.width, y)); y += step }
        }

        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 20.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(48.dp).clip(RoundedCornerShape(16.dp))
                            .background(Brush.linearGradient(listOf(Accent, Cyan))),
                        contentAlignment = Alignment.Center
                    ) { Text("📡", fontSize = 22.sp) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Ubi-Network Scanner", color = Color.White,
                            fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Text(ssid?.let { "Wi-Fi: $it" } ?: "LAN discovery",
                            color = Color(0xFF64748B), fontSize = 11.sp)
                    }
                    StatusChip(if (scanning) "SKENIRAM" else "SPREMAN", scanning)
                }
            }

            item {
                GlassCard {
                    Text("CUSTOM SEGMENT / OPSEG", color = Color(0xFF94A3B8),
                        fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = segment,
                            onValueChange = { segment = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = { Text("192.168.1.0/24, 10.0.0.5-60", fontSize = 12.sp) },
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = Accent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Accent, unfocusedBorderColor = Color(0x22FFFFFF),
                                focusedContainerColor = Color(0x66000000),
                                unfocusedContainerColor = Color(0x66000000)
                            )
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { if (scanning) { job?.cancel(); scanning = false } else start() },
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (scanning) Color(0xFFF43F5E) else Accent,
                                contentColor = if (scanning) Color.White else Color(0xFF04120C)
                            ),
                            modifier = Modifier.height(56.dp)
                        ) { Text(if (scanning) "Stop" else "Skeniraj", fontWeight = FontWeight.Bold) }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        listOf("192.168.0.0/24", "192.168.1.0/24", "10.0.0.1-100", "172.16.0.0/24")
                            .forEach { p -> Chip(p, segment == p) { segment = p } }
                        Chip(if (deepScan) "◉ Port scan" else "○ Port scan", deepScan) { deepScan = !deepScan }
                    }
                    Spacer(Modifier.height(16.dp))
                    RadarView(scanning, hosts.size)
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                        Text(if (scanning) "probe → $currentIp" else "idle",
                            color = Color(0xFF94A3B8), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Text("${(progress * 100).toInt()}%", color = Color(0xFF94A3B8),
                            fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = Accent, trackColor = Color(0x22FFFFFF)
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Stat("Hostovi", hosts.size.toString(), Accent, Modifier.weight(1f))
                        Stat("Portovi", hosts.sumOf { it.ports.size }.toString(), Cyan, Modifier.weight(1f))
                        Stat("Adresa", NetworkScanner.parseTargets(segment).size.toString(), Violet, Modifier.weight(1f))
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    placeholder = { Text("Filter: IP, hostname, port, vendor", fontSize = 12.sp) },
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent, unfocusedBorderColor = Color(0x22FFFFFF),
                        focusedContainerColor = Color(0x66000000), unfocusedContainerColor = Color(0x66000000)
                    )
                )
            }

            items(filtered, key = { it.ip }) { h ->
                HostCard(h, expanded == h.ip) { expanded = if (expanded == h.ip) null else h.ip }
            }

            if (filtered.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(40.dp), Alignment.Center) {
                        Text(if (scanning) "Tražim uređaje…" else "Pokreni skeniranje",
                            color = Color(0xFF475569), fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalContextSafe() = androidx.compose.ui.platform.LocalContext.current

@Composable
fun GlassCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Glass).border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(24.dp))
            .padding(16.dp),
        content = content
    )
}

@Composable
fun StatusChip(text: String, active: Boolean) {
    Box(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (active) Accent.copy(alpha = .18f) else Color(0x14FFFFFF))
            .border(1.dp, if (active) Accent.copy(alpha = .5f) else Color(0x22FFFFFF), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) { Text(text, color = if (active) Accent else Color(0xFF94A3B8), fontSize = 10.sp, fontWeight = FontWeight.Bold) }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (selected) Accent.copy(alpha = .15f) else Color(0x0DFFFFFF))
            .border(1.dp, if (selected) Accent.copy(alpha = .6f) else Color(0x1AFFFFFF), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text, color = if (selected) Accent else Color(0xFF94A3B8),
            fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun Stat(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(Color(0x4D000000)).padding(12.dp)
    ) {
        Text(value, color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Text(label, color = Color(0xFF64748B), fontSize = 9.sp, letterSpacing = 1.sp)
    }
}

@Composable
fun RadarView(active: Boolean, count: Int) {
    val transition = rememberInfiniteTransition(label = "radar")
    val angle by transition.animateFloat(
        0f, 360f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "a"
    )
    val pulse by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "p"
    )
    Box(Modifier.fillMaxWidth().height(180.dp), Alignment.Center) {
        Canvas(Modifier.size(170.dp)) {
            val r = size.minDimension / 2
            for (i in 1..4) drawCircle(Accent.copy(alpha = .18f), r * i / 4f, style = Stroke(1f))
            drawLine(Accent.copy(alpha = .12f), Offset(center.x, 0f), Offset(center.x, size.height))
            drawLine(Accent.copy(alpha = .12f), Offset(0f, center.y), Offset(size.width, center.y))
            if (active) {
                drawCircle(Accent.copy(alpha = (1f - pulse) * .45f), r * pulse, style = Stroke(2f, cap = StrokeCap.Round))
                rotate(angle) {
                    drawArc(
                        Brush.sweepGradient(listOf(Color.Transparent, Accent.copy(alpha = .45f))),
                        startAngle = 0f, sweepAngle = 70f, useCenter = true
                    )
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$count", color = Accent, fontSize = 34.sp,
                fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Text("UREĐAJA", color = Accent.copy(alpha = .6f), fontSize = 9.sp, letterSpacing = 3.sp)
        }
    }
}

@Composable
fun HostCard(h: HostResult, expanded: Boolean, onClick: () -> Unit) {
    val icon = when {
        h.isGateway -> "🛰️"
        h.ports.any { it.port == 9100 || it.port == 631 } -> "🖨️"
        h.ports.any { it.port == 554 } -> "🎥"
        h.ports.any { it.port == 445 || it.port == 5000 } -> "🗄️"
        h.ports.any { it.port == 8008 || it.port == 7000 } -> "📺"
        h.vendor.contains("Apple") || h.vendor.contains("Samsung") -> "📱"
        h.vendor.contains("Espressif") || h.vendor.contains("Raspberry") -> "🔌"
        else -> "💻"
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Glass)
            .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick).padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Color(0x14FFFFFF)),
                Alignment.Center) { Text(icon, fontSize = 18.sp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(h.ip, color = Color.White, fontSize = 15.sp,
                    fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                Text(h.hostname, color = Accent, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(h.vendor, color = Color(0xFF64748B), fontSize = 11.sp, maxLines = 1)
            }
            Text("${h.latencyMs} ms", color = Cyan, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
        if (h.ports.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())) {
                h.ports.forEach { p ->
                    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Accent.copy(alpha = .12f))
                        .border(1.dp, Accent.copy(alpha = .3f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 7.dp, vertical = 3.dp)) {
                        Text("${p.port}/${p.service}", color = Accent,
                            fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Divider(color = Color(0x1AFFFFFF))
                Spacer(Modifier.height(4.dp))
                InfoRow("MAC adresa", h.mac)
                InfoRow("Proizvođač", h.vendor)
                InfoRow("Hostname", h.hostname)
                InfoRow("Otvorenih portova", h.ports.size.toString())
                InfoRow("Uloga", if (h.isGateway) "Gateway / ruter" else "Klijent")
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
        Text(label, color = Color(0xFF64748B), fontSize = 11.sp)
        Text(value, color = Color(0xFFE2E8F0), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    }
}
