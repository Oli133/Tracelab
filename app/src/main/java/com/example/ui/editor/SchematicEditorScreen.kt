package com.example.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.DeepErcAuditResult
import com.example.data.QuickComponentCheckResult
import com.example.model.ComponentType
import com.example.model.EditorTool
import com.example.model.SchematicProject
import com.example.model.ValidationResult
import com.example.ui.canvas.CanvasViewportState
import com.example.ui.canvas.SchematicCanvas
import com.example.viewmodel.AiTaskState
import com.example.viewmodel.CloudSyncStatus
import com.example.viewmodel.PinAnchor
import kotlin.math.roundToInt
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchematicEditorScreen(
    project: SchematicProject,
    validationResult: ValidationResult,
    selectedComponentUid: String?,
    selectedWireUid: String?,
    activeTool: EditorTool,
    pendingWireStart: PinAnchor?,
    wireLayer: String,
    wireTraceWidthMm: Double,
    syncStatus: CloudSyncStatus,
    syncErrorMessage: String?,
    canUndo: Boolean,
    canRedo: Boolean,
    quickCheckState: AiTaskState<QuickComponentCheckResult>,
    deepAuditState: AiTaskState<DeepErcAuditResult>,
    isDarkTheme: Boolean,
    onBack: () -> Unit,
    onOpenBom: () -> Unit,
    onSyncNow: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSetTool: (EditorTool) -> Unit,
    onSetWireLayer: (String) -> Unit,
    onSetWireWidth: (Double) -> Unit,
    onSelectComponent: (String?) -> Unit,
    onSelectWire: (String?) -> Unit,
    onAddComponentAtGrid: (ComponentType, Long, Long) -> Unit,
    onStartDragComponent: () -> Unit,
    onDragComponentToGrid: (String, Long, Long) -> Unit,
    onEndDragComponent: () -> Unit,
    onTapPin: (String, Int) -> Unit,
    onRotateComponent: () -> Unit,
    onDeleteSelected: () -> Unit,
    onUpdateComponent: (String, String, String, String, String, Double, Double) -> Unit,
    onUpdateWire: (String, Double, String) -> Unit,
    onUpdateBoardSettings: (String, String, Double, Double, Long) -> Unit,
    onRunQuickCheck: () -> Unit,
    onApplyQuickCheck: (QuickComponentCheckResult) -> Unit,
    onRunDeepAudit: () -> Unit
) {
    var viewport by remember(project.id) { mutableStateOf(CanvasViewportState()) }
    var canvasRootPos by remember { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    // Palette drag-and-drop overlay state
    var draggingType by remember { mutableStateOf<ComponentType?>(null) }
    var dragRootOffset by remember { mutableStateOf(Offset.Zero) }

    // Compact sheet controls
    var showSheet by remember { mutableStateOf(false) }
    var activeSheetTab by remember { mutableIntStateOf(0) } // 0 = Inspector, 1 = ERC/AI
    var showBoardSettings by remember { mutableStateOf(false) }

    val selectedComponent = remember(project.components, selectedComponentUid) {
        project.components.firstOrNull { it.uid == selectedComponentUid }
    }
    val selectedWire = remember(project.wires, selectedWireUid) {
        project.wires.firstOrNull { it.uid == selectedWireUid }
    }

    fun placeAtViewportCenter(type: ComponentType) {
        val baseGridPx = 32f
        val centerX = if (canvasSize.width > 0) canvasSize.width / 2f else 360f
        val centerY = if (canvasSize.height > 0) canvasSize.height / 2f else 360f
        val worldX = (centerX - viewport.offsetX) / viewport.scale
        val worldY = (centerY - viewport.offsetY) / viewport.scale
        val gx = (worldX / baseGridPx).roundToLong().coerceAtLeast(1L)
        val gy = (worldY / baseGridPx).roundToLong().coerceAtLeast(1L)
        onAddComponentAtGrid(type, gx, gy)
    }

    fun dropPalettePartAtRootOffset(type: ComponentType, rootOffset: Offset) {
        val localX = rootOffset.x - canvasRootPos.x
        val localY = rootOffset.y - canvasRootPos.y
        if (localX in 0f..canvasSize.width.toFloat() && localY in 0f..canvasSize.height.toFloat()) {
            val baseGridPx = 32f
            val worldX = (localX - viewport.offsetX) / viewport.scale
            val worldY = (localY - viewport.offsetY) / viewport.scale
            val gx = (worldX / baseGridPx).roundToLong().coerceAtLeast(0L)
            val gy = (worldY / baseGridPx).roundToLong().coerceAtLeast(0L)
            onAddComponentAtGrid(type, gx, gy)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("editor_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to projects")
                    }
                },
                title = {
                    Column {
                        Text(
                            text = project.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.clickable(onClick = onSyncNow)
                        ) {
                            Icon(
                                imageVector = when (syncStatus) {
                                    CloudSyncStatus.SYNCED -> Icons.Filled.CloudDone
                                    CloudSyncStatus.SYNCING, CloudSyncStatus.PENDING -> Icons.Filled.CloudSync
                                    CloudSyncStatus.ERROR -> Icons.Filled.CloudOff
                                },
                                contentDescription = syncStatus.label,
                                tint = when (syncStatus) {
                                    CloudSyncStatus.SYNCED -> MaterialTheme.colorScheme.primary
                                    CloudSyncStatus.ERROR -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.secondary
                                },
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = if (syncStatus == CloudSyncStatus.ERROR && syncErrorMessage != null)
                                    syncErrorMessage else syncStatus.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = onUndo,
                        enabled = canUndo,
                        modifier = Modifier.testTag("undo_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                    }
                    IconButton(
                        onClick = onRedo,
                        enabled = canRedo,
                        modifier = Modifier.testTag("redo_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                    }
                    IconButton(
                        onClick = { showBoardSettings = true },
                        modifier = Modifier.testTag("board_settings_button")
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = "Board settings")
                    }
                    IconButton(
                        onClick = onOpenBom,
                        modifier = Modifier.testTag("open_bom_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = stringResource(R.string.bom_export))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isExpandedLayout = maxWidth >= 780.dp

            Row(modifier = Modifier.fillMaxSize()) {
                // Left Component Library Rail on Tablets / Foldables
                if (isExpandedLayout) {
                    ComponentPalette(
                        isVerticalRail = true,
                        onQuickPlaceComponent = { placeAtViewportCenter(it) },
                        onDragStart = { type, offset ->
                            draggingType = type
                            dragRootOffset = offset
                        },
                        onDragMove = { offset -> dragRootOffset = offset },
                        onDragEnd = {
                            draggingType?.let { dropPalettePartAtRootOffset(it, dragRootOffset) }
                            draggingType = null
                        },
                        modifier = Modifier
                            .width(268.dp)
                            .fillMaxHeight()
                    )
                }

                // Center Interactive Canvas Column
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .onGloballyPositioned { coords ->
                                canvasRootPos = coords.positionInRoot()
                                canvasSize = coords.size
                            }
                    ) {
                        SchematicCanvas(
                            project = project,
                            issues = validationResult.issues,
                            selectedComponentUid = selectedComponentUid,
                            selectedWireUid = selectedWireUid,
                            activeTool = activeTool,
                            pendingWireStart = pendingWireStart,
                            viewport = viewport,
                            onViewportChange = { viewport = it },
                            onSelectComponent = { uid ->
                                onSelectComponent(uid)
                                if (uid != null && !isExpandedLayout) {
                                    activeSheetTab = 0
                                }
                            },
                            onSelectWire = { uid ->
                                onSelectWire(uid)
                                if (uid != null && !isExpandedLayout) {
                                    activeSheetTab = 0
                                }
                            },
                            onStartDragComponent = onStartDragComponent,
                            onDragComponentToGrid = onDragComponentToGrid,
                            onEndDragComponent = onEndDragComponent,
                            onTapPin = onTapPin,
                            isDarkTheme = isDarkTheme,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("schematic_canvas")
                        )

                        // Top-Left Tool Mode Switcher & Wire Layer Controls
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Row(
                                    modifier = Modifier.padding(4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    ToolPill(
                                        selected = activeTool == EditorTool.SELECT_MOVE,
                                        icon = Icons.Filled.OpenWith,
                                        label = "Move",
                                        onClick = { onSetTool(EditorTool.SELECT_MOVE) },
                                        testTag = "tool_move_button"
                                    )
                                    ToolPill(
                                        selected = activeTool == EditorTool.WIRE_ROUTE,
                                        icon = Icons.Filled.Cable,
                                        label = "Wire",
                                        onClick = { onSetTool(EditorTool.WIRE_ROUTE) },
                                        testTag = "tool_wire_button"
                                    )
                                }
                            }

                            if (activeTool == EditorTool.WIRE_ROUTE) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = if (pendingWireStart == null) "Tap Pin A to start trace"
                                            else "From ${pendingWireStart.refDes}.${pendingWireStart.pinName} -> Tap Pin B",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            listOf("TOP", "BOTTOM").forEach { lyr ->
                                                val active = wireLayer == lyr
                                                Surface(
                                                    color = if (active) MaterialTheme.colorScheme.primaryContainer
                                                    else MaterialTheme.colorScheme.surfaceVariant,
                                                    shape = RoundedCornerShape(6.dp),
                                                    modifier = Modifier.clickable { onSetWireLayer(lyr) }
                                                ) {
                                                    Text(
                                                        text = lyr,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }
                                            listOf(0.25, 0.50).forEach { w ->
                                                val active = wireTraceWidthMm == w
                                                Surface(
                                                    color = if (active) MaterialTheme.colorScheme.secondaryContainer
                                                    else MaterialTheme.colorScheme.surfaceVariant,
                                                    shape = RoundedCornerShape(6.dp),
                                                    modifier = Modifier.clickable { onSetWireWidth(w) }
                                                ) {
                                                    Text(
                                                        text = "${w}mm",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Top-Right Live ERC / DRC Pill + Zoom Reset
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp)
                        ) {
                            Surface(
                                color = if (validationResult.errorCount > 0) MaterialTheme.colorScheme.errorContainer
                                else MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (validationResult.errorCount > 0) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier
                                    .clickable {
                                        activeSheetTab = 1
                                        showSheet = true
                                    }
                                    .testTag("erc_status_chip")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                                ) {
                                    Icon(
                                        imageVector = when {
                                            validationResult.errorCount > 0 -> Icons.Filled.Error
                                            validationResult.warningCount > 0 -> Icons.Filled.Warning
                                            else -> Icons.Filled.CheckCircle
                                        },
                                        contentDescription = "ERC status",
                                        tint = when {
                                            validationResult.errorCount > 0 -> MaterialTheme.colorScheme.error
                                            validationResult.warningCount > 0 -> MaterialTheme.colorScheme.secondary
                                            else -> MaterialTheme.colorScheme.primary
                                        },
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "${validationResult.errorCount}E / ${validationResult.warningCount}W",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }

                            Surface(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.clickable {
                                    viewport = CanvasViewportState()
                                }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Filled.CenterFocusStrong, contentDescription = "Reset zoom", modifier = Modifier.size(15.dp))
                                    Text(
                                        text = "${(viewport.scale * 100).roundToInt()}%",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }

                        // Compact Phone Floating Inspector Action Bar when a part/wire is selected
                        if (!isExpandedLayout && (selectedComponent != null || selectedWire != null)) {
                            Surface(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(10.dp)
                                    .clickable {
                                        activeSheetTab = 0
                                        showSheet = true
                                    }
                                    .testTag("open_inspector_bar")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Tune,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = if (selectedComponent != null)
                                            "${selectedComponent.refDes} (${selectedComponent.valueText}) • Tap to Inspect / AI Check"
                                        else
                                            "Net ${selectedWire!!.netName} (${selectedWire.traceWidthMm}mm) • Tap to Edit",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    }

                    // Bottom Component Palette on Phones
                    if (!isExpandedLayout) {
                        ComponentPalette(
                            isVerticalRail = false,
                            onQuickPlaceComponent = { placeAtViewportCenter(it) },
                            onDragStart = { type, offset ->
                                draggingType = type
                                dragRootOffset = offset
                            },
                            onDragMove = { offset -> dragRootOffset = offset },
                            onDragEnd = {
                                draggingType?.let { dropPalettePartAtRootOffset(it, dragRootOffset) }
                                draggingType = null
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                        )
                    }
                }

                // Right Inspector / Validation Pane on Tablets / Foldables
                if (isExpandedLayout) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .width(320.dp)
                            .fillMaxHeight()
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            TabRow(selectedTabIndex = activeSheetTab) {
                                Tab(
                                    selected = activeSheetTab == 0,
                                    onClick = { activeSheetTab = 0 },
                                    text = { Text("Inspector") }
                                )
                                Tab(
                                    selected = activeSheetTab == 1,
                                    onClick = { activeSheetTab = 1 },
                                    text = { Text("ERC & AI") }
                                )
                            }
                            if (activeSheetTab == 0) {
                                when {
                                    selectedComponent != null -> ComponentInspectorPanel(
                                        component = selectedComponent,
                                        quickCheckState = quickCheckState,
                                        onUpdateComponent = onUpdateComponent,
                                        onRotate = onRotateComponent,
                                        onDelete = onDeleteSelected,
                                        onRunQuickCheck = onRunQuickCheck,
                                        onApplyQuickCheck = onApplyQuickCheck
                                    )
                                    selectedWire != null -> WireInspectorPanel(
                                        wire = selectedWire,
                                        onUpdateWire = onUpdateWire,
                                        onDelete = onDeleteSelected
                                    )
                                    else -> Box(
                                        modifier = Modifier.fillMaxSize().padding(20.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "Select any component or trace on the board to edit its value, footprint, MPN, or run an instant AI check.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            } else {
                                ValidationAuditPanel(
                                    validationResult = validationResult,
                                    deepAuditState = deepAuditState,
                                    onRunDeepAudit = onRunDeepAudit,
                                    onFocusComponent = { compUid ->
                                        onSelectComponent(compUid)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Dragging Ghost Pill Overlay while dragging from Palette to Canvas
            if (draggingType != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(12.dp),
                    shadowElevation = 8.dp,
                    modifier = Modifier.offset {
                        IntOffset(
                            (dragRootOffset.x - 60f).roundToInt(),
                            (dragRootOffset.y - 120f).roundToInt()
                        )
                    }
                ) {
                    Text(
                        text = "+ ${draggingType!!.displayName}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                TabRow(selectedTabIndex = activeSheetTab) {
                    Tab(
                        selected = activeSheetTab == 0,
                        onClick = { activeSheetTab = 0 },
                        text = { Text("Properties") }
                    )
                    Tab(
                        selected = activeSheetTab == 1,
                        onClick = { activeSheetTab = 1 },
                        text = { Text("ERC & AI Audit") }
                    )
                }
                if (activeSheetTab == 0) {
                    when {
                        selectedComponent != null -> ComponentInspectorPanel(
                            component = selectedComponent,
                            quickCheckState = quickCheckState,
                            onUpdateComponent = onUpdateComponent,
                            onRotate = onRotateComponent,
                            onDelete = {
                                onDeleteSelected()
                                showSheet = false
                            },
                            onRunQuickCheck = onRunQuickCheck,
                            onApplyQuickCheck = onApplyQuickCheck
                        )
                        selectedWire != null -> WireInspectorPanel(
                            wire = selectedWire,
                            onUpdateWire = onUpdateWire,
                            onDelete = {
                                onDeleteSelected()
                                showSheet = false
                            }
                        )
                        else -> Text(
                            text = "Tap a component or trace on the schematic canvas to inspect its parameters.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                } else {
                    ValidationAuditPanel(
                        validationResult = validationResult,
                        deepAuditState = deepAuditState,
                        onRunDeepAudit = onRunDeepAudit,
                        onFocusComponent = { compUid ->
                            onSelectComponent(compUid)
                            showSheet = false
                        }
                    )
                }
            }
        }
    }

    if (showBoardSettings) {
        BoardSettingsDialog(
            project = project,
            onDismiss = { showBoardSettings = false },
            onSave = onUpdateBoardSettings
        )
    }
}

@Composable
private fun ToolPill(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
