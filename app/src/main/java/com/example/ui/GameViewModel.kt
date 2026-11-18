package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.GameRepository
import com.example.data.GameRun
import com.example.data.LeaderboardEntry
import com.example.data.PlayerProfile
import com.example.data.UiState
import com.example.game.GameEvent
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface RunSubmissionState {
    data object Idle : RunSubmissionState
    data object Saving : RunSubmissionState
    data class Saved(val isNewBest: Boolean) : RunSubmissionState
    data class Error(val message: String) : RunSubmissionState
}

class GameViewModel(
    private val repository: GameRepository,
    val currentUserId: String,
    val displayName: String,
    private val email: String?
) : ViewModel() {

    val leaderboardState: StateFlow<UiState<List<LeaderboardEntry>>> =
        repository.observeGlobalLeaderboard()
            .map<List<LeaderboardEntry>, UiState<List<LeaderboardEntry>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing leaderboard", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to load global leaderboard"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val playerProfile: StateFlow<PlayerProfile?> =
        repository.observePlayerProfile(currentUserId)
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                Log.w(TAG, "Error observing profile", firestoreError ?: error)
                emit(null)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = null
            )

    val recentRunsState: StateFlow<UiState<List<GameRun>>> =
        repository.observeRecentRuns(currentUserId)
            .map<List<GameRun>, UiState<List<GameRun>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing recent runs", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to load run history"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    private val _localMusicEnabled = MutableStateFlow(true)
    val musicEnabled: StateFlow<Boolean> = _localMusicEnabled.asStateFlow()

    private val _localSfxEnabled = MutableStateFlow(true)
    val sfxEnabled: StateFlow<Boolean> = _localSfxEnabled.asStateFlow()

    private val _submissionState = MutableStateFlow<RunSubmissionState>(RunSubmissionState.Idle)
    val submissionState: StateFlow<RunSubmissionState> = _submissionState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensurePlayerProfile(displayName, email)
        }
        viewModelScope.launch {
            playerProfile.collect { profile ->
                if (profile != null) {
                    _localMusicEnabled.value = profile.musicEnabled
                    _localSfxEnabled.value = profile.sfxEnabled
                }
            }
        }
    }

    fun toggleMusic() {
        val nextMusic = !_localMusicEnabled.value
        _localMusicEnabled.value = nextMusic
        viewModelScope.launch {
            repository.updateAudioPreferences(nextMusic, _localSfxEnabled.value)
        }
    }

    fun toggleSfx() {
        val nextSfx = !_localSfxEnabled.value
        _localSfxEnabled.value = nextSfx
        viewModelScope.launch {
            repository.updateAudioPreferences(_localMusicEnabled.value, nextSfx)
        }
    }

    fun resetSubmissionState() {
        _submissionState.value = RunSubmissionState.Idle
    }

    fun submitRun(gameOver: GameEvent.GameOver) {
        _submissionState.value = RunSubmissionState.Saving
        viewModelScope.launch {
            val result = repository.submitRunResult(
                displayName = displayName,
                score = gameOver.score,
                distanceMeters = gameOver.distanceMeters,
                orbsCollected = gameOver.orbsCollected,
                bestCombo = gameOver.bestCombo,
                shieldsUsed = gameOver.shieldsUsed,
                boostsUsed = gameOver.boostsUsed,
                peakIntensity = gameOver.peakIntensity
            )
            result.fold(
                onSuccess = { isNewBest ->
                    _submissionState.value = RunSubmissionState.Saved(isNewBest)
                },
                onFailure = { error ->
                    _submissionState.value = RunSubmissionState.Error(
                        error.localizedMessage ?: "Could not save run to global leaderboard"
                    )
                }
            )
        }
    }

    private companion object {
        const val TAG = "GameViewModel"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
