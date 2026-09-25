package net.nobu0707.busnav.ui.navigation

import net.nobu0707.busnav.domain.navigation.NavigationCameraState
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import net.nobu0707.busnav.ui.free.*
import net.nobu0707.busnav.ui.traffic.*
import net.nobu0707.busnav.domain.traffic.*
import net.nobu0707.busnav.ui.detour.*
import net.nobu0707.busnav.domain.detour.*
import net.nobu0707.busnav.map.DetourOverlayData
import net.nobu0707.busnav.domain.prescribed.NavigationMode
import net.nobu0707.busnav.domain.navigation.ArrivalState
import androidx.compose.material3.AlertDialog
import android.Manifest
import net.nobu0707.busnav.BuildConfig
import net.nobu0707.busnav.developer.DeveloperConnectionRepository
import net.nobu0707.busnav.map.basemap.BasemapConfig
import net.nobu0707.busnav.ui.settings.developer.DeveloperConnectionScreen
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import net.nobu0707.busnav.location.LocationProvider
import net.nobu0707.busnav.domain.route.ScheduledRouteRepository
import net.nobu0707.busnav.domain.routing.RoutingEngine
import net.nobu0707.busnav.domain.model.GeoPoint
import net.nobu0707.busnav.ui.routeplan.EditorSheetState
import net.nobu0707.busnav.ui.routing.RouteCalculationViewModel
import net.nobu0707.busnav.map.MapScreen
import net.nobu0707.busnav.ui.theme.BusNavTheme
import net.nobu0707.busnav.ui.routeplan.RoutePlanEditorScreen
import net.nobu0707.busnav.ui.routeplan.RoutePlanEditorViewModel

object NavigationTestTags {
    const val NEXT_GUIDANCE = "next_guidance"
    const val MAP = "map_region"
    const val OPERATIONS = "operations"
    const val AUXILIARY = "auxiliary"
    const val CURRENT_LOCATION = "current_location"
    const val ROUTE_OVERVIEW = "route_overview"
    const val PERMISSION = "permission_prompt"
    const val ROUTE_EDIT = "route_edit"
}

private enum class BusNavScreen { NAVIGATION, ROUTE_EDIT, LIBRARY, FREE, DETOUR }

