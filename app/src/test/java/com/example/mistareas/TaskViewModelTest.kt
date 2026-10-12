package com.example.mistareas

import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SnapshotMetadata
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.*

class TaskViewModelTest {
    private val auth = mock(FirebaseAuth::class.java)
    private val db = mock(FirebaseFirestore::class.java)
    private val users = mock(CollectionReference::class.java)
    private val userDocument = mock(DocumentReference::class.java)
    private val tasks = mock(CollectionReference::class.java)
    private val query = mock(Query::class.java)
    private val subscriptions = mutableListOf<EventListener<QuerySnapshot>>()
    private val registrations = mutableListOf<ListenerRegistration>()

    @Before
    fun setupFirestore() {
        doReturn(users).`when`(db).collection("usuarios")
        doReturn(userDocument).`when`(users).document(anyString())
        doReturn(tasks).`when`(userDocument).collection("tareas")
        doReturn(query).`when`(tasks).orderBy("creadaEn", Query.Direction.DESCENDING)
        doAnswer {
            subscriptions += it.getArgument<EventListener<QuerySnapshot>>(1)
            mock(ListenerRegistration::class.java).also { listener -> registrations += listener }
        }.`when`(query).addSnapshotListener(eq(MetadataChanges.INCLUDE), any())
    }

    private fun user(uid: String = "guest-uid", anonymous: Boolean = true): FirebaseUser =
        mock(FirebaseUser::class.java).also {
            doReturn(uid).`when`(it).uid
            doReturn(anonymous).`when`(it).isAnonymous
            doReturn(if (anonymous) null else "Ana").`when`(it).displayName
            doReturn(if (anonymous) null else "ana@example.com").`when`(it).email
        }

    private fun snapshot(pending: Boolean = false): QuerySnapshot {
        val document = mock(DocumentSnapshot::class.java)
        doReturn("task-1").`when`(document).id
        doReturn("Mi tarea").`when`(document).getString("titulo")
        doReturn(false).`when`(document).getBoolean("completada")
        val metadata = mock(SnapshotMetadata::class.java)
        doReturn(pending).`when`(metadata).hasPendingWrites()
        doReturn(false).`when`(metadata).isFromCache
        return mock(QuerySnapshot::class.java).also {
            doReturn(listOf(document)).`when`(it).documents
            doReturn(metadata).`when`(it).metadata
        }
    }

    private fun modelFor(user: FirebaseUser): TaskViewModel {
        doReturn(user).`when`(auth).currentUser
        return TaskViewModel(auth, db).also { subscriptions.last().onEvent(snapshot(), null) }
    }

    // Controlamos cada respuesta sin red ni una cuenta Firebase real.
    private class Pending<T> {
        @Suppress("UNCHECKED_CAST")
        val task = mock(Task::class.java) as Task<T>
        private lateinit var complete: OnCompleteListener<T>

        init {
            doAnswer {
                complete = it.getArgument(0)
                task
            }.`when`(task).addOnCompleteListener(any<OnCompleteListener<T>>())
        }

        fun succeed(value: T) {
            doReturn(true).`when`(task).isSuccessful
            doReturn(value).`when`(task).result
            complete.onComplete(task)
        }

        fun fail(error: Exception) {
            doReturn(false).`when`(task).isSuccessful
            doReturn(error).`when`(task).exception
            complete.onComplete(task)
        }
    }

    @Test
    fun noSessionWaitsForAnExplicitChoice() {
        val model = TaskViewModel(auth, db)
        assertNull(model.status.uid)
        assertFalse(model.status.busy)
        verify(auth, never()).signInAnonymously()
        verifyNoInteractions(db)
    }

    @Test
    fun guestSignInIsExplicitAndCannotBeStartedTwice() {
        val pending = Pending<AuthResult>()
        doReturn(pending.task).`when`(auth).signInAnonymously()
        val model = TaskViewModel(auth, db)
        model.signInAsGuest()
        model.signInAsGuest()
        assertTrue(model.status.authenticating)
        assertNull(model.beginGoogleSignIn())
        verify(auth, times(1)).signInAnonymously()
        val guest = user()
        doReturn(guest).`when`(auth).currentUser
        val result = mock(AuthResult::class.java)
        doReturn(guest).`when`(result).user
        pending.succeed(result)
        subscriptions.last().onEvent(snapshot(), null)
        assertEquals("guest-uid", model.status.uid)
        assertTrue(model.status.isAnonymous)
        assertTrue(model.status.canEdit)
    }

    @Test
    fun linkingPreservesUidAndTasksWithoutSigningIntoAnotherUser() {
        val guest = user()
        val model = modelFor(guest)
        val pending = Pending<AuthResult>()
        doReturn(pending.task).`when`(guest).linkWithCredential(any())
        mockStatic(GoogleAuthProvider::class.java).use { google ->
            val credential = mock(AuthCredential::class.java)
            google.`when`<AuthCredential> { GoogleAuthProvider.getCredential("token", null) }
                .thenReturn(credential)
            model.authenticateWithGoogle(model.beginGoogleSignIn()!!, "token")
            val linked = user(anonymous = false)
            doReturn(linked).`when`(auth).currentUser
            val result = mock(AuthResult::class.java)
            doReturn(linked).`when`(result).user
            pending.succeed(result)
            assertEquals("guest-uid", model.status.uid)
            assertFalse(model.status.isAnonymous)
            assertEquals("Ana", model.status.displayName)
            assertEquals("ana@example.com", model.status.email)
            assertEquals("task-1", model.status.tasks.single().id)
            verify(guest).linkWithCredential(credential)
            verify(auth, never()).signInWithCredential(any())
        }
    }

