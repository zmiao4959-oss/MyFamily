package cn.jiayi.familymemory.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import cn.jiayi.familymemory.data.connection.AccessTokenStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeystoreAccessTokenStore @Inject constructor(
    @param:ApplicationContext context: Context,
) : AccessTokenStore {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun hasToken(): Boolean = preferences.contains(CIPHERTEXT) && preferences.contains(IV)

    override fun read(): String? = runCatching {
        val ciphertext = Base64.decode(preferences.getString(CIPHERTEXT, null), Base64.NO_WRAP)
        val iv = Base64.decode(preferences.getString(IV, null), Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        }
        cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    }.getOrNull()

    override fun save(token: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        }
        val ciphertext = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        preferences.edit {
            putString(CIPHERTEXT, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            putString(IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
        }
    }

    override fun clear() {
        preferences.edit { clear() }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "secure_connection"
        const val CIPHERTEXT = "access_token_ciphertext"
        const val IV = "access_token_iv"
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "family_memory_pairing_token"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