@Composable
fun NavigationRoute(
    locationProvider: LocationProvider,
    routeRepository: ScheduledRouteRepository,
    routingEngine: RoutingEngine,
    prescribedRouteRepository: net.nobu0707.busnav.domain.prescribed.PrescribedRouteRepository? = null,
    connectionRepository: DeveloperConnectionRepository? = null,
    presentationClock: java.time.Clock? = null,
    basemapConfig: BasemapConfig = BasemapConfig.fromBuildValue(BuildConfig.BASEMAP_STYLE_URL, BuildConfig.DEBUG),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val navigationViewModel = viewModel { NavigationViewModel(locationProvider, routeRepository) }
    val stateHolder = navigationViewModel.stateHolder
    val mapPreferences = remember(context) { net.nobu0707.busnav.data.navigationMapPreferenceRepository(context) }
    val orientationFlow = remember(mapPreferences) {
        mapPreferences.orientation.map { it as net.nobu0707.busnav.domain.navigation.NavigationMapOrientation? }
    }
    val orientation by orientationFlow.collectAsState(null)
    val preferenceScope = androidx.compose.runtime.rememberCoroutineScope()
    val uiState by stateHolder.uiState.collectAsState()

    SideEffect { if (!uiState.isNavigationStarted) navigationViewModel.hasNavigationCamera = false }
    val freeHolder = viewModel { FreeNavigationViewModel(routingEngine, stateHolder) }.holder
    val freeState by freeHolder.state.collectAsState()
    val trafficHolder = viewModel { TrafficViewModel(stateHolder) }.holder
    val trafficState by trafficHolder.state.collectAsState()
    var showTraffic by rememberSaveable { mutableStateOf(false) }
    val detourHolder = viewModel { DetourViewModel(routingEngine, stateHolder, trafficHolder) }.holder
    val detourState by detourHolder.state.collectAsState()
    var detourCursorReader by remember { mutableStateOf<(() -> GeoPoint?)?>(null) }
    var freeCursorReader by remember { mutableStateOf<(() -> GeoPoint?)?>(null) }
    val routePlanHolder = viewModel<RoutePlanEditorViewModel>().stateHolder
    val routePlanUiState by routePlanHolder.uiState.collectAsState()
    val calculationHolder = viewModel { RouteCalculationViewModel(routingEngine) }.stateHolder
    val calculationState by calculationHolder.state.collectAsState()
    val library = prescribedRouteRepository?.let { repository ->
        viewModel { net.nobu0707.busnav.ui.prescribed.PrescribedRouteLibraryViewModel(repository) {
            stateHolder.uiState.value.activePrescribedRouteId
        } }.holder
    }
    val libraryState = library?.state?.collectAsState()?.value
    LaunchedEffect(libraryState?.current) {
        libraryState?.current?.let { stateHolder.refreshPrescribedRecord(it) }
    }
    var saveDialog by rememberSaveable { mutableStateOf(false) }
    var saveAsNew by rememberSaveable { mutableStateOf(false) }
    val candidateRoute = calculationHolder.currentCandidate(routePlanUiState.revision)
    var editorViewport by remember { mutableStateOf<Pair<EditorSheetState, Int>?>(null) }
    var cursorReader by remember { mutableStateOf<(() -> GeoPoint?)?>(null) }
    LaunchedEffect(calculationState) {
        routePlanHolder.onCandidateCalculated(candidateRoute)
    }
    var showConnections by rememberSaveable { mutableStateOf(false) }
    var screen by rememberSaveable { mutableStateOf(BusNavScreen.NAVIGATION) }
    var routeMenu by rememberSaveable { mutableStateOf(false) }
    var pendingScreen by rememberSaveable { mutableStateOf<BusNavScreen?>(null) }
    val showActiveNavigation = screen == BusNavScreen.NAVIGATION && !showConnections
    DisposableEffect(context, uiState.keepScreenOn, showActiveNavigation) {
        val window = (context as? android.app.Activity)?.window
        val keepOn = uiState.keepScreenOn && showActiveNavigation
        if (keepOn) window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            if (keepOn) window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(detourState.stage) {
        if (detourState.stage == DetourSessionState.COMPLETED) {
            if (screen == BusNavScreen.DETOUR) screen = BusNavScreen.NAVIGATION
            kotlinx.coroutines.delay(4000)
            detourHolder.clearCompleted()
        } else if (detourState.stage == DetourSessionState.IDLE && screen == BusNavScreen.DETOUR) screen = BusNavScreen.NAVIGATION
    }
    fun considerTraffic(impact: TrafficRouteImpact) {
        if (uiState.activeDetour != null || trafficState.route !== uiState.activeRoute) return
        if (detourHolder.begin(impact.event.detourReason(), impact.detourContext())) {
            showTraffic = false
            screen = BusNavScreen.DETOUR
        }
    }
    fun beginDetour() {
        if (detourHolder.begin()) screen = BusNavScreen.DETOUR
    }
    fun enterScreen(target: BusNavScreen) {
        routeMenu = false
        when (target) {
            BusNavScreen.FREE -> {
                if (!freeHolder.beginSelection()) return
                library?.clearCurrent()
            }
            BusNavScreen.ROUTE_EDIT -> {
                val id = uiState.activePrescribedRouteId
                if (id != null && library != null) {
                    library.edit(id) {
                        calculationHolder.cancel()
                        routePlanHolder.replacePlan(it.routePlan)
                        routePlanHolder.enterEditor(it.route, null)
                        editorViewport = null
                        screen = target
                    }
                    return
                }
                routePlanHolder.enterEditor(uiState.activeRoute, candidateRoute)
                editorViewport = null
            }
            else -> Unit
        }
        screen = target
    }
    fun requestScreen(target: BusNavScreen) {
        routeMenu = false
        if (stateHolder.uiState.value.isNavigationStarted) pendingScreen = target else enterScreen(target)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val permission = when {
            result[Manifest.permission.ACCESS_FINE_LOCATION] == true -> LocationPermissionState.Granted
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true -> LocationPermissionState.Approximate
            else -> LocationPermissionState.Denied
        }
        stateHolder.setPermission(
            permission,
        )
    }

    LaunchedEffect(context) {
        stateHolder.setPermission(
            context.locationPermissionState(),
        )
    }

    LaunchedEffect(routePlanUiState.revision) {
        calculationHolder.onPlanChanged(routePlanUiState.revision)
    }


    DisposableEffect(lifecycleOwner, stateHolder) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> { stateHolder.startLocationUpdates(); trafficHolder.start() }
                Lifecycle.Event.ON_STOP -> { stateHolder.stopLocationUpdates(); trafficHolder.stop() }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) trafficHolder.start()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            stateHolder.stopLocationUpdates()
            trafficHolder.stop()
        }
    }

    fun cancelEditing() {
        calculationHolder.cancel()
        libraryState?.draft?.let { routePlanHolder.replacePlan(libraryState.current?.routePlan ?: it.routePlan) }
        library?.cancelDraft()
        screen = BusNavScreen.NAVIGATION
    }
    BackHandler(enabled = screen != BusNavScreen.NAVIGATION) {
        if (screen == BusNavScreen.DETOUR) { detourHolder.cancelPlanning(); screen = BusNavScreen.NAVIGATION }
        else if (screen == BusNavScreen.FREE) { freeHolder.cancel(); screen = BusNavScreen.NAVIGATION }
        else cancelEditing()
    }

    val navigationActive = screen == BusNavScreen.NAVIGATION && !showConnections && uiState.navigationActive
    val isNight = net.nobu0707.busnav.ui.theme.rememberIsNight(uiState.location?.point, presentationClock)
    var isTunnel by remember { mutableStateOf(false) }
    LaunchedEffect(navigationActive) { if (!navigationActive) isTunnel = false }
    val dark = uiState.location != null && net.nobu0707.busnav.ui.theme.ThemeModeResolver.isDark(
        navigationActive, isNight, isTunnel,
    )
    SideEffect {
        (context as? android.app.Activity)?.window?.let { window ->
            androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    BusNavTheme(darkTheme = dark) {
        if (showTraffic) TrafficPanel(trafficState,
            uiState.navigationActive && uiState.navigationMode == NavigationMode.PRESCRIBED && uiState.activeDetour == null,
            ::considerTraffic, { showTraffic = false }, { TrafficDeveloperControls(trafficHolder.provider) })
        if (routeMenu) AlertDialog(onDismissRequest = { routeMenu = false },
            title = { Text("ルート") },
            text = { Column {
                TextButton(onClick = { requestScreen(BusNavScreen.FREE) }, modifier = Modifier.testTag("open_free")) { Text("現在地からナビ") }
                if (library != null) TextButton(onClick = { requestScreen(BusNavScreen.LIBRARY) }) { Text("所定経路・一覧と保存") }
                TextButton(onClick = { requestScreen(BusNavScreen.ROUTE_EDIT) }) { Text("経路編集") }
                if (uiState.activeRoute != null && !uiState.isNavigationStarted) {
                    uiState.startLocationMessage?.let { Text(it) }
                    if (uiState.startLocationAllowed && uiState.startLocationQuality in listOf(
                            net.nobu0707.busnav.location.LocationQuality.DEGRADED,
                            net.nobu0707.busnav.location.LocationQuality.UNUSABLE))
                        Text("位置精度が低下しています。案内開始後も進路案内を控えめに表示します。")
                    TextButton(onClick = { if (stateHolder.startNavigation()) routeMenu = false }) { Text("案内開始") }
                }
                if (uiState.isNavigationStarted)
                    TextButton(onClick = { freeHolder.endNavigation(); library?.clearCurrent(); routeMenu = false }) { Text("案内終了") }
            } },
            confirmButton = { TextButton(onClick = { routeMenu = false }) { Text("閉じる") } })
        pendingScreen?.let { target ->
            AlertDialog(onDismissRequest = { pendingScreen = null },
                title = { Text("現在の案内を終了しますか？") },
                text = { Text(when (target) {
                    BusNavScreen.FREE -> "現在の案内を終了して現在地からナビを設定します。"
                    BusNavScreen.LIBRARY -> "現在の案内を終了して所定経路を開きます。"
                    else -> "現在の案内を終了して経路を編集します。"
                }) },
                confirmButton = { TextButton(onClick = {
                    freeHolder.endNavigation()
                    pendingScreen = null
                    enterScreen(target)
                }, modifier = Modifier.testTag("session_switch_confirm")) { Text("終了して続ける") } },
                dismissButton = { TextButton(onClick = { pendingScreen = null }) { Text("キャンセル") } })
        }
        when (screen) {
            BusNavScreen.NAVIGATION -> NavigationScreen(
                uiState = uiState,
                hasRoutePlan = routePlanUiState.currentPlan.points.isNotEmpty(),
                trafficState = trafficState, onTraffic = { showTraffic = true }, onConsiderTraffic = ::considerTraffic,
                onOpenLibrary = library?.let { { requestScreen(BusNavScreen.LIBRARY) } },
                onDetour = { beginDetour() },
                onEndDetour = detourHolder::endDetour,
                detourMessage = if (detourState.stage == DetourSessionState.COMPLETED) "所定経路に復帰しました" else detourState.error,
                onFreeRecalculate = { freeHolder.recalculate(); if (freeHolder.state.value.stage != FreeNavigationStage.IDLE) screen = BusNavScreen.FREE },
                onEndNavigation = { freeHolder.endNavigation(); library?.clearCurrent() },
                onLayoutModeChanged = stateHolder::setLayoutMode,
                onRequestPermission = {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        ),
                    )
                },
                onCurrentLocation = stateHolder::onCurrentLocationRequested,
                onRouteOverview = stateHolder::onRouteOverviewRequested,
                onEditRoute = { routeMenu = true },
                mapContent = { modifier ->
                    MapScreen(
                        trafficEvents = trafficState.activeEvents,
                        navigationCamera = NavigationCameraState(
                            active = screen == BusNavScreen.NAVIGATION && uiState.isNavigationStarted,
                            following = uiState.isFollowingLocation,
                            orientation = orientation ?: net.nobu0707.busnav.domain.navigation.NavigationMapOrientation.HEADING_UP,
                            headingDegrees = uiState.navigationHeading.degrees,
                        ),
                        onToggleOrientation = if (orientation == null || showTraffic || showConnections) null else ({ preferenceScope.launch { mapPreferences.toggleOrientation() }; Unit }),
                        initialCamera = if (uiState.isNavigationStarted) navigationViewModel.camera else routePlanHolder.camera?.copy(bearing = 0.0, tilt = 0.0),
                        initialNavigationCamera = navigationViewModel.hasNavigationCamera,
                        onNavigationCameraInitialized = { navigationViewModel.hasNavigationCamera = true },
                        onCameraChanged = {
                            if (uiState.isNavigationStarted) navigationViewModel.camera = it
                            else routePlanHolder.saveCamera(it.copy(bearing = 0.0, tilt = 0.0))
                        },
                        basemapConfig = basemapConfig.withTheme(dark),
                        location = uiState.location.takeUnless { uiState.isNavigationStarted && orientation == null },
                        monitorTunnel = navigationActive,
                        onTunnelChanged = { isTunnel = it },
                        isFollowingLocation = uiState.isFollowingLocation && !showTraffic && !showConnections,
                        recenterRequestId = uiState.recenterRequestId,
                        activeRoute = uiState.prescribedRouteSnapshot ?: uiState.activeRoute,
                        detourOverlay = uiState.activeDetour?.candidate?.let {
                            DetourOverlayData(it.route, selected = it.draft.rejoinTarget, points = it.draft.points)
                        } ?: DetourOverlayData(),
                        routePlan = uiState.freePlan?.destinationOverlay(),
                        routeOverviewRequestId = uiState.routeOverviewRequestId,
                        onMapReady = stateHolder::onMapReady,
                        onMapGesture = stateHolder::onManualMapGesture,
                        onMapError = stateHolder::onMapError,
                        modifier = modifier,
                    )
                },
            )
            BusNavScreen.DETOUR -> DetourScreen(
                state = detourState,
                onCancel = { detourHolder.cancelPlanning(); screen = BusNavScreen.NAVIGATION },
                onTarget = detourHolder::selectTarget, onMapMode = detourHolder::selectMapMode,
                onCursor = { detourCursorReader?.invoke()?.let(detourHolder::setCursor) },
                onRemove = detourHolder::removePoint, onMove = detourHolder::movePoint,
                onCalculate = { detourHolder.calculate() },
                onActivate = { if (detourHolder.activate()) screen = BusNavScreen.NAVIGATION },
                onEdit = detourHolder::edit, cursorReady = detourCursorReader != null && uiState.isMapReady,
                guidanceContent = { NavigationGuidanceCard(uiState, it) },
                mapContent = { modifier ->
                    androidx.compose.runtime.key(detourState.stage == DetourSessionState.PREVIEW) {
                        MapScreen(
                            trafficEvents = trafficState.activeEvents,
                            deviceCompassEnabled = false,
                            location = uiState.location, isFollowingLocation = false, recenterRequestId = 0,
                            activeRoute = uiState.prescribedRouteSnapshot, routeOverviewRequestId = 0,
                            detourOverlay = DetourOverlayData(detourState.candidate?.route ?: uiState.activeDetour?.candidate?.route,
                                detourState.candidates, detourState.target, detourState.points),
                            initialCamera = detourHolder.camera, onCameraChanged = detourHolder::saveCamera,
                            editorCameraRequest = detourState.cameraRequest, editorBottomPadding = 0,
                            onEditorCameraApplied = detourHolder::cameraApplied,
                            selectionMode = when (detourState.mapMode) {
                                DetourMapMode.NONE -> net.nobu0707.busnav.map.MapSelectionMode.NONE
                                DetourMapMode.REJOIN -> net.nobu0707.busnav.map.MapSelectionMode.DETOUR_REJOIN
                                else -> net.nobu0707.busnav.map.MapSelectionMode.DETOUR_POINT
                            },
                            onCursorReader = { detourCursorReader = it }, basemapConfig = basemapConfig.withTheme(false),
                            onMapReady = stateHolder::onMapReady, onMapGesture = {}, onMapError = stateHolder::onMapError,
                            modifier = modifier,
                        )
                    }
                },
            )
            BusNavScreen.FREE -> FreeNavigationScreen(
                state = freeState,
                onCancel = { freeHolder.cancel(); screen = BusNavScreen.NAVIGATION },
                onSetDestination = { freeCursorReader?.invoke()?.let { freeHolder.selectDestination(it) } },
                onCalculate = { freeHolder.calculate() },
                onStart = { if (freeHolder.start()) screen = BusNavScreen.NAVIGATION },
                onChangeDestination = freeHolder::changeDestination,
                onPermission = { permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
                needsPermission = uiState.locationPermissionState != LocationPermissionState.Granted,
                approximatePermission = uiState.locationPermissionState == LocationPermissionState.Approximate,
                startAllowed = uiState.startLocationAllowed,
                startMessage = if (uiState.locationPermissionState == LocationPermissionState.Approximate)
                    "正確な位置情報を許可してください" else uiState.startLocationMessage,
                degraded = uiState.startLocationAllowed && uiState.startLocationQuality in listOf(
                    net.nobu0707.busnav.location.LocationQuality.DEGRADED,
                    net.nobu0707.busnav.location.LocationQuality.UNUSABLE),
                cursorReady = freeCursorReader != null && uiState.isMapReady,
                mapContent = { modifier ->
                    // The preview has different bounds from selection/calculation. Fit a laid-out preview viewport.
                    androidx.compose.runtime.key(freeState.stage == FreeNavigationStage.PREVIEW) {
                        MapScreen(
                            trafficEvents = trafficState.activeEvents,
                            deviceCompassEnabled = !uiState.navigationActive,
                            initialCamera = freeHolder.camera ?: uiState.location?.let { net.nobu0707.busnav.ui.routeplan.EditorCamera(it.point, 14.0) },
                            onCameraChanged = freeHolder::saveCamera,
                            basemapConfig = basemapConfig.withTheme(false),
                            location = uiState.location, isFollowingLocation = false, recenterRequestId = 0,
                            activeRoute = freeState.previewRoute ?: uiState.activeRoute.takeIf { freeState.isRecalculation },
                            routeOverviewRequestId = 0,
                            routePlan = freeState.plan?.destinationOverlay(),
                            editorCameraRequest = freeState.cameraRequest, editorBottomPadding = 0,
                            onEditorCameraApplied = freeHolder::cameraApplied,
                            onCursorReader = { freeCursorReader = it },
                            selectionMode = if (freeState.stage == FreeNavigationStage.SELECTING && !freeState.isRecalculation) net.nobu0707.busnav.map.MapSelectionMode.FREE_DESTINATION else net.nobu0707.busnav.map.MapSelectionMode.NONE,
                            onMapReady = stateHolder::onMapReady, onMapGesture = {}, onMapError = stateHolder::onMapError,
                            modifier = modifier,
                        )
                    }
                },
            )
            BusNavScreen.LIBRARY -> libraryState?.let { ls ->
                net.nobu0707.busnav.ui.prescribed.PrescribedRouteLibraryScreen(
                    ls, uiState.activePrescribedRouteId,
                    onBack = { screen = BusNavScreen.NAVIGATION },
                    onCreate = {
                        library.cancelDraft()
                        calculationHolder.cancel()
                        routePlanHolder.replacePlan(net.nobu0707.busnav.domain.routeplan.RoutePlan(java.util.UUID.randomUUID().toString()))
                        routePlanHolder.enterEditor(null, null)
                        editorViewport = null
                        screen = BusNavScreen.ROUTE_EDIT
                    },
                    onSaveCurrent = { asNew -> library.cancelDraft(); saveAsNew = asNew; saveDialog = true },
                    onEndUse = { stateHolder.clearRoute(); library.clearCurrent() },
                    onOpen = { id -> library.open(id) {
                        stateHolder.openPrescribedRoute(it)
                        routePlanHolder.replacePlan(it.routePlan)
                        screen = BusNavScreen.NAVIGATION
                    } },
                    onNavigate = { id -> library.open(id) {
                        stateHolder.openPrescribedRoute(it)
                        stateHolder.startNavigation()
                        routePlanHolder.replacePlan(it.routePlan)
                        screen = BusNavScreen.NAVIGATION
                    } },
                    onEdit = { id -> library.edit(id) {
                        calculationHolder.cancel()
                        routePlanHolder.replacePlan(it.routePlan)
                        routePlanHolder.enterEditor(it.route, null)
                        editorViewport = null
                        screen = BusNavScreen.ROUTE_EDIT
                    } },
                    onDuplicate = library::duplicate, onRename = library::rename, onDelete = library::delete,
                )
            }
            BusNavScreen.ROUTE_EDIT -> RoutePlanEditorScreen(
                uiState = routePlanUiState,
                onOpenConnections = if (BuildConfig.DEBUG && connectionRepository != null) ({ showConnections = true }) else null,
                onBack = { cancelEditing() },
                onRegisterCursor = { cursorReader?.invoke()?.let(routePlanHolder::registerCursor) },
                onSheetStateChanged = routePlanHolder::setSheetState,
                onSheetHeightChanged = { state, height -> editorViewport = state to height },
                onSelectAddMode = routePlanHolder::selectAddMode,
                onSelectPoint = routePlanHolder::selectPoint,
                onRemovePoint = routePlanHolder::removePoint,
                onMovePoint = routePlanHolder::movePoint,
                onTogglePointType = routePlanHolder::toggleIntermediateType,
                onPlanOverview = routePlanHolder::requestPlanOverview,
                onComplete = {
                    if (libraryState?.draft != null) {
                        saveAsNew = false
                        saveDialog = true
                    } else {
                        calculationHolder.cancel()
                        routePlanHolder.completeEditing()
                        screen = BusNavScreen.NAVIGATION
                    }
                },
                libraryActions = library?.let { {
                    Row {
                        TextButton(onClick = { screen = BusNavScreen.LIBRARY }) { Text("所定経路一覧") }
                        if (libraryState?.draft != null) {
                            TextButton(onClick = { saveAsNew = false; saveDialog = true },
                                enabled = library.canSaveDraft(routePlanUiState.currentPlan)) { Text("上書き保存") }
                            TextButton(onClick = { saveAsNew = true; saveDialog = true },
                                enabled = library.canSaveDraft(routePlanUiState.currentPlan)) { Text("別名保存") }
                        }
                    }
                    libraryState?.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (libraryState?.draft != null && !library.canSaveDraft(routePlanUiState.currentPlan))
                        Text("地点変更後は経路を再計算し、候補を適用してください")
                } },
                calculationState = calculationState,
                onCalculate = {
                    calculationHolder.calculate(routePlanUiState.currentPlan, routePlanUiState.revision,
                        libraryState?.draft?.vehicleProfile ?: libraryState?.current?.vehicleProfile ?: net.nobu0707.busnav.domain.routing.VehicleProfile.DEVELOPMENT_LARGE_BUS)
                },
                onApplyCalculatedRoute = {
                    calculationHolder.currentCandidate(routePlanUiState.revision)?.let { route ->
                        if (library != null && !library.acceptCandidate(routePlanUiState.currentPlan, route,
                            libraryState?.draft?.vehicleProfile ?: libraryState?.current?.vehicleProfile ?: net.nobu0707.busnav.domain.routing.VehicleProfile.DEVELOPMENT_LARGE_BUS)) return@let
                        if (libraryState?.draft != null) {
                            saveAsNew = false
                            saveDialog = true
                        } else {
                            stateHolder.previewEditorCandidate(route)
                            routePlanHolder.completeEditing()
                            screen = BusNavScreen.NAVIGATION
                        }
                    }
                },
                mapContent = { modifier ->
                    MapScreen(
                        trafficEvents = trafficState.activeEvents,
                        initialCamera = routePlanHolder.camera,
                        onCameraChanged = routePlanHolder::saveCamera,
                        basemapConfig = basemapConfig.withTheme(dark),
                        location = uiState.location,
                        isFollowingLocation = false,
                        recenterRequestId = 0,
                        activeRoute = candidateRoute ?: libraryState?.draft?.route ?: uiState.activeRoute,
                        routeOverviewRequestId = 0,
                        editorCameraRequest = routePlanUiState.cameraRequest.takeIf { editorViewport?.first == routePlanUiState.sheetState },
                        editorBottomPadding = editorViewport?.second,
                        onEditorCameraApplied = routePlanHolder::cameraApplied,
                        onCursorReader = { cursorReader = it },
                        routePlan = routePlanUiState.currentPlan.takeIf { candidateRoute == null },
                        planOverviewRequestId = 0,
                        onMapLongPress = routePlanHolder::addPoint,
                        onMapReady = stateHolder::onMapReady,
                        onMapGesture = {},
                        onMapError = stateHolder::onMapError,
                        modifier = modifier,
                    )
                },
            )
        }
        if (saveDialog && library != null && libraryState != null) {
            val source = libraryState.draft ?: libraryState.current
            net.nobu0707.busnav.ui.prescribed.RouteNameDialog(
                if (saveAsNew) "別名で保存" else "上書き保存", source?.name.orEmpty(), source?.description.orEmpty(),
                libraryState.busy, libraryState.error,
                onDismiss = { saveDialog = false; library.dismissError() },
                onSave = { name, description -> library.save(name, description, saveAsNew, routePlanUiState.currentPlan) {
                    stateHolder.openPrescribedRoute(it)
                    routePlanHolder.replacePlan(it.routePlan)
                    saveDialog = false
                    screen = BusNavScreen.NAVIGATION
                } },
            )
        }
        if (BuildConfig.DEBUG && showConnections && connectionRepository != null) {
            Dialog(onDismissRequest = { showConnections = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)) {
                DeveloperConnectionScreen(connectionRepository, onBack = { showConnections = false },
                    navigationActive = uiState.navigationActive)
            }
        }
    }
}

