package app.safevault.platform

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import app.safevault.crypto.VaultException
import app.safevault.crypto.VaultKey
import app.safevault.ui.Strings
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Keystore key that can only be used right after a BIOMETRIC_STRONG authentication (per-use, via CryptoObject)
 * and is invalidated when new fingerprints are enrolled. It wraps a second copy of the DEK; the master password
 * always remains an alternative way to unlock.
 */
object BiometricKeys {
    private const val PROVIDER = "AndroidKeyStore"
    private const val ALIAS = "safevault_bio_key_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    val AAD: ByteArray = "SafeVault/bio/v1".toByteArray()

    fun isAvailable(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS

    /** Creates a fresh key (replacing any old one) and returns a cipher ready for the biometric prompt. */
    fun encryptCipher(): Cipher {
        delete()
        val b = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= 30) {
            b.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            @Suppress("DEPRECATION")
            b.setUserAuthenticationValidityDurationSeconds(-1)
        }
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        kg.init(b.build())
        val key = kg.generateKey()
        return Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
    }

    /** @throws KeyPermanentlyInvalidatedException when the biometric enrollment changed. */
    fun decryptCipher(iv: ByteArray): Cipher = Cipher.getInstance(TRANSFORMATION).apply {
        init(Cipher.DECRYPT_MODE, existingKey(), GCMParameterSpec(128, iv))
    }

    fun delete() {
        runCatching { keyStore().deleteEntry(ALIAS) }
    }

    private fun existingKey(): SecretKey =
        keyStore().getKey(ALIAS, null) as? SecretKey ?: throw java.security.KeyStoreException("Biometric key missing")

    private fun keyStore(): KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
}

class BiometricGate(private val activity: FragmentActivity) {
    private val executor = ContextCompat.getMainExecutor(activity)

    /** Wraps the current DEK under a new biometric-bound key. [onSuccess] receives the blob to persist. */
    fun enable(key: VaultKey, onSuccess: (ByteArray) -> Unit, onFail: () -> Unit) {
        val cipher = try { BiometricKeys.encryptCipher() } catch (e: Exception) { onFail(); return }
        prompt(Strings.BIO_ENABLE_TITLE, cipher) { c ->
            val blob: ByteArray? = if (c == null) null else try {
                c.updateAAD(BiometricKeys.AAD)
                key.withKeyBytes { dek -> BiometricBlob.build(c.iv, c.doFinal(dek)) }
            } catch (e: Exception) { null }
            if (blob == null) onFail() else onSuccess(blob)
        }
    }

    /** [onFail] gets invalidated=true when the stored blob/key can never work again. */
    fun unlock(blob: ByteArray, onSuccess: (VaultKey) -> Unit, onFail: (invalidated: Boolean) -> Unit) {
        val parts = try { BiometricBlob.parse(blob) } catch (e: VaultException) { onFail(true); return }
        val cipher = try { BiometricKeys.decryptCipher(parts.first) }
        catch (e: KeyPermanentlyInvalidatedException) { onFail(true); return }
        catch (e: Exception) { onFail(false); return }
        prompt(Strings.BIO_UNLOCK_TITLE, cipher) { c ->
            val key: VaultKey? = if (c == null) null else try {
                c.updateAAD(BiometricKeys.AAD)
                val dek = c.doFinal(parts.second)
                try { VaultKey.fromRaw(dek) } finally { dek.fill(0) }
            } catch (e: Exception) { null }
            if (key == null) onFail(c != null) else onSuccess(key)
        }
    }

    private fun prompt(title: String, cipher: Cipher, onResult: (Cipher?) -> Unit) {
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onResult(result.cryptoObject?.cipher)
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { onResult(null) }
        }
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setNegativeButtonText(Strings.USE_MASTER_PASSWORD)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        BiometricPrompt(activity, executor, callback).authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }
}
