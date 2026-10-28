package app.safevault.logic

import app.safevault.crypto.KdfParams
import app.safevault.crypto.KdfPolicy
import app.safevault.crypto.VaultKey
import app.safevault.crypto.VaultKeyring
import org.junit.Assert.*
import org.junit.Test

class VaultSessionTest {
    private var now = 0L
    private var settings = AppSettings(idleTimeoutSeconds = 60)
    private val session = VaultSession({ settings }, { now })
    private val keyring = VaultKeyring(policy = KdfPolicy(64, 1024, 1, 3, 2))
    private var lockEvents = 0

    init { session.addOnLockedListener { lockEvents++ } }

    private fun newKey(): VaultKey = keyring.create("pw-12345678".toCharArray(), KdfParams(64, 1, 1)).key

    @Test fun lockClosesKeyAndNotifiesOnce() {
        val k = newKey(); session.unlock(k)
        session.lock(); session.lock()
        assertTrue(k.isClosed); assertFalse(session.isUnlocked()); assertEquals(1, lockEvents)
    }

    @Test fun requireKeyThrowsWhenLocked() {
        assertThrows(IllegalStateException::class.java) { session.requireKey() }
        session.unlock(newKey()); session.requireKey(); session.lock()
        assertThrows(IllegalStateException::class.java) { session.requireKey() }
    }

    @Test fun idleTimeoutLocksExactlyAtThreshold() {
        val k = newKey(); session.unlock(k)
        now = 59_999; session.checkIdle(); assertTrue(session.isUnlocked())
        now = 60_000; session.checkIdle(); assertFalse(session.isUnlocked()); assertTrue(k.isClosed)
    }

    @Test fun interactionResetsIdleTimer() {
        session.unlock(newKey())
        now = 50_000; session.touch()
        now = 100_000; session.checkIdle(); assertTrue(session.isUnlocked())
        now = 110_000; session.checkIdle(); assertFalse(session.isUnlocked())
    }

    @Test fun screenOffLocksOnlyWhenPolicyEnabled() {
        session.unlock(newKey()); settings = settings.copy(lockOnScreenOff = false)
        session.onScreenOff(); assertTrue(session.isUnlocked())
        settings = settings.copy(lockOnScreenOff = true)
        session.onScreenOff(); assertFalse(session.isUnlocked())
    }

    @Test fun backgroundImmediateLocksRightAway() {
        val k = newKey(); session.unlock(k)
        session.onAppBackgrounded()
        assertFalse(session.isUnlocked()); assertTrue(k.isClosed)
    }

    @Test fun backgroundAfterTimeoutKeepsKeyOnlyWithinGracePeriod() {
        settings = settings.copy(backgroundLock = BackgroundLock.AFTER_TIMEOUT)
        session.unlock(newKey())
        now = 1_000; session.onAppBackgrounded(); assertTrue(session.isUnlocked())
        now = 30_000; session.onAppForegrounded(); assertTrue(session.isUnlocked())
        now = 31_000; session.onAppBackgrounded()
        now = 100_000; session.onAppForegrounded(); assertFalse(session.isUnlocked())
    }

    @Test fun foregroundOrBackgroundWhileLockedIsNoOp() {
        session.onAppBackgrounded(); session.onAppForegrounded(); session.onScreenOff(); session.checkIdle()
        assertEquals(0, lockEvents)
    }

    @Test fun replacingKeyClosesOldOneWithoutLockEvent() {
        val a = newKey(); val b = newKey()
        session.unlock(a); session.unlock(b)
        assertTrue(a.isClosed); assertFalse(b.isClosed); assertEquals(0, lockEvents); assertTrue(session.isUnlocked())
    }

    @Test fun reinstallingSameKeyDoesNotCloseIt() {
        val a = newKey(); session.unlock(a); session.unlock(a)
        assertFalse(a.isClosed)
    }
}
