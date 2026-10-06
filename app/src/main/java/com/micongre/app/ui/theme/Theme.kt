package com.micongre.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** Paleta de la web (globals.css): una por color de acento, en claro y oscuro. */
class Palette(
	val bg: Long, val surface: Long, val surfaceAlt: Long, val line: Long, val ink: Long, val inkSoft: Long,
	val accent: Long, val accentStrong: Long, val accentSoft: Long, val accentInk: Long,
)

class AccentOption(val key: String, val label: String, val swatch: Long, val light: Palette, val dark: Palette)

val ACCENTS = listOf(
	AccentOption(
		"salvia", "Salvia", 0xFF3F6B52,
		Palette(0xFFF7F5F0, 0xFFFFFFFF, 0xFFF1EFE8, 0xFFE3DED2, 0xFF1C1B17, 0xFF6C6658, 0xFF3F6B52, 0xFF2F5140, 0xFFE7F0EA, 0xFFFFFFFF),
		Palette(0xFF15171A, 0xFF1C1F23, 0xFF22262B, 0xFF2B2F35, 0xFFEEEAE1, 0xFF9A9488, 0xFF74B191, 0xFF5C987A, 0xFF20332A, 0xFF0C1410),
	),
	AccentOption(
		"anil", "Añil", 0xFF3D5AA3,
		Palette(0xFFF5F6FA, 0xFFFFFFFF, 0xFFECEDF7, 0xFFDFE1EA, 0xFF1A1D29, 0xFF63687A, 0xFF3D5AA3, 0xFF2C4480, 0xFFE4E8F5, 0xFFFFFFFF),
		Palette(0xFF14161F, 0xFF1B1E29, 0xFF20242F, 0xFF262A38, 0xFFE8E9F0, 0xFF9498AA, 0xFF7C93D6, 0xFF6480C9, 0xFF232A42, 0xFF10131F),
	),
	AccentOption(
		"terracota", "Terracota", 0xFFB5563C,
		Palette(0xFFFAF6F1, 0xFFFFFFFF, 0xFFF3EBE2, 0xFFE8DCCF, 0xFF241A14, 0xFF7A6A5C, 0xFFB5563C, 0xFF954530, 0xFFF7E6DE, 0xFFFFFFFF),
		Palette(0xFF1A1512, 0xFF221A16, 0xFF271E19, 0xFF2E2620, 0xFFF0E6DE, 0xFFA89486, 0xFFE2896C, 0xFFD1714F, 0xFF3A241B, 0xFF1F1109),
	),
	AccentOption(
		"petroleo", "Petróleo", 0xFF1F6F78,
		Palette(0xFFF4F8F8, 0xFFFFFFFF, 0xFFEBF3F2, 0xFFD9E6E5, 0xFF142322, 0xFF5B7472, 0xFF1F6F78, 0xFF175A61, 0xFFDCEDEC, 0xFFFFFFFF),
		Palette(0xFF101A1A, 0xFF172322, 0xFF1C2827, 0xFF223231, 0xFFE2ECE9, 0xFF8FA6A3, 0xFF5FB0B6, 0xFF479DA3, 0xFF1C3232, 0xFF0A1615),
	),
	AccentOption(
		"carbon", "Carbón y oro", 0xFF9C7A24,
		Palette(0xFFF7F5F1, 0xFFFFFFFF, 0xFFEFE9DA, 0xFFE6E0D2, 0xFF201D16, 0xFF756D5C, 0xFF9C7A24, 0xFF7F631C, 0xFFF3ECD6, 0xFFFFFFFF),
		Palette(0xFF17150F, 0xFF1F1C15, 0xFF242018, 0xFF2B271C, 0xFFECE7DB, 0xFFA89D87, 0xFFD4AB52, 0xFFC19638, 0xFF362D16, 0xFF1A1408),
	),
)

/** Colores fijos de la web que no cambian con el acento: avisos y las secciones de las reuniones. */
class Extras(
	val warn: Color, val warnSoft: Color,
	val treasures: Color, val treasuresSoft: Color,
	val ministry: Color, val ministrySoft: Color,
	val living: Color, val livingSoft: Color,
	val watchtower: Color, val watchtowerSoft: Color,
	val talk: Color, val talkSoft: Color,
)

private val LightExtras = Extras(
	Color(0xFFC1571F), Color(0xFFFBE8DD),
	Color(0xFF1D5FA8), Color(0xFFE4EEFA),
	Color(0xFF8A6A12), Color(0xFFF6EFD6),
	Color(0xFFA1272F), Color(0xFFFBE7E8),
	Color(0xFF4338CA), Color(0xFFE6E4FB),
	Color(0xFF0F766E), Color(0xFFDCF5F1),
)
private val DarkExtras = Extras(
	Color(0xFFE8935F), Color(0xFF3A2417),
	Color(0xFF7BB0E0), Color(0xFF1C2C3D),
	Color(0xFFD1A94A), Color(0xFF362A14),
	Color(0xFFD97883), Color(0xFF3A1C20),
	Color(0xFFA5B4FC), Color(0xFF2A2750),
	Color(0xFF5EEAD4), Color(0xFF163330),
)

private val LocalExtras = androidx.compose.runtime.staticCompositionLocalOf { LightExtras }

object AppColors {
	val extras: Extras
		@Composable get() = LocalExtras.current
}

private fun scheme(p: Palette, dark: Boolean): ColorScheme {
	val base = if (dark) darkColorScheme() else lightColorScheme()
	return base.copy(
		primary = Color(p.accent), onPrimary = Color(p.accentInk),
		primaryContainer = strong(p, dark), onPrimaryContainer = Color(p.accentStrong),
		secondary = Color(p.accentStrong), onSecondary = Color(p.accentInk),
		secondaryContainer = strong(p, dark), onSecondaryContainer = Color(p.accentStrong),
		background = Color(p.bg), onBackground = Color(p.ink),
		surface = Color(p.surface), onSurface = Color(p.ink),
		surfaceVariant = Color(p.surfaceAlt), onSurfaceVariant = Color(p.inkSoft),
		surfaceContainer = Color(p.surface), surfaceContainerHigh = Color(p.surfaceAlt), surfaceContainerLow = Color(p.surface),
		surfaceContainerHighest = Color(p.surfaceAlt), surfaceContainerLowest = Color(p.surface),
		outline = Color(p.line), outlineVariant = Color(p.line),
	)
}

@Composable
fun MiCongreTheme(accentKey: String = "salvia", darkMode: String = "system", content: @Composable () -> Unit) {
	val dark = when (darkMode) {
		"dark" -> true
		"light" -> false
		else -> isSystemInDarkTheme()
	}
	val option = ACCENTS.firstOrNull { it.key == accentKey } ?: ACCENTS.first()
	androidx.compose.runtime.CompositionLocalProvider(LocalExtras provides (if (dark) DarkExtras else LightExtras)) {
		MaterialTheme(colorScheme = scheme(if (dark) option.dark else option.light, dark), content = content)
	}
}

/** Resaltado del tema: el tono suave mezclado con el acento para que se note más (fondos de la barra, chips y selecciones). */
private fun strong(p: Palette, dark: Boolean): Color =
	lerp(Color(p.accentSoft), Color(p.accent), if (dark) 0.35f else 0.30f)
