package app.safevault

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Static guards for the "no leaks" requirements: no logging, no network, no analytics, no backup, FLAG_SECURE. */
class ProjectHygieneTest {
    private fun sources() = File("src/main").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        .also { assertTrue("no sources found (wrong working dir?)", it.isNotEmpty()) }

    @Test fun noLoggingOrNetworkCodeInAppSources() {
        val forbidden = listOf(
            "android.util.Log", "Log.d(", "Log.i(", "Log.w(", "Log.e(", "Log.v(", "println(", "printStackTrace",
            "System.out", "System.err", "Timber", "java.net.", "HttpURLConnection", "okhttp", "retrofit", "WebView",
        )
        for (f in sources()) { val t = f.readText(); for (bad in forbidden) assertFalse("${f.name} contains $bad", t.contains(bad)) }
    }

    @Test fun manifestHasNoInternetAndNoBackup() {
        val m = File("src/main/AndroidManifest.xml").readText()
        assertFalse(m.contains("android.permission.INTERNET"))
        assertFalse(m.contains("ACCESS_NETWORK_STATE"))
        assertTrue(m.contains("android:allowBackup=\"false\""))
        assertTrue(m.contains("@xml/data_extraction_rules"))
        assertFalse(m.contains("usesCleartextTraffic=\"true\""))
        assertFalse(m.contains("android:debuggable"))
    }

    @Test fun backupRulesExcludeDatabaseAndPrefs() {
        for (name in listOf("backup_rules.xml", "data_extraction_rules.xml")) {
            val x = File("src/main/res/xml/$name").readText()
            assertTrue(x.contains("domain=\"database\"")); assertTrue(x.contains("domain=\"sharedpref\""))
            assertTrue(x.contains("domain=\"file\""))
        }
    }

    @Test fun noAnalyticsOrAdLibrariesInDependencies() {
        val banned = listOf("firebase", "crashlytics", "sentry", "bugsnag", "appcenter", "analytics", "admob", "play-services-ads",
            "facebook", "mixpanel", "amplitude", "segment", "datadog", "newrelic", "okhttp", "retrofit")
        for (path in listOf("build.gradle.kts", "../crypto-core/build.gradle.kts")) {
            val t = File(path).readText().lowercase()
            for (b in banned) assertFalse("$path references $b", t.contains(b))
        }
    }

    @Test fun mainActivitySetsFlagSecure() {
        assertTrue(File("src/main/kotlin/app/safevault/ui/MainActivity.kt").readText().contains("FLAG_SECURE"))
    }
}
