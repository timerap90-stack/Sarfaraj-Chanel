package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.audio.ProceduralAudioEngine
import com.example.data.GameRepository
import com.example.data.authStateFlow
import com.example.data.signOut
import com.example.ui.AuthScreen
import com.example.ui.GameScreen
import com.example.ui.GameViewModel
import com.example.ui.HomeScreen
import com.example.ui.LeaderboardScreen
import com.example.ui.theme.DeepObsidian
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MidnightSlate
import com.example.ui.theme.NeonDashTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

private const val ROUTE_HOME = "home"
private const val ROUTE_LEADERBOARD = "leaderboard"
private const val ROUTE_GAME = "game"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NeonDashTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = { })
    } else {
        key(user.uid) {
            AuthenticatedArcadeApp(
                currentUserId = user.uid,
                displayName = user.displayName?.takeIf { it.isNotBlank() }
                    ?: user.email?.substringBefore("@")
                    ?: "Neon Runner",
                email = user.email
            )
        }
    }
}

@Composable
private fun AuthenticatedArcadeApp(
    currentUserId: String,
    displayName: String,
    email: String?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: GameViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                GameViewModel(
                    repository = GameRepository(app),
                    currentUserId = currentUserId,
                    displayName = displayName,
                    email = email
                )
            }
        }
    )

    val audioEngine = remember { ProceduralAudioEngine() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val musicEnabled by viewModel.musicEnabled.collectAsStateWithLifecycle()
    val sfxEnabled by viewModel.sfxEnabled.collectAsStateWithLifecycle()

    DisposableEffect(audioEngine, lifecycleOwner) {
        audioEngine.start()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> audioEngine.setForeground(true)
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> audioEngine.setForeground(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            audioEngine.release()
        }
    }

    audioEngine.musicEnabled = musicEnabled
    audioEngine.sfxEnabled = sfxEnabled

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_HOME
    val showChrome = currentRoute != ROUTE_GAME

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = DeepObsidian,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (showChrome && !useNavRail) {
                    NavigationBar(
                        containerColor = MidnightSlate
                    ) {
                        NavigationBarItem(
                            selected = currentRoute == ROUTE_HOME,
                            onClick = {
                                navController.navigate(ROUTE_HOME) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (currentRoute == ROUTE_HOME) Icons.Filled.SportsEsports else Icons.Outlined.SportsEsports,
                                    contentDescription = stringResource(id = R.string.home_nav)
                                )
                            },
                            label = { Text(stringResource(id = R.string.home_nav)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = ElectricCyan,
                                selectedTextColor = ElectricCyan,
                                indicatorColor = ElectricCyan.copy(alpha = 0.16f)
                            ),
                            modifier = Modifier.testTag("nav_home_item")
                        )
                        NavigationBarItem(
                            selected = currentRoute == ROUTE_LEADERBOARD,
                            onClick = {
                                navController.navigate(ROUTE_LEADERBOARD) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (currentRoute == ROUTE_LEADERBOARD) Icons.Filled.EmojiEvents else Icons.Outlined.EmojiEvents,
                                    contentDescription = stringResource(id = R.string.leaderboard_nav)
                                )
                            },
                            label = { Text(stringResource(id = R.string.leaderboard_nav)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = ElectricCyan,
                                selectedTextColor = ElectricCyan,
                                indicatorColor = ElectricCyan.copy(alpha = 0.16f)
                            ),
                            modifier = Modifier.testTag("nav_leaderboard_item")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Row(modifier = Modifier.fillMaxSize()) {
                if (showChrome && useNavRail) {
                    NavigationRail(
                        containerColor = MidnightSlate
                    ) {
                        NavigationRailItem(
                            selected = currentRoute == ROUTE_HOME,
                            onClick = {
                                navController.navigate(ROUTE_HOME) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (currentRoute == ROUTE_HOME) Icons.Filled.SportsEsports else Icons.Outlined.SportsEsports,
                                    contentDescription = stringResource(id = R.string.home_nav)
                                )
                            },
                            label = { Text(stringResource(id = R.string.home_nav)) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = ElectricCyan,
                                selectedTextColor = ElectricCyan,
                                indicatorColor = ElectricCyan.copy(alpha = 0.16f)
                            ),
                            modifier = Modifier.testTag("rail_home_item")
                        )
                        NavigationRailItem(
                            selected = currentRoute == ROUTE_LEADERBOARD,
                            onClick = {
                                navController.navigate(ROUTE_LEADERBOARD) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (currentRoute == ROUTE_LEADERBOARD) Icons.Filled.EmojiEvents else Icons.Outlined.EmojiEvents,
                                    contentDescription = stringResource(id = R.string.leaderboard_nav)
                                )
                            },
                            label = { Text(stringResource(id = R.string.leaderboard_nav)) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = ElectricCyan,
                                selectedTextColor = ElectricCyan,
                                indicatorColor = ElectricCyan.copy(alpha = 0.16f)
                            ),
                            modifier = Modifier.testTag("rail_leaderboard_item")
                        )
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = ROUTE_HOME,
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable(ROUTE_HOME) {
                        HomeScreen(
                            viewModel = viewModel,
                            onStartRun = {
                                navController.navigate(ROUTE_GAME)
                            },
                            onOpenLeaderboard = {
                                navController.navigate(ROUTE_LEADERBOARD) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            onSignOut = {
                                audioEngine.setForeground(false)
                                signOut(credentialManager, scope)
                            },
                            contentPadding = PaddingValues(
                                bottom = innerPadding.calculateBottomPadding()
                            )
                        )
                    }
                    composable(ROUTE_LEADERBOARD) {
                        LeaderboardScreen(
                            viewModel = viewModel,
                            contentPadding = PaddingValues(
                                bottom = innerPadding.calculateBottomPadding()
                            )
                        )
                    }
                    composable(ROUTE_GAME) {
                        GameScreen(
                            viewModel = viewModel,
                            audioEngine = audioEngine,
                            onExitToHub = {
                                navController.popBackStack()
                            },
                            onViewLeaderboard = {
                                navController.navigate(ROUTE_LEADERBOARD) {
                                    popUpTo(ROUTE_HOME) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
