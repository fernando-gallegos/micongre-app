package com.micongre.app.data

/** Código de país que reciben los números locales (8 dígitos) de Nicaragua. Igual que `lib/contact/phone.ts` en la web. */
const val DEFAULT_COUNTRY_CODE = "505"
private const val LOCAL_DIGITS = 8

/** Solo dígitos, con el código de país; null si no parece un número de teléfono. Los números se guardan como la persona los escribió. */
fun internationalDigits(raw: String?, countryCode: String = DEFAULT_COUNTRY_CODE): String? {
	if (raw.isNullOrBlank()) return null
	var digits = raw.filter(Char::isDigit)
	if (raw.trim().startsWith("00")) digits = digits.drop(2) // 00505… = +505…
	if (digits.length < 7) return null
	if (digits.length == LOCAL_DIGITS) digits = countryCode + digits
	return digits.takeIf { it.length in 10..15 }
}

data class ContactLinks(val display: String, val tel: String, val sms: String, val whatsapp: String)

fun contactLinks(raw: String?, countryCode: String = DEFAULT_COUNTRY_CODE): ContactLinks? {
	val digits = internationalDigits(raw, countryCode) ?: return null
	val local = if (digits.startsWith(countryCode)) digits.drop(countryCode.length) else null
	val display = if (local != null && local.length == LOCAL_DIGITS) "+$countryCode ${local.take(4)} ${local.drop(4)}" else "+$digits"
	return ContactLinks(display, "tel:+$digits", "smsto:+$digits", "https://wa.me/$digits")
}
