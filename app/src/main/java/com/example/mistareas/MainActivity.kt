package com.example.mistareas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.mistareas.ui.theme.MistareasTheme
import com.google.firebase.FirebaseApp
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val firebaseConfig = FirebaseApp.getApps(this).isNotEmpty()
        setContent {
            MistareasTheme {
                if(firebaseConfig){
                    val model: TaskViewModel = viewModel()
                    TaskScreen(
                        estado = model.status,
                        onTitle = model::changeTitle,
                        onAdd = model::addTask,
                        onDelete = model::deleteTask,
                        onComplete = model::completeTask,
                        onRetry = model::connect
                    )
                } else {
                    Column(
                        modifier = Modifier.safeDrawingPadding().padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text("Conectemos Firebase", style = MaterialTheme.typography.headlineMedium)
                        Text("1. Registra la app com.example.mistareas en Firebase.")
                        Text("2. Copia google-services.json dentro de app/.")
                        Text("3. Sincroniza Gradle y vuelve a ejecutar la app.")
                    }
                }
            }
        }
    }
}