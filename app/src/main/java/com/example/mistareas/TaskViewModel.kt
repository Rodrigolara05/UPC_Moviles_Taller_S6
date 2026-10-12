package com.example.mistareas

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query

data class TaskStatus(
    val uid: String? = null,
    val isAnonymous: Boolean = false,
    val displayName: String? = null,
    val email: String? = null,
    val authenticating: Boolean = false,
    val tasks: List<TaskItem> = emptyList(),
    val newTitle: String = "",
    val connecting: Boolean = false,
    val writing: Boolean = false,
    val listening: Boolean = false,
    val fromCache: Boolean = true,
    val pendingChange: Boolean = false,
    val error: String? = null,
) {
    val busy: Boolean
        get() = authenticating || connecting || writing || pendingChange
    val canEdit: Boolean
        get() = uid != null && listening && !busy
}

class TaskViewModel(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : ViewModel() {
    var status by mutableStateOf(TaskStatus())
        private set

    private var listener: ListenerRegistration? = null
    private var closed = false

    // Una respuesta antigua no puede cambiar otra sesión.
    private var sessionVersion = 0L
    private var authRequest = 0L

    init {
        showSession(auth.currentUser)
    }

    // 1. AUTH: sin sesión, esperamos una elección; nunca creamos invitados al salir.
    fun signInAsGuest() {
        if (auth.currentUser != null) return
        val request = beginAuthentication() ?: return
        auth.signInAnonymously().addOnCompleteListener { result ->
            if (!isCurrentRequest(request)) return@addOnCompleteListener
            if (result.isSuccessful) {
                showSession(result.result.user)
            } else {
                authenticationFailed(request, authMessage(result.exception))
            }
        }
    }

    fun beginGoogleSignIn(): Long? {
        if (auth.currentUser?.isAnonymous == false) return null
        return beginAuthentication()
    }

    private fun beginAuthentication(): Long? {
        if (closed || status.busy) return null
        authRequest += 1
        status = status.copy(authenticating = true, error = null)
        return authRequest
    }

    // La Activity obtiene el token, pero solo el ViewModel modifica Firebase.
    fun authenticateWithGoogle(request: Long, idToken: String) {
        if (!isCurrentRequest(request)) return
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val guest = auth.currentUser
        // Vincular conserva el UID; iniciar sesión sin invitado recupera la cuenta Google.
        val operation = if (guest?.isAnonymous == true) {
            guest.linkWithCredential(credential)
        } else {
            auth.signInWithCredential(credential)
        }
        operation.addOnCompleteListener { result ->
            if (!isCurrentRequest(request)) return@addOnCompleteListener
            if (result.isSuccessful) {
                showSession(result.result.user)
            } else {
                // No cambiamos a la cuenta existente: conservar el invitado es deliberado.
                authenticationFailed(request, authMessage(result.exception))
            }
        }
    }

    fun authenticationFailed(request: Long, message: String?) {
        if (!isCurrentRequest(request)) return
        status = status.copy(authenticating = false, error = message ?: status.error)
    }

    private fun isCurrentRequest(request: Long): Boolean =
        !closed && request == authRequest && status.authenticating

    fun beginSignOut(): Long? {
        if (auth.currentUser == null || auth.currentUser?.isAnonymous == true) return null
        val request = beginAuthentication() ?: return null
        stopListening()
        // La lista desaparece antes de esperar a Credential Manager.
        auth.signOut()
        status = TaskStatus(authenticating = true)
        return request
    }

    fun finishSignOut(request: Long, message: String? = null) {
        if (!isCurrentRequest(request)) return
        status = TaskStatus(error = message)
    }

    fun connect() {
        if (closed || status.busy) return
        showSession(auth.currentUser)
    }

    fun dismissError() {
        status = status.copy(error = null)
    }

    private fun showSession(user: FirebaseUser?) {
        stopListening()
        val sameUser = user != null && status.uid == user.uid
        status = TaskStatus(
            uid = user?.uid,
            isAnonymous = user?.isAnonymous == true,
            displayName = user?.displayName,
            email = user?.email,
            tasks = if (sameUser) status.tasks else emptyList(),
            newTitle = if (sameUser) status.newTitle else "",
        )
        if (user != null) listenToTasks(user.uid)
    }

    private fun taskFrom(uid: String): CollectionReference =
        db.collection("usuarios").document(uid).collection("tareas")

    private fun stopListening() {
        sessionVersion += 1
        listener?.remove()
        listener = null
    }

    private fun isCurrentSession(version: Long, uid: String): Boolean =
        !closed && version == sessionVersion && status.uid == uid && auth.currentUser?.uid == uid

    // 2. READ: se conserva exactamente la ruta del taller anterior.
    private fun listenToTasks(uid: String) {
        val version = sessionVersion
        status = status.copy(connecting = true, listening = true)
        listener = taskFrom(uid)
            .orderBy("creadaEn", Query.Direction.DESCENDING)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, failure ->
                if (!isCurrentSession(version, uid)) return@addSnapshotListener
                if (failure != null) {
                    status = status.copy(
                        connecting = false,
                        listening = false,
                        tasks = emptyList(),
                        pendingChange = false,
                        error = firestoreMessage(failure),
                    )
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    status = status.copy(
                        tasks = snapshot.documents.map { document ->
                            TaskItem(
                                id = document.id,
                                title = document.getString("titulo").orEmpty(),
                                complete = document.getBoolean("completada") ?: false,
                            )
                        },
                        connecting = false,
                        fromCache = snapshot.metadata.isFromCache,
                        pendingChange = snapshot.metadata.hasPendingWrites(),
                    )
                }
            }
    }

    fun changeTitle(value: String) {
        if (status.canEdit) status = status.copy(newTitle = value.take(80))
    }

    // 3. CREATE
    fun addTask() {
        val title = status.newTitle.trim()
        if (title.isBlank()) return
        write(clearTitle = true) { collection ->
            collection.add(
                mapOf(
                    "titulo" to title,
                    "completada" to false,
                    "creadaEn" to FieldValue.serverTimestamp(),
                )
            )
        }
    }

    // 4. UPDATE
    fun completeTask(task: TaskItem, complete: Boolean) {
        write { it.document(task.id).update("completada", complete) }
    }

    // 5. DELETE
    fun deleteTask(task: TaskItem) {
        write { it.document(task.id).delete() }
    }

    private fun write(
        clearTitle: Boolean = false,
        operation: (CollectionReference) -> Task<*>,
    ) {
        val uid = status.uid ?: return
        if (closed || !status.canEdit || auth.currentUser?.uid != uid) return
        val version = sessionVersion
        status = status.copy(writing = true, error = null)
        operation(taskFrom(uid)).addOnCompleteListener { result ->
            if (!isCurrentSession(version, uid)) return@addOnCompleteListener
            status = status.copy(
                writing = false,
                newTitle = if (result.isSuccessful && clearTitle) "" else status.newTitle,
                error = if (result.isSuccessful) status.error else firestoreMessage(result.exception),
            )
        }
    }

    private fun authMessage(error: Exception?): String = when (error) {
        is FirebaseAuthUserCollisionException ->
            "Esta cuenta Google ya pertenece a otro usuario de este proyecto. " +
                "Tu sesión invitada y sus tareas se conservan. Vincula otra cuenta Google."
        is FirebaseNetworkException ->
            "No se pudo conectar con Firebase. Revisa Internet y vuelve a pulsar el botón de acceso."
        is FirebaseTooManyRequestsException ->
            "Firebase recibió demasiados intentos. Espera unos minutos antes de volver a intentarlo."
        is FirebaseAuthException -> when (error.errorCode) {
            "ERROR_OPERATION_NOT_ALLOWED" ->
                "Habilita el proveedor de acceso (Anónimo o Google) en Firebase Authentication."
            "ERROR_INVALID_CREDENTIAL" ->
                "La credencial no es válida. Revisa la SHA-1 y descarga el google-services.json actualizado."
            else -> "No se pudo autenticar (" + error.errorCode +
                "). Revisa la configuración de Firebase y reintenta."
        }
        else -> "No se pudo completar el acceso. Revisa Internet y la configuración de Firebase."
    }

    private fun firestoreMessage(error: Exception?): String {
        val code = (error as? FirebaseFirestoreException)?.code
        return when (code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                "Firestore rechazó el acceso. Revisa las reglas: solo el propietario del UID puede acceder a sus tareas."
            FirebaseFirestoreException.Code.UNAVAILABLE ->
                "Firestore no está disponible. Revisa Internet y reintenta."
            else -> "No se pudo leer o guardar en Firestore. Revisa la conexión y vuelve a intentarlo."
        }
    }

    override fun onCleared() {
        closed = true
        authRequest += 1
        stopListening()
        super.onCleared()
    }
}
