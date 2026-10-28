package app.safevault.platform

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle

/** Copies with the "sensitive" flag (hidden from clipboard previews) and clears automatically after a delay. */
class ClipboardHelper(context: Context) {
    private val cm = context.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val handler = Handler(Looper.getMainLooper())
    private var counter = 0
    private var currentLabel: String? = null
    private var pending: Runnable? = null

    fun copy(text: String, clearAfterSeconds: Int) {
        pending?.let { handler.removeCallbacks(it) }
        val label = "SafeVault#${++counter}"
        val clip = ClipData.newPlainText(label, text)
        clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
        cm.setPrimaryClip(clip)
        currentLabel = label
        val r = Runnable { clearNow() }
        pending = r
        handler.postDelayed(r, clearAfterSeconds * 1000L)
    }

    /**
     * Clears the clipboard if it still holds what we copied. Android 10+ hides the clip from background apps
     * (it reads as null); in that case we clear anyway, since we can't tell and our copy is the likely content.
     */
    fun clearNow() {
        pending?.let { handler.removeCallbacks(it) }
        pending = null
        val label = currentLabel ?: return
        currentLabel = null
        val clip = try { cm.primaryClip } catch (e: SecurityException) { null }
        val mine = clip == null || clip.description?.label?.toString() == label
        if (!mine) return
        if (Build.VERSION.SDK_INT >= 28) cm.clearPrimaryClip() else cm.setPrimaryClip(ClipData.newPlainText("", ""))
    }
}
