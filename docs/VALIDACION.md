# Validación del taller

## Resultado de las comprobaciones locales

Ejecutado sobre esta implementación:

~~~powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain
~~~

| Comprobación | Resultado |
|---|---|
| Compilación debug | Correcta; APK generado. |
| Pruebas unitarias | 10 aprobadas, 0 fallos, 0 omitidas. |
| Android Lint | 0 errores y 46 advertencias. |
| Dispositivo Android conectado | Ninguno disponible durante esta validación. |
| Acceso real con Google | Pendiente de configuración OAuth y prueba en dispositivo. |
| Reglas desplegadas y verificadas en Firebase | Pendiente; se entrega el archivo y el procedimiento del simulador. |

Las advertencias de Lint corresponden a versiones de dependencias/SDK, uso del catálogo de versiones y recursos/manifiesto existentes. No se realizó una actualización general del proyecto como parte del taller.

Artefactos generados:

- APK: `app/build/outputs/apk/debug/app-debug.apk`.
- Pruebas: `app/build/reports/tests/testDebugUnitTest/index.html`.
- Lint: `app/build/reports/lint-results-debug.html`.

## Qué prueban las pruebas automáticas

[TaskViewModel: archivo de pruebas](../app/src/test/java/com/example/mistareas/TaskViewModelTest.kt).

Se usan objetos simulados de Firebase y respuestas controladas. No se llama a una cuenta real, no se escribe en una base remota y no se abre el selector de Android.

1. Sin sesión, la app espera la elección del usuario y no crea un invitado automáticamente.
2. El acceso como invitado se inicia explícitamente y bloquea intentos duplicados.
3. Vincular conserva UID y tareas; no usa inicio de sesión como sustituto.
4. Un conflicto mantiene el invitado y sus tareas.
5. Entrar con Google sin sesión usa la ruta del UID devuelto.
6. Cancelar conserva datos; una respuesta antigua no cancela un nuevo intento.
7. Salir elimina la lista inmediatamente y descarta eventos de la escucha anterior.
8. Las escrituras pendientes bloquean vinculación y salida.
9. Un fallo al escribir conserva el texto y presenta un error.
10. Reintentar invalida la escucha anterior incluso cuando se mantiene el mismo UID.

Estas pruebas verifican el comportamiento de la app ante respuestas simuladas. La aceptación del token por Google/Firebase, las reglas y la persistencia entre procesos necesitan la comprobación manual siguiente.

## Matriz de aceptación manual

Marcar al ejecutar con el JSON actualizado y el proyecto Firebase del docente.

| Hecho | Caso | Procedimiento | Resultado esperado |
|---|---|---|---|
| [ ] | Sesión anterior | Instalar la actualización sin borrar datos. | Reutiliza el invitado anterior y su UID. |
| [ ] | Invitado nuevo | Desde bienvenida, pulsar invitado. | Aparece un UID anónimo y se inicia la escucha. |
| [ ] | CRUD invitado | Crear dos tareas, completar una y borrar una tercera de prueba. | Cambios correctos en la app y en Firestore. |
| [ ] | Vincular | Anotar UID/IDs y elegir Google no registrado en este proyecto. | Mismo UID, IDs y estado de tareas; nombre y correo visibles. |
| [ ] | Persistencia | Cerrar y volver a abrir el proceso de la app. | Se restaura la cuenta Google y las tareas. |
| [ ] | Cerrar sesión | Pulsar salida con escrituras ya confirmadas. | Bienvenida inmediata, sin tareas de la cuenta anterior. |
| [ ] | Volver a entrar | Desde bienvenida, pulsar Google con la misma cuenta. | Mismo UID y tareas almacenadas. |
| [ ] | Otra cuenta | Salir y entrar con otra cuenta Google. | Solo aparecen las tareas del otro UID. |
| [ ] | CRUD Google | Crear, completar y eliminar una tarea de prueba. | Las tres operaciones funcionan. |
| [ ] | Cancelación | Abrir selector y cancelarlo desde invitado. | Conserva invitado, lista y botones operativos. |
| [ ] | Colisión | En un segundo invitado de prueba, vincular Google ya registrado. | Mensaje de conflicto y mismo UID invitado; no combina tareas. |
| [ ] | Red durante acceso | Desconectar antes de acceder y esperar la respuesta. | Error recuperable, sin descartar la sesión previa. |
| [ ] | Escritura offline | Crear una tarea offline con sesión existente. | Cambio pendiente y cambio de cuenta bloqueado hasta reconectar. |
| [ ] | Rotación | Rotar durante selector y durante autenticación. | No queda bloqueado; reintentar si se cancela el selector. |
| [ ] | Doble pulsación | Pulsar acceso repetidamente. | Una sola operación activa. |
| [ ] | JSON anterior | Ejecutar una copia de prueba sin cliente OAuth. | Mensaje de configuración claro; modo invitado disponible. |
| [ ] | Regla propietario | Simular get/create con UID igual al de la ruta. | Permitido. |
| [ ] | Regla otro usuario | Simular get/create con otro UID en la misma ruta. | Denegado. |
| [ ] | Regla sin sesión | Simular get/create sin autenticación. | Denegado. |

Para un segundo invitado usa otro dispositivo o instalación de prueba. No borres los datos de un invitado que quieras conservar. El taller no implementa migración entre cuentas.

## Configuración que queda a cargo de cada participante

- Activar Google y mantener Anónimo.
- Registrar su SHA-1.
- Descargar su propio JSON actualizado.
- Publicar las reglas en su proyecto de práctica.
- Completar el recorrido real con su cuenta Google.

El proyecto compila con el JSON anterior gracias a la comprobación explícita del recurso OAuth. Eso no significa que Google pueda autenticar hasta terminar la configuración.

Los pasos exactos están en [GUIA_PARTICIPANTE.md](GUIA_PARTICIPANTE.md).
