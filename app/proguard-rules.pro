# R8 en modo completo (minify + shrinkResources en release). Las bibliotecas (kotlinx-serialization, Retrofit, OkHttp, MapLibre, Firebase,
# WorkManager, Compose) traen sus propias reglas "consumer"; aquí van solo las de la app y las que endurecen la ofuscación.

# --- Ofuscación fuerte: todo el código propio en un solo paquete, nombres cortos y acceso ampliado (permite inlinear más).
-repackageclasses 'mc'
-allowaccessmodification
-overloadaggressively

# --- Trazas legibles con el mapping.txt (Play lo desofusca): se conservan líneas y se oculta el nombre del archivo fuente.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- Atributos que Retrofit/kotlinx-serialization leen por reflexión (tipos genéricos y anotaciones).
-keepattributes Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,RuntimeVisibleTypeAnnotations,AnnotationDefault

# --- Modelos @Serializable de la API: los nombres de los campos viajan como texto en los descriptores, así que se pueden ofuscar; solo hay
# que conservar los serializadores generados (kotlinx-serialization ya trae estas reglas; se repiten por seguridad para los modelos propios).
-keepclassmembers class com.micongre.app.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class com.micongre.app.**
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

# --- Avisos de OkHttp por proveedores TLS opcionales que no están en Android.
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# --- Widgets (Glance): las acciones se instancian por nombre de clase al tocar el widget; si R8 las renombra o quita, el toque no hace nada.
-keep class * implements androidx.glance.appwidget.action.ActionCallback {
    public <init>();
}

# --- Widgets (Glance): Glance guarda en disco qué widget corresponde a cada receptor usando el NOMBRE de la clase del widget. Si R8 los ofusca, el
# nombre cambia en cada versión y, al actualizar la app, un widget guardado (p. ej. el de precursorado) pasa a ser otro (el de asignaciones).
-keepnames class * extends androidx.glance.appwidget.GlanceAppWidget
-keepnames class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver
