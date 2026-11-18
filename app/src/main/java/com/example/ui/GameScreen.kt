package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.audio.ProceduralAudioEngine
import com.example.game.CollectibleType
import com.example.game.EndlessRunnerEngine
import com.example.game.GameCollectible
import com.example.game.GameEvent
import com.example.game.GameObstacle
import com.example.game.ObstacleType
import com.example.game.ParticleColor
import com.example.game.RunnerSnapshot
import com.example.ui.theme.DeepObsidian
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElevatedNavy
import com.example.ui.theme.GridLineSlate
import com.example.ui.theme.HyperMagenta
import com.example.ui.theme.LaserAmber
import com.example.ui.theme.MidnightSlate
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.PlasmaRed
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun GameScreen(
    viewModel: GameViewModel,
    audioEngine: ProceduralAudioEngine,
    onExitToHub: () -> Unit,
    onViewLeaderboard: () -> Unit
) {
    val musicEnabled by viewModel.musicEnabled.collectAsStateWithLifecycle()
    val sfxEnabled by viewModel.sfxEnabled.collectAsStateWithLifecycle()
    val submissionState by viewModel.submissionState.collectAsStateWithLifecycle()

    val haptics = LocalHapticFeedback.current
    val textMeasurer = rememberTextMeasurer()

    val engine = remember { EndlessRunnerEngine() }
    var snapshot by remember { mutableStateOf(RunnerSnapshot()) }
    var gameOverSummary by remember { mutableStateOf<GameEvent.GameOver?>(null) }
    var runKey by remember { mutableStateOf(0) }

    // Configure audio mode for active gameplay
    DisposableEffect(Unit) {
        audioEngine.isInGameplay = true
        onDispose {
            audioEngine.isInGameplay = false
            audioEngine.isPaused = false
            audioEngine.intensity = 0f
            audioEngine.boostActive = false
            audioEngine.shieldActive = false
        }
    }

    LaunchedEffect(musicEnabled, sfxEnabled) {
        audioEngine.musicEnabled = musicEnabled
        audioEngine.sfxEnabled = sfxEnabled
    }

    LaunchedEffect(runKey) {
        viewModel.resetSubmissionState()
        gameOverSummary = null
        engine.startNewRun()
        var lastFrameNanos = 0L

        while (true) {
            withFrameNanos { frameNanos ->
                if (lastFrameNanos == 0L) {
                    lastFrameNanos = frameNanos
                    return@withFrameNanos
                }
                val dt = ((frameNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.04f)
                lastFrameNanos = frameNanos

                val (nextSnap, events) = engine.step(dt)
                snapshot = nextSnap

                audioEngine.isPaused = nextSnap.isPaused || nextSnap.isGameOver
                audioEngine.intensity = nextSnap.intensity
                audioEngine.boostActive = nextSnap.boostRemainingSec > 0f
                audioEngine.shieldActive = nextSnap.shieldRemainingSec > 0f

                for (ev in events) {
                    when (ev) {
                        is GameEvent.Jump -> audioEngine.playJump()
                        is GameEvent.DoubleJump -> {
                            audioEngine.playDoubleJump()
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        is GameEvent.OrbCollected -> audioEngine.playOrbCollect(ev.combo)
                        is GameEvent.ShieldPickedUp -> {
                            audioEngine.playShieldPickup()
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        is GameEvent.BoostPickedUp -> {
                            audioEngine.playBoostPickup()
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        is GameEvent.ShieldBroken -> {
                            audioEngine.playShieldBreak()
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        is GameEvent.ObstacleDestroyedByBoost -> {
                            audioEngine.playShieldBreak()
                        }
                        is GameEvent.GameOver -> {
                            audioEngine.playCrash()
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            gameOverSummary = ev
                            viewModel.submitRun(ev)
                        }
                    }
                }
            }
        }
    }

    // Play triumphant fanfare when Firestore confirms a new personal best!
    LaunchedEffect(submissionState) {
        val saved = submissionState as? RunSubmissionState.Saved
        if (saved?.isNewBest == true) {
            audioEngine.playNewHighScore()
        }
    }

    BackHandler {
        if (snapshot.isGameOver || snapshot.isPaused) {
            onExitToHub()
        } else {
            engine.setPaused(true)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepObsidian)
            .pointerInput(runKey) {
                detectTapGestures(
                    onPress = {
                        if (engine.isRunning && !engine.isPaused && !engine.isGameOver) {
                            val jumpEvents = engine.requestJump()
                            for (ev in jumpEvents) {
                                when (ev) {
                                    is GameEvent.Jump -> audioEngine.playJump()
                                    is GameEvent.DoubleJump -> {
                                        audioEngine.playDoubleJump()
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    else -> Unit
                                }
                            }
                        }
                    }
                )
            }
    ) {
        // 60FPS Endless Runner Canvas
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("runner_game_canvas")
        ) {
            drawRunnerWorld(
                snap = snapshot,
                textMeasurer = textMeasurer,
                popupStyle = TextStyle(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }

        // Top HUD Overlay
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .safeDrawingPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Score & Distance
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MidnightSlate.copy(alpha = 0.82f),
                    border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "${snapshot.score}",
                            style = MaterialTheme.typography.headlineMedium,
                            color = ElectricCyan
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${snapshot.distanceMeters}m",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (snapshot.combo >= 2) {
                                Text(
                                    text = "${snapshot.combo}x COMBO",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = LaserAmber
                                )
                            }
                        }
                    }
                }

                // Intensity & Synced BPM Meter
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MidnightSlate.copy(alpha = 0.82f),
                    border = BorderStroke(
                        1.dp,
                        when (snapshot.intensityTier) {
                            4 -> HyperMagenta.copy(alpha = 0.7f)
                            3 -> LaserAmber.copy(alpha = 0.55f)
                            else -> GridLineSlate
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.GraphicEq,
                                contentDescription = "Audio intensity sync",
                                tint = if (snapshot.intensityTier >= 3) HyperMagenta else ElectricCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "TIER ${snapshot.intensityTier} • ${audioEngine.currentBpm} BPM",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(5.dp))
                        LinearProgressIndicator(
                            progress = { snapshot.intensity.coerceIn(0.05f, 1f) },
                            modifier = Modifier
                                .width(118.dp)
                                .height(5.dp),
                            color = if (snapshot.boostRemainingSec > 0f) HyperMagenta else ElectricCyan,
                            trackColor = ElevatedNavy
                        )
                    }
                }

                // Audio & Pause Controls
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MidnightSlate.copy(alpha = 0.82f),
                        border = BorderStroke(1.dp, GridLineSlate)
                    ) {
                        Row {
                            IconButton(
                                onClick = { viewModel.toggleMusic() },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("game_music_toggle")
                            ) {
                                Icon(
                                    imageVector = if (musicEnabled) Icons.Filled.MusicNote else Icons.Filled.MusicOff,
                                    contentDescription = stringResource(id = R.string.music_label),
                                    tint = if (musicEnabled) ElectricCyan else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { viewModel.toggleSfx() },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("game_sfx_toggle")
                            ) {
                                Icon(
                                    imageVector = if (sfxEnabled) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                                    contentDescription = stringResource(id = R.string.sfx_label),
                                    tint = if (sfxEnabled) HyperMagenta else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { engine.setPaused(!snapshot.isPaused) },
                                enabled = !snapshot.isGameOver,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("pause_game_button")
                            ) {
                                Icon(
                                    imageVector = if (snapshot.isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                    contentDescription = "Pause game",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // Active Power-Up Pills
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (snapshot.boostRemainingSec > 0f) {
                    PowerUpPill(
                        icon = Icons.Filled.Bolt,
                        label = "HYPER BOOST %.1fs".format(snapshot.boostRemainingSec),
                        accent = HyperMagenta
                    )
                }
                if (snapshot.shieldRemainingSec > 0f) {
                    PowerUpPill(
                        icon = Icons.Filled.Shield,
                        label = "AURA SHIELD %.1fs".format(snapshot.shieldRemainingSec),
                        accent = ElectricCyan
                    )
                }
            }
        }

        // First-moment Jump Hint
        AnimatedVisibility(
            visible = snapshot.isRunning && !snapshot.isPaused && !snapshot.isGameOver && snapshot.distanceMeters < 45L,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding()
                .padding(bottom = 28.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MidnightSlate.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.5f))
            ) {
                Text(
                    text = stringResource(id = R.string.tap_to_jump_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = ElectricCyan,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        // Pause Overlay
        AnimatedVisibility(
            visible = snapshot.isPaused && !snapshot.isGameOver,
            enter = fadeIn() + scaleIn(initialScale = 0.94f),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DeepObsidian.copy(alpha = 0.78f))
                    .safeDrawingPadding()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.widthIn(max = 400.dp).fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MidnightSlate),
                    border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.45f))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(id = R.string.paused_title),
                            style = MaterialTheme.typography.headlineMedium,
                            color = ElectricCyan
                        )
                        Text(
                            text = "Score: ${snapshot.score} • ${snapshot.distanceMeters}m",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Button(
                            onClick = { engine.setPaused(false) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("resume_game_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricCyan,
                                contentColor = DeepObsidian
                            )
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(id = R.string.resume_game), fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { runKey++ },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("restart_from_pause_button")
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(id = R.string.restart_game))
                        }
                        OutlinedButton(
                            onClick = onExitToHub,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("exit_from_pause_button")
                        ) {
                            Icon(Icons.Filled.Home, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(id = R.string.exit_to_hub))
                        }
                    }
                }
            }
        }

        // Game Over Overlay
        AnimatedVisibility(
            visible = snapshot.isGameOver && gameOverSummary != null,
            enter = fadeIn() + scaleIn(initialScale = 0.92f),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            val summary = gameOverSummary
            if (summary != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DeepObsidian.copy(alpha = 0.84f))
                        .safeDrawingPadding()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MidnightSlate),
                        border = BorderStroke(1.5.dp, HyperMagenta.copy(alpha = 0.65f))
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = stringResource(id = R.string.game_over_title),
                                style = MaterialTheme.typography.headlineMedium,
                                color = PlasmaRed
                            )

                            val saved = submissionState as? RunSubmissionState.Saved
                            if (saved?.isNewBest == true) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = LaserAmber.copy(alpha = 0.2f),
                                    border = BorderStroke(1.dp, LaserAmber)
                                ) {
                                    Text(
                                        text = stringResource(id = R.string.new_best_badge),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = LaserAmber,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }

                            Text(
                                text = "${summary.score}",
                                style = MaterialTheme.typography.displayLarge,
                                color = ElectricCyan
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                SummaryStat("DISTANCE", "${summary.distanceMeters}m", ElectricCyan)
                                SummaryStat("ORBS", "${summary.orbsCollected}", LaserAmber)
                                SummaryStat("COMBO", "${summary.bestCombo}x", HyperMagenta)
                                SummaryStat("TIER", "${summary.peakIntensity}", NeonEmerald)
                            }

                            when (val sub = submissionState) {
                                is RunSubmissionState.Saving -> {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = ElectricCyan,
                                            strokeWidth = 2.dp
                                        )
                                        Text(
                                            text = "Syncing to Global Leaderboard...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                is RunSubmissionState.Saved -> {
                                    Text(
                                        text = "Score synced to Global Leaderboard",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = NeonEmerald
                                    )
                                }
                                is RunSubmissionState.Error -> {
                                    Text(
                                        text = sub.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error,
                                        textAlign = TextAlign.Center
                                    )
                                }
                                is RunSubmissionState.Idle -> Unit
                            }

                            Button(
                                onClick = { runKey++ },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .minimumInteractiveComponentSize()
                                    .testTag("play_again_button"),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ElectricCyan,
                                    contentColor = DeepObsidian
                                )
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(id = R.string.play_again), fontWeight = FontWeight.Bold)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onViewLeaderboard,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .minimumInteractiveComponentSize()
                                        .testTag("gameover_leaderboard_button")
                                ) {
                                    Icon(Icons.Filled.EmojiEvents, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Ranks")
                                }
                                OutlinedButton(
                                    onClick = onExitToHub,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .minimumInteractiveComponentSize()
                                        .testTag("gameover_home_button")
                                ) {
                                    Icon(Icons.Filled.Home, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Hub")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PowerUpPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    accent: Color
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = accent.copy(alpha = 0.2f),
        border = BorderStroke(1.dp, accent)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = accent,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = accent
            )
        }
    }
}

