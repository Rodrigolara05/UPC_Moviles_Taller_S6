package com.example.mistareas

import android.annotation.SuppressLint
import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

// Solo conoce la interfaz de Google; no guarda tokens ni modifica Firebase.
class GoogleSignInHelper(private val activity: Activity) {
    // El recurso se genera desde el JSON. La búsqueda permite explicar el error
    // en pantalla incluso cuando el alumno todavía tiene el JSON del taller anterior.
    @SuppressLint("DiscouragedApi")
    suspend fun getIdToken(): String {
        val resourceId = activity.resources.getIdentifier(
            "default_web_client_id", "string", activity.packageName,
        )
        check(resourceId != 0) {
            "Falta configurar Google: habilítalo en Firebase Authentication, registra tu SHA-1 " +
                "y reemplaza app/google-services.json por el archivo actualizado."
        }
        val webClientId = activity.getString(resourceId)
        check(webClientId.isNotBlank()) { "El identificador OAuth web está vacío." }

        // Opción específica para un botón pulsado por el usuario.
        val option = GetSignInWithGoogleOption.Builder(webClientId).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val result = CredentialManager.create(activity).getCredential(
            context = activity,
            request = request,
        )
        val credential = result.credential
        check(
            credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) { "Google devolvió una credencial no compatible. Vuelve a intentarlo." }
        return GoogleIdTokenCredential.createFrom(credential.data).idToken
    }
}
