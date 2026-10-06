package com.micongre.app.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PushCryptoTest {
	// Vector generado con el código del servidor (micongre-web/src/lib/push/e2e.ts, WebCrypto): comprueba que la app
	// descifra exactamente lo que cifra el servidor.
	private val priv = "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgPHuh6e3ixA-vUk_1fIwaUCkOiTICJgNg_mcehPw_dIihRANCAARqdlFZ_qhH-pAFt6R8AT1w9NkVF6qdoxNe1E1UprH2OGldj2-PwoqikkjII1leDNqmj480_R8x5eAQsKSJB5SB"
	private val ek = "BMrKjzrCnE8eXEsev8CKEmIO1XDYSG4gmtiI3va4Fv6_w-Pvcx_2GmPrY96reA0KIjWTZFWgE3MUq4PNEWPWLWY"
	private val iv = "ucU56zo8rtSTiuX7"
	private val ct = "J67kEADz3XR5A5luVAd4qhv3Y0Jmp3uWhwaeSgwW3-GLFKwb7cEVAcJCGWmq9mbj7dV2pKph9J8gqmRRDve3Lai67pTJ9YuyXLfCx9hLz3nO8Ctz4qvb5pg"

	@Test
	fun decryptsWhatTheServerSealed() {
		val plain = PushCrypto.decrypt(priv, ek, iv, ct)
		assertNotNull(plain)
		assertEquals("""{"title":"Prueba áéí","body":"Territorio 5 — Centro","url":"/panel"}""", plain)
	}

	@Test
	fun wrongKeyOrTamperedDataFails() {
		val other = PushCrypto.generate()
		assertNull(PushCrypto.decrypt(other.privatePkcs8, ek, iv, ct))
		val tampered = ct.dropLast(2) + (if (ct.endsWith("AA")) "BB" else "AA")
		assertNull(PushCrypto.decrypt(priv, ek, iv, tampered))
	}

	@Test
	fun generatedPublicKeyIsUncompressedP256() {
		val key = PushCrypto.generate()
		val raw = java.util.Base64.getUrlDecoder().decode(key.publicRaw)
		assertEquals(65, raw.size)
		assertEquals(0x04.toByte(), raw[0])
	}
}