@Composable
private fun SummaryStat(label: String, value: String, accent: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = accent
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun DrawScope.drawRunnerWorld(
    snap: RunnerSnapshot,
    textMeasurer: TextMeasurer,
    popupStyle: TextStyle
) {
    val w = size.width
    val h = size.height
    val scale = w / EndlessRunnerEngine.WORLD_WIDTH
    val groundScreenY = h * 0.78f

    val shakeX = if (snap.screenShake > 0.2f) (Random.nextFloat() - 0.5f) * snap.screenShake * scale else 0f
    val shakeY = if (snap.screenShake > 0.2f) (Random.nextFloat() - 0.5f) * snap.screenShake * scale else 0f

    withTransform({
        translate(left = shakeX, top = shakeY)
    }) {
        // 1. Dynamic Night Sky Gradient shifting subtly with intensity
        val horizonColor = when {
            snap.boostRemainingSec > 0f -> Color(0xFF3A0F3B)
            snap.intensityTier >= 4 -> Color(0xFF2B103A)
            snap.intensityTier >= 3 -> Color(0xFF1D1740)
            else -> Color(0xFF121C36)
        }
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF060913), horizonColor, Color(0xFF0B1120)),
                startY = 0f,
                endY = groundScreenY
            ),
            size = Size(w, groundScreenY)
        )

        // 2. Parallax Stars
        for (i in 0 until 28) {
            val sx = ((i * 173f - snap.scrollOffset * 0.04f) % w + w) % w
            val sy = ((i * 89f) % (groundScreenY * 0.62f))
            val alpha = 0.25f + (i % 4) * 0.16f
            drawCircle(
                color = Color.White.copy(alpha = alpha),
                radius = if (i % 5 == 0) 2.2f else 1.3f,
                center = Offset(sx, sy)
            )
        }

        // 3. Parallax Cyber Skyline Towers
        val buildingCount = 14
        val buildingSpan = w / 7f
        for (i in 0 until buildingCount) {
            val worldScroll = (snap.scrollOffset * 0.18f)
            val rawX = i * buildingSpan - (worldScroll % (buildingSpan * buildingCount))
            val bx = if (rawX < -buildingSpan) rawX + buildingSpan * buildingCount else rawX
            val bh = (90f + ((i * 53) % 140)) * scale.coerceAtLeast(0.7f)
            val bw = buildingSpan * 0.82f
            drawRect(
                color = Color(0xFF101930),
                topLeft = Offset(bx, groundScreenY - bh),
                size = Size(bw, bh)
            )
            // Neon roof accent
            val roofColor = if (i % 2 == 0) ElectricCyan.copy(alpha = 0.35f) else HyperMagenta.copy(alpha = 0.35f)
            drawLine(
                color = roofColor,
                start = Offset(bx, groundScreenY - bh),
                end = Offset(bx + bw, groundScreenY - bh),
                strokeWidth = 2.5f
            )
        }

        // 4. Overdrive Speed Streaks during Boost or High Tier
        if (snap.boostRemainingSec > 0f || snap.intensityTier >= 3) {
            val lineColor = if (snap.boostRemainingSec > 0f) HyperMagenta.copy(alpha = 0.35f) else ElectricCyan.copy(alpha = 0.16f)
            for (i in 0 until 8) {
                val lx = ((w - ((snap.scrollOffset * 2.2f + i * 240f) % (w + 220f))) + w) % w
                val ly = groundScreenY * (0.18f + i * 0.09f)
                drawLine(
                    color = lineColor,
                    start = Offset(lx, ly),
                    end = Offset(lx + 90f * scale, ly),
                    strokeWidth = 2.5f,
                    cap = StrokeCap.Round
                )
            }
        }

        // 5. Perspective Neon Grid Highway Ground
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF131D38), Color(0xFF070A14)),
                startY = groundScreenY,
                endY = h
            ),
            topLeft = Offset(0f, groundScreenY),
            size = Size(w, h - groundScreenY)
        )

        // Glowing Horizon Track Rail
        val trackGlow = if (snap.boostRemainingSec > 0f) HyperMagenta else ElectricCyan
        drawLine(
            color = trackGlow.copy(alpha = 0.35f),
            start = Offset(0f, groundScreenY),
            end = Offset(w, groundScreenY),
            strokeWidth = 10f
        )
        drawLine(
            color = trackGlow,
            start = Offset(0f, groundScreenY),
            end = Offset(w, groundScreenY),
            strokeWidth = 3f
        )

        // Scrolling perspective grid ribs
        val ribSpacing = 90f * scale
        val ribShift = (snap.scrollOffset * scale) % ribSpacing
        var gx = -ribShift
        while (gx < w + ribSpacing) {
            drawLine(
                color = GridLineSlate.copy(alpha = 0.7f),
                start = Offset(gx, groundScreenY),
                end = Offset(gx - 65f * scale, h),
                strokeWidth = 2f
            )
            gx += ribSpacing
        }
        for (row in 1..4) {
            val ry = groundScreenY + (h - groundScreenY) * (row * 0.22f)
            drawLine(
                color = GridLineSlate.copy(alpha = 0.45f),
                start = Offset(0f, ry),
                end = Offset(w, ry),
                strokeWidth = 1.5f
            )
        }

        // 6. Shockwave Rings
        for (sw in snap.shockwaves) {
            val sx = sw.x * scale
            val sy = groundScreenY - sw.y * scale
            drawCircle(
                color = resolveParticleColor(sw.colorType).copy(alpha = sw.alpha.coerceIn(0f, 1f)),
                radius = sw.radius * scale,
                center = Offset(sx, sy),
                style = Stroke(width = 4f * scale)
            )
        }

        // 7. Obstacles
        for (obs in snap.obstacles) {
            drawObstacle(obs, scale, groundScreenY)
        }

        // 8. Collectibles & Power-Ups
        for (col in snap.collectibles) {
            drawCollectible(col, scale, groundScreenY)
        }

        // 9. Runner Trail & Cyber Runner Character
        drawRunner(snap, scale, groundScreenY)

        // 10. Particles
        for (p in snap.particles) {
            val px = p.x * scale
            val py = groundScreenY - p.y * scale
            val alpha = (p.life / p.maxLife).coerceIn(0f, 1f)
            drawCircle(
                color = resolveParticleColor(p.colorType).copy(alpha = alpha),
                radius = p.size * scale * (0.4f + 0.6f * alpha),
                center = Offset(px, py)
            )
        }

        // 11. Floating Score / Power-Up Popups
        for (pop in snap.popups) {
            val px = (pop.x * scale).coerceIn(20f, w - 160f)
            val py = (groundScreenY - pop.y * scale).coerceIn(40f, groundScreenY)
            val alpha = (pop.life / pop.maxLife).coerceIn(0f, 1f)
            val color = resolveParticleColor(pop.colorType).copy(alpha = alpha)
            drawText(
                textMeasurer = textMeasurer,
                text = pop.text,
                topLeft = Offset(px, py),
                style = popupStyle.copy(color = color)
            )
        }
    }
}

