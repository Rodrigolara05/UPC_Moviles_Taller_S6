# Guía del participante

## 1. Qué vas a construir

Ampliarás **Mis tareas** con un botón «Continuar con Google». Podrás guardar tareas como invitado, vincular esa sesión y recuperarlas luego con Google.

No necesitas crear otra base de datos. Seguiremos usando:

~~~text
usuarios
  UID_DEL_USUARIO
    tareas
      ID_DE_TAREA
        titulo: texto
        completada: booleano
        creadaEn: timestamp
~~~

## 2. Preparar el proyecto

- Abre tu proyecto de la clase anterior y comprueba que puedes agregar una tarea.
- Haz una copia o un commit antes de incorporar los cambios.
- Conserva el paquete `com.example.mistareas`. Si tu proyecto utiliza otro, adapta los paquetes Kotlin y registra ese paquete exacto en Firebase.
- Usa tu propio proyecto Firebase.
- Ejecuta en un teléfono con Google Play Services o un emulador que tenga Google Play. Añade una cuenta Google.
- Para la primera vinculación, elige una cuenta Google que todavía no aparezca en Authentication de tu proyecto.

No desinstales la app ni borres sus datos para preparar el ejercicio si contiene tareas de invitado que quieras conservar. Esa sesión todavía no tiene una forma de recuperación.

## 3. Configurar Firebase desde el navegador

