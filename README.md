# MiCongre Android

App nativa (Kotlin + Jetpack Compose) de solo lectura para el "Mi Panel" de
[micongre-admin](https://github.com/fernando-gallegos/micongre-admin) — repositorio y proyecto completamente
separados, conectados solo por la API HTTPS del backend.

## Qué hace hoy

Mismo alcance y forma que la web para una persona con su sesión; todo se rige por los privilegios y nombramientos
que devuelve `GET /api/auth/me` (igual que el menú de la web).

- Login y activación (`docs/mobile-activation-protocol.md` en `micongre-admin`): un enlace `https://<sitio>/activar/<token>` abre la app (App Link verificado para `*.micongre.app`) y muestra `ActivationScreen`; con refresh automático del token.
- **Eliminar mi cuenta** (Más → Mi perfil): `POST /api/auth/delete-account` con la contraseña; quita el acceso pero no la ficha de publicador.
- **Mi Panel**: asignaciones futuras (incluye territorios con mapa dibujado, completar y devolver), limpieza, avisos
  (ocultar), pastoreo (solicitar, cancelar y seguimiento: completar/postergar/cancelar)
  y los botones redondos de enlace (Hub, grupos, Zoom) al final. Los arreglos de discursos se gestionan solo en la web.
- **Reuniones**: dos pestañas (Entre semana / Fin de semana), solo lectura, con colores por sección, asignados y las
  tareas operativas como una sección más al final.
- **Informes**: tarjeta S-21 en lectura y, para quien puede editar, modo edición por mes.
- **Publicadores**: lista con búsqueda y filtros, y ficha en lectura.
- **Barra inferior**: Panel, un botón configurable (se guarda en el servidor, igual que en la web), Avisos y Más.
- **Mi perfil**: solo apariencia (modo oscuro y color) y el botón personalizable.
- Los territorios (altas, mapas, asignar) se administran solo desde la web.

Compilar: `gradle :app:assembleDebug` (Gradle 8.13 + JDK de Android Studio). Requiere los endpoints nuevos del
backend (`/api/auth/me` con `navShortcut` y `appointmentTypes`, nombres en el detalle de las reuniones y
`/api/meetings/operational-view`).

## Abrir el proyecto

1. Abre esta carpeta directamente en Android
   Studio (Open → selecciona la carpeta). Android Studio genera `local.properties`
   y el `gradlew`/wrapper jar solos la primera vez que sincroniza — no hace
   falta tenerlos a mano.
2. Deja que termine el "Gradle Sync". El proyecto compila igual sin Firebase
   configurado todavía (el plugin de `google-services` se salta solo si no
   encuentra `app/google-services.json`, ver `app/build.gradle.kts`).
3. Corre la app en un emulador. `BuildConfig.BASE_URL` en la variante *debug*
   apunta a `http://10.0.2.2:3000/` — el alias que el emulador usa para el
   `npm run dev` de tu máquina. Si pruebas en un teléfono físico, cambia esa
   IP por la de tu red local en `app/build.gradle.kts`.
4. La variante *release* ya apunta a producción:
   `https://tu-congregacion.micongre.app/` (cámbialo por el sitio de tu congregación).

## Notificaciones

Cada sitio de congregación es independiente y no hay un servicio central de push, así que la app revisa por su cuenta
(WorkManager, cada ~15 minutos, con red) los avisos y las asignaciones próximas de Mi Panel y muestra una notificación
por cada cosa nueva (`push/SyncWorker.kt`). La primera revisión solo toma la foto de lo existente. En Android 13+ se
pide el permiso de notificaciones al entrar. Para push instantáneo haría falta FCM con un relay central (pendiente).

## Estructura

```
app/src/main/java/com/micongre/app/
  MainActivity.kt              — punto de entrada, decide Login vs Home
  MicongreApplication.kt       — contenedor de dependencias a mano
  data/                        — modelos, Retrofit, DataStore de tokens
  push/                        — FirebaseMessagingService
  ui/                          — pantallas y ViewModel (Compose)
```

## Push en tiempo real (Firebase, opcional)

Si agregas `app/google-services.json` (ver `docs/firebase-push.md` en `micongre-admin`, con el paso a paso para crear la cuenta
gratuita de Firebase) y el sitio de la congregación tiene el secreto `FCM_SERVICE_ACCOUNT`, la app usa push en tiempo real y
deja de revisar cada tanto. Sin esos dos elementos sigue con la revisión periódica de arriba.

## Publicar en Google Play

- `targetSdk`/`compileSdk` 36 (Play lo exige desde el 31-ago-2026) con AGP 8.9.3.
- Bundle firmado con la clave de carga: `gradle :app:bundleRelease` → `app/build/outputs/bundle/release/MiCongre-<versión>-release.aab` (cada build release sube el consecutivo de `version.properties`).
- `micongre-release.jks` y `keystore.properties` no están en ningún repositorio: **respáldalos** (gestor de contraseñas / nube privada). Con Play App Signing, perder la clave de carga se puede recuperar con soporte de Google; aun así, guárdala.