private fun DrawScope.drawObstacle(
    obs: GameObstacle,
    scale: Float,
    groundScreenY: Float
) {
    val left = (obs.x - obs.width * 0.5f) * scale
    val bottom = groundScreenY - obs.yOffset * scale
    val widthPx = obs.width * scale
    val heightPx = obs.height * scale
    val top = bottom - heightPx

    when (obs.type) {
        ObstacleType.LOW_LASER, ObstacleType.TALL_SPIRE -> {
            val accent = if (obs.type == ObstacleType.TALL_SPIRE) HyperMagenta else PlasmaRed
            // Outer glow
            drawRoundRect(
                color = accent.copy(alpha = 0.25f),
                topLeft = Offset(left - 5f * scale, top - 4f * scale),
                size = Size(widthPx + 10f * scale, heightPx + 4f * scale),
                cornerRadius = CornerRadius(10f * scale)
            )
            // Body
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(accent, Color(0xFF4C0D26))),
                topLeft = Offset(left, top),
                size = Size(widthPx, heightPx),
                cornerRadius = CornerRadius(7f * scale)
            )
            // Bright inner laser core
            drawRoundRect(
                color = Color.White.copy(alpha = 0.85f),
                topLeft = Offset(left + widthPx * 0.35f, top + 6f * scale),
                size = Size(widthPx * 0.3f, heightPx - 12f * scale),
                cornerRadius = CornerRadius(4f * scale)
            )
        }
        ObstacleType.DOUBLE_BARRIER -> {
            val spikeW = widthPx * 0.44f
            for (s in 0..1) {
                val sx = left + s * (widthPx - spikeW)
                val path = Path().apply {
                    moveTo(sx, bottom)
                    lineTo(sx + spikeW * 0.5f, top)
                    lineTo(sx + spikeW, bottom)
                    close()
                }
                drawPath(path, color = PlasmaRed)
                drawPath(path, color = LaserAmber.copy(alpha = 0.7f), style = Stroke(width = 2.5f * scale))
            }
        }
        ObstacleType.HOVER_DRONE -> {
            val center = Offset(left + widthPx * 0.5f, top + heightPx * 0.5f)
            // Drone hull
            drawRoundRect(
                color = Color(0xFF28102A),
                topLeft = Offset(left, top),
                size = Size(widthPx, heightPx),
                cornerRadius = CornerRadius(12f * scale)
            )
            drawRoundRect(
                color = HyperMagenta,
                topLeft = Offset(left, top),
                size = Size(widthPx, heightPx),
                cornerRadius = CornerRadius(12f * scale),
                style = Stroke(width = 2.5f * scale)
            )
            // Glowing scanner eye
            drawCircle(
                color = PlasmaRed,
                radius = 7f * scale,
                center = center
            )
            drawCircle(
                color = Color.White,
                radius = 3f * scale,
                center = center
            )
        }
    }
}

