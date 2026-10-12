# Mis tareas: de invitado a usuario con Google

Taller práctico de **90 minutos** con Kotlin, Jetpack Compose, Firebase Authentication y Cloud Firestore. El código final está integrado en la aplicación de este repositorio.

**Resultado:** crear tareas como invitado, vincular una cuenta Google conservando el UID, cerrar sesión y recuperar las tareas al volver a entrar.

## Material del taller

- [Guía docente: tiempos, explicación y comprobaciones](docs/GUIA_DOCENTE.md).
- [Guía del participante: configuración, código y ejercicios](docs/GUIA_PARTICIPANTE.md).
- [Validación técnica y pruebas manuales](docs/VALIDACION.md).
- [Reglas de Firestore por propietario](firestore.rules).

## Antes de ejecutar

1. Conserva tu proyecto Firebase del taller anterior y el paquete `com.example.mistareas`.
2. Activa los proveedores **Anónimo** y **Google** en Authentication.
3. Obtén la SHA-1 de tu computadora con `.\gradlew.bat signingReport` y agrégala a la app Android en Firebase.
4. Descarga el `google-services.json` actualizado y reemplaza el archivo dentro de `app/`.
5. Publica las reglas de `firestore.rules` en tu proyecto de práctica.
6. Sincroniza Gradle y ejecuta en un dispositivo con Google Play Services y una cuenta Google.

El JSON presente al preparar este taller **no tenía clientes OAuth**. El código compila con ese JSON y permite continuar como invitado, pero el acceso Google requiere completar la configuración anterior. Los valores OAuth los genera Firebase; no se inventan ni se copian desde otro proyecto.

## Código final

| Archivo | Responsabilidad |
|---|---|
| [GoogleSignInHelper.kt](app/src/main/java/com/example/mistareas/GoogleSignInHelper.kt) | Solicitar y extraer el token Google usando la Activity. |
| [MainActivity.kt](app/src/main/java/com/example/mistareas/MainActivity.kt) | Conectar botones, corrutinas, errores del selector y limpieza de credenciales. |
| [TaskViewModel.kt](app/src/main/java/com/example/mistareas/TaskViewModel.kt) | Vincular o iniciar sesión, mantener estado y operar Firestore. |
| [TaskScreen.kt](app/src/main/java/com/example/mistareas/TaskScreen.kt) | Pantalla Compose de bienvenida, sesión y tareas. |
| [TaskItem.kt](app/src/main/java/com/example/mistareas/TaskItem.kt) | Modelo de tarea, sin cambios de esquema. |
| [app/build.gradle.kts](app/build.gradle.kts) | Dependencias de la app y pruebas. |

## Comprobación local

Desde la raíz del proyecto:

~~~powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
~~~

Si Java no está configurado en la terminal, puedes usar el JDK de Android Studio (ajusta la ruta si lo instalaste en otra carpeta):

~~~powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
~~~

El APK se genera en `app/build/outputs/apk/debug/app-debug.apk`. Cada participante debe compilar con su propio JSON y registrar la huella de la clave que firma su APK.

Las pruebas automáticas no abren una cuenta Google ni publican reglas. Consulta la matriz de validación antes de impartir el taller.
