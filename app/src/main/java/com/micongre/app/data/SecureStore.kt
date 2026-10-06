package com.micongre.app.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Cifra y descifra textos con una clave AES-GCM que vive en el Android Keystore (no sale del dispositivo). */
object SecureStore {
	private const val ALIAS = "micongre_tokens"
	private const val TRANSFORM = "AES/GCM/NoPadding"

	private fun key(): SecretKey {
		val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
		(store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
		val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
		generator.init(
			KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
				.setBlockModes(KeyProperties.BLOCK_MODE_GCM)
				.setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
				.setKeySize(256)
				.build(),
		)
		return generator.generateKey()
	}

	fun encrypt(plain: String): String {
		val cipher = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, key()) }
		val data = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
		return Base64.encodeToString(data, Base64.NO_WRAP)
	}

	/** null si no se puede descifrar (valor viejo en claro, clave perdida o manipulado). */
	fun decrypt(encoded: String?): String? {
		if (encoded.isNullOrEmpty()) return null
		return try {
			val data = Base64.decode(encoded, Base64.NO_WRAP)
			val cipher = Cipher.getInstance(TRANSFORM).apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data.copyOfRange(0, 12))) }
			String(cipher.doFinal(data.copyOfRange(12, data.size)), Charsets.UTF_8)
		} catch (e: Exception) {
			null
		}
	}
}
