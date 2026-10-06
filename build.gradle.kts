// Plugins declarados aquí y aplicados en app/build.gradle.kts (Gradle plugin
// management moderno) — nada que compilar a nivel raíz.
plugins {
	id("com.android.application") version "8.9.3" apply false
	id("org.jetbrains.kotlin.android") version "2.0.21" apply false
	id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
	id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
	id("com.google.gms.google-services") version "4.4.2" apply false
}