@Composable
fun NavigationScreen(
    uiState: NavigationUiState,
    onLayoutModeChanged: (NavigationLayoutMode) -> Unit,
    onRequestPermission: () -> Unit,
    onCurrentLocation: () -> Unit,
    onRouteOverview: () -> Unit,
    modifier: Modifier = Modifier,
    onEditRoute: () -> Unit = {},
    hasRoutePlan: Boolean = false,
    onOpenLibrary: (() -> Unit)? = null,
    onFreeRecalculate: () -> Unit = {},
    onEndNavigation: () -> Unit = {},
    onDetour: () -> Unit = {},
    onEndDetour: () -> Unit = {},
    detourMessage: String? = null,
    trafficState: TrafficUiState = TrafficUiState(),
    onTraffic: () -> Unit = {},
    onConsiderTraffic: (TrafficRouteImpact) -> Unit = {},
    mapContent: @Composable (Modifier) -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        val layoutMode = resolveNavigationLayout(maxWidth.value, maxHeight.value)
        LaunchedEffect(layoutMode) { onLayoutModeChanged(layoutMode) }

        when (layoutMode) {
            NavigationLayoutMode.PortraitMap -> PortraitNavigationLayout(
                uiState = uiState,
                onRequestPermission = onRequestPermission,
                onCurrentLocation = onCurrentLocation,
                onRouteOverview = onRouteOverview,
                onEditRoute = onEditRoute,
                hasRoutePlan = hasRoutePlan,
                onOpenLibrary = onOpenLibrary,
                onFreeRecalculate = onFreeRecalculate,
                onEndNavigation = onEndNavigation,
                onDetour = onDetour, onEndDetour = onEndDetour, detourMessage = detourMessage,
                trafficState = trafficState, onTraffic = onTraffic, onConsiderTraffic = onConsiderTraffic,
                mapContent = mapContent,
            )
            NavigationLayoutMode.LandscapeThreeColumn -> LandscapeNavigationLayout(
                uiState = uiState,
                onRequestPermission = onRequestPermission,
                onCurrentLocation = onCurrentLocation,
                onRouteOverview = onRouteOverview,
                onEditRoute = onEditRoute,
                hasRoutePlan = hasRoutePlan,
                onOpenLibrary = onOpenLibrary,
                onFreeRecalculate = onFreeRecalculate,
                onEndNavigation = onEndNavigation,
                onDetour = onDetour, onEndDetour = onEndDetour, detourMessage = detourMessage,
                trafficState = trafficState, onTraffic = onTraffic, onConsiderTraffic = onConsiderTraffic,
                mapContent = mapContent,
            )
        }
    }
}

