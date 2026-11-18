package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.GameRun
import com.example.data.LeaderboardEntry
import com.example.data.UiState
import com.example.ui.theme.DeepObsidian
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElevatedNavy
import com.example.ui.theme.GridLineSlate
import com.example.ui.theme.HyperMagenta
import com.example.ui.theme.LaserAmber
import com.example.ui.theme.MidnightSlate

@Composable
fun LeaderboardScreen(
    viewModel: GameViewModel,
    contentPadding: PaddingValues
) {
    val leaderboardState by viewModel.leaderboardState.collectAsStateWithLifecycle()
    val recentRunsState by viewModel.recentRunsState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepObsidian)
            .statusBarsPadding()
            .padding(contentPadding),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 800.dp)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.EmojiEvents,
                    contentDescription = null,
                    tint = LaserAmber,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(id = R.string.leaderboard_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                SegmentedButton(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    modifier = Modifier.testTag("tab_global_leaderboard")
                ) {
                    Text("Global Top 50")
                }
                SegmentedButton(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    modifier = Modifier.testTag("tab_my_runs")
                ) {
                    Text("My Recent Runs")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (selectedTab == 0) {
                when (val state = leaderboardState) {
                    is UiState.Loading -> LoadingPlaceholder()
                    is UiState.Error -> ErrorPlaceholder(state.message)
                    is UiState.Success -> {
                        if (state.data.isEmpty()) {
                            EmptyPlaceholder(
                                title = "No Global Scores Yet",
                                subtitle = "Finish a run in Endless Mode to post the first score on the global board."
                            )
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(bottom = 16.dp)
                            ) {
                                itemsIndexed(
                                    items = state.data,
                                    key = { _, entry -> entry.userId }
                                ) { index, entry ->
                                    LeaderboardRowCard(
                                        rank = index + 1,
                                        entry = entry,
                                        isCurrentUser = entry.userId == viewModel.currentUserId
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                when (val state = recentRunsState) {
                    is UiState.Loading -> LoadingPlaceholder()
                    is UiState.Error -> ErrorPlaceholder(state.message)
                    is UiState.Success -> {
                        if (state.data.isEmpty()) {
                            EmptyPlaceholder(
                                title = "No Runs Recorded",
                                subtitle = "Your completed endless runs, power-up counts, and peak intensity tiers will appear here."
                            )
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(bottom = 16.dp)
                            ) {
                                items(
                                    items = state.data,
                                    key = { it.id.ifBlank { "${it.score}_${it.distanceMeters}" } }
                                ) { run ->
                                    RecentRunCard(run)
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
private fun LeaderboardRowCard(
    rank: Int,
    entry: LeaderboardEntry,
    isCurrentUser: Boolean
) {
    val rankAccent = when (rank) {
        1 -> LaserAmber
        2 -> Color(0xFFE2E8F0)
        3 -> Color(0xFFFB923C)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentUser) ElectricCyan.copy(alpha = 0.14f) else MidnightSlate
        ),
        border = BorderStroke(
            width = if (isCurrentUser || rank <= 3) 1.5.dp else 1.dp,
            color = if (isCurrentUser) ElectricCyan.copy(alpha = 0.7f) else if (rank <= 3) rankAccent.copy(alpha = 0.45f) else GridLineSlate
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(rankAccent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "#$rank",
                    style = MaterialTheme.typography.labelMedium,
                    color = rankAccent,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isCurrentUser) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = ElectricCyan.copy(alpha = 0.25f)
                        ) {
                            Text(
                                text = "YOU",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${entry.maxDistanceMeters}m max • ${entry.bestCombo}x combo • ${entry.gamesPlayed} runs",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${entry.bestScore}",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (rank == 1) LaserAmber else ElectricCyan
                )
                Text(
                    text = "PTS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RecentRunCard(run: GameRun) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MidnightSlate),
        border = BorderStroke(1.dp, GridLineSlate)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ElevatedNavy),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.History,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "${run.score} pts • ${run.distanceMeters}m",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${run.orbsCollected} orbs",
                            style = MaterialTheme.typography.labelSmall,
                            color = LaserAmber
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Bolt,
                                contentDescription = "Boosts used",
                                tint = HyperMagenta,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "${run.boostsUsed}",
                                style = MaterialTheme.typography.labelSmall,
                                color = HyperMagenta
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Shield,
                                contentDescription = "Shields used",
                                tint = ElectricCyan,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "${run.shieldsUsed}",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan
                            )
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = HyperMagenta.copy(alpha = 0.16f)
            ) {
                Text(
                    text = "TIER ${run.peakIntensity}",
                    style = MaterialTheme.typography.labelSmall,
                    color = HyperMagenta,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun LoadingPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = ElectricCyan)
    }
}

@Composable
private fun ErrorPlaceholder(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun EmptyPlaceholder(title: String, subtitle: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MidnightSlate),
        border = BorderStroke(1.dp, GridLineSlate)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.EmojiEvents,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
