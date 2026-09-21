package net.nobu0707.busnav.ui.navigation

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

private enum class BusNavScreen { NAVIGATION, ROUTE_EDIT, LIBRARY }

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
    val stateHolder = viewModel { NavigationViewModel(locationProvider, routeRepository) }.stateHolder
    val uiState by stateHolder.uiState.collectAsState()
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
    LaunchedEffect(libraryState?.current?.name) {
        libraryState?.current?.let { stateHolder.refreshPrescribedName(it.id, it.name) }
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

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        stateHolder.setPermission(
            if (granted) LocationPermissionState.Granted else LocationPermissionState.Denied,
        )
    }

    LaunchedEffect(context) {
        stateHolder.setPermission(
            if (context.hasLocationPermission()) {
                LocationPermissionState.Granted
            } else {
                LocationPermissionState.Requestable
            },
        )
    }

    LaunchedEffect(routePlanUiState.revision) {
        calculationHolder.onPlanChanged(routePlanUiState.revision)
    }


    DisposableEffect(lifecycleOwner, stateHolder) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> stateHolder.startLocationUpdates()
                Lifecycle.Event.ON_STOP -> stateHolder.stopLocationUpdates()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            stateHolder.stopLocationUpdates()
        }
    }

    fun cancelEditing() {
        calculationHolder.cancel()
        libraryState?.draft?.let { routePlanHolder.replacePlan(libraryState.current?.routePlan ?: it.routePlan) }
        library?.cancelDraft()
        screen = BusNavScreen.NAVIGATION
    }
    BackHandler(enabled = screen != BusNavScreen.NAVIGATION) { cancelEditing() }

    // Guidance starts when an applicable route is adopted; viewing/editing a plan is inactive.
    val navigationActive = screen == BusNavScreen.NAVIGATION && !showConnections &&
        (uiState.activePrescribedRouteId == null || uiState.isNavigationStarted) &&
        !uiState.activeRoute?.guidance?.maneuvers.isNullOrEmpty()
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
        when (screen) {
            BusNavScreen.NAVIGATION -> NavigationScreen(
                uiState = uiState,
                hasRoutePlan = routePlanUiState.currentPlan.points.isNotEmpty(),
                onOpenLibrary = library?.let { { screen = BusNavScreen.LIBRARY } },
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
                onEditRoute = {
                    val id = uiState.activePrescribedRouteId
                    if (id != null && library != null) {
                        library.edit(id) {
                            calculationHolder.cancel()
                            routePlanHolder.replacePlan(it.routePlan)
                            routePlanHolder.enterEditor(it.route, null)
                            editorViewport = null
                            screen = BusNavScreen.ROUTE_EDIT
                        }
                    } else {
                        routePlanHolder.enterEditor(uiState.activeRoute, candidateRoute)
                        editorViewport = null
                        screen = BusNavScreen.ROUTE_EDIT
                    }
                },
                mapContent = { modifier ->
                    MapScreen(
                        initialCamera = routePlanHolder.camera,
                        onCameraChanged = routePlanHolder::saveCamera,
                        basemapConfig = basemapConfig.withTheme(dark),
                        location = uiState.location,
                        monitorTunnel = navigationActive,
                        onTunnelChanged = { isTunnel = it },
                        isFollowingLocation = uiState.isFollowingLocation,
                        recenterRequestId = uiState.recenterRequestId,
                        activeRoute = uiState.activeRoute,
                        routeOverviewRequestId = uiState.routeOverviewRequestId,
                        onMapReady = stateHolder::onMapReady,
                        onMapGesture = stateHolder::onManualMapGesture,
                        onMapError = stateHolder::onMapError,
                        modifier = modifier,
                    )
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
                            stateHolder.applyCalculatedRoute(route)
                            routePlanHolder.completeEditing()
                            screen = BusNavScreen.NAVIGATION
                        }
                    }
                },
                mapContent = { modifier ->
                    MapScreen(
                        initialCamera = routePlanHolder.camera,
                        onCameraChanged = routePlanHolder::saveCamera,
                        basemapConfig = basemapConfig.withTheme(dark),
                        location = uiState.location,
                        isFollowingLocation = false,
                        recenterRequestId = 0,
                        activeRoute = candidateRoute ?: libraryState?.draft?.route ?: uiState.activeRoute,
                        routeOverviewRequestId = 0,
                        editorCameraRequest = routePlanUiState.cameraRequest.takeIf { editorViewport?.first == routePlanUiState.sheetState },
                        editorBottomPadding = editorViewport?.second?.plus(with(androidx.compose.ui.platform.LocalDensity.current) { 60.dp.roundToPx() }),
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
                DeveloperConnectionScreen(connectionRepository, onBack = { showConnections = false })
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
    mapContent: @Composable (Modifier) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DeviationBanner(uiState.deviation)
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
            title = if (onOpenLibrary != null) "所定経路 • 一覧・保存" else "運行情報",
            detail = operationsSummary(uiState, hasRoutePlan),
            modifier = Modifier.fillMaxWidth().height(70.dp).testTag(NavigationTestTags.OPERATIONS)
                .then(if (onOpenLibrary != null) Modifier.clickable(onClick = onOpenLibrary).semantics { contentDescription = "所定経路一覧を開く" } else Modifier),
        )
        AuxiliaryControls(
            onEditRoute = onEditRoute,
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
            NavigationGuidanceCard(uiState, Modifier.fillMaxWidth().weight(2f))
            PlaceholderPanel(
                title = if (onOpenLibrary != null) "所定経路 • 一覧・保存" else "運行情報",
                detail = operationsSummary(uiState, hasRoutePlan),
                modifier = Modifier.fillMaxWidth().weight(1f).testTag(NavigationTestTags.OPERATIONS)
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
        mapContent(Modifier.fillMaxSize())

        if (uiState.locationPermissionState != LocationPermissionState.Granted) {
            PermissionPrompt(
                denied = uiState.locationPermissionState == LocationPermissionState.Denied,
                onRequestPermission = onRequestPermission,
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
            )
        }

        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onRouteOverview,
                enabled = uiState.activeRoute != null,
                modifier = Modifier
                    .testTag(NavigationTestTags.ROUTE_OVERVIEW)
                    .semantics { contentDescription = "所定経路全体を表示" },
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
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.testTag(NavigationTestTags.PERMISSION),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)),
    ) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (denied) "位置情報が許可されていません。地図はそのまま閲覧できます。"
                else "自車位置と追従表示のため、位置情報を使用します。",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onRequestPermission) {
                Text(if (denied) "位置情報を再要求" else "位置情報を許可")
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
) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        if (vertical) {
            Column(Modifier.fillMaxSize().padding(4.dp), verticalArrangement = Arrangement.SpaceEvenly) {
                BottomControl("ルート", true, onEditRoute, Modifier.fillMaxWidth())
                BottomLabelLayout.secondaryLabels.forEach { BottomControl(it, false, {}, Modifier.fillMaxWidth()) }
            }
        } else {
            Row(Modifier.fillMaxSize().padding(4.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                BottomControl("ルート", true, onEditRoute, Modifier.weight(1.5f))
                BottomLabelLayout.secondaryLabels.forEach { BottomControl(it, false, {}, Modifier.weight(1f)) }
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
            .testTag(if (enabled) NavigationTestTags.ROUTE_EDIT else "bottom_$label")
            .semantics { if (enabled) contentDescription = "ルート編集画面を開く" },
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

private fun operationsSummary(state: NavigationUiState, hasRoutePlan: Boolean = false): String {
    val route = when {
        state.isRouteLoading -> "所定経路：読み込み中"
        state.routeError != null -> "所定経路：読込失敗"
        state.activeRoute != null -> "所定経路：${state.activePrescribedRouteName ?: state.activeRoute.name}"
        else -> "所定経路：未選択"
    }
    val location = state.locationError ?: locationSummary(state)
    val plan = if (hasRoutePlan) "・編集プランあり" else ""
    return "$route$plan\n$location"
}

private fun Context.hasLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

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