    @Test
    fun accountCollisionKeepsTheGuestAndTasks() {
        val guest = user()
        val model = modelFor(guest)
        val pending = Pending<AuthResult>()
        doReturn(pending.task).`when`(guest).linkWithCredential(any())
        mockStatic(GoogleAuthProvider::class.java).use { google ->
            google.`when`<AuthCredential> { GoogleAuthProvider.getCredential("token", null) }
                .thenReturn(mock(AuthCredential::class.java))
            model.authenticateWithGoogle(model.beginGoogleSignIn()!!, "token")
            pending.fail(mock(FirebaseAuthUserCollisionException::class.java))
            assertEquals("guest-uid", model.status.uid)
            assertTrue(model.status.isAnonymous)
            assertEquals(1, model.status.tasks.size)
            assertFalse(model.status.authenticating)
            assertTrue(model.status.error!!.contains("se conservan"))
            verify(auth, never()).signInWithCredential(any())
            verify(auth, never()).signOut()
        }
    }

    @Test
    fun signedOutGoogleAccessUsesSignInAndLoadsItsOwnPath() {
        val model = TaskViewModel(auth, db)
        val pending = Pending<AuthResult>()
        doReturn(pending.task).`when`(auth).signInWithCredential(any())
        mockStatic(GoogleAuthProvider::class.java).use { google ->
            val credential = mock(AuthCredential::class.java)
            google.`when`<AuthCredential> { GoogleAuthProvider.getCredential("token", null) }
                .thenReturn(credential)
            model.authenticateWithGoogle(model.beginGoogleSignIn()!!, "token")
            val returning = user("google-uid", false)
            doReturn(returning).`when`(auth).currentUser
            val result = mock(AuthResult::class.java)
            doReturn(returning).`when`(result).user
            pending.succeed(result)
            verify(auth).signInWithCredential(credential)
            verify(users).document("google-uid")
            assertEquals("google-uid", model.status.uid)
        }
    }

    @Test
    fun cancellationKeepsGuestDataAndLateFailureDoesNotCancelANewAttempt() {
        val model = modelFor(user())
        val cancelled = model.beginGoogleSignIn()!!
        model.authenticationFailed(cancelled, null)
        assertEquals(1, model.status.tasks.size)
        assertNull(model.status.error)
        assertFalse(model.status.authenticating)
        val current = model.beginGoogleSignIn()!!
        model.authenticationFailed(cancelled, "Respuesta antigua")
        assertTrue(model.status.authenticating)
        assertNull(model.status.error)
        model.authenticationFailed(current, null)
        assertTrue(model.status.canEdit)
    }

    @Test
    fun signOutClearsImmediatelyAndIgnoresTheOldListener() {
        val model = modelFor(user("google-uid", false))
        val oldListener = subscriptions.last()
        val request = model.beginSignOut()!!
        doReturn(null).`when`(auth).currentUser
        assertNull(model.status.uid)
        assertTrue(model.status.tasks.isEmpty())
        assertTrue(model.status.authenticating)
        verify(registrations.last()).remove()
        verify(auth).signOut()
        oldListener.onEvent(snapshot(), null)
        assertTrue(model.status.tasks.isEmpty())
        model.finishSignOut(request)
        assertFalse(model.status.busy)
        verify(auth, never()).signInAnonymously()
    }

    @Test
    fun pendingWritesPreventLinkingAndSigningOut() {
        val model = modelFor(user("google-uid", false))
        subscriptions.last().onEvent(snapshot(pending = true), null)
        assertFalse(model.status.canEdit)
        assertNull(model.beginSignOut())
        verify(auth, never()).signOut()
        val guestModel = modelFor(user())
        subscriptions.last().onEvent(snapshot(pending = true), null)
        assertNull(guestModel.beginGoogleSignIn())
    }

    @Test
    fun failedWriteKeepsTitleAndShowsAnError() {
        val model = modelFor(user())
        val pending = Pending<DocumentReference>()
        doReturn(pending.task).`when`(tasks).add(any())
        model.changeTitle("No perder el borrador")
        model.addTask()
        assertTrue(model.status.writing)
        assertNull(model.beginGoogleSignIn())
        pending.fail(Exception("Sin conexión"))
        assertFalse(model.status.writing)
        assertEquals("No perder el borrador", model.status.newTitle)
        assertNotNull(model.status.error)
    }

    @Test
    fun retryInvalidatesPreviousListenerEvenWhenUidIsTheSame() {
        val model = modelFor(user())
        val oldListener = subscriptions.last()
        model.connect()
        subscriptions.last().onEvent(snapshot(), null)
        oldListener.onEvent(snapshot(pending = true), null)
        assertFalse(model.status.pendingChange)
        assertTrue(model.status.canEdit)
    }
}