private fun DrawScope.drawCollectible(
    col: GameCollectible,
    scale: Float,
    groundScreenY: Float
) {
    val cx = col.x * scale
    val cy = groundScreenY - (col.baseY + sin(col.bobPhase) * 8f) * scale
    val r = col.radius * scale

    when (col.type) {
        CollectibleType.ENERGY_ORB -> {
            drawCircle(color = LaserAmber.copy(alpha = 0.24f), radius = r * 1.4f, center = Offset(cx, cy))
            val diamond = Path().apply {
                moveTo(cx, cy - r)
                lineTo(cx + r * 0.85f, cy)
                lineTo(cx, cy + r)
                lineTo(cx - r * 0.85f, cy)
                close()
            }
            drawPath(diamond, color = LaserAmber)
            drawPath(diamond, color = Color.White.copy(alpha = 0.85f), style = Stroke(width = 2f * scale))
        }
        CollectibleType.SPEED_BOOST -> {
            drawCircle(color = HyperMagenta.copy(alpha = 0.25f), radius = r * 1.6f, center = Offset(cx, cy))
            drawCircle(color = MidnightSlate, radius = r, center = Offset(cx, cy))
            drawCircle(color = HyperMagenta, radius = r, center = Offset(cx, cy), style = Stroke(width = 3f * scale))
            // Lightning bolt icon
            val bolt = Path().apply {
                moveTo(cx + r * 0.15f, cy - r * 0.62f)
                lineTo(cx - r * 0.35f, cy + r * 0.05f)
                lineTo(cx + r * 0.05f, cy + r * 0.05f)
                lineTo(cx - r * 0.15f, cy + r * 0.62f)
                lineTo(cx + r * 0.38f, cy - r * 0.05f)
                lineTo(cx - r * 0.02f, cy - r * 0.05f)
                close()
            }
            drawPath(bolt, color = LaserAmber)
        }
        CollectibleType.AURA_SHIELD -> {
            drawCircle(color = ElectricCyan.copy(alpha = 0.25f), radius = r * 1.6f, center = Offset(cx, cy))
            drawCircle(color = MidnightSlate, radius = r, center = Offset(cx, cy))
            drawCircle(color = ElectricCyan, radius = r, center = Offset(cx, cy), style = Stroke(width = 3f * scale))
            // Inner shield badge
            val shield = Path().apply {
                moveTo(cx, cy - r * 0.55f)
                lineTo(cx + r * 0.45f, cy - r * 0.30f)
                lineTo(cx + r * 0.35f, cy + r * 0.30f)
                lineTo(cx, cy + r * 0.58f)
                lineTo(cx - r * 0.35f, cy + r * 0.30f)
                lineTo(cx - r * 0.45f, cy - r * 0.30f)
                close()
            }
            drawPath(shield, color = ElectricCyan)
        }
    }
}

