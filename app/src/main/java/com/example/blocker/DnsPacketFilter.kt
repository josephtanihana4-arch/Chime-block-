package com.example.blocker

/**
 * Pure Kotlin IPv4/UDP/DNS parser & NXDOMAIN packet builder for VaultLock's
 * on-device VpnService local DNS firewall.
 *
 * Blocks AI Studio web domains across Chrome and every Android browser on the device,
 * as well as known Secure DNS (DoH) bootstrap hosts so browsers fall back to
 * the local filtered VPN DNS resolver rather than bypassing domain blocks.
 */
object DnsPacketFilter {

    data class DnsPacketInfo(
        val domain: String,
        val dnsOffset: Int,
        val dnsLength: Int,
        val qType: Int
    )

    val aiStudioDomains: Set<String> = setOf(
        "aistudio.google.com",
        "makersuite.google.com",
        "alkalimakersuite-pa.clients6.google.com",
        "aistudio.sandbox.google.com",
        "ai.studio",
        "aistudio.google"
    )

    private val dohBootstrapDomains: Set<String> = setOf(
        "dns.google",
        "dns.google.com",
        "cloudflare-dns.com",
        "mozilla.cloudflare-dns.com",
        "chrome.cloudflare-dns.com",
        "dns.quad9.net",
        "doh.opendns.com",
        "dns.nextdns.io",
        "dns.adguard.com",
        "dns.adguard-dns.com",
        "dns.controld.com"
    )

    val blockedDomains: Set<String> = aiStudioDomains + dohBootstrapDomains

    fun isAiStudioDomain(domain: String): Boolean {
        val clean = normalizeDomain(domain)
        if (clean.isEmpty()) return false
        return aiStudioDomains.any { target ->
            clean == target || clean.endsWith(".$target")
        }
    }

    fun isBlockedDomain(domain: String): Boolean {
        val clean = normalizeDomain(domain)
        if (clean.isEmpty()) return false
        return blockedDomains.any { target ->
            clean == target || clean.endsWith(".$target")
        }
    }

    fun normalizeDomain(domain: String): String {
        return domain.trim().trimEnd('.').lowercase()
    }

    fun extractDnsQueryDomain(packet: ByteArray, length: Int): DnsPacketInfo? {
        if (length < 28 || length > packet.size) return null
        val version = (packet[0].toInt() ushr 4) and 0x0F
        if (version != 4) return null

        val ihl = (packet[0].toInt() and 0x0F) * 4
        if (ihl < 20 || length < ihl + 8 + 12) return null

        val protocol = packet[9].toInt() and 0xFF
        if (protocol != 17) return null // UDP only

        val dstPort = ((packet[ihl + 2].toInt() and 0xFF) shl 8) or (packet[ihl + 3].toInt() and 0xFF)
        if (dstPort != 53) return null

        val udpLength = ((packet[ihl + 4].toInt() and 0xFF) shl 8) or (packet[ihl + 5].toInt() and 0xFF)
        val dnsOffset = ihl + 8
        val dnsLength = minOf(udpLength - 8, length - dnsOffset)
        if (dnsLength < 12) return null

        // Check QR == 0 (query)
        val flagsHigh = packet[dnsOffset + 2].toInt() and 0xFF
        if ((flagsHigh and 0x80) != 0) return null

        val qdCount = ((packet[dnsOffset + 4].toInt() and 0xFF) shl 8) or (packet[dnsOffset + 5].toInt() and 0xFF)
        if (qdCount < 1) return null

        val domainBuilder = StringBuilder()
        var idx = dnsOffset + 12
        val dnsEnd = dnsOffset + dnsLength
        var safety = 0
        while (idx < dnsEnd && safety < 128) {
            safety++
            val labelLen = packet[idx].toInt() and 0xFF
            if (labelLen == 0) {
                idx++
                break
            }
            if ((labelLen and 0xC0) != 0 || labelLen > 63 || idx + 1 + labelLen >= dnsEnd) {
                return null
            }
            if (domainBuilder.isNotEmpty()) domainBuilder.append('.')
            for (j in 1..labelLen) {
                domainBuilder.append((packet[idx + j].toInt() and 0xFF).toChar())
            }
            idx += 1 + labelLen
        }

        if (domainBuilder.isEmpty() || idx + 2 > dnsEnd) return null
        val qType = ((packet[idx].toInt() and 0xFF) shl 8) or (packet[idx + 1].toInt() and 0xFF)
        return DnsPacketInfo(
            domain = domainBuilder.toString().lowercase(),
            dnsOffset = dnsOffset,
            dnsLength = dnsLength,
            qType = qType
        )
    }

