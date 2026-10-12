# Guía docente — 90 minutos

## Qué aprenderá el grupo

Al finalizar, cada participante podrá explicar y demostrar:

- La diferencia entre autenticar a una persona y guardar sus datos.
- Por qué un usuario anónimo también tiene un UID.
- Cómo vincular Google sin cambiar el UID ni copiar tareas.
- Cómo cerrar sesión y recuperar las tareas de esa cuenta.
- Cómo las reglas impiden acceder a tareas de otro UID.

**Proyecto:** la app Mis tareas de la sesión anterior. Su almacenamiento es **Cloud Firestore**, una base de datos de documentos. Cloud Storage es otro producto, orientado a archivos.

**Frase de apertura:** «La semana pasada nuestra app reconocía a un invitado. Hoy le daremos una forma de volver a encontrar sus tareas, incluso desde otro dispositivo».

## Preparación antes de la clase

1. Pide que traigan el proyecto anterior funcionando, acceso a su consola Firebase y una cuenta Google en el dispositivo.
2. Prueba el recorrido completo en tu propio proyecto: dos tareas de invitado → vincular → mismo UID → salir → entrar con Google → mismas tareas.
3. Para la demostración principal usa una cuenta Google que aún no figure en Authentication de ese proyecto. Si ya está registrada, muestra el acceso directo desde bienvenida; no elimines cuentas con datos para repetir la demo.
4. Descarga las dependencias antes de la clase y comprueba que cada emulador tenga Google Play. Una imagen AOSP sin servicios Google no sirve para esta práctica.
5. Abre Android Studio, Firebase Authentication y Firestore lado a lado. Ten disponible la guía del participante y el código final.
6. Aclara que cada alumno usa su proyecto, su JSON y la SHA-1 de su propia computadora.
7. Reserva los últimos ocho minutos. Si alguien se retrasa, entrégale los archivos completos indicados en el README y continúa con las comprobaciones.

No es necesario escribir cada importación en vivo. Entrega el código base y explica los bloques marcados; dedica el tiempo a que el grupo configure, ejecute y observe.

## 0–10 min — Problema y demostración

**Qué hacer**

- Abre la app con un invitado existente o pulsa «Continuar como invitado».
- Crea «Preparar exposición» y «Entregar ejercicio».
- Copia el UID visible y localiza `usuarios / UID / tareas` en Firestore.
- Muestra brevemente el resultado final: cuenta vinculada, nombre y correo.

**Qué decir**

«El UID es la llave con la que buscamos nuestras tareas. Google nos ayuda a demostrar quiénes somos. Firestore continúa guardando exactamente los mismos documentos».

«Vincular transforma el acceso de este invitado; entrar con otra cuenta puede llevarnos a otro UID».

**Código que mostrar:** `taskFrom` en `TaskViewModel.kt` y la tarjeta de sesión en `TaskScreen.kt`.

~~~kotlin
db.collection("usuarios").document(uid).collection("tareas")
~~~

**Comprobación antes de avanzar:** el grupo identifica su UID y al menos una tarea en su consola. Un documento padre puede no tener campos propios y aun así tener una subcolección.

## 10–25 min — Configurar Google en Firebase

**Qué hacer**

1. En Authentication → método de acceso/proveedores, mantener Anónimo y habilitar Google.
2. Elegir el correo de soporte que pide la consola y guardar.
3. Ejecutar `.\gradlew.bat signingReport` en la terminal del proyecto.
4. Copiar la SHA-1 de la variante debug.
5. En configuración del proyecto → tus aplicaciones → app Android, agregar esa huella.
6. Descargar otra vez `google-services.json` y reemplazar el de `app/`.
7. Abrir Firestore → Reglas, revisar y publicar el contenido de `firestore.rules` en el proyecto del alumno.

**Qué decir**

«El nombre del paquete identifica nuestra app. La huella identifica la clave que firma esta instalación. Si ejecuto desde otra computadora, esa clave puede ser diferente».

«El JSON actualizado trae la configuración OAuth. Debe pertenecer al mismo proyecto y paquete».

**Código que mostrar:** la búsqueda de `default_web_client_id` en `GoogleSignInHelper.kt` y la condición de propiedad en `firestore.rules`.

**Comprobación antes de avanzar:** proveedor Google habilitado, SHA-1 registrada y JSON reemplazado. La app mantiene el permiso INTERNET que ya tenía.