@Composable
private fun PortraitNavigationLayout(
    uiState: NavigationUiState,
    onRequestPermission: () -> Unit,
    onCurrentLocation: () -> Unit,
    onRouteOverview: () -> Unit,
    onEditRoute: () -> Unit,
    hasRoutePlan: Boolean,
    onOpenLibrary: (() -> Unit)?,
    onFreeRecalculate: () -> Unit,
    onEndNavigation: () -> Unit,
    onDetour: () -> Unit,
    onEndDetour: () -> Unit,
    detourMessage: String?,
    trafficState: TrafficUiState,
    onTraffic: () -> Unit,
    onConsiderTraffic: (TrafficRouteImpact) -> Unit,
    mapContent: @Composable (Modifier) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DeviationBanner(uiState.deviation)
        DetourNavigationActions(uiState, onDetour, onEndDetour, detourMessage)
        FreeNavigationActions(uiState, onFreeRecalculate, onEndNavigation)
        TrafficAlert(trafficState, uiState.navigationActive && uiState.navigationMode == NavigationMode.PRESCRIBED && uiState.activeDetour == null, onConsiderTraffic, onTraffic)
        NavigationGuidanceCard(uiState, Modifier.fillMaxWidth().heightIn(max = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.35f).dp))
        MapArea(
            uiState = uiState,
            onRequestPermission = onRequestPermission,
            onCurrentLocation = onCurrentLocation,
            onRouteOverview = onRouteOverview,
            modifier = Modifier.fillMaxWidth().weight(1f).testTag(NavigationTestTags.MAP),
            mapContent = mapContent,
        )
        PlaceholderPanel(
            title = if (uiState.navigationMode == NavigationMode.FREE) "現在地からナビ" else if (onOpenLibrary != null) "所定経路 • 一覧・保存" else "運行情報",
            detail = operationsSummary(uiState, hasRoutePlan),
            modifier = Modifier.fillMaxWidth().height(70.dp).testTag(NavigationTestTags.OPERATIONS)
                .then(if (onOpenLibrary != null) Modifier.clickable(onClick = onOpenLibrary).semantics { contentDescription = "所定経路一覧を開く" } else Modifier),
        )
        AuxiliaryControls(
            onEditRoute = onEditRoute,
            onTraffic = onTraffic,
            onDetour = onDetour, detourEnabled = uiState.navigationActive && uiState.navigationMode == NavigationMode.PRESCRIBED,
            modifier = Modifier.fillMaxWidth().height(72.dp).testTag(NavigationTestTags.AUXILIARY),
        )
    }
}

