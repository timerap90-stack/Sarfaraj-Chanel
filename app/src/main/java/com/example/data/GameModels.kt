package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class LeaderboardEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "Neon Runner",
    val bestScore: Long = 0L,
    val maxDistanceMeters: Long = 0L,
    val bestCombo: Long = 0L,
    val gamesPlayed: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class PlayerProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "Neon Runner",
    val email: String? = null,
    val musicEnabled: Boolean = true,
    val sfxEnabled: Boolean = true,
    val totalOrbsCollected: Long = 0L,
    val totalRuns: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class GameRun(
    @DocumentId val id: String = "",
    val userId: String = "",
    val score: Long = 0L,
    val distanceMeters: Long = 0L,
    val orbsCollected: Long = 0L,
    val shieldsUsed: Long = 0L,
    val boostsUsed: Long = 0L,
    val peakIntensity: Long = 1L,
    @ServerTimestamp val createdAt: Timestamp? = null
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
