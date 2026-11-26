package com.example.blocker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.repository.VaultRepository
import com.example.security.SystemPermissionsHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Local on-device Android VpnService DNS Firewall.
 * Filters DNS queries across Chrome and every browser on the phone so
 * aistudio.google.com and makersuite.google.com cannot resolve while locked,
 * while forwarding all other DNS queries directly to upstream DNS without
 * proxying user browsing traffic.
 */
class WebFilterVpnService : VpnService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var vpnJob: Job? = null
    private var vpnInterface: ParcelFileDescriptor? = null
    private val tunWriteLock = Any()

    @Volatile
    private var lastLoggedDomain: String? = null

    @Volatile
    private var lastLogMillis: Long = 0L

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpnAndSelf()
            return START_NOT_STICKY
        }

        val repository = VaultRepository.getInstance(this)
        val config = repository.securityPrefs.getConfig()
        if (!config.isProtectionActive || !config.isAiStudioWebBlockerEnabled || prepare(this) != null) {
            stopVpnAndSelf()
            return START_NOT_STICKY
        }

        ensureNotificationChannel()
        startAsForeground(buildNotification())
        startTunnelLoop(repository)
        return START_STICKY
    }

    override fun onRevoke() {
        stopVpnAndSelf()
        super.onRevoke()
    }

    private fun startAsForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startTunnelLoop(repository: VaultRepository) {
        if (vpnJob?.isActive == true && vpnInterface != null) return

        val pfd = establishTunnel() ?: run {
            stopVpnAndSelf()
            return
        }
        vpnInterface = pfd
        isRunning = true

        vpnJob = serviceScope.launch {
            val input = FileInputStream(pfd.fileDescriptor)
            val output = FileOutputStream(pfd.fileDescriptor)
            val buffer = ByteArray(MAX_PACKET_SIZE)

            try {
                while (isActive) {
                    val length = input.read(buffer)
                    if (length <= 0) continue

                    val dnsInfo = DnsPacketFilter.extractDnsQueryDomain(buffer, length) ?: continue

                    if (DnsPacketFilter.isBlockedDomain(dnsInfo.domain)) {
                        val nxResponse = DnsPacketFilter.buildNxDomainIpUdpResponse(
                            requestPacket = buffer,
                            requestLength = length,
                            dnsOffset = dnsInfo.dnsOffset,
                            dnsLength = dnsInfo.dnsLength
                        )
                        if (nxResponse != null) {
                            synchronized(tunWriteLock) {
                                output.write(nxResponse)
                            }
                        }
                        if (DnsPacketFilter.isAiStudioDomain(dnsInfo.domain)) {
                            handleAiStudioBlockedAttempt(repository, dnsInfo.domain)
                        }
                    } else {
                        val packetCopy = buffer.copyOf(length)
                        val dnsCopy = ByteArray(dnsInfo.dnsLength)
                        System.arraycopy(buffer, dnsInfo.dnsOffset, dnsCopy, 0, dnsInfo.dnsLength)
                        launch {
                            forwardDnsQuery(packetCopy, length, dnsCopy, output)
                        }
                    }
                }
            } catch (_: Exception) {
                // TUN closed or stopped
            } finally {
                isRunning = false
            }
        }
    }

    private fun establishTunnel(): ParcelFileDescriptor? {
        return try {
            val builder = Builder()
                .setSession("VaultLock AI Studio Web Shield")
                .setMtu(MAX_PACKET_SIZE)
                .addAddress(VPN_ADDRESS, 32)
                .addDnsServer(VPN_DNS_SERVER)
                .addRoute(VPN_DNS_SERVER, 32)

            // Also intercept direct connections to major public DNS / DoH endpoints so
            // Chrome and other browsers cannot bypass the local DNS filter via hardcoded DNS.
            PUBLIC_DNS_IPS.forEach { ip ->
                builder.addRoute(ip, 32)
            }

            try {
                builder.addDisallowedApplication(packageName)
            } catch (_: Exception) {
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setMetered(false)
            }
            builder.establish()
        } catch (_: Exception) {
            null
        }
    }

    private fun forwardDnsQuery(
        requestPacket: ByteArray,
        requestLength: Int,
        dnsQuery: ByteArray,
        tunOutput: FileOutputStream
    ) {
        val responseBuf = ByteArray(2048)
        for (upstream in UPSTREAM_DNS_SERVERS) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket()
                if (!protect(socket)) {
                    socket.close()
                    continue
                }
                socket.soTimeout = 2_500
                val addr = InetAddress.getByName(upstream)
                socket.send(DatagramPacket(dnsQuery, dnsQuery.size, addr, 53))
                val replyPacket = DatagramPacket(responseBuf, responseBuf.size)
                socket.receive(replyPacket)

                val ipUdpReply = DnsPacketFilter.buildIpUdpResponse(
                    requestPacket = requestPacket,
                    requestLength = requestLength,
                    dnsPayload = responseBuf,
                    dnsPayloadLength = replyPacket.length
                )
                if (ipUdpReply != null) {
                    synchronized(tunWriteLock) {
                        tunOutput.write(ipUdpReply)
                    }
                }
                return
            } catch (_: Exception) {
                // Try fallback upstream DNS
            } finally {
                try {
                    socket?.close()
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun handleAiStudioBlockedAttempt(repository: VaultRepository, domain: String) {
        val now = System.currentTimeMillis()
        if (lastLoggedDomain == domain && (now - lastLogMillis) < LOG_DEBOUNCE_MS) {
            return
        }
        lastLoggedDomain = domain
        lastLogMillis = now

        serviceScope.launch {
            repository.logSecurityEvent(
                eventType = VaultRepository.EVENT_WEB_BLOCKED,
                title = "Blocked AI Studio in Browser",
                detail = "VaultLock DNS firewall blocked $domain across device browsers.",
                packageName = domain
            )
        }

        if (SystemPermissionsHelper.hasOverlayPermission(this)) {
            val shieldIntent = Intent(this, BlockedOverlayActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
                putExtra(BlockedOverlayActivity.EXTRA_BLOCKED_PACKAGE, domain)
                putExtra(BlockedOverlayActivity.EXTRA_BLOCKED_APP_NAME, "Google AI Studio ($domain)")
                putExtra(BlockedOverlayActivity.EXTRA_IS_WEB_SHIELD, true)
            }
            try {
                startActivity(shieldIntent)
            } catch (_: SecurityException) {
            }
        }
    }

    private fun stopVpnAndSelf() {
        isRunning = false
        vpnJob?.cancel()
        vpnJob = null
        try {
            vpnInterface?.close()
        } catch (_: Exception) {
        }
        vpnInterface = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun buildNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("VaultLock AI Studio Web Shield")
            .setContentText("Blocking aistudio.google.com across Chrome and all browsers.")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppIntent)
            .build()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "VaultLock Web Shield VPN",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active DNS firewall blocking AI Studio across all browsers."
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        isRunning = false
        vpnJob?.cancel()
        try {
            vpnInterface?.close()
        } catch (_: Exception) {
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val VPN_ADDRESS = "10.111.222.1"
        private const val VPN_DNS_SERVER = "10.111.222.2"
        private const val MAX_PACKET_SIZE = 32767
        private const val CHANNEL_ID = "vaultlock_vpn_channel"
        private const val NOTIFICATION_ID = 4109
        private const val LOG_DEBOUNCE_MS = 5_000L
        private const val ACTION_STOP = "com.example.blocker.VPN_ACTION_STOP"

        private val PUBLIC_DNS_IPS = listOf(
            "8.8.8.8", "8.8.4.4",
            "1.1.1.1", "1.0.0.1",
            "9.9.9.9", "149.112.112.112",
            "208.67.222.222", "208.67.220.220"
        )
        private val UPSTREAM_DNS_SERVERS = listOf("8.8.8.8", "1.1.1.1")

        @Volatile
        var isRunning: Boolean = false
            private set

        fun start(context: Context) {
            if (prepare(context) != null) return
            val intent = Intent(context, WebFilterVpnService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, WebFilterVpnService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {
            }
        }
    }
}