Referencia para esta configuración: [Firebase: acceso con Google en Android](https://firebase.google.com/docs/auth/android/google-signin).

## 25–35 min — Dependencias y responsabilidades

**Qué hacer**

- Añadir las dependencias de Credential Manager, su integración con Play Services, Google ID y lifecycle-runtime.
- Sincronizar Gradle y compilar.
- Abrir los cuatro archivos de la implementación.

**Qué decir**

«Credential Manager presenta el selector. Google devuelve una prueba de identidad, llamada token. Firebase recibe esa prueba y establece la sesión. Compose muestra el estado».

«La Activity puede abrir una ventana del sistema. El ViewModel conserva los datos de la pantalla durante una rotación, por eso no guardamos allí una referencia a la Activity».

**Código que mostrar:** el bloque de dependencias del módulo `app` y las llamadas de `MainActivity` a `TaskScreen`.

**Comprobación antes de avanzar:** Gradle sincroniza sin errores. El grupo puede señalar dónde se abre el selector y dónde se escribe en Firebase.

## 35–55 min — Vincular o iniciar sesión

**Qué hacer**

- Recorrer `GoogleSignInHelper.getIdToken`.
- Mostrar la solicitud con `GetSignInWithGoogleOption`, diseñada para el botón explícito.
- Recorrer `MainActivity.signInWithGoogle`.
- Detenerse en `TaskViewModel.authenticateWithGoogle` y sus dos caminos.
- Pedir al grupo que prediga qué método se usará con su sesión actual.

**Qué decir**

«No recibimos la contraseña de Google. Recibimos un token temporal que entregamos a Firebase. No lo imprimimos ni lo guardamos».

«Con invitado usamos link; sin sesión usamos sign-in. No hay que mover documentos cuando la vinculación mantiene el UID».

**Código que mostrar**

~~~kotlin
val credential = GoogleAuthProvider.getCredential(idToken, null)
val guest = auth.currentUser
val operation = if (guest?.isAnonymous == true) {
    guest.linkWithCredential(credential)
} else {
    auth.signInWithCredential(credential)
}
~~~

**Comprobación antes de avanzar:** el botón abre Google. Si se cancela el selector, el invitado y las tareas permanecen y el botón vuelve a estar habilitado.

Si Google ya pertenece a otro usuario Firebase, explicar: «Es otra identidad que ya tiene su UID. En este taller conservamos el invitado y mostramos el conflicto». No sustituir el fallo de vinculación por un inicio de sesión automático.

Referencia: [Firebase: convertir una cuenta anónima](https://firebase.google.com/docs/auth/android/anonymous-auth#convert-an-anonymous-account-to-a-permanent-account).

## 55–70 min — Pantalla, persistencia y salida

**Qué hacer**

- Revisar `TaskStatus` y cómo Compose reacciona a sus cambios.
- Mostrar el nombre, correo, tipo de sesión y UID.
- Recorrer `beginSignOut`, la limpieza de Credential Manager y `finishSignOut`.
- Cerrar y volver a iniciar sesión con la cuenta vinculada.

**Qué decir**

«Firebase recuerda la sesión entre aperturas. Cerrar la app no equivale a cerrar sesión».

«Al salir quitamos la escucha y la lista de la pantalla. Los documentos siguen guardados en Firestore».

«Mientras un cambio no esté confirmado, esperamos antes de permitir un cambio de cuenta. Si desconectamos Internet, la app muestra que hay una escritura pendiente».

**Código que mostrar:** `showSession`, `stopListening`, `beginSignOut` y `MainActivity.signOut`. Menciona que los contadores de operación descartan respuestas antiguas; no es necesario explicar todos sus detalles en esta clase.

**Comprobación antes de avanzar:** salir muestra bienvenida sin tareas; volver con Google recupera el mismo UID y la lista. No se crea automáticamente un nuevo invitado al salir.

## 70–82 min — Ejercicio individual

**Qué hacer**

Entregar esta consigna:

1. Entra como invitado o utiliza tu invitado anterior.
2. Crea dos tareas y completa una.
3. Anota el UID y los IDs de los documentos.
4. Vincula una cuenta Google todavía no registrada en tu proyecto.
5. Comprueba nombre, correo, mismo UID y mismos documentos.
6. Cierra sesión y comprueba que la lista desaparece.
7. Desde bienvenida pulsa directamente «Continuar con Google».
8. Comprueba que vuelve la lista; elimina solo una tarea creada para el ejercicio.

**Qué decir**

«La evidencia de que conservamos la cuenta es el mismo UID. Ver el mismo texto por sí solo no demostraría que son los mismos documentos».

**Código que mostrar:** `linkWithCredential`, `signInWithCredential` y `taskFrom`, juntos.

**Comprobación antes de avanzar:** cada participante muestra los valores antes/después y realiza una operación de escritura con Google.

Si pulsa «Continuar como invitado» después de salir, crea otro invitado: intentar vincularlo a la cuenta Google anterior producirá el conflicto previsto. No borres datos del invitado para resolverlo; usa otra cuenta Google para vincularlo.

## 82–90 min — Verificación y cierre del taller

**Qué hacer**

- Reiniciar la app y confirmar que mantiene la sesión Google.
- Repasar cancelación, cuenta existente y configuración incompleta.
- En el simulador de reglas, comparar una lectura con UID propietario, otro UID y sin autenticar.
- Entregar la matriz de pruebas para completar después de clase.

**Qué decir**

«Authentication responde quién eres. Firestore almacena las tareas. Las reglas deciden cuáles puedes leer y modificar».

«La app oculta botones, pero la protección real de los datos se aplica en las reglas del servidor».

**Código que mostrar**

~~~javascript
allow read, write: if request.auth != null && request.auth.uid == uid;
~~~

**Comprobación final:** el grupo puede explicar por qué Google no exige cambiar la ruta de las tareas y por qué un usuario autenticado no debe leer las de otro.

## Errores para tener a mano

| Síntoma | Qué revisar primero |
|---|---|
| Mensaje de configuración Google incompleta | JSON nuevo después de habilitar el proveedor. |
| Selector falla o error de desarrollador | Paquete, SHA-1 de la clave que firma la app y proyecto correcto. |
| No hay cuentas disponibles | Cuenta en el dispositivo, imagen con Google Play y servicios actualizados. |
| Firestore rechaza el acceso | Reglas publicadas, UID y ruta `usuarios/{uid}/tareas`. |
| Cuenta ya vinculada a otro usuario | Es el conflicto previsto; el invitado se conserva. |
| Botones bloqueados sin Internet | Hay escrituras pendientes; reconectar y esperar confirmación. |

La [guía del participante](GUIA_PARTICIPANTE.md) contiene los pasos detallados y la [matriz de validación](VALIDACION.md) distingue lo automatizado de lo que requiere cuenta y dispositivo.
