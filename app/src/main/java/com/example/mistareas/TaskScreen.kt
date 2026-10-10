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
) {
    val enabled = estado.uid != null && estado.listening &&
            !estado.connecting && !estado.writing

    LazyColumn(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Mis tareas", style = MaterialTheme.typography.headlineLarge)
            Text("Kotlin + Compose + Firebase", style = MaterialTheme.typography.bodyMedium)
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Mi sesión anónima", style = MaterialTheme.typography.titleSmall)
                    SelectionContainer {
                        Text(estado.uid ?: "Conectando…", style = MaterialTheme.typography.bodySmall)
                    }
                    val mensaje = when {
                        estado.connecting -> "Iniciando sesión y escuchando tareas…"
                        !estado.listening -> "Sin escucha activa. Pulsa Reintentar."
                        estado.writing || estado.pendingChange -> "Cambio pendiente de confirmación del servidor"
                        estado.fromCache -> "Datos locales; esperando al servidor"
                        else -> "Lista actualizada desde Firestore"
                    }
                    Text(mensaje, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (estado.connecting || estado.writing) {
            item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        }
        estado.error?.let { error ->
            item {
                Text(error, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry, enabled = !estado.connecting && !estado.writing) {
                    Text("Reintentar conexión")
                }
            }
        }
        item {
            OutlinedTextField(
                value = estado.newTitle,
                onValueChange = onTitle,
                label = { Text("¿Qué necesitas hacer?") },
                supportingText = { Text("${estado.newTitle.length}/80 caracteres") },
                singleLine = true,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = onAdd,
                enabled = enabled && estado.newTitle.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Agregar tarea") }
        }
        item {
            val pendientes = estado.tasks.count { !it.complete }
            Text("$pendientes pendientes · ${estado.tasks.size} en total")
        }
        if (!estado.connecting && estado.tasks.isEmpty()) {
            item { Text("Todavía no hay tareas en esta lista. Agrega la primera.") }
        }
        items(estado.tasks, key = { it.id }) { tarea ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = tarea.complete,
                        onCheckedChange = { onComplete(tarea, it) },
                        enabled = enabled,
                        modifier = Modifier.semantics { contentDescription = "Completar ${tarea.title}" },
                    )
                    Text(
                        text = tarea.title,
                        modifier = Modifier.weight(1f),
                        textDecoration = if (tarea.complete) TextDecoration.LineThrough else TextDecoration.None,
                    )
                    TextButton(onClick = { onDelete(tarea) }, enabled = enabled) {
                        Text("Eliminar")
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
                connecting = false,
                listening = true,
                fromCache = false,
                tasks = listOf(
                    TaskItem("1", "Conectar mi app a Firebase", false),
                    TaskItem("2", "Crear mi primera tarea", true),
                ),
            ),
            onTitle = {}, onAdd = {}, onComplete = { _, _ -> },
            onDelete = {}, onRetry = {},
        )
    }
}
