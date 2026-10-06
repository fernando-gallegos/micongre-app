package com.micongre.app.push

import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Cifrado de punta a punta de los push (esquema "v1", el mismo que micongre-admin/src/lib/push/e2e.ts): el teléfono tiene
 * una llave P-256 propia; el servidor cifra con ECDH (llave efímera) + HKDF-SHA256 (salt = iv, info = "micongre-push-v1") +
 * AES-256-GCM, de modo que ni el relay ni Firebase pueden leer el aviso. La llave privada nunca sale del teléfono.
 * Solo usa la librería estándar de Java, para poder probarlo en la JVM.
 */
object PushCrypto {
	private val encoder = Base64.getUrlEncoder().withoutPadding()
	private val decoder = Base64.getUrlDecoder()
	private const val INFO = "micongre-push-v1"

	class KeyMaterial(val privatePkcs8: String, val publicRaw: String)

	private fun curve(): ECParameterSpec =
		AlgorithmParameters.getInstance("EC").apply { init(ECGenParameterSpec("secp256r1")) }.getParameterSpec(ECParameterSpec::class.java)

	private fun fixed32(value: BigInteger): ByteArray {
		val raw = value.toByteArray().let { if (it.size > 32) it.copyOfRange(it.size - 32, it.size) else it }
		return ByteArray(32 - raw.size) + raw
	}

	/** Llave nueva: privada en PKCS#8 y pública sin comprimir (65 bytes), ambas en base64url. */
	fun generate(): KeyMaterial {
		val pair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
		val point = (pair.public as ECPublicKey).w
		val raw = byteArrayOf(0x04) + fixed32(point.affineX) + fixed32(point.affineY)
		return KeyMaterial(encoder.encodeToString(pair.private.encoded), encoder.encodeToString(raw))
	}

	/** Descifra los campos { ek, iv, ct }; devuelve el JSON {"title","body","url"} o null si no se puede (llave distinta, manipulado). */
	fun decrypt(privatePkcs8: String, ek: String, iv: String, ct: String): String? = try {
		val privateKey = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(decoder.decode(privatePkcs8)))
		val peer = decoder.decode(ek)
		require(peer.size == 65 && peer[0] == 0x04.toByte())
		val point = ECPoint(BigInteger(1, peer.copyOfRange(1, 33)), BigInteger(1, peer.copyOfRange(33, 65)))
		val peerKey = KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(point, curve()))
		val shared = KeyAgreement.getInstance("ECDH").apply { init(privateKey); doPhase(peerKey, true) }.generateSecret()

		val salt = decoder.decode(iv)
		// HKDF-SHA256, una sola vuelta (32 bytes): PRK = HMAC(salt, secreto); clave = HMAC(PRK, info || 0x01).
		val prk = hmac(salt, shared)
		val key = hmac(prk, INFO.toByteArray(Charsets.UTF_8) + byteArrayOf(0x01))

		val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, salt)) }
		String(cipher.doFinal(decoder.decode(ct)), Charsets.UTF_8)
	} catch (e: Exception) {
		null
	}

	private fun hmac(key: ByteArray, data: ByteArray): ByteArray =
		Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }.doFinal(data)
}
