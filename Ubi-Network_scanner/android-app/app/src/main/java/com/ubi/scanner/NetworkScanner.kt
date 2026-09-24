package com.ubi.scanner

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.wifi.WifiManager
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.File
import java.net.*

data class PortResult(val port: Int, val service: String)

data class HostResult(
    val ip: String,
    val hostname: String,
    val mac: String,
    val vendor: String,
    val latencyMs: Long,
    val ports: List<PortResult> = emptyList(),
    val isGateway: Boolean = false
)

/** Portovi koji se probaju TCP connect skenom. */
val COMMON_PORTS = linkedMapOf(
    21 to "ftp", 22 to "ssh", 23 to "telnet", 25 to "smtp", 53 to "domain",
    80 to "http", 110 to "pop3", 139 to "netbios", 143 to "imap", 443 to "https",
    445 to "smb", 515 to "printer", 548 to "afp", 554 to "rtsp", 631 to "ipp",
    873 to "rsync", 1883 to "mqtt", 3000 to "http-dev", 3306 to "mysql",
    3389 to "rdp", 5000 to "upnp/dsm", 5432 to "postgres", 5900 to "vnc",
    6379 to "redis", 7000 to "airplay", 8000 to "http-alt", 8008 to "cast",
    8080 to "http-proxy", 8443 to "https-alt", 8883 to "mqtts", 9100 to "jetdirect",
    32400 to "plex", 62078 to "iphone-sync"
)

object NetworkScanner {

    /** Vraca npr. "192.168.1.0/24" na osnovu aktivne Wi-Fi/LAN konekcije. */
    fun detectSubnet(ctx: Context): String? {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return null
        val props = cm.getLinkProperties(net) ?: return null
        val addr: LinkAddress = props.linkAddresses.firstOrNull {
            it.address is Inet4Address && !it.address.isLoopbackAddress
        } ?: return null
        val ip = addr.address.hostAddress ?: return null
        return "$ip/${addr.prefixLength}"
    }

    fun wifiSsid(ctx: Context): String? = try {
        val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wm.connectionInfo?.ssid?.trim('"')?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
    } catch (e: Exception) { null }

    /** Parsira "192.168.1.1/24", "192.168.1.10-80", "10.0.0.5" i liste odvojene zarezom. */
    fun parseTargets(input: String): List<String> {
        val out = LinkedHashSet<String>()
        for (raw in input.split(",").map { it.trim() }.filter { it.isNotEmpty() }) {
            val cidr = Regex("^(\\d{1,3}(?:\\.\\d{1,3}){3})/(\\d{1,2})$").find(raw)
            val range = Regex("^(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})\\.(\\d{1,3})\\s*-\\s*(\\d{1,3})$").find(raw)
            val single = Regex("^\\d{1,3}(?:\\.\\d{1,3}){3}$").find(raw)
            when {
                cidr != null -> {
                    val prefix = cidr.groupValues[2].toInt().coerceIn(16, 32)
                    val base = ipToLong(cidr.groupValues[1])
                    val mask = if (prefix == 0) 0L else (-1L shl (32 - prefix)) and 0xFFFFFFFFL
                    val network = base and mask
                    val size = 1L shl (32 - prefix)
                    if (size > 65536) continue
                    val from = if (size > 2) network + 1 else network
                    val to = if (size > 2) network + size - 2 else network
                    for (v in from..to) out.add(longToIp(v))
                }
                range != null -> {
                    val a = range.groupValues[2].toInt()
                    val b = range.groupValues[3].toInt()
                    for (i in minOf(a, b)..maxOf(a, b).coerceAtMost(255))
                        out.add("${range.groupValues[1]}.$i")
                }
                single != null -> out.add(raw)
            }
        }
        return out.toList()
    }

    private fun ipToLong(ip: String): Long =
        ip.split(".").fold(0L) { acc, s -> (acc shl 8) or s.toLong() }

    private fun longToIp(v: Long): String =
        "${(v shr 24) and 255}.${(v shr 16) and 255}.${(v shr 8) and 255}.${v and 255}"

    /** Cita kernel ARP kes (radi na vecini uredjaja) -> mapa IP -> MAC. */
    fun arpTable(): Map<String, String> {
        val map = HashMap<String, String>()
        try {
            File("/proc/net/arp").forEachLine { line ->
                val p = line.split(Regex("\\s+")).filter { it.isNotBlank() }
                if (p.size >= 4 && p[0] != "IP") {
                    val mac = p[3].uppercase()
                    if (mac != "00:00:00:00:00:00") map[p[0]] = mac
                }
            }
        } catch (_: Exception) { }
        return map
    }

