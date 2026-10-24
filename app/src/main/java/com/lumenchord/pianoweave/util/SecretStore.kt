package com.lumenchord.pianoweave.util

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Secrets in [AppPrefs], encrypted with a key that never leaves the Android Keystore. A backup restored
 * on another device can't be decrypted there, so the secret then reads as missing and is asked for again.
 */
object SecretStore {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "piano_weave_secrets"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    // Requests read the secret every time; decrypting once per stored value is enough
    @Volatile private var cache: Pair<String, String?>? = null

    fun get(context: Context, key: String): String? {
        val stored = AppPrefs.get(context).getString(key, null)?.takeIf { it.isNotEmpty() } ?: return null
        cache?.let { (cachedStored, plain) -> if (cachedStored == stored) return plain }
        val plain = decrypt(stored)
        cache = stored to plain
        return plain
    }

    /** Saves [value] under [key]; null or empty removes it. */
    fun put(context: Context, key: String, value: String?) {
        val edit = AppPrefs.get(context).edit()
        if (value.isNullOrEmpty()) {
            edit.remove(key)
        } else {
            val stored = encrypt(value)
            edit.putString(key, stored)
            cache = stored to value
        }
        edit.apply()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val sealed = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(sealed, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String? = try {
        val sealed = Base64.decode(stored, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES))
        }
        String(cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES), Charsets.UTF_8)
    } catch (_: Exception) {
        null
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
        }.generateKey()
    }
}