private fun DrawScope.drawRunner(
    snap: RunnerSnapshot,
    scale: Float,
    groundScreenY: Float
) {
    val rx = snap.runnerX * scale
    val ry = groundScreenY - snap.runnerY * scale
    val bodyH = EndlessRunnerEngine.RUNNER_HEIGHT * scale * snap.squashStretch
    val bodyW = EndlessRunnerEngine.RUNNER_WIDTH * scale / snap.squashStretch

    val primaryColor = if (snap.boostRemainingSec > 0f) HyperMagenta else ElectricCyan
    val secondaryColor = if (snap.boostRemainingSec > 0f) LaserAmber else HyperMagenta

    // Energy Scarf / Speed Ribbon Trail
    if (snap.runnerTrailY.size >= 2) {
        val scarfPath = Path()
        snap.runnerTrailY.forEachIndexed { idx, worldY ->
            val tx = rx - idx * 14f * scale
            val ty = groundScreenY - worldY * scale + sin(snap.runCycle + idx * 0.6f) * 3f * scale
            if (idx == 0) scarfPath.moveTo(tx, ty) else scarfPath.lineTo(tx, ty)
        }
        drawPath(
            path = scarfPath,
            color = secondaryColor.copy(alpha = 0.72f),
            style = Stroke(width = 7f * scale, cap = StrokeCap.Round)
        )
    }

    // Overdrive Boost Ghost Afterimages
    if (snap.boostRemainingSec > 0f) {
        for (ghost in 1..2) {
            val gx = rx - ghost * 24f * scale
            drawRoundRect(
                color = HyperMagenta.copy(alpha = 0.25f / ghost),
                topLeft = Offset(gx - bodyW * 0.35f, ry - bodyH * 0.8f),
                size = Size(bodyW * 0.7f, bodyH * 0.65f),
                cornerRadius = CornerRadius(10f * scale)
            )
        }
    }

    // Flicker alpha during brief post-shield grace invulnerability
    val charAlpha = if (snap.invulnerableSec > 0f && ((snap.invulnerableSec * 20).toInt() % 2 == 0)) 0.45f else 1f

    val stride = if (snap.runnerY > 2f) 0.55f else sin(snap.runCycle)
    val hip = Offset(rx, ry - bodyH * 0.36f)
    val shoulder = Offset(rx + 6f * scale, ry - bodyH * 0.72f)
    val headCenter = Offset(rx + 10f * scale, ry - bodyH * 0.88f)

    // Back & Front Legs
    val frontFoot = Offset(rx + stride * 18f * scale, ry - (if (snap.runnerY > 2f) 10f else 2f) * scale)
    val backFoot = Offset(rx - stride * 18f * scale, ry - (if (snap.runnerY > 2f) 18f else 2f) * scale)
    drawLine(
        color = primaryColor.copy(alpha = 0.55f * charAlpha),
        start = hip,
        end = backFoot,
        strokeWidth = 7f * scale,
        cap = StrokeCap.Round
    )
    drawLine(
        color = primaryColor.copy(alpha = charAlpha),
        start = hip,
        end = frontFoot,
        strokeWidth = 7.5f * scale,
        cap = StrokeCap.Round
    )

    // Torso
    drawLine(
        color = Color(0xFF0F172A).copy(alpha = charAlpha),
        start = hip,
        end = shoulder,
        strokeWidth = 15f * scale,
        cap = StrokeCap.Round
    )
    drawLine(
        color = primaryColor.copy(alpha = charAlpha),
        start = hip,
        end = shoulder,
        strokeWidth = 9f * scale,
        cap = StrokeCap.Round
    )

    // Swinging Arm
    val hand = Offset(shoulder.x - stride * 14f * scale + 6f * scale, shoulder.y + 14f * scale)
    drawLine(
        color = secondaryColor.copy(alpha = charAlpha),
        start = shoulder,
        end = hand,
        strokeWidth = 6f * scale,
        cap = StrokeCap.Round
    )

    // Helmet & Glowing Cyber Visor
    drawCircle(
        color = Color(0xFF0F172A).copy(alpha = charAlpha),
        radius = 12.5f * scale,
        center = headCenter
    )
    drawCircle(
        color = primaryColor.copy(alpha = charAlpha),
        radius = 12.5f * scale,
        center = headCenter,
        style = Stroke(width = 2.5f * scale)
    )
    drawLine(
        color = Color.White.copy(alpha = charAlpha),
        start = Offset(headCenter.x + 2f * scale, headCenter.y - 2f * scale),
        end = Offset(headCenter.x + 11f * scale, headCenter.y - 2f * scale),
        strokeWidth = 4f * scale,
        cap = StrokeCap.Round
    )

    // Active Aura Shield Hexagonal Forcefield
    if (snap.shieldRemainingSec > 0f) {
        val shieldCenter = Offset(rx + 2f * scale, ry - bodyH * 0.5f)
        val shieldRadius = 54f * scale
        val pulse = 0.85f + 0.15f * sin(snap.scrollOffset * 0.03f)
        drawCircle(
            color = ElectricCyan.copy(alpha = 0.14f),
            radius = shieldRadius * pulse,
            center = shieldCenter
        )
        val hexPath = Path()
        for (i in 0 until 6) {
            val angle = (i * 60.0 + (snap.scrollOffset * 0.15)) * PI / 180.0
            val hx = shieldCenter.x + cos(angle).toFloat() * shieldRadius * pulse
            val hy = shieldCenter.y + sin(angle).toFloat() * shieldRadius * pulse
            if (i == 0) hexPath.moveTo(hx, hy) else hexPath.lineTo(hx, hy)
        }
        hexPath.close()
        drawPath(
            path = hexPath,
            color = ElectricCyan.copy(alpha = 0.85f),
            style = Stroke(width = 3f * scale)
        )
    }
}

private fun resolveParticleColor(colorType: ParticleColor): Color = when (colorType) {
    ParticleColor.CYAN -> ElectricCyan
    ParticleColor.MAGENTA -> HyperMagenta
    ParticleColor.AMBER -> LaserAmber
    ParticleColor.EMERALD -> NeonEmerald
    ParticleColor.RED -> PlasmaRed
}