    /**
     * Pravo skeniranje: paralelni ICMP/TCP "ping", zatim reverse DNS i TCP port scan.
     * Rezultati se emituju kroz [onHost] cim se host pronadje.
     */
    suspend fun scan(
        targets: List<String>,
        gateway: String?,
        timeoutMs: Int = 400,
        portTimeoutMs: Int = 260,
        scanPorts: Boolean = true,
        portList: List<Int> = COMMON_PORTS.keys.toList(),
        concurrency: Int = 96,
        onProgress: (done: Int, total: Int, current: String) -> Unit,
        onHost: (HostResult) -> Unit
    ) = withContext(Dispatchers.IO) {
        val total = targets.size
        var done = 0
        val gate = Channel<Unit>(concurrency).apply { repeat(concurrency) { trySend(Unit) } }
        val jobs = targets.map { ip ->
            async {
                gate.receive()
                try {
                    val start = System.currentTimeMillis()
                    val alive = isAlive(ip, timeoutMs)
                    if (alive) {
                        val latency = System.currentTimeMillis() - start
                        val hostname = reverseDns(ip)
                        val mac = arpTable()[ip] ?: "—"
                        val ports = if (scanPorts) scanPorts(ip, portList, portTimeoutMs) else emptyList()
                        onHost(
                            HostResult(
                                ip = ip,
                                hostname = hostname ?: "—",
                                mac = mac,
                                vendor = OuiDb.lookup(mac),
                                latencyMs = latency,
                                ports = ports,
                                isGateway = ip == gateway
                            )
                        )
                    }
                } catch (_: Exception) {
                } finally {
                    synchronized(this@NetworkScanner) { done++ }
                    onProgress(done, total, ip)
                    gate.trySend(Unit)
                }
            }
        }
        jobs.awaitAll()
    }

    /** ICMP echo (InetAddress.isReachable) uz TCP fallback na cesto otvorene portove. */
    private fun isAlive(ip: String, timeoutMs: Int): Boolean {
        try {
            if (InetAddress.getByName(ip).isReachable(timeoutMs)) return true
        } catch (_: Exception) { }
        for (p in intArrayOf(80, 443, 22, 445, 8080, 53)) {
            try {
                Socket().use { s ->
                    s.connect(InetSocketAddress(ip, p), timeoutMs / 2)
                    return true
                }
            } catch (_: Exception) { }
        }
        return false
    }

    private fun scanPorts(ip: String, ports: List<Int>, timeoutMs: Int): List<PortResult> {
        val open = ArrayList<PortResult>()
        for (p in ports) {
            try {
                Socket().use { s ->
                    s.connect(InetSocketAddress(ip, p), timeoutMs)
                    open.add(PortResult(p, COMMON_PORTS[p] ?: "unknown"))
                }
            } catch (_: Exception) { }
        }
        return open
    }

    private fun reverseDns(ip: String): String? = try {
        val a = InetAddress.getByName(ip)
        val n = a.canonicalHostName
        if (n.isNullOrBlank() || n == ip) null else n
    } catch (e: Exception) { null }
}

/** Mini OUI baza – prepoznavanje proizvodjaca iz MAC prefiksa. */
object OuiDb {
    private val db = mapOf(
        "00:15:6D" to "Ubiquiti", "24:5A:4C" to "Ubiquiti", "78:8A:20" to "Ubiquiti",
        "FC:EC:DA" to "Ubiquiti", "68:D7:9A" to "Ubiquiti",
        "3C:07:54" to "Apple", "F0:18:98" to "Apple", "AC:BC:32" to "Apple",
        "D0:81:7A" to "Apple", "A4:83:E7" to "Apple",
        "B8:27:EB" to "Raspberry Pi", "DC:A6:32" to "Raspberry Pi", "E4:5F:01" to "Raspberry Pi",
        "24:0A:C4" to "Espressif (ESP32)", "30:AE:A4" to "Espressif", "A4:CF:12" to "Espressif",
        "C8:3A:35" to "Tenda", "50:C7:BF" to "TP-Link", "A4:2B:B0" to "TP-Link",
        "00:1A:11" to "Google", "F4:F5:D8" to "Google", "54:60:09" to "Google",
        "00:17:88" to "Philips Hue", "00:0C:29" to "VMware", "52:54:00" to "QEMU/KVM",
        "00:11:32" to "Synology", "00:1D:7E" to "Cisco-Linksys", "00:25:9C" to "Cisco",
        "00:1B:63" to "Apple", "8C:85:90" to "Apple", "00:26:B9" to "Dell",
        "B8:AC:6F" to "Dell", "3C:D9:2B" to "HP", "70:5A:0F" to "HP",
        "00:80:77" to "Brother", "00:00:48" to "Epson", "AC:18:26" to "Seiko Epson",
        "E8:AB:FA" to "Shenzhen Reecam", "C0:56:E3" to "Hikvision", "44:19:B6" to "Hikvision",
        "00:12:FB" to "Samsung", "5C:F6:DC" to "Samsung", "F8:04:2E" to "Samsung",
        "20:DF:B9" to "Google Nest", "00:04:20" to "Slim Devices", "00:1E:C0" to "Microchip"
    )

    fun lookup(mac: String): String {
        if (mac.length < 8) return "Nepoznat proizvođač"
        return db[mac.substring(0, 8).uppercase()] ?: "Nepoznat proizvođač"
    }
}
