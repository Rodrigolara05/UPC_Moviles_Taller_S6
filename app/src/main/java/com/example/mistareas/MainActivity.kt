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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.mistareas.ui.theme.MistareasTheme
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val firebaseConfig = FirebaseApp.getApps(this).isNotEmpty()
        setContent {
            MistareasTheme {
                if (firebaseConfig) {
                    val model: TaskViewModel = viewModel()
                    TaskScreen(
                        estado = model.status,
                        onTitle = model::changeTitle,
                        onAdd = model::addTask,
                        onDelete = model::deleteTask,
                        onComplete = model::completeTask,
                        onRetry = model::connect,
                        onGuestSignIn = model::signInAsGuest,
                        onGoogleSignIn = { signInWithGoogle(model) },
                        onSignOut = { signOut(model) },
                        onDismissError = model::dismissError,
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

    private fun signInWithGoogle(model: TaskViewModel) {
        val request = model.beginGoogleSignIn() ?: return
        // El selector necesita una Activity. El ViewModel no retiene esa referencia.
        lifecycleScope.launch {
            try {
                val idToken = GoogleSignInHelper(this@MainActivity).getIdToken()
                model.authenticateWithGoogle(request, idToken)
            } catch (_: GetCredentialCancellationException) {
                model.authenticationFailed(request, null) // Cancelar no es un error.
            } catch (cancelled: CancellationException) {
                // Una rotación cancela el selector, pero no deja el botón bloqueado.
                model.authenticationFailed(request, null)
                throw cancelled
            } catch (_: NoCredentialException) {
                model.authenticationFailed(
                    request,
                    "No hay una cuenta Google disponible. Añádela al dispositivo y usa un emulador con Google Play.",
                )
            } catch (_: GoogleIdTokenParsingException) {
                model.authenticationFailed(request, "No se pudo interpretar la respuesta de Google. Reintenta.")
            } catch (_: GetCredentialException) {
                model.authenticationFailed(
                    request,
                    "No se pudo abrir el acceso con Google. Revisa Internet, Google Play Services, " +
                        "la SHA-1 y el google-services.json actualizado.",
                )
            } catch (configuration: IllegalStateException) {
                model.authenticationFailed(request, configuration.message)
            } catch (_: Exception) {
                model.authenticationFailed(request, "No se pudo completar el acceso con Google. Vuelve a intentarlo.")
            }
        }
    }

    private fun signOut(model: TaskViewModel) {
        val request = model.beginSignOut() ?: return
        lifecycleScope.launch {
            try {
                CredentialManager.create(this@MainActivity)
                    .clearCredentialState(ClearCredentialStateRequest())
                model.finishSignOut(request)
            } catch (cancelled: CancellationException) {
                model.finishSignOut(
                    request, "Sesión cerrada. Se interrumpió la limpieza del selector de cuentas.",
                )
                throw cancelled
            } catch (_: Exception) {
                // Firebase ya cerró la sesión; no restauramos la cuenta anterior.
                model.finishSignOut(
                    request,
                    "Sesión cerrada en Firebase. No se pudo limpiar el selector de Google; " +
                        "comprueba la cuenta que elijas al volver a entrar.",
                )
            }
        }
    }
}