@Composable
private fun LandscapeNavigationLayout(
    uiState: NavigationUiState,
    onRequestPermission: () -> Unit,
    onCurrentLocation: () -> Unit,
    onRouteOverview: () -> Unit,
    onEditRoute: () -> Unit,
    hasRoutePlan: Boolean,
    onOpenLibrary: (() -> Unit)?,
    onFreeRecalculate: () -> Unit,
    onEndNavigation: () -> Unit,
    onDetour: () -> Unit,
    onEndDetour: () -> Unit,
    detourMessage: String?,
    trafficState: TrafficUiState,
    onTraffic: () -> Unit,
    onConsiderTraffic: (TrafficRouteImpact) -> Unit,
    mapContent: @Composable (Modifier) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxHeight().weight(0.24f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DeviationBanner(uiState.deviation)
            DetourNavigationActions(uiState, onDetour, onEndDetour, detourMessage)
        FreeNavigationActions(uiState, onFreeRecalculate, onEndNavigation)
            TrafficAlert(trafficState, uiState.navigationActive && uiState.navigationMode == NavigationMode.PRESCRIBED && uiState.activeDetour == null, onConsiderTraffic, onTraffic)
            NavigationGuidanceCard(uiState, Modifier.fillMaxWidth().weight(2f))
            PlaceholderPanel(
                title = if (uiState.navigationMode == NavigationMode.FREE) "現在地からナビ" else if (onOpenLibrary != null) "所定経路 • 一覧・保存" else "運行情報",
                detail = operationsSummary(uiState, hasRoutePlan),
                modifier = Modifier.fillMaxWidth().height(80.dp).testTag(NavigationTestTags.OPERATIONS)
                .then(if (onOpenLibrary != null) Modifier.clickable(onClick = onOpenLibrary).semantics { contentDescription = "所定経路一覧を開く" } else Modifier),
            )
        }
        MapArea(
            uiState = uiState,
            onRequestPermission = onRequestPermission,
            onCurrentLocation = onCurrentLocation,
            onRouteOverview = onRouteOverview,
            modifier = Modifier.fillMaxHeight().weight(0.58f).testTag(NavigationTestTags.MAP),
            mapContent = mapContent,
        )
        AuxiliaryControls(
            onEditRoute = onEditRoute,
            onTraffic = onTraffic,
            onDetour = onDetour, detourEnabled = uiState.navigationActive && uiState.navigationMode == NavigationMode.PRESCRIBED,
            vertical = true,
            modifier = Modifier.fillMaxHeight().weight(0.18f).testTag(NavigationTestTags.AUXILIARY),
        )
    }
}

