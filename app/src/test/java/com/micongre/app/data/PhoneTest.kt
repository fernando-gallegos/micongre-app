package com.micongre.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneTest {
	@Test fun localNumberGetsNicaraguaCode() {
		assertEquals("50588889999", internationalDigits("8888-9999"))
		assertEquals("50588889999", internationalDigits("8888 9999"))
	}

	@Test fun keepsExistingCountryCode() {
		assertEquals("50588889999", internationalDigits("+505 8888 9999"))
		assertEquals("50588889999", internationalDigits("(505) 88889999"))
		assertEquals("50588889999", internationalDigits("00505 8888 9999"))
		assertEquals("13055550100", internationalDigits("+1 (305) 555-0100"))
	}

	@Test fun rejectsNonPhones() {
		assertNull(internationalDigits(null))
		assertNull(internationalDigits(""))
		assertNull(internationalDigits("no tiene"))
		assertNull(internationalDigits("12345"))
	}

	@Test fun buildsLinks() {
		assertEquals(
			ContactLinks("+505 8888 9999", "tel:+50588889999", "smsto:+50588889999", "https://wa.me/50588889999"),
			contactLinks("8888-9999"),
		)
		assertNull(contactLinks("sin número"))
	}
}
