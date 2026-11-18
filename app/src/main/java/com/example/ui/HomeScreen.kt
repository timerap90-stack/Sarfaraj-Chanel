package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.LeaderboardEntry
import com.example.data.UiState
import com.example.ui.theme.DeepObsidian
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElevatedNavy
import com.example.ui.theme.GridLineSlate
import com.example.ui.theme.HyperMagenta
import com.example.ui.theme.LaserAmber
import com.example.ui.theme.MidnightSlate
import com.example.ui.theme.NeonEmerald

@Composable
fun HomeScreen(
    viewModel: GameViewModel,
    onStartRun: () -> Unit,
    onOpenLeaderboard: () -> Unit,
    onSignOut: () -> Unit,
    contentPadding: PaddingValues
) {
    val leaderboardState by viewModel.leaderboardState.collectAsStateWithLifecycle()
    val profile by viewModel.playerProfile.collectAsStateWithLifecycle()
    val musicEnabled by viewModel.musicEnabled.collectAsStateWithLifecycle()
    val sfxEnabled by viewModel.sfxEnabled.collectAsStateWithLifecycle()

    val entries = (leaderboardState as? UiState.Success<List<LeaderboardEntry>>)?.data.orEmpty()
    val myRankIndex = entries.indexOfFirst { it.userId == viewModel.currentUserId }
    val myEntry = if (myRankIndex >= 0) entries[myRankIndex] else null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepObsidian)
            .statusBarsPadding()
            .padding(contentPadding),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 800.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header row with player identity, audio toggles, and sign out
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(listOf(ElectricCyan, HyperMagenta))
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = viewModel.displayName.take(1).uppercase(),
                                style = MaterialTheme.typography.titleLarge,
                                color = DeepObsidian,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "PILOT READY",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan
                            )
                            Text(
                                text = viewModel.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { viewModel.toggleMusic() },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("home_music_toggle")
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
                                .testTag("home_sfx_toggle")
                        ) {
                            Icon(
                                imageVector = if (sfxEnabled) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                                contentDescription = stringResource(id = R.string.sfx_label),
                                tint = if (sfxEnabled) HyperMagenta else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = onSignOut,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = stringResource(id = R.string.sign_out),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Hero Launch Banner
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.45f)),
                    colors = CardDefaults.cardColors(containerColor = MidnightSlate)
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Image(
                            painter = painterResource(id = R.drawable.img_cyber_city_skyline),
                            contentDescription = "Neon Dash endless track artwork",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(230.dp),
                            alpha = 0.65f
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(230.dp)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            DeepObsidian.copy(alpha = 0.25f),
                                            DeepObsidian.copy(alpha = 0.85f),
                                            MidnightSlate
                                        )
                                    )
                                )
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = HyperMagenta.copy(alpha = 0.2f),
                                    border = BorderStroke(1.dp, HyperMagenta.copy(alpha = 0.6f))
                                ) {
                                    Text(
                                        text = "ENDLESS MODE",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = HyperMagenta,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                if (myRankIndex >= 0) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = LaserAmber.copy(alpha = 0.2f),
                                        border = BorderStroke(1.dp, LaserAmber.copy(alpha = 0.6f))
                                    ) {
                                        Text(
                                            text = "GLOBAL #${myRankIndex + 1}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = LaserAmber,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            Text(
                                text = "OUTRUN THE GRID",
                                style = MaterialTheme.typography.displaySmall,
                                color = MaterialTheme.colorScheme.onBackground
                            )

                            Text(
                                text = "Tap to jump & double-jump laser gates. Grab Hyper Boost & Aura Shields as the synth BPM climbs.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Button(
                                onClick = onStartRun,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .minimumInteractiveComponentSize()
                                    .testTag("start_run_button"),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ElectricCyan,
                                    contentColor = DeepObsidian
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(26.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(id = R.string.play_endless),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Personal Stats Strip
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatTile(
                        label = "BEST SCORE",
                        value = "${myEntry?.bestScore ?: 0}",
                        accent = ElectricCyan,
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        label = "MAX DIST",
                        value = "${myEntry?.maxDistanceMeters ?: 0}m",
                        accent = HyperMagenta,
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        label = "TOTAL RUNS",
                        value = "${profile?.totalRuns ?: myEntry?.gamesPlayed ?: 0}",
                        accent = LaserAmber,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Power-Ups & Audio Sync Cards
            item {
                Text(
                    text = "LOADOUT & POWER-UPS",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PowerUpChipCard(
                        icon = Icons.Filled.Bolt,
                        title = "Speed Boost",
                        detail = "1.65x speed • 3x pts • smash gates",
                        accent = HyperMagenta,
                        modifier = Modifier.weight(1f)
                    )
                    PowerUpChipCard(
                        icon = Icons.Filled.Shield,
                        title = "Aura Shield",
                        detail = "Absorbs 1 hit • shatters barrier",
                        accent = ElectricCyan,
                        modifier = Modifier.weight(1f)
                    )
                    PowerUpChipCard(
                        icon = Icons.Filled.GraphicEq,
                        title = "BPM Sync",
                        detail = "118–174 BPM reactive synth",
                        accent = NeonEmerald,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Global Top 5 Preview
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MidnightSlate),
                    border = BorderStroke(1.dp, GridLineSlate)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.EmojiEvents,
                                    contentDescription = null,
                                    tint = LaserAmber,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "GLOBAL TOP RUNNERS",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            TextButton(
                                onClick = onOpenLeaderboard,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("view_full_leaderboard_button")
                            ) {
                                Text(
                                    text = "VIEW ALL",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = ElectricCyan
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        when (val state = leaderboardState) {
                            is UiState.Loading -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = ElectricCyan,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                            is UiState.Error -> {
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(vertical = 12.dp)
                                )
                            }
                            is UiState.Success -> {
                                if (state.data.isEmpty()) {
                                    Text(
                                        text = "No scores recorded yet. Complete your first run to claim #1 globally!",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 12.dp)
                                    )
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        state.data.take(5).forEachIndexed { index, entry ->
                                            val isMe = entry.userId == viewModel.currentUserId
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(
                                                        if (isMe) ElectricCyan.copy(alpha = 0.14f)
                                                        else ElevatedNavy.copy(alpha = 0.65f)
                                                    )
                                                    .clickable(onClick = onOpenLeaderboard)
                                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text(
                                                        text = "#${index + 1}",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = when (index) {
                                                            0 -> LaserAmber
                                                            1 -> Color(0xFFE2E8F0)
                                                            2 -> Color(0xFFFB923C)
                                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                                        },
                                                        modifier = Modifier.width(34.dp)
                                                    )
                                                    Text(
                                                        text = if (isMe) "${entry.displayName} (You)" else entry.displayName,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = if (isMe) FontWeight.Bold else FontWeight.Medium,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                Text(
                                                    text = "${entry.bestScore} pts",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = ElectricCyan
                                                )
                                            }
                                        }
                                    }
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
private fun StatTile(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MidnightSlate),
        border = BorderStroke(1.dp, GridLineSlate)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = accent,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun PowerUpChipCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    detail: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MidnightSlate),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = accent,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
