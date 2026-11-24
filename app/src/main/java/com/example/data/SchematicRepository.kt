package com.example.data

import android.content.Context
import com.example.R
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.example.model.SchematicProject
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class SchematicRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeSchematics(userId: String): Flow<List<SchematicProject>> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "users/$userId/schematics"
        emitAll(
            db.collection("users")
                .document(userId)
                .collection("schematics")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        SchematicProject::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createSchematic(project: SchematicProject): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/schematics"
        return try {
            val uid = requireUserId()
            val cleanName = project.name.trim().take(80).ifBlank { "Untitled Board" }
            val cleanDesc = project.description.trim().take(300)
            val boundedComponents = project.components.take(120)
            val boundedWires = project.wires.take(300)
            val width = project.boardWidthMm.coerceIn(10.0, 500.0)
            val height = project.boardHeightMm.coerceIn(10.0, 500.0)
            val layers = project.layerCount.coerceIn(1L, 6L)
            val snap = project.gridSnapMm.coerceIn(0.5, 10.0)

            val docRef = db.collection("users")
                .document(uid)
                .collection("schematics")
                .document()
            docPath = docRef.path

            val payload = mapOf(
                "userId" to uid,
                "name" to cleanName,
                "description" to cleanDesc,
                "boardWidthMm" to width,
                "boardHeightMm" to height,
                "layerCount" to layers,
                "gridSnapMm" to snap,
                "components" to boundedComponents.map { it.toMap() },
                "wires" to boundedWires.map { it.toMap() },
                "componentCount" to boundedComponents.size.toLong(),
                "wireCount" to boundedWires.size.toLong(),
                "validationErrorCount" to project.validationErrorCount.coerceIn(0L, 500L),
                "validationSummary" to project.validationSummary.take(500),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateSchematic(project: SchematicProject): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/schematics/${project.id}"
        return try {
            val uid = requireUserId()
            require(project.id.isNotBlank()) { "Schematic ID must not be blank for update" }
            val cleanName = project.name.trim().take(80).ifBlank { "Untitled Board" }
            val cleanDesc = project.description.trim().take(300)
            val boundedComponents = project.components.take(120)
            val boundedWires = project.wires.take(300)
            val width = project.boardWidthMm.coerceIn(10.0, 500.0)
            val height = project.boardHeightMm.coerceIn(10.0, 500.0)
            val layers = project.layerCount.coerceIn(1L, 6L)
            val snap = project.gridSnapMm.coerceIn(0.5, 10.0)

            val docRef = db.collection("users")
                .document(uid)
                .collection("schematics")
                .document(project.id)
            docPath = docRef.path

            val payload = mapOf(
                "name" to cleanName,
                "description" to cleanDesc,
                "boardWidthMm" to width,
                "boardHeightMm" to height,
                "layerCount" to layers,
                "gridSnapMm" to snap,
                "components" to boundedComponents.map { it.toMap() },
                "wires" to boundedWires.map { it.toMap() },
                "componentCount" to boundedComponents.size.toLong(),
                "wireCount" to boundedWires.size.toLong(),
                "validationErrorCount" to project.validationErrorCount.coerceIn(0L, 500L),
                "validationSummary" to project.validationSummary.take(500),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteSchematic(schematicId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/schematics/$schematicId"
        return try {
            val uid = requireUserId()
            require(schematicId.isNotBlank()) { "Schematic ID must not be blank for delete" }
            val docRef = db.collection("users")
                .document(uid)
                .collection("schematics")
                .document(schematicId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }
}