@Composable
private fun MapArea(
    uiState: NavigationUiState,
    onRequestPermission: () -> Unit,
    onCurrentLocation: () -> Unit,
    onRouteOverview: () -> Unit,
    modifier: Modifier,
    mapContent: @Composable (Modifier) -> Unit,
) {
    Box(modifier = modifier, contentAlignment = Alignment.BottomEnd) {
        val bottomOcclusion = remember { mutableIntStateOf(0) }
        CompositionLocalProvider(net.nobu0707.busnav.map.LocalNavigationMapBottomOcclusionPx provides bottomOcclusion.intValue) {
            mapContent(Modifier.fillMaxSize())
        }

        if (uiState.locationPermissionState != LocationPermissionState.Granted) {
            PermissionPrompt(
                denied = uiState.locationPermissionState == LocationPermissionState.Denied,
                approximate = uiState.locationPermissionState == LocationPermissionState.Approximate,
                onRequestPermission = onRequestPermission,
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
            )
        }

        Column(
            modifier = Modifier.onSizeChanged { bottomOcclusion.intValue = it.height }.padding(12.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onRouteOverview,
                enabled = uiState.activeRoute != null,
                modifier = Modifier
                    .testTag(NavigationTestTags.ROUTE_OVERVIEW)
                    .semantics { contentDescription = uiState.routeLabel + "全体を表示" },
            ) {
                Text("経路全体")
            }
            Button(
                onClick = onCurrentLocation,
                enabled = uiState.location != null,
                modifier = Modifier
                    .testTag(NavigationTestTags.CURRENT_LOCATION)
                    .semantics { contentDescription = "現在地へ戻る" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(if (uiState.isFollowingLocation) "現在地・追従中" else "現在地へ戻る")
            }
        }
    }
}

@Composable
private fun PermissionPrompt(
    denied: Boolean,
    approximate: Boolean,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.testTag(NavigationTestTags.PERMISSION),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)),
    ) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (approximate) "正確な位置情報を許可すると案内を開始できます。"
                else if (denied) "位置情報が許可されていません。地図はそのまま閲覧できます。"
                else "自車位置と追従表示のため、位置情報を使用します。",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onRequestPermission) {
                Text(if (approximate) "正確な位置情報を許可" else if (denied) "位置情報を再要求" else "位置情報を許可")
            }
        }
    }
}

