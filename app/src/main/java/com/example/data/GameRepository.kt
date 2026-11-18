package com.example.data

import android.content.Context
import com.example.R
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

class GameRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeGlobalLeaderboard(): Flow<List<LeaderboardEntry>> = flow {
        val uid = requireUserId()
        val path = "leaderboard"
        emitAll(
            db.collection(path)
                .orderBy("bestScore", Query.Direction.DESCENDING)
                .limit(50)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        LeaderboardEntry::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observePlayerProfile(userId: String): Flow<PlayerProfile?> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Refusing cross-user profile observation." }
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            PlayerProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else null
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeRecentRuns(userId: String): Flow<List<GameRun>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Refusing cross-user runs observation." }
        val path = "users/$uid/runs"
        emitAll(
            db.collection("users").document(uid).collection("runs")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(20)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        GameRun::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun ensurePlayerProfile(displayName: String, email: String?): Result<Unit> {
        var docPath = "users/unknown"
        return try {
            val uid = requireUserId()
            docPath = "users/$uid"
            val docRef = db.collection("users").document(uid)
            val snapshot = docRef.get().await()
            val cleanName = sanitizeDisplayName(displayName)
            val cleanEmail = email?.trim()?.takeIf { it.isNotEmpty() }?.take(120)

            if (!snapshot.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to cleanName,
                    "email" to cleanEmail,
                    "musicEnabled" to true,
                    "sfxEnabled" to true,
                    "totalOrbsCollected" to 0L,
                    "totalRuns" to 0L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.set(payload).await()
            } else {
                val updatePayload = mutableMapOf<String, Any>(
                    "displayName" to cleanName,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (cleanEmail != null) {
                    updatePayload["email"] = cleanEmail
                }
                docRef.update(updatePayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateAudioPreferences(musicEnabled: Boolean, sfxEnabled: Boolean): Result<Unit> {
        var docPath = "users/unknown"
        return try {
            val uid = requireUserId()
            docPath = "users/$uid"
            val docRef = db.collection("users").document(uid)
            docRef.update(
                mapOf(
                    "musicEnabled" to musicEnabled,
                    "sfxEnabled" to sfxEnabled,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun submitRunResult(
        displayName: String,
        score: Long,
        distanceMeters: Long,
        orbsCollected: Long,
        bestCombo: Long,
        shieldsUsed: Long,
        boostsUsed: Long,
        peakIntensity: Long
    ): Result<Boolean> {
        var currentPath = "leaderboard"
        return try {
            val uid = requireUserId()
            val cleanName = sanitizeDisplayName(displayName)
            val safeScore = score.coerceIn(0L, 10_000_000L)
            val safeDistance = distanceMeters.coerceIn(0L, 1_000_000L)
            val safeOrbs = orbsCollected.coerceIn(0L, 100_000L)
            val safeCombo = bestCombo.coerceIn(0L, 10_000L)
            val safeShields = shieldsUsed.coerceIn(0L, 1_000L)
            val safeBoosts = boostsUsed.coerceIn(0L, 1_000L)
            val safeIntensity = peakIntensity.coerceIn(1L, 4L)

            // 1. Save immutable GameRun record under users/{uid}/runs/{runId}
            val runRef = db.collection("users").document(uid).collection("runs").document()
            currentPath = runRef.path
            val runPayload = mapOf(
                "userId" to uid,
                "score" to safeScore,
                "distanceMeters" to safeDistance,
                "orbsCollected" to safeOrbs,
                "shieldsUsed" to safeShields,
                "boostsUsed" to safeBoosts,
                "peakIntensity" to safeIntensity,
                "createdAt" to FieldValue.serverTimestamp()
            )
            runRef.set(runPayload).await()

            // 2. Update cumulative totals on users/{uid}
            val userRef = db.collection("users").document(uid)
            currentPath = userRef.path
            val userSnap = userRef.get().await()
            if (userSnap.exists()) {
                val existingOrbs = (userSnap.getLong("totalOrbsCollected") ?: 0L).coerceAtLeast(0L)
                val existingRuns = (userSnap.getLong("totalRuns") ?: 0L).coerceAtLeast(0L)
                userRef.update(
                    mapOf(
                        "totalOrbsCollected" to (existingOrbs + safeOrbs).coerceAtMost(10_000_000L),
                        "totalRuns" to (existingRuns + 1L).coerceAtMost(1_000_000L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            // 3. Upsert monotonic global Leaderboard entry at leaderboard/{uid}
            val leaderboardRef = db.collection("leaderboard").document(uid)
            currentPath = leaderboardRef.path
            val lbSnap = leaderboardRef.get().await()
            var isNewBest = false

            if (!lbSnap.exists()) {
                isNewBest = safeScore > 0L
                val createPayload = mapOf(
                    "userId" to uid,
                    "displayName" to cleanName,
                    "bestScore" to safeScore,
                    "maxDistanceMeters" to safeDistance,
                    "bestCombo" to safeCombo,
                    "gamesPlayed" to 1L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                leaderboardRef.set(createPayload).await()
            } else {
                val prevBest = lbSnap.getLong("bestScore") ?: 0L
                val prevDist = lbSnap.getLong("maxDistanceMeters") ?: 0L
                val prevCombo = lbSnap.getLong("bestCombo") ?: 0L
                val prevGames = lbSnap.getLong("gamesPlayed") ?: 0L

                isNewBest = safeScore > prevBest
                val updatePayload = mapOf(
                    "displayName" to cleanName,
                    "bestScore" to maxOf(prevBest, safeScore),
                    "maxDistanceMeters" to maxOf(prevDist, safeDistance),
                    "bestCombo" to maxOf(prevCombo, safeCombo),
                    "gamesPlayed" to (prevGames + 1L).coerceIn(1L, 1_000_000L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                leaderboardRef.update(updatePayload).await()
            }

            Result.success(isNewBest)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, currentPath)
            Result.failure(e)
        }
    }

    private fun sanitizeDisplayName(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.isEmpty()) "Neon Runner" else trimmed.take(30)
    }
}
