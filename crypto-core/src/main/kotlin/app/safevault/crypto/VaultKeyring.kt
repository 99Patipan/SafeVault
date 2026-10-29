package app.safevault.crypto

import java.security.SecureRandom

class CreatedVault(val header: VaultHeader, val key: VaultKey)

/**
 * Result of beginning a DEK rotation: old key decrypts existing rows; new key + header must be
 * committed only after every row has been re-sealed under [newKey].
 */
class DekRotation(
    val header: VaultHeader,
    val oldKey: VaultKey,
    val newKey: VaultKey,
) : AutoCloseable {
    override fun close() {
        oldKey.close()
        newKey.close()
    }
}

/**
 * Master password --Argon2id(random salt)--> KEK --AES-GCM wrap--> random DEK.
 * - No password hash is stored; a correct password is proven only by the GCM tag verifying.
 * - The master password is never used as a data key.
 * - Changing the password re-wraps the same DEK (no bulk re-encryption, no data loss).
 * - Rotating the DEK issues a fresh DEK and requires re-encrypting every entry (see [beginRotateDek]).
 * - There is no recovery path: no escrow, no backdoor.
 */
class VaultKeyring(
    private val kdf: Kdf = Argon2idKdf,
    private val policy: KdfPolicy = KdfPolicy.PRODUCTION,
    private val random: SecureRandom = SecureRandom(),
) {
    fun create(password: CharArray, params: KdfParams = KdfParams.DEFAULT): CreatedVault {
        require(password.isNotEmpty()) { "Password must not be empty" }
        policy.check(params)
        val dek = ByteArray(AesGcm.KEY_SIZE).also { random.nextBytes(it) }
        val key = VaultKey(dek)
        try {
            return CreatedVault(wrap(password, newSalt(), params, dek), key)
        } catch (e: Throwable) {
            key.close(); throw e
        }
    }

    /** @throws UnlockFailedException wrong password or tampered header; [VaultFormatException] for bad parameters. */
    fun unlock(password: CharArray, header: VaultHeader): VaultKey {
        policy.check(header.params)
        val kek = deriveKek(password, header.salt, header.params)
        try {
            val dek = AesGcm.decrypt(kek, header.wrappedDek, header.aad())
            if (dek.size != AesGcm.KEY_SIZE) {
                dek.wipe(); throw UnlockFailedException()
            }
            return VaultKey(dek)
        } catch (e: AuthenticationFailedException) {
            throw UnlockFailedException()
        } finally {
            kek.wipe()
        }
    }

    /** Verifies the old password, then re-wraps the same DEK under a new salt and KEK. */
    fun changePassword(
        oldPassword: CharArray, newPassword: CharArray, header: VaultHeader,
        newParams: KdfParams = header.params,
    ): CreatedVault {
        require(newPassword.isNotEmpty()) { "Password must not be empty" }
        policy.check(newParams)
        val key = unlock(oldPassword, header)
        try {
            return CreatedVault(wrap(newPassword, newSalt(), newParams, key.raw()), key)
        } catch (e: Throwable) {
            key.close(); throw e
        }
    }

    /**
     * Verifies [password], unlocks the current DEK, generates a fresh DEK, and wraps it under a new salt.
     * Caller must re-encrypt all ciphertext with [DekRotation.newKey], persist [DekRotation.header],
     * then close the rotation (zeros both keys if still open).
     */
    fun beginRotateDek(
        password: CharArray,
        header: VaultHeader,
        newParams: KdfParams = header.params,
    ): DekRotation {
        policy.check(newParams)
        val oldKey = unlock(password, header)
        val newDek = ByteArray(AesGcm.KEY_SIZE).also { random.nextBytes(it) }
        val newKey = VaultKey(newDek)
        try {
            val newHeader = wrap(password, newSalt(), newParams, newDek)
            return DekRotation(newHeader, oldKey, newKey)
        } catch (e: Throwable) {
            oldKey.close(); newKey.close(); throw e
        }
    }

    private fun newSalt() = ByteArray(VaultHeader.SALT_SIZE).also { random.nextBytes(it) }

    private fun deriveKek(password: CharArray, salt: ByteArray, params: KdfParams): ByteArray {
        val pw = password.toUtf8Nfkc()
        try {
            return kdf.derive(pw, salt, params)
        } finally {
            pw.wipe()
        }
    }

    private fun wrap(password: CharArray, salt: ByteArray, params: KdfParams, dek: ByteArray): VaultHeader {
        val kek = deriveKek(password, salt, params)
        try {
            val wrapped = AesGcm.encrypt(kek, dek, VaultHeader.prefix(params, salt))
            return VaultHeader(VaultHeader.VERSION, params, salt, wrapped)
        } finally {
            kek.wipe()
        }
    }
}
