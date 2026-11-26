package com.example.data.catalog

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.example.data.db.BlockedAppEntity

data class CatalogBankApp(
    val packageName: String,
    val displayName: String,
    val category: String,
    val defaultBlocked: Boolean = true
)

data class InstalledDeviceApp(
    val packageName: String,
    val displayName: String
)

object BankingAppCatalog {
    const val CAT_NEOBANK = "Neobank & Fintech"
    const val CAT_BANK = "Major Bank"
    const val CAT_P2P = "P2P & Wallets"
    const val CAT_MAIL = "Email & Recovery"
    const val CAT_TRADING = "Investing & Crypto"
    const val CAT_CUSTOM = "Custom App"

    val knownBankingApps: List<CatalogBankApp> = listOf(
        CatalogBankApp("com.onedebit.chime", "Chime Finance", CAT_NEOBANK, true),
        CatalogBankApp("com.yahoo.mobile.client.android.mail", "Yahoo Mail", CAT_MAIL, true),
        CatalogBankApp("com.yahoo.mobile.client.android.mail.lite", "Yahoo Mail Lite", CAT_MAIL, true),
        CatalogBankApp("com.squareup.cash", "Cash App", CAT_P2P, true),
        CatalogBankApp("com.venmo", "Venmo", CAT_P2P, true),
        CatalogBankApp("com.paypal.android.p2pmobile", "PayPal", CAT_P2P, true),
        CatalogBankApp("com.chase.sig.android", "Chase Mobile", CAT_BANK, true),
        CatalogBankApp("com.infonow.bofa", "Bank of America", CAT_BANK, true),
        CatalogBankApp("com.wf.wellsfargomobile", "Wells Fargo", CAT_BANK, true),
        CatalogBankApp("com.konylabs.capitalone", "Capital One", CAT_BANK, true),
        CatalogBankApp("com.citi.citimobile", "Citi Mobile", CAT_BANK, true),
        CatalogBankApp("com.sofi.mobile", "SoFi", CAT_NEOBANK, true),
        CatalogBankApp("com.ally.MobileBanking", "Ally Mobile", CAT_NEOBANK, true),
        CatalogBankApp("com.discover.mobile", "Discover Mobile", CAT_BANK, true),
        CatalogBankApp("com.dave", "Dave Banking", CAT_NEOBANK, true),
        CatalogBankApp("com.moneylion", "MoneyLion", CAT_NEOBANK, true),
        CatalogBankApp("com.hellobrigit.app", "Brigit", CAT_NEOBANK, true),
        CatalogBankApp("com.usbank.mobilebanking", "U.S. Bank", CAT_BANK, true),
        CatalogBankApp("com.pnc.ecommerce.mobile", "PNC Mobile", CAT_BANK, true),
        CatalogBankApp("com.tdbank", "TD Bank", CAT_BANK, true),
        CatalogBankApp("com.robinhood.android", "Robinhood", CAT_TRADING, false),
        CatalogBankApp("com.coinbase.android", "Coinbase", CAT_TRADING, false)
    )

    val protectedSystemPackages: Set<String> = setOf(
        "com.android.settings",
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.samsung.android.packageinstaller"
    )

    /**
     * App stores and system package installers blocked during an active 2-year lock
     * so users cannot install Chime, Yahoo Mail, or banking apps to bypass restrictions.
     */
    val installGuardPackages: Set<String> = setOf(
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.samsung.android.packageinstaller",
        "com.sec.android.app.samsungapps",
        "com.amazon.venezia",
        "com.aurora.store"
    )

    val systemUpdatePackages: Set<String> = setOf(
        "com.samsung.android.fotaagent",
        "com.google.android.gms.updater",
        "com.sec.android.app.parser",
        "com.google.android.apps.restore"
    )

