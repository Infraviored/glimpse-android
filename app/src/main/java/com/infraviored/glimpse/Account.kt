package com.infraviored.glimpse

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
 * The Nextcloud login: server, user and the app password Login Flow v2 handed out.
 * The password never leaves the device; Nextcloud lists it under Settings → Security,
 * where the user can revoke it.
 */
data class Account(val server: String, val user: String, val appPassword: String) {

    /** "Basic …" header value: Nextcloud logs the WebView in with it and sets its session cookie. */
    val basicAuth: String
        get() = "Basic " + Base64.encodeToString("$user:$appPassword".toByteArray(), Base64.NO_WRAP)

    val glimpseUrl: String
        get() = "$server/index.php/apps/glimpse/"

    companion object {
        private const val PREFS = "account"
        private const val KEY_ALIAS = "glimpse-login"

        fun load(context: Context): Account? {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val server = p.getString("server", null) ?: return null
            val user = p.getString("user", null) ?: return null
            val iv = p.getString("iv", null) ?: return null
            val data = p.getString("password", null) ?: return null
            return try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
                Account(server, user, String(cipher.doFinal(Base64.decode(data, Base64.NO_WRAP))))
            } catch (e: Exception) {
                // Key gone (device restored, lock screen reset): log in again.
                null
            }
        }

        fun save(context: Context, account: Account) {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val data = cipher.doFinal(account.appPassword.toByteArray())
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("server", account.server)
                .putString("user", account.user)
                .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .putString("password", Base64.encodeToString(data, Base64.NO_WRAP))
                .putString("lastServer", account.server)
                .apply()
        }

        /** Forget the login; the server address stays as a suggestion for the next one. */
        fun clear(context: Context) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .remove("server").remove("user").remove("iv").remove("password")
                .apply()
        }

        fun lastServer(context: Context): String =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("lastServer", "") ?: ""

        private fun key(): SecretKey {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            generator.init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
            return generator.generateKey()
        }
    }
}
