package com.staticradio.app.ui.nav

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.staticradio.app.StaticRadioApp
import com.staticradio.app.data.local.StationDao
import com.staticradio.app.data.settings.ImageShape
import com.staticradio.app.data.toResolved
import com.staticradio.app.ui.addstation.AddStationScreen
import com.staticradio.app.ui.common.LocalPlayerBarBottomInset
import com.staticradio.app.ui.common.PlayerBarReservedHeight
import com.staticradio.app.ui.editstation.EditStationScreen
import com.staticradio.app.ui.home.HomeScreen
import com.staticradio.app.ui.home.HomeViewModel
import com.staticradio.app.ui.home.StationViewMode
import com.staticradio.app.ui.map.MapScreen
import com.staticradio.app.ui.mixes.MixFormScreen
import com.staticradio.app.ui.mixes.MixesScreen
import com.staticradio.app.ui.player.PersistentPlayerBar
import com.staticradio.app.ui.player.PlayerViewModel
import com.staticradio.app.ui.settings.GenreVocabularyScreen
import com.staticradio.app.ui.settings.SettingsScreen
import com.staticradio.app.ui.settings.TagVocabularyScreen
import com.staticradio.app.ui.theme.toComposeShape

private sealed class Destination(val route: String) {
    data object Home : Destination("home")
    data object Map : Destination("map")
    data object Settings : Destination("settings")
    data object AddStation : Destination("add_station")
    data object EditStation : Destination("edit_station/{stationId}") {
        fun createRoute(stationId: String) = "edit_station/$stationId"
    }
    data object GenreVocabulary : Destination("genre_vocabulary")
    data object MoodVocabulary : Destination("mood_vocabulary")
    data object StyleVocabulary : Destination("style_vocabulary")
    data object PickLocation : Destination("pick_location")
    data object Mixes : Destination("mixes")
    data object AddMix : Destination("add_mix")
    data object EditMix : Destination("edit_mix/{mixId}") {
        fun createRoute(mixId: String) = "edit_mix/$mixId"
    }
}

/**
 * No bottom nav bar — Home's own top bar (list/grid/map/filter/add/settings
 * icons) is the sole navigation surface. Map and Settings are regular pushed
 * destinations with their own back affordance, not tabs.
 */
