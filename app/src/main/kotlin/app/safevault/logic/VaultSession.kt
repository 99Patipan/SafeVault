package app.safevault.logic

import app.safevault.crypto.VaultKey
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Holds the unlocked key in memory and decides when to lock. Pure logic with an injected clock so every
 * policy (idle, background, screen-off) is unit-testable. Locking zeroes the key.
 */
class VaultSession(
    private val settings: () -> AppSettings,
    private val clock: () -> Long,
) {
    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private var key: VaultKey? = null
    private var lastActivity = 0L
    private var backgroundedAt: Long? = null

    fun addOnLockedListener(l: () -> Unit) { listeners += l }
    fun removeOnLockedListener(l: () -> Unit) { listeners -= l }

    @Synchronized fun isUnlocked(): Boolean = key != null

    /** Installs [newKey] (closing any previous one silently; this is not a lock event). */
    @Synchronized fun unlock(newKey: VaultKey) {
        if (key !== newKey) key?.close()
        key = newKey
        lastActivity = clock()
        backgroundedAt = null
    }

    @Synchronized fun requireKey(): VaultKey = key ?: throw IllegalStateException("Vault is locked")

    @Synchronized fun touch() { if (key != null) lastActivity = clock() }

    fun lock() {
        val wasUnlocked = synchronized(this) {
            val k = key
            key = null
            backgroundedAt = null
            k?.close()
            k != null
        }
        if (wasUnlocked) listeners.forEach { it() }
    }

    fun onAppBackgrounded() {
        val lockNow = synchronized(this) {
            if (key == null) return
            if (settings().backgroundLock == BackgroundLock.IMMEDIATE) true
            else { backgroundedAt = clock(); false }
        }
        if (lockNow) lock()
    }

    fun onAppForegrounded() {
        val lockNow = synchronized(this) {
            if (key == null) return
            val bg = backgroundedAt
            backgroundedAt = null
            bg != null && clock() - bg >= timeoutMs()
        }
        if (lockNow) lock() else touch()
    }

    fun onScreenOff() { if (settings().lockOnScreenOff) lock() }

    fun checkIdle() {
        val lockNow = synchronized(this) { key != null && clock() - lastActivity >= timeoutMs() }
        if (lockNow) lock()
    }

    private fun timeoutMs() = settings().idleTimeoutSeconds * 1000L
}
