package app.safevault.platform

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import app.safevault.crypto.HeaderProtector
import app.safevault.crypto.KeyHeaderProtector
import java.security.KeyStore
import java.security.ProviderException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Non-extractable AES-256 key in the Android Keystore (StrongBox when the device has it). It wraps the vault
 * header, so a copy of the database file is useless without this device. No user authentication is required
 * for this key; the master password remains the user-facing secret.
 */
object DeviceKey {
    private const val PROVIDER = "AndroidKeyStore"
    private const val ALIAS = "safevault_device_key_v1"

    fun protector(): HeaderProtector = KeyHeaderProtector({ getOrCreate() })

    fun delete() {
        runCatching { keyStore().deleteEntry(ALIAS) }
    }

    @Synchronized
    private fun getOrCreate(): SecretKey {
        (keyStore().getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return if (Build.VERSION.SDK_INT >= 28) {
            try { generate(strongBox = true) } catch (e: ProviderException) { generate(strongBox = false) }
        } else generate(strongBox = false)
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

    private fun generate(strongBox: Boolean): SecretKey {
        val b = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
        // Prefer keys that are only usable while the device is unlocked (API 28+).
        // This does not replace the master password; it raises the bar for cold offline DB copies
        // used on a locked device session.
        if (Build.VERSION.SDK_INT >= 28) {
            b.setUnlockedDeviceRequired(true)
        }
        if (strongBox && Build.VERSION.SDK_INT >= 28) b.setIsStrongBoxBacked(true)
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        kg.init(b.build())
        return kg.generateKey()
    }
}
