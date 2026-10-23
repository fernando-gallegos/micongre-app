import java.time.LocalDate
import java.util.Properties

plugins {
	id("com.android.application")
	id("org.jetbrains.kotlin.android")
	id("org.jetbrains.kotlin.plugin.serialization")
	id("org.jetbrains.kotlin.plugin.compose")
}

// El plugin de Firebase exige google-services.json para poder correr — hasta
// que agregues el tuyo (ver README.md), Gradle lo salta en vez de fallar, así
// el resto de la app compila igual.
val hasGoogleServices = file("google-services.json").exists()
if (hasGoogleServices) {
	apply(plugin = "com.google.gms.google-services")
}

// Firma de la versión release: keystore.properties (fuera de control de versiones) apunta al almacén de claves.
// Mientras se firme siempre con la misma clave y el versionCode suba, el APK se instala limpio o actualiza el anterior.
val keystoreProps = Properties().apply {
	val f = rootProject.file("keystore.properties")
	if (f.exists()) f.inputStream().use { load(it) }
}

// Versión: 1.0.[yy][MM].[####] — año, mes y un consecutivo que sube con cada build release (se reinicia cada mes); así las
// versiones se ordenan solas. Por ejemplo, el primer release de octubre de 2026 es 1.0.2610.0001. El consecutivo vive en version.properties (junto a este proyecto).
// versionCode = yy * 1.000.000 + MM * 10.000 + ####, así siempre crece y cada APK actualiza al anterior.
val versionFile = rootProject.file("version.properties")
val versionProps = Properties().apply { if (versionFile.exists()) versionFile.inputStream().use { load(it) } }
val today = LocalDate.now()
val monthKey = "%02d%02d".format(today.year % 100, today.monthValue)
var releaseSeq = if (versionProps.getProperty("month") == monthKey) versionProps.getProperty("seq", "0").toInt() else 0
if (gradle.startParameter.taskNames.any { it.contains("release", ignoreCase = true) }) {
	releaseSeq += 1
	versionProps.setProperty("month", monthKey)
	versionProps.setProperty("seq", releaseSeq.toString())
	versionFile.outputStream().use { versionProps.store(it, "Consecutivo de releases del mes (lo actualiza el build)") }
}
val shownSeq = maxOf(releaseSeq, 1)
val appVersionName = "1.0.$monthKey." + "%04d".format(shownSeq)
val appVersionCode = (today.year % 100) * 1_000_000 + today.monthValue * 10_000 + shownSeq

base.archivesName.set("MiCongre-$appVersionName")

android {
	namespace = "com.micongre.app"
	compileSdk = 36

	defaultConfig {
		applicationId = "com.micongre.app"
		minSdk = 26
		targetSdk = 36
		versionCode = appVersionCode
		versionName = appVersionName
	}

	signingConfigs {
		if (keystoreProps.getProperty("storeFile") != null) {
			create("release") {
				storeFile = file(keystoreProps.getProperty("storeFile"))
				storePassword = keystoreProps.getProperty("storePassword")
				keyAlias = keystoreProps.getProperty("keyAlias")
				keyPassword = keystoreProps.getProperty("keyPassword")
			}
		}
	}

	buildTypes {
		debug {
			// 10.0.2.2 es el alias que el emulador de Android usa para "localhost"
			// de la máquina donde corre `npm run dev` — cambia esto por la IP de
			// tu red si pruebas desde un dispositivo físico.
			buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:3000/\"")
		}
		release {
			buildConfigField("String", "BASE_URL", "\"https://tu-congregacion.micongre.app/\"")
			// R8 completo: ofusca y reduce el código y quita recursos sin uso (Google Play pide una ofuscación alta). El mapping.txt
			// se genera con cada build y el .aab lo lleva dentro para que Play desofusque los errores.
			isMinifyEnabled = true
			isShrinkResources = true
			// Símbolos de las bibliotecas nativas (MapLibre) para los informes de fallos de Play.
			ndk { debugSymbolLevel = "SYMBOL_TABLE" }
			signingConfigs.findByName("release")?.let { signingConfig = it }
			proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
		}
	}

	buildFeatures {
		compose = true
		buildConfig = true
	}

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_17
		targetCompatibility = JavaVersion.VERSION_17
	}
	kotlinOptions {
		jvmTarget = "17"
	}
}

dependencies {
	implementation("org.maplibre.gl:android-sdk:11.8.0")
	implementation(platform("androidx.compose:compose-bom:2025.04.01"))
	implementation("androidx.compose.ui:ui")
	implementation("androidx.compose.ui:ui-graphics")
	implementation("androidx.compose.ui:ui-tooling-preview")
	implementation("androidx.compose.material3:material3")
	implementation("androidx.compose.material:material-icons-extended")
	implementation("androidx.activity:activity-compose:1.9.3")
	implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
	implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
	implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
	implementation("androidx.navigation:navigation-compose:2.8.4")
	debugImplementation("androidx.compose.ui:ui-tooling")

	implementation("androidx.datastore:datastore-preferences:1.1.1")
	implementation("androidx.work:work-runtime-ktx:2.9.1")
	// Widgets de la pantalla de inicio (pequeño y grande), dibujados con Compose.
	implementation("androidx.glance:glance-appwidget:1.1.1")
	implementation("androidx.biometric:biometric:1.1.0")

	// Push en tiempo real (Firebase Cloud Messaging). Solo se activa si se agrega app/google-services.json y el sitio de la
	// congregación tiene configurada su cuenta de servicio; si no, la app sigue revisando por su cuenta (ver README).
	implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
	implementation("com.google.firebase:firebase-messaging-ktx")

	// Escáner de QR de Google (sin permiso de cámara en la app): para conectarse con el código de la congregación.
	implementation("com.google.android.gms:play-services-code-scanner:16.1.0")

	implementation("com.squareup.retrofit2:retrofit:2.11.0")
	implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
	implementation("com.squareup.okhttp3:okhttp:4.12.0")
	implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
	implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")


	testImplementation("junit:junit:4.13.2")
}
