package net.nobu0707.busnav.ui.navigation

import android.Manifest
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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

private enum class BusNavScreen { NAVIGATION, ROUTE_EDIT }

@Composable
fun NavigationRoute(
    locationProvider: LocationProvider,
    routeRepository: ScheduledRouteRepository,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val stateHolder = remember(locationProvider, routeRepository, scope) {
        NavigationStateHolder(locationProvider, routeRepository, scope)
    }
    val uiState by stateHolder.uiState.collectAsState()
    val routePlanHolder = viewModel<RoutePlanEditorViewModel>().stateHolder
    val routePlanUiState by routePlanHolder.uiState.collectAsState()
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

    BackHandler(enabled = screen == BusNavScreen.ROUTE_EDIT) { screen = BusNavScreen.NAVIGATION }

    when (screen) {
        BusNavScreen.NAVIGATION -> NavigationScreen(
            uiState = uiState,
            hasRoutePlan = routePlanUiState.currentPlan.points.isNotEmpty(),
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
            onEditRoute = { screen = BusNavScreen.ROUTE_EDIT },
            mapContent = { modifier ->
                MapScreen(
                    location = uiState.location,
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
        BusNavScreen.ROUTE_EDIT -> RoutePlanEditorScreen(
            uiState = routePlanUiState,
            onBack = { screen = BusNavScreen.NAVIGATION },
            onSelectAddMode = routePlanHolder::selectAddMode,
            onSelectPoint = routePlanHolder::selectPoint,
            onRemovePoint = routePlanHolder::removePoint,
            onMovePoint = routePlanHolder::movePoint,
            onTogglePointType = routePlanHolder::toggleIntermediateType,
            onPlanOverview = routePlanHolder::requestPlanOverview,
            onComplete = {
                routePlanHolder.completeEditing()
                screen = BusNavScreen.NAVIGATION
            },
            mapContent = { modifier ->
                MapScreen(
                    location = uiState.location,
                    isFollowingLocation = false,
                    recenterRequestId = 0,
                    activeRoute = uiState.activeRoute,
                    routeOverviewRequestId = 0,
                    routePlan = routePlanUiState.currentPlan,
                    planOverviewRequestId = routePlanUiState.planOverviewRequestId,
                    onMapLongPress = routePlanHolder::addPoint,
                    onMapReady = stateHolder::onMapReady,
                    onMapGesture = {},
                    onMapError = stateHolder::onMapError,
                    modifier = modifier,
                )
            },
        )
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
                mapContent = mapContent,
            )
            NavigationLayoutMode.LandscapeThreeColumn -> LandscapeNavigationLayout(
                uiState = uiState,
                onRequestPermission = onRequestPermission,
                onCurrentLocation = onCurrentLocation,
                onRouteOverview = onRouteOverview,
                onEditRoute = onEditRoute,
                hasRoutePlan = hasRoutePlan,
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
    mapContent: @Composable (Modifier) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PlaceholderPanel(
            title = "次の案内",
            detail = "所定経路の案内は次フェーズで追加",
            modifier = Modifier.fillMaxWidth().height(72.dp).testTag(NavigationTestTags.NEXT_GUIDANCE),
        )
        MapArea(
            uiState = uiState,
            onRequestPermission = onRequestPermission,
            onCurrentLocation = onCurrentLocation,
            onRouteOverview = onRouteOverview,
            modifier = Modifier.fillMaxWidth().weight(1f).testTag(NavigationTestTags.MAP),
            mapContent = mapContent,
        )
        PlaceholderPanel(
            title = "運行情報",
            detail = operationsSummary(uiState, hasRoutePlan),
            modifier = Modifier.fillMaxWidth().height(70.dp).testTag(NavigationTestTags.OPERATIONS),
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
            PlaceholderPanel(
                title = "次の案内",
                detail = "案内待機中",
                modifier = Modifier.fillMaxWidth().weight(1f).testTag(NavigationTestTags.NEXT_GUIDANCE),
            )
            PlaceholderPanel(
                title = "運行情報",
                detail = operationsSummary(uiState, hasRoutePlan),
                modifier = Modifier.fillMaxWidth().weight(1f).testTag(NavigationTestTags.OPERATIONS),
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
private fun AuxiliaryControls(
    onEditRoute: () -> Unit,
    modifier: Modifier = Modifier,
    vertical: Boolean = false,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        if (vertical) {
            Column(
                modifier = Modifier.fillMaxSize().padding(8.dp),
                verticalArrangement = Arrangement.SpaceEvenly,
            ) {
                RouteEditControl(onEditRoute)
                FutureControl("迂回")
                FutureControl("規制")
                FutureControl("音声")
                FutureControl("表示")
            }
        } else {
            Row(
                modifier = Modifier.fillMaxSize().padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RouteEditControl(onEditRoute)
                FutureControl("迂回")
                FutureControl("規制")
                FutureControl("音声")
                FutureControl("表示")
            }
        }
    }
}

@Composable
private fun RouteEditControl(onEditRoute: () -> Unit) {
    OutlinedButton(
        onClick = onEditRoute,
        modifier = Modifier
            .width(88.dp)
            .testTag(NavigationTestTags.ROUTE_EDIT)
            .semantics { contentDescription = "ルート編集画面を開く" },
    ) { Text("ルート編集") }
}

@Composable
private fun FutureControl(label: String) {
    OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.width(72.dp)) {
        Text(label)
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
        state.activeRoute != null -> "所定経路：${state.activeRoute.name}"
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