@Composable
fun StaticApp(stationDao: StationDao, sharedMixUrl: String? = null) {
    val navController = rememberNavController()
    val app = LocalContext.current.applicationContext as StaticRadioApp
    val mixDao = app.database.mixDao()

    var pendingSharedMixUrl by remember { mutableStateOf(sharedMixUrl) }
    LaunchedEffect(sharedMixUrl) {
        if (sharedMixUrl != null) {
            pendingSharedMixUrl = sharedMixUrl
            navController.navigate(Destination.AddMix.route)
        }
    }

    // One shared MediaController connection for the whole app lifetime — see
    // RadioController's own note on why this shouldn't be reconnected per-screen.
    LaunchedEffect(Unit) { app.radioController.connect() }

    val playerViewModel: PlayerViewModel = viewModel(
        factory = PlayerViewModel.Factory(
            repository = app.playbackRepository,
            controller = app.radioController,
            stationMetadataProvider = { stationId ->
                stationDao.getStationWithTags(stationId)?.toResolved()?.let { station ->
                    PlayerViewModel.StationBarInfo(
                        title = station.name,
                        imageUrl = station.imageUrl,
                        countryCode = station.countryCode,
                        genres = station.genres,
                        bitrate = station.bitrate,
                        websiteUrl = station.websiteUrl
                    )
                }
            }
        )
    )
    val barState = playerViewModel.barState
    val imageShape by app.settingsRepository.imageShape.collectAsState(initial = ImageShape.SQUARE)
    val showBackgroundGrid by app.settingsRepository.showBackgroundGrid.collectAsState(initial = true)
    val gridSpacingDp by app.settingsRepository.gridSpacingDp.collectAsState(initial = com.staticradio.app.data.settings.DEFAULT_GRID_SPACING_DP)
    val gridLineWidthDp by app.settingsRepository.gridLineWidthDp.collectAsState(initial = com.staticradio.app.data.settings.DEFAULT_GRID_LINE_WIDTH_DP)
    val gridOpacity by app.settingsRepository.gridOpacity.collectAsState(initial = com.staticradio.app.data.settings.DEFAULT_GRID_OPACITY)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            val playerBarInset = if (barState.isVisible) PlayerBarReservedHeight else 0.dp
            CompositionLocalProvider(LocalPlayerBarBottomInset provides playerBarInset) {
                NavHost(
                    navController = navController,
                    startDestination = Destination.Home.route,
                    modifier = Modifier.fillMaxSize(),
                    enterTransition = { EnterTransition.None },
                    exitTransition = { ExitTransition.None },
                    popEnterTransition = { EnterTransition.None },
                    popExitTransition = { ExitTransition.None }
                ) {
                    composable(Destination.Home.route) {
                        HomeScreen(
                            stationDao = stationDao,
                            onStationClick = { item ->
                                app.radioController.playStation(
                                    stationId = item.station.id,
                                    streamUrl = item.station.streamUrl,
                                    title = item.station.name,
                                    imageUrl = item.station.imageUrl
                                )
                            },
                            onEditStation = { item -> navController.navigate(Destination.EditStation.createRoute(item.station.id)) },
                            onAddStation = { navController.navigate(Destination.AddStation.route) },
                            onMapClick = { navController.navigate(Destination.Map.route) },
                            onMixesClick = { navController.navigate(Destination.Mixes.route) },
                            onSettingsClick = { navController.navigate(Destination.Settings.route) },
                            nowPlayingStationId = barState.stationId,
                            isPlaying = barState.isPlaying,
                            gridImageShape = imageShape.toComposeShape(),
                            showBackgroundGrid = showBackgroundGrid,
                            gridSpacing = gridSpacingDp.dp,
                            gridLineWidth = gridLineWidthDp.dp,
                            gridOpacity = gridOpacity
                        )
                    }
                    composable(Destination.Map.route) {
                        // Same HomeViewModel instance Home's own composable uses (it's scoped to
                        // Home's still-on-the-back-stack NavBackStackEntry) — shares Home's filter
                        // state (so Map filtering matches List/Grid) and lets List/Grid taps here
                        // set the real view mode Home returns to, instead of just popping back to
                        // whatever Home's viewMode happened to be before Map was opened.
                        val homeEntry = remember(navController) { navController.getBackStackEntry(Destination.Home.route) }
                        val homeViewModel: HomeViewModel = viewModel(homeEntry, factory = HomeViewModel.Factory(stationDao))
                        MapScreen(
                            stationDao = stationDao,
                            homeViewModel = homeViewModel,
                            onStationClick = { station ->
                                app.radioController.playStation(
                                    stationId = station.id,
                                    streamUrl = station.streamUrl,
                                    title = station.name,
                                    imageUrl = station.imageUrl
                                )
                            },
                            onListClick = {
                                homeViewModel.setViewMode(StationViewMode.LIST)
                                navController.popBackStack()
                            },
                            onGridClick = {
                                homeViewModel.setViewMode(StationViewMode.GRID)
                                navController.popBackStack()
                            },
                            onMixesClick = { navController.navigate(Destination.Mixes.route) },
                            onAddClick = { navController.navigate(Destination.AddStation.route) },
                            onSettingsClick = { navController.navigate(Destination.Settings.route) }
                        )
                    }
                    composable(Destination.Settings.route) {
                        SettingsScreen(
                            settingsRepository = app.settingsRepository,
                            stationDao = stationDao,
                            mixDao = mixDao,
                            radioController = app.radioController,
                            playbackRepository = app.playbackRepository,
                            onManageGenres = { navController.navigate(Destination.GenreVocabulary.route) },
                            onManageMoods = { navController.navigate(Destination.MoodVocabulary.route) },
                            onManageStyles = { navController.navigate(Destination.StyleVocabulary.route) },
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(Destination.GenreVocabulary.route) {
                        GenreVocabularyScreen(
                            settingsRepository = app.settingsRepository,
                            stationDao = stationDao,
                            mixDao = mixDao,
                            radioController = app.radioController,
                            playbackRepository = app.playbackRepository,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(Destination.MoodVocabulary.route) {
                        TagVocabularyScreen(
                            stationDao = stationDao,
                            tagType = com.staticradio.app.data.local.TagType.MOOD,
                            title = "Mood",
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(Destination.StyleVocabulary.route) {
                        TagVocabularyScreen(
                            stationDao = stationDao,
                            tagType = com.staticradio.app.data.local.TagType.STYLE,
                            title = "Style",
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(Destination.AddStation.route) {
                        AddStationScreen(
                            stationDao = stationDao,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(
                        route = Destination.EditStation.route,
                        arguments = listOf(navArgument("stationId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val stationId = backStackEntry.arguments?.getString("stationId")
                        val pickedLat by backStackEntry.savedStateHandle
                            .getStateFlow<Float?>("pickedLat", null).collectAsState()
                        val pickedLon by backStackEntry.savedStateHandle
                            .getStateFlow<Float?>("pickedLon", null).collectAsState()
                        if (stationId != null) {
                            EditStationScreen(
                                stationId = stationId,
                                stationDao = stationDao,
                                onBack = { navController.popBackStack() },
                                onPickLocation = { navController.navigate(Destination.PickLocation.route) },
                                pickedLatitude = pickedLat?.toDouble(),
                                pickedLongitude = pickedLon?.toDouble()
                            )
                        }
                    }
                    composable(Destination.Mixes.route) {
                        MixesScreen(
                            mixDao = mixDao,
                            onEditMix = { mixId -> navController.navigate(Destination.EditMix.createRoute(mixId)) },
                            onAddMix = { navController.navigate(Destination.AddMix.route) },
                            onSettingsClick = { navController.navigate(Destination.Settings.route) },
                            onBack = { navController.popBackStack() },
                            showBackgroundGrid = showBackgroundGrid,
                            gridSpacing = gridSpacingDp.dp,
                            gridLineWidth = gridLineWidthDp.dp,
                            gridOpacity = gridOpacity
                        )
                    }
                    composable(Destination.AddMix.route) {
                        MixFormScreen(
                            mixId = null,
                            mixDao = mixDao,
                            stationDao = stationDao,
                            prefillUrl = pendingSharedMixUrl,
                            onBack = { pendingSharedMixUrl = null; navController.popBackStack() }
                        )
                    }
                    composable(
                        route = Destination.EditMix.route,
                        arguments = listOf(navArgument("mixId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val mixId = backStackEntry.arguments?.getString("mixId")
                        if (mixId != null) {
                            MixFormScreen(
                                mixId = mixId,
                                mixDao = mixDao,
                                stationDao = stationDao,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                    composable(Destination.PickLocation.route) {
                        MapScreen(
                            stationDao = stationDao,
                            onStationClick = {},
                            pickMode = true,
                            onLocationPicked = { lat, lon ->
                                navController.previousBackStackEntry?.savedStateHandle?.set("pickedLat", lat.toFloat())
                                navController.previousBackStackEntry?.savedStateHandle?.set("pickedLon", lon.toFloat())
                                navController.popBackStack()
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }

            PersistentPlayerBar(
                state = barState,
                onTogglePlayPause = playerViewModel::togglePlayPause,
                onRandom = playerViewModel::playRandom,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            )
        }
    }
}