    /**
     * Builds a valid IPv4 + UDP + DNS NXDOMAIN response packet from the intercepted query packet.
     */
    fun buildNxDomainIpUdpResponse(
        requestPacket: ByteArray,
        requestLength: Int,
        dnsOffset: Int,
        dnsLength: Int
    ): ByteArray? {
        if (dnsLength < 12 || dnsOffset + dnsLength > requestLength) return null
        val dnsResponse = ByteArray(dnsLength)
        System.arraycopy(requestPacket, dnsOffset, dnsResponse, 0, dnsLength)

        // DNS Header: QR=1, copy RD, RA=1, RCODE=3 (NXDOMAIN)
        val rdBit = requestPacket[dnsOffset + 2].toInt() and 0x01
        dnsResponse[2] = (0x80 or rdBit).toByte()
        dnsResponse[3] = 0x83.toByte()
        // ANCOUNT = 0, NSCOUNT = 0, ARCOUNT = 0
        for (i in 6 until 12) {
            dnsResponse[i] = 0
        }
        return buildIpUdpResponse(requestPacket, requestLength, dnsResponse, dnsResponse.size)
    }

    /**
     * Wraps a DNS response payload inside an IPv4 + UDP response packet with swapped src/dst.
     */
    fun buildIpUdpResponse(
        requestPacket: ByteArray,
        requestLength: Int,
        dnsPayload: ByteArray,
        dnsPayloadLength: Int
    ): ByteArray? {
        if (requestLength < 28 || dnsPayloadLength <= 0 || dnsPayloadLength > dnsPayload.size) return null
        val reqIhl = (requestPacket[0].toInt() and 0x0F) * 4
        if (reqIhl < 20 || requestLength < reqIhl + 8) return null

        val ipHeaderLen = 20
        val udpLen = 8 + dnsPayloadLength
        val totalLen = ipHeaderLen + udpLen
        val out = ByteArray(totalLen)

        // IPv4 Header
        out[0] = 0x45.toByte() // Version 4, IHL 5 (20 bytes)
        out[1] = 0x00
        out[2] = ((totalLen ushr 8) and 0xFF).toByte()
        out[3] = (totalLen and 0xFF).toByte()
        out[4] = requestPacket[4]
        out[5] = requestPacket[5]
        out[6] = 0x40.toByte() // Don't fragment
        out[7] = 0x00
        out[8] = 64.toByte()   // TTL
        out[9] = 17.toByte()   // UDP
        out[10] = 0x00
        out[11] = 0x00

        // Swap Source IP (12..15) and Destination IP (16..19)
        System.arraycopy(requestPacket, 16, out, 12, 4)
        System.arraycopy(requestPacket, 12, out, 16, 4)

        val ipChecksum = computeChecksum(out, 0, ipHeaderLen)
        out[10] = ((ipChecksum ushr 8) and 0xFF).toByte()
        out[11] = (ipChecksum and 0xFF).toByte()

        // UDP Header (swap srcPort and dstPort)
        out[20] = requestPacket[reqIhl + 2]
        out[21] = requestPacket[reqIhl + 3]
        out[22] = requestPacket[reqIhl]
        out[23] = requestPacket[reqIhl + 1]
        out[24] = ((udpLen ushr 8) and 0xFF).toByte()
        out[25] = (udpLen and 0xFF).toByte()
        out[26] = 0x00
        out[27] = 0x00

        System.arraycopy(dnsPayload, 0, out, 28, dnsPayloadLength)

        val udpChecksum = computeUdpChecksum(out, ipHeaderLen, udpLen)
        out[26] = ((udpChecksum ushr 8) and 0xFF).toByte()
        out[27] = (udpChecksum and 0xFF).toByte()

        return out
    }

    private fun computeChecksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var i = offset
        val end = offset + length
        while (i + 1 < end) {
            val word = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        if (i < end) {
            sum += (data[i].toInt() and 0xFF) shl 8
        }
        while ((sum ushr 16) != 0L) {
            sum = (sum and 0xFFFF) + (sum ushr 16)
        }
        return (sum.inv() and 0xFFFF).toInt()
    }

    private fun computeUdpChecksum(packet: ByteArray, udpOffset: Int, udpLength: Int): Int {
        var sum = 0L
        // Pseudo-header: src IP (12..15), dst IP (16..19), zero + proto(17), udpLength
        for (i in 12..18 step 2) {
            sum += ((packet[i].toInt() and 0xFF) shl 8) or (packet[i + 1].toInt() and 0xFF)
        }
        sum += 17L
        sum += udpLength.toLong()

        var i = udpOffset
        val end = udpOffset + udpLength
        while (i + 1 < end) {
            val word = ((packet[i].toInt() and 0xFF) shl 8) or (packet[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        if (i < end) {
            sum += (packet[i].toInt() and 0xFF) shl 8
        }
        while ((sum ushr 16) != 0L) {
            sum = (sum and 0xFFFF) + (sum ushr 16)
        }
        val checksum = (sum.inv() and 0xFFFF).toInt()
        return if (checksum == 0) 0xFFFF else checksum
    }
}
