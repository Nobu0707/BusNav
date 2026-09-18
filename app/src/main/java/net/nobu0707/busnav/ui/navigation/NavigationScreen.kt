package net.nobu0707.busnav.ui.navigation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import net.nobu0707.busnav.location.LocationProvider
import net.nobu0707.busnav.map.MapScreen
import net.nobu0707.busnav.ui.theme.BusNavTheme

object NavigationTestTags {
    const val NEXT_GUIDANCE = "next_guidance"
    const val MAP = "map_region"
    const val OPERATIONS = "operations"
    const val AUXILIARY = "auxiliary"
    const val CURRENT_LOCATION = "current_location"
    const val PERMISSION = "permission_prompt"
}

@Composable
fun NavigationRoute(locationProvider: LocationProvider) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val stateHolder = remember(locationProvider, scope) { NavigationStateHolder(locationProvider, scope) }
    val uiState by stateHolder.uiState.collectAsState()

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

    NavigationScreen(
        uiState = uiState,
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
        mapContent = { modifier ->
            MapScreen(
                location = uiState.location,
                isFollowingLocation = uiState.isFollowingLocation,
                recenterRequestId = uiState.recenterRequestId,
                onMapReady = stateHolder::onMapReady,
                onMapGesture = stateHolder::onManualMapGesture,
                onMapError = stateHolder::onMapError,
                modifier = modifier,
            )
        },
    )
}

@Composable
fun NavigationScreen(
    uiState: NavigationUiState,
    onLayoutModeChanged: (NavigationLayoutMode) -> Unit,
    onRequestPermission: () -> Unit,
    onCurrentLocation: () -> Unit,
    modifier: Modifier = Modifier,
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
                mapContent = mapContent,
            )
            NavigationLayoutMode.LandscapeThreeColumn -> LandscapeNavigationLayout(
                uiState = uiState,
                onRequestPermission = onRequestPermission,
                onCurrentLocation = onCurrentLocation,
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
            modifier = Modifier.fillMaxWidth().weight(1f).testTag(NavigationTestTags.MAP),
            mapContent = mapContent,
        )
        PlaceholderPanel(
            title = "運行情報",
            detail = uiState.locationError ?: locationSummary(uiState),
            modifier = Modifier.fillMaxWidth().height(62.dp).testTag(NavigationTestTags.OPERATIONS),
        )
        AuxiliaryControls(
            modifier = Modifier.fillMaxWidth().height(72.dp).testTag(NavigationTestTags.AUXILIARY),
        )
    }
}

@Composable
private fun LandscapeNavigationLayout(
    uiState: NavigationUiState,
    onRequestPermission: () -> Unit,
    onCurrentLocation: () -> Unit,
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
                detail = uiState.locationError ?: locationSummary(uiState),
                modifier = Modifier.fillMaxWidth().weight(1f).testTag(NavigationTestTags.OPERATIONS),
            )
        }
        MapArea(
            uiState = uiState,
            onRequestPermission = onRequestPermission,
            onCurrentLocation = onCurrentLocation,
            modifier = Modifier.fillMaxHeight().weight(0.58f).testTag(NavigationTestTags.MAP),
            mapContent = mapContent,
        )
        AuxiliaryControls(
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

        Button(
            onClick = onCurrentLocation,
            enabled = uiState.location != null,
            modifier = Modifier.padding(12.dp).testTag(NavigationTestTags.CURRENT_LOCATION),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text(if (uiState.isFollowingLocation) "現在地・追従中" else "現在地へ戻る")
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
private fun AuxiliaryControls(modifier: Modifier = Modifier, vertical: Boolean = false) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        if (vertical) {
            Column(
                modifier = Modifier.fillMaxSize().padding(8.dp),
                verticalArrangement = Arrangement.SpaceEvenly,
            ) {
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
                FutureControl("迂回")
                FutureControl("規制")
                FutureControl("音声")
                FutureControl("表示")
            }
        }
    }
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
        mapContent = { modifier -> PreviewMap(modifier) },
    )
}

@Composable
private fun PreviewMap(modifier: Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        Text("MapLibre 地図")
    }
}