1. Abre [Firebase Console](https://console.firebase.google.com/) y selecciona tu proyecto anterior.
2. Abre **Authentication** y su pestaña de proveedores o métodos de acceso.
3. Mantén habilitado **Anónimo**.
4. Añade o abre **Google**, habilítalo, selecciona el correo de soporte y guarda.
5. Vuelve a Android Studio y abre Terminal en la carpeta que contiene `gradlew.bat`.
6. Ejecuta:

~~~powershell
.\gradlew.bat signingReport
~~~

7. Busca el bloque de la variante **debug** y copia su **SHA1**.
8. En Firebase abre el engranaje → configuración del proyecto → General → tus aplicaciones.
9. Selecciona la aplicación Android cuyo paquete coincide con tu proyecto.
10. Agrega la huella SHA-1 y guarda.
11. Descarga de nuevo **google-services.json** desde esa aplicación.
12. Reemplaza el archivo `app/google-services.json`. No lo pongas en `src/main`.
13. Sincroniza y reconstruye el proyecto.

Cada computadora puede firmar con una clave debug distinta. Registra la huella de la computadora que realmente compila tu instalación. Si otra variante se firma con otra clave, necesita su huella correspondiente.

El plugin Google Services genera `default_web_client_id` a partir de la configuración OAuth web del JSON. No crees un valor ficticio ni uses el ID del cliente Android como sustituto. Este taller detecta si falta el recurso y muestra instrucciones sin impedir que compile el modo invitado.

Consulta: [configuración oficial Firebase + Google](https://firebase.google.com/docs/auth/android/google-signin).

## 4. Incorporar el código completo

Si estás trabajando directamente en este repositorio, los cambios ya están integrados. Si continúas en tu copia de la clase anterior, usa los archivos completos enlazados aquí; conserva tu propio JSON.

| Archivo relativo a la raíz | Acción |
|---|---|
| [app/build.gradle.kts](../app/build.gradle.kts) | Añadir las dependencias del bloque siguiente. |
| [GoogleSignInHelper.kt](../app/src/main/java/com/example/mistareas/GoogleSignInHelper.kt) | Crear dentro del paquete `com.example.mistareas`. |
| [MainActivity.kt](../app/src/main/java/com/example/mistareas/MainActivity.kt) | Reemplazar por la versión final. |
| [TaskViewModel.kt](../app/src/main/java/com/example/mistareas/TaskViewModel.kt) | Reemplazar por la versión final. |
| [TaskScreen.kt](../app/src/main/java/com/example/mistareas/TaskScreen.kt) | Reemplazar por la versión final. |
| [TaskItem.kt](../app/src/main/java/com/example/mistareas/TaskItem.kt) | Conservar el modelo existente. |

Dentro de `dependencies` del módulo `app` se agregan:

~~~kotlin
implementation("androidx.credentials:credentials:1.3.0")
implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.1")
~~~

Se conservan el BoM Firebase `34.4.0`, `firebase-auth` y `firebase-firestore`. Las versiones están fijadas para reproducir el taller; no es necesario actualizar todas las bibliotecas durante la clase.

Para ejecutar las pruebas del repositorio se incluyen JUnit y Mockito. No son necesarios para explicar el flujo de Google.

Sincroniza Gradle. Comprueba que el manifiesto mantiene el permiso `android.permission.INTERNET`; este proyecto ya lo tiene.

## 5. Entender los cuatro pasos del acceso

### A. El botón inicia la operación

`TaskScreen` recibe `onGoogleSignIn`. `MainActivity` lo conecta a su función `signInWithGoogle`. El ViewModel activa `authenticating` para evitar dobles pulsaciones.

### B. Credential Manager obtiene la identidad

`GoogleSignInHelper` lee el cliente web generado por Google Services y construye:

~~~kotlin
val option = GetSignInWithGoogleOption.Builder(webClientId).build()
val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
~~~

Se utiliza la opción del botón explícito. La selección se abre con el contexto de la Activity y se extrae el ID token de `GoogleIdTokenCredential`. El token se entrega inmediatamente al ViewModel; no se almacena en Firestore ni se imprime.

Implementación de referencia: [Credential Manager y botón Google](https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation).

### C. Firebase vincula o inicia sesión

En `authenticateWithGoogle`:

~~~kotlin
val credential = GoogleAuthProvider.getCredential(idToken, null)
val guest = auth.currentUser
val operation = if (guest?.isAnonymous == true) {
    guest.linkWithCredential(credential)
} else {
    auth.signInWithCredential(credential)
}
~~~

- **Hay invitado:** se intenta vincular la cuenta Google a su UID.
- **No hay sesión:** se inicia sesión Google y se usan las tareas de esa identidad.
- **Ya hay sesión Google:** se muestra la cuenta actual; para cambiarla se cierra sesión primero.

Vincular no copia tareas. Mantener el UID permite seguir usando la misma ruta. [Referencia de cuentas anónimas](https://firebase.google.com/docs/auth/android/anonymous-auth#convert-an-anonymous-account-to-a-permanent-account).

### D. Compose muestra el resultado

`showSession` actualiza el nombre, correo, UID y tipo de sesión. `listenToTasks` conecta Firestore a `usuarios/{uid}/tareas`. Cuando cambia el estado, Compose vuelve a dibujar la pantalla.

## 6. Proteger los datos con reglas

Abre [firestore.rules](../firestore.rules). En tu proyecto Firebase entra a Firestore Database → Reglas. Para esta app de práctica, publica ese contenido:

~~~javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /usuarios/{uid}/tareas/{tareaId} {
      allow read, write: if request.auth != null && request.auth.uid == uid;
    }
  }
}
~~~

El invitado ya está autenticado, por eso también puede acceder a su propia ruta. Todo acceso que no cumpla la condición queda denegado. No mantengas junto a esta regla otra regla general que permita acceso a todos.

Estas reglas se centran en propiedad; la validación de campos y límites del documento queda como ampliación posterior. El límite de 80 caracteres de la pantalla es una validación de interfaz, no una restricción del servidor.

### Comprobar en el simulador de reglas

1. Crea una tarea desde la app y copia su UID y su ID de documento.
2. En la pestaña Reglas abre el simulador o playground.
3. Selecciona la operación `get` y la ruta `/usuarios/TU_UID/tareas/ID_TAREA`.
4. Activa autenticación e introduce **TU_UID**: debe permitir la lectura.
5. En la misma ruta, cambia el UID autenticado a **otro-uid**: debe denegar.
6. Desactiva autenticación: debe denegar.
7. Repite con una operación de escritura: `create` en `/usuarios/TU_UID/tareas/prueba-reglas`, usando campos `titulo`, `completada` y `creadaEn`. Solo el UID propietario debe estar permitido.
8. Las simulaciones no crean datos reales. No abras las reglas generales para resolver un fallo.

## 7. Realizar la práctica principal

1. Abre la app. Si ya tenías un invitado, se reutiliza.
2. Si aparece bienvenida, pulsa **Continuar como invitado**.
3. Agrega «Preparar exposición» y «Entregar ejercicio».
4. Completa una y espera la confirmación del servidor.
5. Anota el UID y los IDs de ambas tareas en Firestore.
6. Pulsa **Continuar con Google** y selecciona una cuenta aún no registrada en tu proyecto.
7. Comprueba el nombre y correo. El UID debe ser exactamente el mismo.
8. Comprueba que los documentos conservan sus IDs y el estado de la tarea completada.
9. Cierra la app y vuelve a abrirla: debe conservar la sesión.
10. Pulsa **Cerrar sesión**: la lista desaparece y aparece bienvenida.
11. Pulsa directamente **Continuar con Google** y elige la misma cuenta.
12. Confirma que vuelven el mismo UID y las dos tareas.
13. Agrega, completa y elimina una tarea de prueba usando la sesión Google.

Al cerrar sesión, Firebase sale de la cuenta y Credential Manager recibe la petición de limpiar su estado. No se borran documentos ni se elimina tu cuenta Google del dispositivo.

La salida se ofrece únicamente para cuentas Google. Los invitados deben vincular primero para no perder el acceso a su UID.

## 8. Probar los casos de error

| Caso | Cómo probarlo | Resultado esperado |
|---|---|---|
| Cancelación | Abrir Google desde un invitado y cerrar el selector. | Mismo UID y tareas; botón habilitado, sin mensaje de fallo. |
| Cuenta existente | Desde otro invitado de prueba, intentar vincular la cuenta Google ya usada. | Mensaje de conflicto; no cambia de UID ni se pierden tareas. |
| Sin conexión | Desactivar Internet antes de intentar acceder. | Error de acceso al terminar el intento; la sesión anterior permanece. Puede tardar en responder. |
| Escritura sin conexión | Con sesión abierta, crear una tarea sin Internet. | Se ve el cambio pendiente; no permite cambiar de cuenta. Reconectar para confirmar. |
| Regla denegada | Usar otro UID en el simulador. | Acceso rechazado. |
| Rotación | Rotar mientras se abre el selector. | No queda bloqueado indefinidamente; si el selector se cancela, se puede reintentar. |

Para probar un segundo invitado utiliza otro dispositivo o una instalación de prueba sin datos importantes. Después de crear un nuevo invitado, vincular la cuenta Google ya usada debe fallar deliberadamente. La app no combina cuentas. Para conservar ese segundo invitado, vincúlalo con otra cuenta Google.

## 9. Solucionar problemas frecuentes

- **Falta configurar Google:** habilita el proveedor, registra SHA-1, descarga el JSON nuevo y vuelve a compilar. Pulsar otra vez sin reconstruir no incorpora el archivo.
- **Error del selector:** revisa paquete, huella de la variante instalada, cliente web y que todos pertenezcan al mismo proyecto.
- **No hay una cuenta disponible:** añade una cuenta al dispositivo, comprueba Google Play Services y evita imágenes AOSP.
- **Proveedor no permitido:** habilita Anónimo o Google en Authentication según el botón utilizado.
- **Firestore rechaza el acceso:** publica las reglas y verifica `usuarios`, `tareas` y UID exactos.
- **Lista vacía al volver:** comprueba la cuenta seleccionada y el UID. No pulses invitado si quieres recuperar la cuenta Google.
- **Escritura pendiente:** vuelve a conectar Internet; no borres los datos de la app. Se bloquea el cambio de cuenta hasta recibir confirmación.
- **Demasiados intentos:** espera antes de repetir. La creación repetida de invitados puede activar límites de Firebase.
- **Gradle no encuentra Java:** configura Gradle JDK en Android Studio o usa la ruta de JAVA_HOME indicada en el README.

Si un proyecto institucional restringe las cuentas permitidas, revisa la configuración de audiencia/acceso de Google Auth Platform con el responsable del proyecto.

## Entrega del ejercicio

Presenta el UID antes/después, los IDs de dos tareas conservadas y una demostración de cerrar/volver a iniciar sesión. Explica con tus palabras cuándo se usa `linkWithCredential` y cuándo `signInWithCredential`.

Consulta [VALIDACION.md](VALIDACION.md) para ejecutar la compilación, las pruebas locales y los escenarios completos.
