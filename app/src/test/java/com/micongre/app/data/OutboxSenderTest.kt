package com.micongre.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** La decisión de qué hacer con cada respuesta del servidor es lo que garantiza que no se pierda nada: errores transitorios se reintentan; los
 * demás se conservan marcados. */
class OutboxSenderTest {
	private fun state(outcome: SendOutcome) = (outcome as SendOutcome.Problem).state

	@Test fun conflictoSeConservaComoChoque() = assertEquals(OpState.CONFLICT, state(classifyFailure(409, "")))

	@Test fun sesionVencidaEsperaInicioDeSesion() = assertEquals(OpState.NEEDS_LOGIN, state(classifyFailure(401, "")))

	@Test fun sinPermisoSeConservaRechazado() = assertEquals(OpState.REJECTED, state(classifyFailure(403, "")))

	@Test fun datosInvalidosSeConservanRechazados() {
		val outcome = classifyFailure(400, "{\"error\":\"invalid_body\"}")
		assertEquals(OpState.REJECTED, state(outcome))
		assertTrue((outcome as SendOutcome.Problem).message.contains("invalid_body"))
	}

	@Test fun noEncontradoSeConservaRechazado() = assertEquals(OpState.REJECTED, state(classifyFailure(404, "")))

	@Test fun erroresDelServidorSeReintentan() {
		for (code in listOf(500, 502, 503, 504, 408, 429)) assertEquals("código $code", SendOutcome.Retry, classifyFailure(code, ""))
	}

	@Test fun versionIsoSeConvierteASegundos() {
		assertEquals(1792280019L, isoVersion("2026-10-17T23:33:39.000Z"))
		assertNull(isoVersion(null))
		assertNull(isoVersion("no es una fecha"))
	}
}