@Composable
private fun PlaceholderPanel(title: String, detail: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun AuxiliaryControls(
    onEditRoute: () -> Unit,
    modifier: Modifier = Modifier,
    vertical: Boolean = false,
    onDetour: () -> Unit = {},
    detourEnabled: Boolean = false,
    onTraffic: () -> Unit = {},
) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        if (vertical) {
            Column(Modifier.fillMaxSize().padding(4.dp), verticalArrangement = Arrangement.SpaceEvenly) {
                BottomControl("ルート", true, onEditRoute, Modifier.fillMaxWidth())
                BottomLabelLayout.secondaryLabels.forEach { BottomControl(it, it == "規制" || it == "迂回" && detourEnabled, if (it == "規制") onTraffic else if (it == "迂回") onDetour else ({}), Modifier.fillMaxWidth()) }
            }
        } else {
            Row(Modifier.fillMaxSize().padding(4.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                BottomControl("ルート", true, onEditRoute, Modifier.weight(1.5f))
                BottomLabelLayout.secondaryLabels.forEach { BottomControl(it, it == "規制" || it == "迂回" && detourEnabled, if (it == "規制") onTraffic else if (it == "迂回") onDetour else ({}), Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun BottomControl(label: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    OutlinedButton(
        onClick = onClick, enabled = enabled,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp, vertical = 8.dp),
        modifier = modifier.heightIn(min = 48.dp)
            .testTag(if (label == "ルート") NavigationTestTags.ROUTE_EDIT else "bottom_$label")
            .semantics { if (label == "ルート") contentDescription = "ルートメニューを開く" },
    ) {
        Text(label, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = BottomLabelLayout.maxLines, softWrap = false,
            style = MaterialTheme.typography.labelMedium)
    }
}
private fun locationSummary(state: NavigationUiState): String = when {
    state.location != null && state.isFollowingLocation -> "現在地を追従中"
    state.location != null -> "地図操作により追従を一時停止"
    state.locationPermissionState == LocationPermissionState.Granted -> "現在地を取得中"
    else -> "位置情報なしでも地図を閲覧できます"
}

internal fun operationsSummary(state: NavigationUiState, hasRoutePlan: Boolean = false): String {
    val route = when {
        state.isRouteLoading -> "経路：読み込み中"
        state.routeError != null -> "経路：読込失敗"
        state.activeRoute != null -> "${state.routeLabel}：${if (state.navigationMode == NavigationMode.FREE) state.freePlan?.destinationName ?: "目的地まで" else state.activePrescribedRouteName ?: state.activeRoute.name}"
        else -> "経路：未選択"
    }
    val location = state.locationError ?: state.startLocationMessage ?: locationSummary(state)
    val plan = if (hasRoutePlan) "・編集プランあり" else ""
    return "$route$plan\n$location"
}

private fun Context.locationPermissionState(): LocationPermissionState = when {
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED -> LocationPermissionState.Granted
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED -> LocationPermissionState.Approximate
    else -> LocationPermissionState.Requestable
}

@Preview(widthDp = 412, heightDp = 915, showBackground = true)
@Composable
private fun PortraitPreview() = BusNavTheme {
    NavigationScreen(
        uiState = NavigationUiState(locationPermissionState = LocationPermissionState.Requestable),
        onLayoutModeChanged = {},
        onRequestPermission = {},
        onCurrentLocation = {},
        onRouteOverview = {},
        mapContent = { modifier -> PreviewMap(modifier) },
    )
}

@Preview(widthDp = 915, heightDp = 412, showBackground = true)
@Composable
private fun LandscapePreview() = BusNavTheme {
    NavigationScreen(
        uiState = NavigationUiState(locationPermissionState = LocationPermissionState.Requestable),
        onLayoutModeChanged = {},
        onRequestPermission = {},
        onCurrentLocation = {},
        onRouteOverview = {},
        mapContent = { modifier -> PreviewMap(modifier) },
    )
}

@Composable
private fun PreviewMap(modifier: Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        Text("MapLibre 地図")
    }
}

@Composable
private fun DetourNavigationActions(state: NavigationUiState, begin: () -> Unit, end: () -> Unit, message: String?) {
    if (state.navigationMode != NavigationMode.PRESCRIBED || !state.navigationActive) return
    Column {
        message?.let { Text(it, Modifier.testTag("detour_message"), style = MaterialTheme.typography.bodySmall) }
        if (state.activeDetour != null) {
            Text("迂回案内中・水色：所定経路 / 太い紫線：迂回経路", Modifier.testTag("detour_active"), style = MaterialTheme.typography.bodySmall)
            if (state.rejoin.state == RejoinState.CANDIDATE) Text("所定経路への復帰を確認中", style = MaterialTheme.typography.bodySmall)
            Row {
                TextButton(onClick = begin, modifier = Modifier.testTag("detour_replan")) { Text("迂回を再設定") }
                TextButton(onClick = end, modifier = Modifier.testTag("detour_end")) { Text("迂回案内を終了") }
            }
        } else if (state.deviationSnapshot.state == net.nobu0707.busnav.domain.navigation.RouteDeviationState.OFF_ROUTE) {
            TextButton(onClick = begin, modifier = Modifier.testTag("detour_off_route")) { Text("迂回を検討") }
        }
    }
}

@Composable
private fun FreeNavigationActions(state: NavigationUiState, recalculate: () -> Unit, end: () -> Unit) {
    if (!state.navigationActive || state.navigationMode != NavigationMode.FREE) return
    Column {
        if (state.arrival.state == ArrivalState.ARRIVED) Text("目的地周辺です", Modifier.testTag("free_arrived"))
        Row {
            if (state.arrival.state != ArrivalState.ARRIVED)
                TextButton(onClick = recalculate, modifier = Modifier.testTag("free_recalculate")) { Text("現在地から再計算") }
            TextButton(onClick = end, modifier = Modifier.testTag("free_end")) { Text("案内終了") }
        }
    }
}