    val browserPackages: Set<String> = setOf(
        "com.android.chrome",
        "com.chrome.beta",
        "com.chrome.dev",
        "com.chrome.canary",
        "com.sec.android.app.sbrowser",
        "org.mozilla.firefox",
        "org.mozilla.firefox_beta",
        "org.mozilla.fenix",
        "org.mozilla.focus",
        "com.microsoft.emmx",
        "com.brave.browser",
        "com.opera.browser",
        "com.opera.mini.native",
        "com.opera.gx",
        "com.duckduckgo.mobile.android",
        "com.kiwibrowser.browser",
        "com.vivaldi.browser",
        "com.UCMobile.intl",
        "org.torproject.torbrowser",
        "com.ecosia.android",
        "com.qwant.liberty",
        "com.yandex.browser"
    )

    fun isBrowserApp(packageName: String): Boolean {
        if (browserPackages.contains(packageName)) return true
        val lower = packageName.lowercase()
        return lower.contains("browser") || lower.contains("chrome")
    }

    fun getCatalogMatch(packageName: String): CatalogBankApp? {
        return knownBankingApps.firstOrNull { it.packageName.equals(packageName, ignoreCase = true) }
    }

    fun friendlySystemAppName(packageName: String): String? {
        return when (packageName) {
            "com.android.settings" -> "Android Settings"
            "com.android.vending" -> "Google Play Store"
            "com.samsung.android.fotaagent" -> "Samsung Software Update"
            "com.google.android.gms.updater" -> "Android System Update"
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.samsung.android.packageinstaller" -> "Package Installer"
            "com.sec.android.app.samsungapps" -> "Galaxy Store"
            "com.amazon.venezia" -> "Amazon Appstore"
            "com.aurora.store" -> "Aurora Store"
            "com.android.chrome",
            "com.chrome.beta",
            "com.chrome.dev",
            "com.chrome.canary" -> "Google Chrome"
            "com.sec.android.app.sbrowser" -> "Samsung Internet"
            "org.mozilla.firefox",
            "org.mozilla.firefox_beta",
            "org.mozilla.fenix",
            "org.mozilla.focus" -> "Mozilla Firefox"
            "com.microsoft.emmx" -> "Microsoft Edge"
            "com.brave.browser" -> "Brave Browser"
            "com.opera.browser",
            "com.opera.mini.native",
            "com.opera.gx" -> "Opera Browser"
            "com.duckduckgo.mobile.android" -> "DuckDuckGo Browser"
            else -> null
        }
    }

    fun toSeedEntities(): List<BlockedAppEntity> {
        return knownBankingApps.map {
            BlockedAppEntity(
                packageName = it.packageName,
                displayName = it.displayName,
                category = it.category,
                isBlocked = it.defaultBlocked,
                isCustom = false
            )
        }
    }

    private val bankingOrMailKeywords = listOf(
        "chime", "yahoo", "bank", "creditunion", "credit union", "fintech",
        "cashapp", "cash app", "venmo", "paypal", "zelle", "chase", "wellsfargo",
        "wells fargo", "bofa", "capitalone", "capital one", "citibank", "citi",
        "sofi", "ally", "discover", "moneylion", "brigit", "usbank", "u.s. bank",
        "pnc", "tdbank", "td bank", "usaa", "navyfederal", "navy federal",
        "truist", "regions", "robinhood", "coinbase", "schwab", "fidelity", "wise", "revolut",
        "onepay", "opay", "prepaid", "debit", "wallet", "pay", "money", "card", "finance",
        "capital", "wealth", "trust", "loan", "cash", "credit", "netspend", "greendot",
        "varo", "current", "albert", "step", "payoneer", "skrill", "neteller", "momo", "gcash",
        "dana", "truemoney", "zalopay", "viettelpay"
    )

    fun isLikelyBankOrMailApp(packageName: String, displayName: String): Boolean {
        val combined = "${packageName.lowercase()} ${displayName.lowercase()}"
        return bankingOrMailKeywords.any { keyword -> combined.contains(keyword) }
    }
}

object DeviceAppScanner {

    fun isPackageInstalled(context: Context, packageName: String): Boolean {
        val pm = context.packageManager
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun getInstalledLauncherApps(context: Context): List<InstalledDeviceApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
        val ownPackage = context.packageName
        return resolveInfos
            .asSequence()
            .mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == ownPackage) return@mapNotNull null
                val label = info.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() } ?: pkg
                InstalledDeviceApp(packageName = pkg, displayName = label)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.displayName.lowercase() }
            .toList()
    }
}
