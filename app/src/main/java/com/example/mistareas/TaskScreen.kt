package com.example.mistareas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun TaskScreen(
    estado: TaskStatus,
    onTitle: (String) -> Unit,
    onAdd: () -> Unit,
    onComplete: (TaskItem, Boolean) -> Unit,
    onDelete: (TaskItem) -> Unit,
    onRetry: () -> Unit,
    onGuestSignIn: () -> Unit,
    onGoogleSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onDismissError: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Mis tareas", style = MaterialTheme.typography.headlineLarge)
            Text("De invitado a usuario con Google", style = MaterialTheme.typography.bodyMedium)
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        when {
                            estado.uid == null -> "Bienvenido"
                            estado.isAnonymous -> "Sesión de invitado"
                            else -> "Sesión con Google"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (estado.uid != null) {
                        estado.displayName?.let { Text(it) }
                        estado.email?.let { Text(it) }
                        SelectionContainer {
                            Text("UID: " + estado.uid, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (estado.uid == null || estado.isAnonymous) {
                        Text(
                            if (estado.isAnonymous) "Vincula Google para conservar tus tareas y recuperarlas en otro dispositivo."
                            else "Elige cómo entrar para consultar tus tareas.",
                        )
                        Button(
                            onClick = onGoogleSignIn,
                            enabled = !estado.busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Continuar con Google") }
                        if (estado.uid == null) {
                            TextButton(
                                onClick = onGuestSignIn,
                                enabled = !estado.busy,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Continuar como invitado") }
                        }
                    } else {
                        TextButton(onClick = onSignOut, enabled = !estado.busy) {
                            Text("Cerrar sesión")
                        }
                    }
                    val message = when {
                        estado.authenticating -> "Procesando sesión…"
                        estado.uid == null -> null
                        estado.connecting -> "Escuchando tareas…"
                        estado.writing || estado.pendingChange ->
                            "Cambio pendiente del servidor. Si estás sin conexión, reconecta para continuar."
                        !estado.listening -> "Sin escucha activa. Pulsa Reintentar conexión."
                        estado.fromCache -> "Datos locales; esperando al servidor"
                        else -> "Lista actualizada desde Firestore"
                    }
                    message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        if (estado.authenticating || estado.connecting || estado.writing) {
            item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        }
        estado.error?.let { error ->
            item {
                Text(error, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onDismissError) { Text("Entendido") }
            }
        }
        // Reintentar queda disponible también si el usuario cerró el mensaje de error.
        if (estado.uid != null && !estado.listening) {
            item {
                TextButton(onClick = onRetry, enabled = !estado.busy) {
                    Text("Reintentar conexión")
                }
            }
        }
        if (estado.uid != null) {
            item {
                OutlinedTextField(
                    value = estado.newTitle,
                    onValueChange = onTitle,
                    label = { Text("¿Qué necesitas hacer?") },
                    supportingText = { Text(estado.newTitle.length.toString() + "/80 caracteres") },
                    singleLine = true,
                    enabled = estado.canEdit,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = onAdd,
                    enabled = estado.canEdit && estado.newTitle.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Agregar tarea") }
            }
            item {
                val pending = estado.tasks.count { !it.complete }
                Text("$pending pendientes · " + estado.tasks.size + " en total")
            }
            if (!estado.connecting && estado.listening && estado.tasks.isEmpty()) {
                item { Text("Todavía no hay tareas en esta lista. Agrega la primera.") }
            }
            items(estado.tasks, key = { it.id }) { task ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = task.complete,
                            onCheckedChange = { onComplete(task, it) },
                            enabled = estado.canEdit,
                            modifier = Modifier.semantics { contentDescription = "Completar " + task.title },
                        )
                        Text(
                            text = task.title,
                            modifier = Modifier.weight(1f),
                            textDecoration = if (task.complete) TextDecoration.LineThrough else TextDecoration.None,
                        )
                        TextButton(onClick = { onDelete(task) }, enabled = estado.canEdit) {
                            Text("Eliminar")
                        }
                    }
                }
            }
        }
    }
}

// La vista previa no inicia Firebase ni necesita Internet.
@Preview(showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun VistaPreviaTareas() {
    MaterialTheme {
        TaskScreen(
            estado = TaskStatus(
                uid = "usuario-de-ejemplo",
                isAnonymous = true,
                listening = true,
                fromCache = false,
                tasks = listOf(
                    TaskItem("1", "Conectar mi app a Firebase", false),
                    TaskItem("2", "Crear mi primera tarea", true),
                ),
            ),
            onTitle = {}, onAdd = {}, onComplete = { _, _ -> },
            onDelete = {}, onRetry = {}, onGuestSignIn = {}, onGoogleSignIn = {},
            onSignOut = {}, onDismissError = {},
        )
    }
}
