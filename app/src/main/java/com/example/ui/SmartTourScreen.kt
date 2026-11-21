package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.launch
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.OptimizationService
import com.example.data.StopEntity
import android.content.Intent
import android.net.Uri

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartTourScreen(
    viewModel: SmartTourViewModel = viewModel()
) {
    val stops by viewModel.stopsFlow.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val savedAddresses by viewModel.savedAddressesFlow.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val streetRoutePoints by viewModel.streetRoutePoints.collectAsState()
    val listState = rememberLazyListState()
    val isFabExpanded by remember {
        derivedStateOf {
            val firstVisibleItemIndex = listState.firstVisibleItemIndex
            val firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset
            val isAtTop = firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0

            val layoutInfo = listState.layoutInfo
            val visibleItemsInfo = layoutInfo.visibleItemsInfo
            val totalItemsCount = layoutInfo.totalItemsCount
            val lastVisibleItem = visibleItemsInfo.lastOrNull()
            val isAtBottom = if (totalItemsCount == 0) {
                true
            } else {
                lastVisibleItem != null && lastVisibleItem.index == totalItemsCount - 1
            }

            isAtTop || isAtBottom
        }
    }

    var showAddAddressDialog by remember { mutableStateOf(false) }
    var showConfigureHubDialog by remember { mutableStateOf(false) }
    var editingStop by remember { mutableStateOf<StopEntity?>(null) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(320.dp),
                drawerContainerColor = MaterialTheme.colorScheme.background
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "RouteFlow Pro",
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // THÈME SECTION
                    Text(
                        text = "AMBIANCE & THÈME",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            Triple(null, "Auto", Icons.Default.Settings),
                            Triple(false, "Clair", Icons.Default.Star),
                            Triple(true, "Sombre", Icons.Default.Home)
                        ).forEach { (mode, label, icon) ->
                            val isSelected = themeMode == mode
                            val bg = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                            val tc = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(bg)
                                    .clickable { viewModel.setThemeMode(mode) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = tc,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = label,
                                        color = tc,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // CONFIGURATION DÉPÔT CENTRAL
                    Text(
                        text = "DÉPÔT CENTRAL (DÉPART & ARRIVÉE)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = viewModel.hubAddress,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Coordonnées: ${String.format("%.4f", viewModel.hubX)}, ${String.format("%.4f", viewModel.hubY)}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { showConfigureHubDialog = true },
                                modifier = Modifier.fillMaxWidth().height(36.dp),
                                contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Changer de dépôt", fontSize = 12.sp, color = Color.White)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // ADRESSES ENREGISTRÉES SECTION
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ADRESSES FAVORITES",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )
                        IconButton(
                            onClick = { showAddAddressDialog = true },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddCircle,
                                contentDescription = "Enregistrer une adresse",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (savedAddresses.isEmpty()) {
                        Text(
                            text = "Aucune adresse favorite enregistrée.",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(savedAddresses) { saved ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            // Click to instantly add as stop in current active route!
                                            viewModel.addStop(
                                                address = saved.address,
                                                contactName = saved.label,
                                                hasTimeSlot = false,
                                                timeSlotStart = "08:00",
                                                timeSlotEnd = "18:00",
                                                durationMinutes = 15,
                                                notes = "Adresse favorite"
                                            )
                                            coroutineScope.launch { drawerState.close() }
                                        },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = saved.label,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = saved.address,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        IconButton(
                                            onClick = { viewModel.deleteSavedAddress(saved) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Effacer",
                                                tint = Color.Red.copy(alpha = 0.6f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "RouteFlow Pro v2.1",
                        fontSize = 10.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    drawerState.open()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Menu",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    title = {
                        Text(
                            text = "RouteFlow Pro",
                            fontWeight = FontWeight.Medium,
                            fontSize = 20.sp,
                            letterSpacing = (-0.5).sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.clearAllStops() },
                            modifier = Modifier.testTag("clear_all_button")
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Effacer tout",
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            floatingActionButton = {
                if (uiState.selectedStop == null) {
                    ExtendedFloatingActionButton(
                        onClick = { viewModel.toggleAddingStop(true) },
                        icon = { Icon(Icons.Default.Add, contentDescription = "Ajouter un arrêt") },
                        text = { Text("Ajouter un arrêt", fontWeight = FontWeight.Medium) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        expanded = isFabExpanded,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .navigationBarsPadding()
                            .testTag("add_stop_fab")
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Main unified scroll list
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .testTag("stops_lazy_list"),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    item {
                        // Interactive Map
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                                .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                                .shadow(4.dp)
                        ) {
                            CustomInteractiveMap(
                                stops = stops,
                                hubX = viewModel.hubX,
                                hubY = viewModel.hubY,
                                hubAddress = viewModel.hubAddress,
                                streetRoutePoints = streetRoutePoints,
                                selectedStop = uiState.selectedStop,
                                isSimulating = uiState.isSimulating,
                                vehicleStopIndex = uiState.vehicleStopIndex,
                                vehicleProgress = uiState.vehicleProgress,
                                isLoading = uiState.isLoading,
                                onStopSelected = { viewModel.selectStop(it) }
                            )

                            MapOverlayStatus(
                                stops = stops,
                                isSimulating = uiState.isSimulating,
                                vehicleStopIndex = uiState.vehicleStopIndex,
                                trafficFactor = uiState.trafficFactor
                            )
                        }
                    }

                    // STICKY OVERLAY HEADER: Etat du trafic & Controls are pinned beautifully during scroll!
                    @OptIn(ExperimentalFoundationApi::class)
                    stickyHeader {
                        TourStatsBanner(
                            stops = stops,
                            uiState = uiState,
                            hubX = viewModel.hubX,
                            hubY = viewModel.hubY,
                            onOptimizeClicked = { viewModel.optimizeRoute() },
                            onSimulateClicked = { viewModel.startVehicleSimulation() }
                        )
                    }

                    item {
                        // Alert ticker (traffic incidents or time slot violations)
                        AlertTicker(alerts = uiState.alerts)
                    }

                    if (stops.isEmpty()) {
                        item {
                            EmptyTourPlaceholder()
                        }
                    } else {
                        items(stops, key = { it.id }) { stop ->
                            val isSelected = uiState.selectedStop?.id == stop.id
                            Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                                StopCard(
                                    stop = stop,
                                    isSelected = isSelected,
                                    onClick = { viewModel.selectStop(stop) },
                                    onToggleCompleted = { viewModel.toggleCompleted(stop) },
                                    onDelete = { viewModel.deleteStop(stop) }
                                )
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        TrafficQuickSelector(
                            currentFactor = uiState.trafficFactor,
                            onFactorChanged = { viewModel.updateTrafficFactor(it) }
                        )
                    }
                }

                // Selected Stop Floating Detail Sheet (Minimalistic overlay)
                androidx.compose.animation.AnimatedVisibility(
                    visible = uiState.selectedStop != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(12.dp)
                ) {
                    uiState.selectedStop?.let { stop ->
                        SelectedStopBanner(
                            stop = stop,
                            onClose = { viewModel.selectStop(null) },
                            onEdit = { editingStop = stop },
                            onDelete = {
                                viewModel.deleteStop(stop)
                                viewModel.selectStop(null)
                            },
                            onComplete = {
                                viewModel.toggleCompleted(stop)
                                viewModel.selectStop(null)
                            }
                        )
                    }
                }
            }
        }

        // Add Stop dialog overlay
        if (uiState.isAddingStop) {
            AddStopDialog(
                viewModel = viewModel,
                onDismiss = { viewModel.toggleAddingStop(false) },
                onConfirm = { address, contact, hasSlot, start, end, duration, notes, lat, lon, priority ->
                    viewModel.addStop(address, contact, hasSlot, start, end, duration, notes, lat, lon, priority)
                }
            )
        }

        // Edit Stop dialog overlay
        if (editingStop != null) {
            AddStopDialog(
                viewModel = viewModel,
                stopToEdit = editingStop,
                onDismiss = { editingStop = null },
                onConfirm = { address, contact, hasSlot, start, end, duration, notes, lat, lon, priority ->
                    val original = editingStop!!
                    val updated = original.copy(
                        address = address,
                        contactName = contact,
                        hasTimeSlot = hasSlot,
                        timeSlotStart = start,
                        timeSlotEnd = end,
                        durationMinutes = duration,
                        notes = notes,
                        latitude = lat ?: original.latitude,
                        longitude = lon ?: original.longitude,
                        priority = priority
                    )
                    viewModel.updateStop(updated)
                    editingStop = null
                    // Also clear selected stop to close the bottom banner
                    viewModel.selectStop(null)
                }
            )
        }

        // Add Saved Address dialog overlay
        if (showAddAddressDialog) {
            AddSavedAddressDialog(
                viewModel = viewModel,
                onDismiss = { showAddAddressDialog = false },
                onConfirm = { label, addr, lat, lon ->
                    viewModel.addSavedAddress(label, addr, lat, lon)
                    showAddAddressDialog = false
                }
            )
        }

        // Configure Depot Location dialog overlay
        if (showConfigureHubDialog) {
            ConfigureDepotDialog(
                viewModel = viewModel,
                onDismiss = { showConfigureHubDialog = false },
                onConfirm = { address, lat, lon ->
                    viewModel.updateHub(address, lat, lon)
                    showConfigureHubDialog = false
                }
            )
        }
    }
}

@Composable
fun AddSavedAddressDialog(
    viewModel: SmartTourViewModel,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Float?, Float?) -> Unit
) {
    var label by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var selectedLat by remember { mutableStateOf<Float?>(null) }
    var selectedLon by remember { mutableStateOf<Float?>(null) }

    val suggestions by viewModel.addressSuggestions.collectAsState()

    AlertDialog(
        onDismissRequest = {
            viewModel.clearSuggestions()
            onDismiss()
        },
        title = {
            Text(
                "Enregistrer favori",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Nom de l'adresse (ex: Bureau)") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = address,
                    onValueChange = {
                        address = it
                        selectedLat = null
                        selectedLon = null
                        viewModel.searchAddress(it)
                    },
                    label = { Text("Adresse Complète (ex: Boulevard Haussmann)") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Render autocomplete suggestions dropdown
                if (suggestions.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            suggestions.forEach { suggestion ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            address = suggestion.label
                                            selectedLat = suggestion.latitude
                                            selectedLon = suggestion.longitude
                                            viewModel.clearSuggestions()
                                        }
                                        .padding(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp).align(Alignment.CenterVertically)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = suggestion.label,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                                )
                            }
                        }
                    }
                } else if (selectedLat != null) {
                    Text(
                        text = "Adresse validée par l'API",
                        fontSize = 11.sp,
                        color = Color(0xFF4CAF50),
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (label.isNotEmpty() && address.isNotEmpty()) {
                        onConfirm(label, address, selectedLat, selectedLon)
                        viewModel.clearSuggestions()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Enregistrer")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    viewModel.clearSuggestions()
                    onDismiss()
                }
            ) {
                Text("Annuler")
            }
        }
    )
}

// Custom Helper vector layout icon to draw active paths
@Composable
fun imageOfNavigation() = Icons.Default.LocationOn

/**
 * Custom Painted Map Canvas. Supports rendering paths, sequences, traffic segments
 * as colored wires, departure star hubs, and a moving technician auto-tracking vehicle!
 */
@Composable
fun CustomInteractiveMap(
    stops: List<StopEntity>,
    hubX: Float,
    hubY: Float,
    hubAddress: String,
    streetRoutePoints: List<Pair<Double, Double>>,
    selectedStop: StopEntity?,
    isSimulating: Boolean,
    vehicleStopIndex: Int,
    vehicleProgress: Float,
    isLoading: Boolean,
    onStopSelected: (StopEntity?) -> Unit
) {
    val context = LocalContext.current
    
    // Keep reference to MapView (ensure userAgentValue is configured synchronously before MapView creation)
    val mapView = remember {
        Configuration.getInstance().userAgentValue = context.packageName
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            
            // Set zoom level
            controller.setZoom(13.5)
            // Center on Hub (Paris center)
            controller.setCenter(GeoPoint(hubX.toDouble(), hubY.toDouble()))
            
            // Disable default zoom buttons so it looks cleaner and more modern!
            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
        }
    }
    
    // We update the map markers whenever stops, selectedStop, or vehicle details change!
    LaunchedEffect(stops, streetRoutePoints, selectedStop, vehicleStopIndex, vehicleProgress, isSimulating) {
        mapView.overlays.clear()
        
        // 1. Add Star Depot/Hub Marker
        val hubGeo = GeoPoint(hubX.toDouble(), hubY.toDouble())
        val hubMarker = Marker(mapView).apply {
            position = hubGeo
            title = hubAddress
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            subDescription = "Point de départ et de retour de la tournée"
        }
        mapView.overlays.add(hubMarker)
        
        // 2. Add Stops Markers and draw Polyline route path
        val routePoints = streetRoutePoints.map { GeoPoint(it.first, it.second) }
        
        stops.forEachIndexed { index, stop ->
            val stopGeo = GeoPoint(stop.latitude.toDouble(), stop.longitude.toDouble())
            
            val marker = Marker(mapView).apply {
                position = stopGeo
                title = "Arrêt #${index + 1}: ${stop.contactName}"
                snippet = "${stop.address}\nHoraire: ${if (stop.hasTimeSlot) "${stop.timeSlotStart} - ${stop.timeSlotEnd}" else "Libre"}"
                subDescription = "Notes: ${stop.notes}"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                
                setOnMarkerClickListener { m, _ ->
                    onStopSelected(stop)
                    m.showInfoWindow()
                    true
                }
            }
            mapView.overlays.add(marker)
        }
        
        // Draw Polyline path
        if (routePoints.size > 1) {
            val polyline = Polyline(mapView).apply {
                setPoints(routePoints)
                outlinePaint.color = android.graphics.Color.parseColor("#6750A4")
                outlinePaint.strokeWidth = 6f
            }
            mapView.overlays.add(polyline)
        }
        
        // 3. Active Vehicle simulation marker
        if (isSimulating && stops.isNotEmpty()) {
            // Find start and end points of current leg
            val startLoc = if (vehicleStopIndex <= 0 || (vehicleStopIndex - 1) >= stops.size) {
                hubGeo
            } else {
                stops.getOrNull(vehicleStopIndex - 1)?.let {
                    GeoPoint(it.latitude.toDouble(), it.longitude.toDouble())
                } ?: hubGeo
            }
            
            val endLoc = if (vehicleStopIndex >= 0 && vehicleStopIndex < stops.size) {
                stops.getOrNull(vehicleStopIndex)?.let {
                    GeoPoint(it.latitude.toDouble(), it.longitude.toDouble())
                } ?: hubGeo
            } else {
                hubGeo
            }
            
            // Interpolate position
            val interpLat = startLoc.latitude + (endLoc.latitude - startLoc.latitude) * vehicleProgress
            val interpLng = startLoc.longitude + (endLoc.longitude - startLoc.longitude) * vehicleProgress
            val vehicleGeo = GeoPoint(interpLat, interpLng)
            
            val vehicleMarker = Marker(mapView).apply {
                position = vehicleGeo
                title = "Technicien En Route"
                subDescription = if (vehicleStopIndex < stops.size) {
                    "Destination: Arrêt #${vehicleStopIndex + 1}"
                } else {
                    "Retour au dépôt"
                }
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            }
            mapView.overlays.add(vehicleMarker)
            
            // Smoothly pan camera to follow active technician during animation simulation!
            mapView.controller.animateTo(vehicleGeo)
        } else {
            // Center Map dynamically to include all markers / depot via BoundingBox zoom or direct center
            if (stops.isNotEmpty()) {
                try {
                    val points = mutableListOf<GeoPoint>().apply {
                        add(hubGeo)
                        stops.forEach { add(GeoPoint(it.latitude.toDouble(), it.longitude.toDouble())) }
                    }
                    val minLat = points.minOf { it.latitude }
                    val maxLat = points.maxOf { it.latitude }
                    val minLng = points.minOf { it.longitude }
                    val maxLng = points.maxOf { it.longitude }
                    
                    val latDelta = maxLat - minLat
                    val lngDelta = maxLng - minLng
                    
                    // Simple padding heuristic to avoid edge-cuts on different screen sizes
                    val paddingFactor = 0.20
                    val finalMinLat = java.lang.Math.max(-90.0, minLat - (latDelta * paddingFactor) - 0.01)
                    val finalMaxLat = java.lang.Math.min(90.0, maxLat + (latDelta * paddingFactor) + 0.01)
                    val finalMinLng = java.lang.Math.max(-180.0, minLng - (lngDelta * paddingFactor) - 0.01)
                    val finalMaxLng = java.lang.Math.min(180.0, maxLng + (lngDelta * paddingFactor) + 0.01)

                    val box = org.osmdroid.util.BoundingBox(finalMaxLat, finalMaxLng, finalMinLat, finalMinLng)
                    mapView.post {
                        if (mapView.width > 0 && mapView.height > 0) {
                            mapView.zoomToBoundingBox(box, true)
                        } else {
                            mapView.addOnLayoutChangeListener(object : android.view.View.OnLayoutChangeListener {
                                override fun onLayoutChange(
                                    v: android.view.View?,
                                    left: Int, top: Int, right: Int, bottom: Int,
                                    oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int
                                ) {
                                    val w = right - left
                                    val h = bottom - top
                                    if (w > 0 && h > 0) {
                                        mapView.removeOnLayoutChangeListener(this)
                                        mapView.zoomToBoundingBox(box, true)
                                    }
                                }
                            })
                        }
                    }
                } catch (e: Exception) {
                    mapView.controller.setCenter(hubGeo)
                    mapView.controller.setZoom(13.5)
                }
            } else if (hubX != 0f) {
                mapView.controller.setCenter(hubGeo)
                mapView.controller.setZoom(13.5)
            }
        }
        
        mapView.invalidate()
    }
    
    // Dispose Osmdroid mapView cleanly to avoid potential context leaks
    DisposableEffect(Unit) {
        onDispose {
            mapView.onDetach()
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun CustomInteractiveMapOld(
    stops: List<StopEntity>,
    hubX: Float,
    hubY: Float,
    selectedStop: StopEntity?,
    isSimulating: Boolean,
    vehicleStopIndex: Int,
    vehicleProgress: Float,
    isLoading: Boolean,
    onStopSelected: (StopEntity?) -> Unit
) {
    // Canvas dimensions configuration
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
    // Small test marker
    val pathFluidColor = Color(0xFF4CAF50)
    val pathDenseColor = Color(0xFFFF9800)
    val pathHeavyColor = Color(0xFFF44336)
    val routeLineColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)

    // Pulse animation for loading scanning effect
    val transition = rememberInfiniteTransition(label = "RadarSweep")
    val radarRadius by transition.animateFloat(
        initialValue = 10f,
        targetValue = 280f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Radar"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1B1C20)) // Dark ambient dashboard styling
            .pointerInput(stops) {
                detectTapGestures { offset ->
                    // Perform simple distance hit-testing to see if user tapped a stop node
                    var tappedStop: StopEntity? = null
                    val clickTolerance = 48f // dp to pixels scale tolerance

                    for (stop in stops) {
                        // Project relative stop coordinates on the canvas view width/height
                        val realX = (stop.latitude / 800f) * size.width
                        val realY = (stop.longitude / 600f) * size.height
                        val dist = Math.sqrt(
                            Math.pow((offset.x - realX).toDouble(), 2.0) +
                                    Math.pow((offset.y - realY).toDouble(), 2.0)
                        )
                        if (dist < clickTolerance) {
                            tappedStop = stop
                            break
                        }
                    }
                    onStopSelected(tappedStop)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // --- 1. Draw Map Grid Backplane (Slate GPS Grid) ---
            val gridSpacing = 40.dp.toPx()
            var x = 0f
            while (x < width) {
                drawLine(
                    color = gridColor,
                    start = Offset(x, 0f),
                    end = Offset(x, height),
                    strokeWidth = 1f
                )
                x += gridSpacing
            }
            var y = 0f
            while (y < height) {
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1f
                )
                y += gridSpacing
            }

            // Draw soft water/parks background shapes for dynamic flavor
            drawCircle(
                color = Color(0xFF0F262F),
                radius = 120.dp.toPx(),
                center = Offset(width * 0.15f, height * 0.75f)
            )
            drawCircle(
                color = Color(0xFF19251E),
                radius = 160.dp.toPx(),
                center = Offset(width * 0.82f, height * 0.3f)
            )

            // Normalize coordinate system mapping functions
            fun getCanvasPos(xVal: Float, yVal: Float): Offset {
                val rx = (xVal / 800f) * width
                val ry = (yVal / 600f) * height
                return Offset(rx, ry)
            }

            val startPoint = getCanvasPos(hubX, hubY)

            // --- 2. Draw Delivery Paths / Route Networks ---
            if (stops.isNotEmpty()) {
                var prevOffset = startPoint
                
                stops.forEachIndexed { index, stop ->
                    val stopOffset = getCanvasPos(stop.latitude, stop.longitude)
                    
                    // Style route lines based on stop traffic speeds
                    val segmentColor = when {
                        stop.trafficStatus.contains("Saturé") -> pathHeavyColor
                        stop.trafficStatus.contains("Dense") -> pathDenseColor
                        else -> pathFluidColor
                    }

                    // Draw main path connect line
                    drawLine(
                        color = segmentColor,
                        start = prevOffset,
                        end = stopOffset,
                        strokeWidth = 5.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    // Draw soft pulsing glow behind travel line
                    drawLine(
                        color = segmentColor.copy(alpha = 0.25f),
                        start = prevOffset,
                        end = stopOffset,
                        strokeWidth = 11.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    prevOffset = stopOffset
                }

                // Draw return line from last stop back to central hub
                val lastStopOffset = getCanvasPos(stops.last().latitude, stops.last().longitude)
                drawLine(
                    color = routeLineColor,
                    start = lastStopOffset,
                    end = startPoint,
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f),
                    cap = StrokeCap.Round
                )
            }

            // --- 3. Draw Radar Scan Sweep (during Optimization loadings) ---
            if (isLoading) {
                drawCircle(
                    color = Color(0x1F2196F3),
                    radius = radarRadius,
                    center = startPoint,
                    style = Stroke(width = 3.dp.toPx())
                )
                drawCircle(
                    color = Color(0x0F2196F3),
                    radius = radarRadius / 2f,
                    center = startPoint
                )
            }

            // --- 4. Draw Departure Central Depot Hub ---
            drawCircle(
                color = Color(0xFF2196F3),
                radius = 16.dp.toPx(),
                center = startPoint
            )
            drawCircle(
                color = Color.White,
                radius = 12.dp.toPx(),
                center = startPoint
            )
            drawCircle(
                color = Color(0xFF0D47A1),
                radius = 6.dp.toPx(),
                center = startPoint
            )

            // --- 5. Draw Stop Nodes with dynamic sequence indices ---
            stops.forEach { stop ->
                val nodeOffset = getCanvasPos(stop.latitude, stop.longitude)
                val isSelected = selectedStop?.id == stop.id

                // Pulsing highlight structure around selected node
                if (isSelected) {
                    drawCircle(
                        color = Color(0xFFE91E63).copy(alpha = 0.4f),
                        radius = 24.dp.toPx(),
                        center = nodeOffset
                    )
                }

                // Outer node ring depending on completion state
                val stopRingColor = if (stop.isCompleted) Color(0xFF4CAF50) else Color(0xFF2196F3)
                drawCircle(
                    color = stopRingColor,
                    radius = 12.dp.toPx(),
                    center = nodeOffset
                )

                drawCircle(
                    color = if (stop.isCompleted) Color(0xFFE8F5E9) else Color(0xFF1B1C20),
                    radius = 9.dp.toPx(),
                    center = nodeOffset
                )
            }

            // --- 6. Draw Moving Delivery Technician Vehicle (Simulation) ---
            if (isSimulating && stops.isNotEmpty()) {
                val startLoc = if (vehicleStopIndex <= 0 || (vehicleStopIndex - 1) >= stops.size) {
                    startPoint
                } else {
                    stops.getOrNull(vehicleStopIndex - 1)?.let {
                        getCanvasPos(it.latitude, it.longitude)
                    } ?: startPoint
                }

                val endLoc = if (vehicleStopIndex >= 0 && vehicleStopIndex < stops.size) {
                    stops.getOrNull(vehicleStopIndex)?.let {
                        getCanvasPos(it.latitude, it.longitude)
                    } ?: startPoint
                } else {
                    startPoint
                }

                // Calculate current interpolated position point on map
                val vehicleX = startLoc.x + (endLoc.x - startLoc.x) * vehicleProgress
                val vehicleY = startLoc.y + (endLoc.y - startLoc.y) * vehicleProgress
                val vehiclePos = Offset(vehicleX, vehicleY)

                // Render shining GPS vehicle node
                drawCircle(
                    color = Color.White,
                    radius = 11.dp.toPx(),
                    center = vehiclePos,
                    style = Stroke(width = 2.dp.toPx())
                )
                drawCircle(
                    color = Color(0xFFE91E63),
                    radius = 7.dp.toPx(),
                    center = vehiclePos
                )
            }
        }

        // Overlay text labels on top of spots to minimize overlap confusion
        CanvasTextLabels(stops = stops, selectedStop = selectedStop, hubX = hubX, hubY = hubY)
    }
}

/**
 * Superimposes sequence names and badges on the canvas stops using Compose layout elements
 */
@Composable
fun CanvasTextLabels(
    stops: List<StopEntity>,
    selectedStop: StopEntity?,
    hubX: Float,
    hubY: Float
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Departure Hub Text
        Box(
            modifier = Modifier
                .offset(x = 180.dp, y = 145.dp) // mapped near Parisian center coordinates
                .background(Color(0xE0131416), RoundedCornerShape(4.dp))
                .padding(vertical = 2.dp, horizontal = 4.dp)
        ) {
            Text(
                "DÉPÔT CENTRAL",
                color = Color(0xFF90CAF9),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }

        stops.forEach { stop ->
            key(stop.id) {
                // Recompute coordinates on relative percentages
                val px = (stop.latitude / 800f)
                val py = (stop.longitude / 600f)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = px * size.width - 20.dp.toPx()
                            translationY = py * size.height - 35.dp.toPx()
                        }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .background(
                                color = if (selectedStop?.id == stop.id) Color(0xFFE91E63) else Color(0xCC202124),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = if (stop.isCompleted) Color(0xFF4CAF50) else Color(0xFF64B5F6),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "#${stop.orderIndex}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                        if (stop.isCompleted) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Fait",
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Top Status overlay on the map showing real-time metrics & traffic incidents simulator
 */
@Composable
fun MapOverlayStatus(
    stops: List<StopEntity>,
    isSimulating: Boolean,
    vehicleStopIndex: Int,
    trafficFactor: Float
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.align(Alignment.TopStart),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xCC202124)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val statusColor = when {
                        trafficFactor >= 1.8f -> Color(0xFFF44336)
                        trafficFactor >= 1.4f -> Color(0xFFFF9800)
                        else -> Color(0xFF4CAF50)
                    }
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Text(
                        text = "Trafic: " + when {
                            trafficFactor >= 1.8f -> "Saturé (Haute-Densité)"
                            trafficFactor >= 1.4f -> "Dense"
                            else -> "Fluide"
                        },
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (isSimulating) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xCCE91E63)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (vehicleStopIndex >= 0 && vehicleStopIndex < stops.size) {
                            "En route vers l'arrêt #${vehicleStopIndex + 1}"
                        } else {
                            "Retour au dépôt"
                        },
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

/**
 * Stats banner containing travel counts, optimizer buttons, and simulation controls
 */
@Composable
fun TourStatsBanner(
    stops: List<StopEntity>,
    uiState: SmartTourUiState,
    hubX: Float,
    hubY: Float,
    onOptimizeClicked: () -> Unit,
    onSimulateClicked: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(24.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: État du trafic / Title and OPTIMISÉ badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ÉTAT DU TRAFIC",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = when {
                            uiState.trafficFactor >= 1.8f -> "Saturé • Retard +25 min"
                            uiState.trafficFactor >= 1.4f -> "Dense • Ralentissement +8 min"
                            else -> "Fluide • Gain de 12 min"
                        },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }

                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text(
                        text = "OPTIMISÉ",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Thin custom progress track line representing optimization strength or progression
            val progressProgress = when {
                stops.isEmpty() -> 0f
                else -> (stops.count { it.isCompleted }.toFloat() / stops.size.toFloat())
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(if (progressProgress > 0f) progressProgress else 0.65f)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Information/AI statement bubble
            val detailsText = uiState.aiExplanation ?: when {
                uiState.trafficFactor >= 1.8f -> "Itinéraire recalculé via les boulevards extérieurs pour éviter les accidents."
                uiState.trafficFactor >= 1.4f -> "Ralentissement détecté. Itinéraire secondaire suggéré."
                else -> "Itinéraire optimal via Boulevard Haussmann pour éviter les zones de travaux."
            }
            Text(
                text = detailsText,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(12.dp))

            // Control Actions inside the footer banner
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val compCount = stops.count { it.isCompleted }
                val remainingCount = stops.size - compCount
                Text(
                    text = "$compCount faits • $remainingCount à visiter",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Simuler / Stop button block
                    Button(
                        onClick = onSimulateClicked,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (uiState.isSimulating) Color(0xFFE91E63) else Color(0xFF625B71),
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(100.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("simulation_toggle_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (uiState.isSimulating) Icons.Default.Close else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(if (uiState.isSimulating) "Stop" else "Simuler", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    // Optimiser button block
                    Button(
                        onClick = onOptimizeClicked,
                        enabled = !uiState.isLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(100.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("optimize_route_button")
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                color = Color.White,
                                strokeWidth = 1.5.dp
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                Text("Optimiser", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Color(0xFFCAC4D0).copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(10.dp))

            // Navigation Export Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val context = LocalContext.current
                
                Text(
                    text = "Navigation:", 
                    fontSize = 11.sp, 
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 4.dp)
                )

                // Google Maps Full Tour
                OutlinedButton(
                    onClick = {
                        try {
                            if (stops.isNotEmpty()) {
                                val originStr = "$hubX,$hubY"
                                val destinationStr = "$hubX,$hubY"
                                val uncompletedStops = stops.filter { !it.isCompleted }
                                val waypointsStr = uncompletedStops.joinToString("|") { "${it.latitude},${it.longitude}" }
                                
                                val url = if (waypointsStr.isNotEmpty()) {
                                    "https://www.google.com/maps/dir/?api=1&origin=$originStr&destination=$destinationStr&waypoints=$waypointsStr&travelmode=driving"
                                } else {
                                    "https://www.google.com/maps/dir/?api=1&origin=$originStr&destination=$destinationStr&travelmode=driving"
                                }
                                
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                    setPackage("com.google.android.apps.maps")
                                }
                                context.startActivity(intent)
                            }
                        } catch (e: Exception) {
                            val url = "https://www.google.com/maps/dir/?api=1&origin=$hubX,$hubY&destination=$hubX,$hubY&travelmode=driving"
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }
                    },
                    modifier = Modifier.weight(1f).height(36.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF1976D2)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1976D2).copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Maps complet", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }

                // Waze Next Stop
                val nextUncompletedStop = stops.firstOrNull { !it.isCompleted }
                OutlinedButton(
                    onClick = {
                        try {
                            val targetStop = nextUncompletedStop
                            val url = if (targetStop != null) {
                                "https://waze.com/ul?ll=${targetStop.latitude},${targetStop.longitude}&navigate=yes"
                            } else {
                                "https://waze.com/ul?ll=$hubX,$hubY&navigate=yes"
                            }
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                setPackage("com.waze")
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            val targetStop = nextUncompletedStop
                            val url = if (targetStop != null) {
                                "https://waze.com/ul?ll=${targetStop.latitude},${targetStop.longitude}&navigate=yes"
                            } else {
                                "https://waze.com/ul?ll=$hubX,$hubY&navigate=yes"
                            }
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }
                    },
                    modifier = Modifier.weight(1f).height(36.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00ADB5)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00ADB5).copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (nextUncompletedStop != null) "Waze suivant" else "Waze dépôt", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/**
 * Traffic scale density slide/factor picker
 */
@Composable
fun TrafficQuickSelector(
    currentFactor: Float,
    onFactorChanged: (Float) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Ajustement du Trafic Routier global",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(
                    Triple(1.0f, "Normal (Fluide)", Color(0xFF4CAF50)),
                    Triple(1.5f, "Embouteillages", Color(0xFFFF9800)),
                    Triple(2.0f, "Heures de pointe", Color(0xFFF44336))
                ).forEach { (factor, label, color) ->
                    val isSelected = currentFactor == factor
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) color.copy(alpha = 0.2f) else Color.Transparent)
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) color else MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { onFactorChanged(factor) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) color else MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

/**
 * Scrollable ticker list displaying active safety warnings or delay warnings
 */
@Composable
fun AlertTicker(alerts: List<String>) {
    if (alerts.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
    ) {
        alerts.take(2).forEach { alert ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (alert.startsWith("Alerte") || alert.contains("Retard")) Color(0x2FF44336) else Color(0x2F4CAF50)
                ),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = alert,
                        fontSize = 11.sp,
                        color = if (alert.startsWith("Alerte") || alert.contains("Retard")) Color(0xFFEF5350) else Color(0xFF66BB6A),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Empty placeholder layout illustrated with an assistant guideline
 */
@Composable
fun EmptyTourPlaceholder() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.LocationOn,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Aucun arrêt dans la tournée",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Ajoute des adresses de livraison en bas à droite pour tracer un itinéraire optimal.",
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Highly responsive Stop Card Lists
 */
@Composable
fun StopsLazyList(
    stops: List<StopEntity>,
    selectedStop: StopEntity?,
    onStopClick: (StopEntity) -> Unit,
    onToggleCompleted: (StopEntity) -> Unit,
    onDeleteStop: (StopEntity) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize().testTag("stops_lazy_list")
    ) {
        items(stops, key = { it.id }) { stop ->
            val isSelected = selectedStop?.id == stop.id
            StopCard(
                stop = stop,
                isSelected = isSelected,
                onClick = { onStopClick(stop) },
                onToggleCompleted = { onToggleCompleted(stop) },
                onDelete = { onDeleteStop(stop) }
            )
        }
        item {
            Spacer(modifier = Modifier.height(72.dp)) // padding for FAB
        }
    }
}

@Composable
fun StopCard(
    stop: StopEntity,
    isSelected: Boolean,
    onClick: () -> Unit,
    onToggleCompleted: () -> Unit,
    onDelete: () -> Unit
) {
    val cardBg = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    val cardBorderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }

    val textColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val subtextColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.83f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = cardBorderColor, shape = RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .testTag("stop_card_${stop.id}"),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 2.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Left sequence timeline indicator dot
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(24.dp)
                    .padding(top = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(
                            if (stop.isCompleted) MaterialTheme.colorScheme.primary else Color(0xFFCAC4D0)
                        )
                        .border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.surface,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (stop.isCompleted) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Fait",
                            tint = Color.White,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }
                
                // Timeline Connector stem
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(34.dp)
                        .background(Color(0xFFCAC4D0).copy(alpha = 0.4f))
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Body content
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Category Tag label and Priority Badge
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val stopTag = if (stop.orderIndex == 1) "DÉPART" else if (stop.hasTimeSlot) "URGENT" else "CLIENT"
                        Text(
                            text = stopTag,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        val priorityColor = when (stop.priority) {
                            "High" -> Color(0xFFD32F2F)
                            "Low" -> Color(0xFF388E3C)
                            else -> Color(0xFF1976D2)
                        }
                        val priorityLabel = when (stop.priority) {
                            "High" -> "Haute"
                            "Low" -> "Basse"
                            else -> "Normale"
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = priorityColor.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = priorityLabel,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = priorityColor,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }

                    // Scheduled Hours or constraints in pink capsule or raw text
                    if (stop.hasTimeSlot) {
                        Surface(
                            shape = RoundedCornerShape(100.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${stop.timeSlotStart} - ${stop.timeSlotEnd}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    } else if (stop.estimatedArrivalTime != null) {
                        Text(
                            text = stop.estimatedArrivalTime ?: "",
                            fontSize = 11.sp,
                            color = subtextColor,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Heading / Address
                Text(
                    text = stop.address,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Subtitle: Contact name and duration metric
                Text(
                    text = "${stop.contactName} • ${stop.durationMinutes} min",
                    fontSize = 12.sp,
                    color = subtextColor
                )

                if (stop.notes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Note: ${stop.notes}",
                        fontSize = 11.sp,
                        color = subtextColor.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Estimated arrival details / status OK warning
                if (stop.estimatedArrivalTime != null && stop.hasTimeSlot) {
                    Spacer(modifier = Modifier.height(6.dp))
                    val arrivalMinutes = OptimizationService.parseTimeToMinutes(stop.estimatedArrivalTime)
                    val endMinutes = OptimizationService.parseTimeToMinutes(stop.timeSlotEnd)
                    val exceeded = arrivalMinutes > endMinutes

                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = if (exceeded) Color(0x3FFF4646) else Color(0x3F4CAF50),
                        modifier = Modifier.align(Alignment.Start)
                    ) {
                        Text(
                            text = if (exceeded) "Hors créneau" else "Dans les temps (estimé à ${stop.estimatedArrivalTime})",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (exceeded) Color(0xFFD32F2F) else Color(0xFF2E7D32),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Action Checkbox
            IconButton(
                onClick = onToggleCompleted,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("toggle_complete_${stop.id}")
                    .align(Alignment.CenterVertically)
            ) {
                Icon(
                    imageVector = if (stop.isCompleted) Icons.Default.CheckCircle else Icons.Default.AddCircle,
                    contentDescription = "Fait",
                    tint = if (stop.isCompleted) MaterialTheme.colorScheme.primary else Color(0xFFCAC4D0),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/**
 * Overlay details banner for a selected stop with deletion options
 */
@Composable
fun SelectedStopBanner(
    stop: StopEntity,
    onClose: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onComplete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Arrêt #${stop.orderIndex} sélectionné",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        stop.address,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Fermer")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "Client: ${stop.contactName}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )

            if (stop.notes.isNotEmpty()) {
                Text(
                    "Détail d'intervention:\n${stop.notes}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            if (stop.hasTimeSlot) {
                Text(
                    "Créneau de livraison: ${stop.timeSlotStart} à ${stop.timeSlotEnd}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1976D2)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onEdit,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF1976D2)),
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Modifier")
                }

                OutlinedButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Retirer")
                }

                Button(
                    onClick = onComplete,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (stop.isCompleted) Color(0xFF9E9E9E) else Color(0xFF4CAF50)
                    )
                ) {
                    Text(if (stop.isCompleted) "Marquer à faire" else "Marquer Livré")
                }
            }
        }
    }
}

/**
 * Dialog form to Add a Stop
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddStopDialog(
    viewModel: SmartTourViewModel,
    stopToEdit: StopEntity? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Boolean, String, String, Int, String, Float?, Float?, String) -> Unit
) {
    var address by remember { mutableStateOf(stopToEdit?.address ?: "") }
    var contact by remember { mutableStateOf(stopToEdit?.contactName ?: "") }
    var durationMinutes by remember { mutableStateOf(stopToEdit?.durationMinutes?.toString() ?: "20") }
    var hasSlot by remember { mutableStateOf(stopToEdit?.hasTimeSlot ?: false) }
    var slotStart by remember { mutableStateOf(stopToEdit?.timeSlotStart ?: "09:00") }
    var slotEnd by remember { mutableStateOf(stopToEdit?.timeSlotEnd ?: "11:00") }
    var notes by remember { mutableStateOf(stopToEdit?.notes ?: "") }
    var selectedPriority by remember { mutableStateOf(stopToEdit?.priority ?: "Normal") }

    var selectedLat by remember { mutableStateOf<Float?>(stopToEdit?.latitude) }
    var selectedLon by remember { mutableStateOf<Float?>(stopToEdit?.longitude) }

    var addressError by remember { mutableStateOf(false) }
    var contactError by remember { mutableStateOf(false) }

    val suggestions by viewModel.addressSuggestions.collectAsState()

    AlertDialog(
        onDismissRequest = {
            viewModel.clearSuggestions()
            onDismiss()
        },
        title = {
            Text(
                if (stopToEdit != null) "Modifier l'Arrêt" else "Planifier une Tournée d'Arrêt",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Address input
                OutlinedTextField(
                    value = address,
                    onValueChange = {
                        address = it
                        addressError = false
                        selectedLat = null
                        selectedLon = null
                        viewModel.searchAddress(it)
                    },
                    label = { Text("Adresse de destination") },
                    isError = addressError,
                    placeholder = { Text("Ex: 15 Rue de la Paix, Paris") },
                    modifier = Modifier.fillMaxWidth().testTag("address_input"),
                    singleLine = true
                )

                // Render autocomplete dropdown list if suggestions are present
                if (suggestions.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            suggestions.forEach { suggestion ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            address = suggestion.label
                                            selectedLat = suggestion.latitude
                                            selectedLon = suggestion.longitude
                                            viewModel.clearSuggestions()
                                        }
                                        .padding(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp).align(Alignment.CenterVertically)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = suggestion.label,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                                )
                            }
                        }
                    }
                } else if (selectedLat != null) {
                    Text(
                        text = "Adresse validée par l'API",
                        fontSize = 11.sp,
                        color = Color(0xFF4CAF50),
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }

                // Contact input
                OutlinedTextField(
                    value = contact,
                    onValueChange = {
                        contact = it
                        contactError = false
                    },
                    label = { Text("Client / Technicien de contact") },
                    isError = contactError,
                    placeholder = { Text("Ex: Nicolas Durand") },
                    modifier = Modifier.fillMaxWidth().testTag("contact_input"),
                    singleLine = true
                )

                // Duration input
                OutlinedTextField(
                    value = durationMinutes,
                    onValueChange = { durationMinutes = it },
                    label = { Text("Temps sur place (minutes)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth().testTag("duration_input"),
                    singleLine = true
                )

                // Optional preferred window constraint toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { hasSlot = !hasSlot }
                        .padding(vertical = 4.dp)
                ) {
                    Checkbox(
                        checked = hasSlot,
                        onCheckedChange = { hasSlot = it },
                        modifier = Modifier.testTag("timeslot_checkbox")
                    )
                    Text("Spécifier une contrainte d'horaire", fontSize = 13.sp)
                }

                if (hasSlot) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = slotStart,
                            onValueChange = { slotStart = it },
                            label = { Text("Début (HH:MM)") },
                            modifier = Modifier.weight(1f).testTag("start_time_input")
                        )
                        OutlinedTextField(
                            value = slotEnd,
                            onValueChange = { slotEnd = it },
                            label = { Text("Fin (HH:MM)") },
                            modifier = Modifier.weight(1f).testTag("end_time_input")
                        )
                    }
                }

                // Notes / Job Instructions input
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Instructions de livraison / panne") },
                    placeholder = { Text("Ex: Code porte 1234, ascenseur en panne") },
                    modifier = Modifier.fillMaxWidth().testTag("notes_input"),
                    maxLines = 2
                )

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Priorité de l'intervention", 
                    fontSize = 12.sp, 
                    fontWeight = FontWeight.SemiBold, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val priorities = listOf("Low", "Normal", "High")
                    priorities.forEach { p ->
                        val isSelected = selectedPriority == p
                        val btnColor = when (p) {
                            "High" -> if (isSelected) Color(0xFFD32F2F) else Color(0xFFFFEBEE)
                            "Low" -> if (isSelected) Color(0xFF388E3C) else Color(0xFFE8F5E9)
                            else -> if (isSelected) Color(0xFF1976D2) else Color(0xFFE3F2FD)
                        }
                        val textColor = when (p) {
                            "High" -> if (isSelected) Color.White else Color(0xFFC62828)
                            "Low" -> if (isSelected) Color.White else Color(0xFF2E7D32)
                            else -> if (isSelected) Color.White else Color(0xFF1565C0)
                        }
                        Button(
                            onClick = { selectedPriority = p },
                            modifier = Modifier.weight(1f).height(36.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = btnColor),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            val l10n = when (p) {
                                "High" -> "Haute"
                                "Low" -> "Basse"
                                else -> "Normale"
                            }
                            Text(l10n, fontSize = 11.sp, color = textColor, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (address.isBlank()) {
                        addressError = true
                        return@Button
                    }
                    if (contact.isBlank()) {
                        contactError = true
                        return@Button
                    }
                    val duration = durationMinutes.toIntOrNull() ?: 20
                    onConfirm(address, contact, hasSlot, slotStart, slotEnd, duration, notes, selectedLat, selectedLon, selectedPriority)
                    viewModel.clearSuggestions()
                },
                modifier = Modifier.testTag("submit_stop_button")
            ) {
                Text("Enregistrer")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    viewModel.clearSuggestions()
                    onDismiss()
                }
            ) {
                Text("Annuler")
            }
        }
    )
}

@Composable
fun ConfigureDepotDialog(
    viewModel: SmartTourViewModel,
    onDismiss: () -> Unit,
    onConfirm: (String, Float, Float) -> Unit
) {
    var address by remember { mutableStateOf("") }
    var selectedLat by remember { mutableStateOf<Float?>(null) }
    var selectedLon by remember { mutableStateOf<Float?>(null) }

    val suggestions by viewModel.addressSuggestions.collectAsState()

    AlertDialog(
        onDismissRequest = {
            viewModel.clearSuggestions()
            onDismiss()
        },
        title = {
            Text(
                "Configurer le dépôt central",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Recherchez et configurez la base de départ de votre flotte de techniciens.",
                    fontSize = 12.sp,
                    color = Color.Gray
                )

                OutlinedTextField(
                    value = address,
                    onValueChange = {
                        address = it
                        selectedLat = null
                        selectedLon = null
                        viewModel.searchAddress(it)
                    },
                    label = { Text("Adresse ou Ville du dépôt") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Render autocomplete suggestions dropdown
                if (suggestions.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            suggestions.forEach { suggestion ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            address = suggestion.label
                                            selectedLat = suggestion.latitude
                                            selectedLon = suggestion.longitude
                                            viewModel.clearSuggestions()
                                        }
                                        .padding(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp).align(Alignment.CenterVertically)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = suggestion.label,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                                )
                            }
                        }
                    }
                } else if (selectedLat != null) {
                    Text(
                        text = "Base validée par l'API",
                        fontSize = 11.sp,
                        color = Color(0xFF4CAF50),
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val lat = selectedLat
                    val lon = selectedLon
                    if (address.isNotEmpty() && lat != null && lon != null) {
                        onConfirm(address, lat, lon)
                        viewModel.clearSuggestions()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                enabled = selectedLat != null
            ) {
                Text("Enregistrer")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    viewModel.clearSuggestions()
                    onDismiss()
                }
            ) {
                Text("Annuler")
            }
        }
    )
}
