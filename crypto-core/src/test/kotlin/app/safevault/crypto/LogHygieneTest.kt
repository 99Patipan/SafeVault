package app.safevault.crypto

import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/** Static guard: crypto code must never log, print, or touch the network. */
class LogHygieneTest {
    private val forbidden = listOf(
        "android.util.Log", "println(", "print(", "printStackTrace", "System.out", "System.err",
        "Timber", "java.net.", "HttpURLConnection", "okhttp",
    )

    @Test fun noLoggingOrNetworkInCryptoSources() {
        val files = File("src/main").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        if (files.isEmpty()) fail("No sources found; test working directory is wrong")
        for (f in files) {
            val text = f.readText()
            for (bad in forbidden) if (text.contains(bad)) fail("${f.name} contains forbidden token: $bad")
        }
    }
}
