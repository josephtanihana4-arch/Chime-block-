package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.blocker.DnsPacketFilter
import com.example.data.catalog.BankingAppCatalog
import com.example.data.db.SavingsDepositEntity
import com.example.data.db.VaultDatabase
import com.example.data.repository.VaultRepository
import com.example.security.PinHasher
import com.example.security.SecurityPrefs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app name matches VaultLock`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("VaultLock", context.getString(R.string.app_name))
    }

    @Test
    fun `pin hasher verifies valid pin and rejects wrong pin`() {
        val hashed = PinHasher.hashPin("482910")
        assertTrue(PinHasher.verifyPin("482910", hashed.hashBase64, hashed.saltBase64))
        assertFalse(PinHasher.verifyPin("000000", hashed.hashBase64, hashed.saltBase64))
    }

    @Test
    fun `security prefs enforces one time pin per 2 years and lockout`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = SecurityPrefs(context)
        prefs.setAdminPin("739150")

        repeat(4) {
            val res = prefs.verifyAdminPin("111111")
            assertTrue(res is SecurityPrefs.PinCheckResult.Invalid)
        }
        val fifth = prefs.verifyAdminPin("111111")
        assertTrue(fifth is SecurityPrefs.PinCheckResult.LockedOut)

        prefs.setAdminPin("739150")
        val now = System.currentTimeMillis()
        prefs.startTwoYearCommitment(savingsGoalCents = 1_200_000L, startTimeMillis = now)
        val config = prefs.getConfig()
        assertTrue(config.isProtectionActive)
        assertTrue(config.isTamperGuardEnabled)
        assertTrue(config.lockEndMillis >= now + (729L * 24 * 60 * 60 * 1000))
        assertEquals(1, config.remainingPinUsesInCycle)

        // Consuming the one-time PIN succeeds once, then blocks reuse for the 2-year cycle
        assertEquals(
            SecurityPrefs.PinCheckResult.Success,
            prefs.verifyAdminPin("739150", consumeOneTimePin = true)
        )
        assertTrue(prefs.getConfig().isOneTimePinConsumed)
        assertEquals(0, prefs.getConfig().remainingPinUsesInCycle)
        assertEquals(
            SecurityPrefs.PinCheckResult.OneTimePinAlreadyConsumed,
            prefs.verifyAdminPin("739150", consumeOneTimePin = true)
        )
    }

    @Test
    fun `room database seeds Chime and Yahoo Mail and tracks 2-year deposits`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, VaultDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.vaultDao()
            dao.insertBlockedAppsIfMissing(BankingAppCatalog.toSeedEntities())
            val activePackages = dao.getActiveBlockedPackageNames()
            assertTrue(activePackages.contains("com.onedebit.chime"))
            assertTrue(activePackages.contains("com.yahoo.mobile.client.android.mail"))
            assertTrue(activePackages.contains("com.squareup.cash"))

            dao.insertDeposit(
                SavingsDepositEntity(
                    amountCents = 50_000L,
                    note = "Month 18 saved",
                    monthNumber = 18
                )
            )
            assertEquals(50_000L, dao.observeTotalSavedCents().first())
        } finally {
            db.close()
        }
    }

    @Test
    fun `auto detector identifies Chime, Yahoo Mail, and banking apps accurately`() {
        assertTrue(BankingAppCatalog.isLikelyBankOrMailApp("com.onedebit.chime", "Chime"))
        assertTrue(BankingAppCatalog.isLikelyBankOrMailApp("com.yahoo.mobile.client.android.mail", "Yahoo Mail"))
        assertTrue(BankingAppCatalog.isLikelyBankOrMailApp("com.regions.mobbanking", "Regions Bank"))
        assertFalse(BankingAppCatalog.isLikelyBankOrMailApp("com.android.calculator2", "Calculator"))
    }

    @Test
    fun `security prefs automatically relocks itself for next 2-year cycle upon expiry`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = SecurityPrefs(context)
        prefs.setAdminPin("739150")

        val pastStart = 1_600_000_000_000L
        prefs.startTwoYearCommitment(savingsGoalCents = 1_200_000L, startTimeMillis = pastStart)
        val firstEnd = prefs.getConfig().lockEndMillis

        // Consume PIN in Cycle #1
        assertEquals(
            SecurityPrefs.PinCheckResult.Success,
            prefs.verifyAdminPin("739150", consumeOneTimePin = true)
        )

        // Rollover at a timestamp after Cycle #1 ended
        val afterFirstCycle = firstEnd + 1_000L
        val newCycle = prefs.checkAndRolloverTwoYearCycleIfNeeded(nowMillis = afterFirstCycle)
        assertEquals(2, newCycle)

        val updated = prefs.getConfig()
        assertEquals(2, updated.lockCycleNumber)
        assertTrue(updated.isProtectionActive)
        assertTrue(updated.isTamperGuardEnabled)
        assertTrue(updated.isInstallBlockerEnabled)
        assertTrue(updated.lockEndMillis > afterFirstCycle)
        assertEquals(1, updated.remainingPinUsesInCycle)
    }

    @Test
    fun `install guard protects app stores and detects newly installed Chime and Yahoo Mail`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertFalse(BankingAppCatalog.installGuardPackages.contains("com.android.vending"))
        assertTrue(BankingAppCatalog.installGuardPackages.contains("com.google.android.packageinstaller"))
        assertTrue(BankingAppCatalog.installGuardPackages.contains("com.samsung.android.packageinstaller"))
        assertTrue(BankingAppCatalog.isLikelyBankOrMailApp("com.onepay", "OnePay"))

        val db = Room.inMemoryDatabaseBuilder(context, VaultDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val prefs = SecurityPrefs(context)
            prefs.setAdminPin("739150")
            prefs.startTwoYearCommitment(1_200_000L)
            assertTrue(prefs.getConfig().isInstallBlockerEnabled)

            val repo = VaultRepository(context, db.vaultDao(), prefs)
            repo.ensureSeeded()

            // Simulate newly installed Chime, Yahoo Mail Lite, and an unknown bank APK + benign app
            val chimeBlocked = repo.handleNewlyInstalledPackage("com.onedebit.chime", "Chime")
            val yahooBlocked = repo.handleNewlyInstalledPackage("com.yahoo.mobile.client.android.mail", "Yahoo Mail")
            val customBankBlocked = repo.handleNewlyInstalledPackage("com.acme.creditunion", "Acme Credit Union")
            val weatherIgnored = repo.handleNewlyInstalledPackage("com.example.weather", "Local Weather")

            assertEquals("Chime", chimeBlocked?.displayName)
            assertTrue(chimeBlocked?.isBlocked == true)
            assertEquals("Yahoo Mail", yahooBlocked?.displayName)
            assertTrue(yahooBlocked?.isBlocked == true)
            assertEquals("Acme Credit Union", customBankBlocked?.displayName)
            assertTrue(customBankBlocked?.isBlocked == true)
            assertEquals(null, weatherIgnored)

            val activePackages = repo.getActiveBlockedPackages()
            assertTrue(activePackages.contains("com.onedebit.chime"))
            assertTrue(activePackages.contains("com.yahoo.mobile.client.android.mail"))
            assertTrue(activePackages.contains("com.acme.creditunion"))

            val events = db.vaultDao().observeSecurityEvents().first()
            assertTrue(events.any { it.eventType == VaultRepository.EVENT_INSTALL_BLOCKED && it.packageName == "com.onedebit.chime" })
            assertTrue(events.any { it.eventType == VaultRepository.EVENT_INSTALL_BLOCKED && it.packageName == "com.yahoo.mobile.client.android.mail" })
        } finally {
            db.close()
        }
    }

    @Test
    fun `dns packet filter detects and builds NXDOMAIN response for AI Studio and DoH domains`() {
        assertTrue(DnsPacketFilter.isAiStudioDomain("aistudio.google.com"))
        assertTrue(DnsPacketFilter.isAiStudioDomain("makersuite.google.com"))
        assertTrue(DnsPacketFilter.isBlockedDomain("aistudio.google.com"))
        assertTrue(DnsPacketFilter.isBlockedDomain("dns.google"))
        assertFalse(DnsPacketFilter.isBlockedDomain("www.wikipedia.org"))

        assertTrue(BankingAppCatalog.isBrowserApp("com.android.chrome"))
        assertTrue(BankingAppCatalog.isBrowserApp("com.sec.android.app.sbrowser"))
        assertTrue(BankingAppCatalog.isBrowserApp("org.mozilla.firefox"))
        assertTrue(BankingAppCatalog.isBrowserApp("com.brave.browser"))
        assertFalse(BankingAppCatalog.isBrowserApp("com.android.calculator2"))

        // Construct synthetic IPv4 + UDP + DNS query for aistudio.google.com
        val qName = byteArrayOf(
            8, 'a'.code.toByte(), 'i'.code.toByte(), 's'.code.toByte(), 't'.code.toByte(), 'u'.code.toByte(), 'd'.code.toByte(), 'i'.code.toByte(), 'o'.code.toByte(),
            6, 'g'.code.toByte(), 'o'.code.toByte(), 'o'.code.toByte(), 'g'.code.toByte(), 'l'.code.toByte(), 'e'.code.toByte(),
            3, 'c'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(),
            0
        )
        val dnsQuery = ByteArray(12 + qName.size + 4)
        dnsQuery[0] = 0x12
        dnsQuery[1] = 0x34
        dnsQuery[2] = 0x01 // RD=1
        dnsQuery[5] = 0x01 // QDCOUNT=1
        System.arraycopy(qName, 0, dnsQuery, 12, qName.size)
        dnsQuery[12 + qName.size + 1] = 0x01 // QTYPE=A
        dnsQuery[12 + qName.size + 3] = 0x01 // QCLASS=IN

        val totalLen = 20 + 8 + dnsQuery.size
        val ipPacket = ByteArray(totalLen)
        ipPacket[0] = 0x45.toByte()
        ipPacket[2] = ((totalLen ushr 8) and 0xFF).toByte()
        ipPacket[3] = (totalLen and 0xFF).toByte()
        ipPacket[9] = 17.toByte()
        ipPacket[12] = 10; ipPacket[13] = 111; ipPacket[14] = 222.toByte(); ipPacket[15] = 1
        ipPacket[16] = 10; ipPacket[17] = 111; ipPacket[18] = 222.toByte(); ipPacket[19] = 2
        // UDP srcPort=50000, dstPort=53
        ipPacket[20] = 0xC3.toByte(); ipPacket[21] = 0x50
        ipPacket[22] = 0x00; ipPacket[23] = 53
        val udpLen = 8 + dnsQuery.size
        ipPacket[24] = ((udpLen ushr 8) and 0xFF).toByte()
        ipPacket[25] = (udpLen and 0xFF).toByte()
        System.arraycopy(dnsQuery, 0, ipPacket, 28, dnsQuery.size)

        val info = DnsPacketFilter.extractDnsQueryDomain(ipPacket, totalLen)
        assertEquals("aistudio.google.com", info?.domain)

        val nxResponse = DnsPacketFilter.buildNxDomainIpUdpResponse(
            requestPacket = ipPacket,
            requestLength = totalLen,
            dnsOffset = info!!.dnsOffset,
            dnsLength = info.dnsLength
        )
        assertTrue(nxResponse != null)
        // Verify swapped destination port == 50000, QR == 1, RCODE == 3 (NXDOMAIN)
        assertEquals(0xC3.toByte(), nxResponse!![22])
        assertEquals(0x50.toByte(), nxResponse[23])
        assertEquals(0x81.toByte(), nxResponse[30])
        assertEquals(0x83.toByte(), nxResponse[31])
    }
}
