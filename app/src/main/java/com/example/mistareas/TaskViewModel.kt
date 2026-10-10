package com.example.mistareas

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query

data class TaskStatus(
    val uid: String? = null,
    val tasks: List<TaskItem> = emptyList(),
    val newTitle: String = "",
    val connecting: Boolean = true,
    val writing: Boolean = false,
    val listening: Boolean = false,
    val fromCache: Boolean = true,
    val pendingChange: Boolean = false,
    val error: String? = null,
)


class TaskViewModel : ViewModel(){
    var status by mutableStateOf(TaskStatus())
        private set
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private var listener: ListenerRegistration? = null

    private var close = false

    init{ connect() }

    // 1. AUTH
    fun connect(){
        if(close) return
        status = status.copy(connecting = true, error = null)

        val user = auth.currentUser
        if (user!=null){
            listeningTasks(user.uid)
        } else {
            auth.signInAnonymously()
                .addOnSuccessListener {
                    result ->
                    if(!close){
                        listeningTasks(checkNotNull(result.user).uid)
                    }
                }
                .addOnFailureListener {
                    fail ->
                    if(!close){
                        status = status.copy(connecting = false)
                    }
                }
        }

    }

    private fun taskFrom(uid: String): CollectionReference =
        db.collection("usuarios").document(uid).collection("tareas")

    // 2. READ: Firestore
    private fun listeningTasks(uid: String){
        listener?.remove()
        status = status.copy(uid = uid, connecting = true, listening = true)
        listener = taskFrom(uid)
            .orderBy("creadaEn", Query.Direction.DESCENDING)
            .addSnapshotListener(MetadataChanges.INCLUDE)
            {
                snapshot, fail ->
                if(close) return@addSnapshotListener
                if (fail != null){
                    status = status.copy(connecting = false, listening = false)
                    return@addSnapshotListener
                }
                if(snapshot!= null){
                    val tasks = snapshot.documents.map {
                        document ->
                        TaskItem(
                            id = document.id,
                            title = document.getString("titulo").orEmpty(),
                            complete = document.getBoolean("completada") ?: false,
                        )
                    }
                    status = status.copy(
                        tasks = tasks,
                        connecting = false,
                        fromCache = snapshot.metadata.isFromCache,
                        pendingChange = snapshot.metadata.hasPendingWrites()
                    )

                }

            }
    }


    fun changeTitle(value: String) {
        if (!status.writing) status = status.copy(newTitle = value.take(80))
    }

    // 3. Create
    fun addTask() {
        val title = status.newTitle.trim()
        if (title.isBlank()) return
        write { collection ->
            collection.add(
                mapOf(
                    "titulo" to title,
                    "completada" to false,
                    "creadaEn" to FieldValue.serverTimestamp()
                )
            )
                .addOnSuccessListener {
                    if (!close)
                        status = status.copy(newTitle = "")
                }
        }
    }
    // 3. UPDATE
    fun completeTask(task: TaskItem, complete: Boolean){
        write {
            it.document(task.id).update("completada", complete)
        }
    }

    //4. DELETE
    fun deleteTask(task: TaskItem){
        write {
            it.document(task.id).delete()
        }
    }


    // Centraliza los estados de escritura para no repetir callbacks en cada operación.
    private fun write(operacion: (CollectionReference) -> Task<*>) {
        val uid = status.uid ?: return
        if (status.writing || status.connecting || !status.listening) return
        status = status.copy(writing = true, error = null)
        operacion(taskFrom(uid)).addOnCompleteListener { resultado ->
            if (close) return@addOnCompleteListener
            status = status.copy(writing = false)
            if (!resultado.isSuccessful) {
                //mostrarError("No se pudo guardar el cambio. Revisa las reglas y vuelve a intentarlo.", resultado.exception)
            }
        }
    }

    override fun onCleared() {
        close = true
        listener?.remove()
        super.onCleared()
    }

}