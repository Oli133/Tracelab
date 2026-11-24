package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BomSourcingSuggestion
import com.example.data.DeepErcAuditResult
import com.example.data.PcbAiRepository
import com.example.data.QuickComponentCheckResult
import com.example.data.SchematicRepository
import com.example.data.StarterTemplates
import com.example.model.ComponentType
import com.example.model.EditorTool
import com.example.model.SchematicComponent
import com.example.model.SchematicProject
import com.example.model.SchematicWire
import com.example.model.StarterTemplate
import com.example.model.ValidationResult
import com.example.validation.SchematicValidator
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

sealed interface AiTaskState<out T> {
    data object Idle : AiTaskState<Nothing>
    data object Loading : AiTaskState<Nothing>
    data class Success<T>(val data: T) : AiTaskState<T>
    data class Error(val message: String) : AiTaskState<Nothing>
}

enum class CloudSyncStatus(val label: String) {
    SYNCED("Cloud Synced"),
    SYNCING("Syncing..."),
    PENDING("Unsaved Edits"),
    ERROR("Sync Error")
}

data class PinAnchor(
    val componentUid: String,
    val pinIndex: Int,
    val refDes: String,
    val pinName: String
)

class TraceLabViewModel(
    private val repository: SchematicRepository,
    private val aiRepository: PcbAiRepository,
    private val currentUserId: String
) : ViewModel() {

    val projectsState: StateFlow<UiState<List<SchematicProject>>> = repository.observeSchematics(currentUserId)
        .map<List<SchematicProject>, UiState<List<SchematicProject>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing schematics", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load cloud schematics"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    private val _activeProject = MutableStateFlow<SchematicProject?>(null)
    val activeProject: StateFlow<SchematicProject?> = _activeProject.asStateFlow()

    private val _validationResult = MutableStateFlow(
        ValidationResult(emptyList(), 0, 0, "Ready")
    )
    val validationResult: StateFlow<ValidationResult> = _validationResult.asStateFlow()

    private val _selectedComponentUid = MutableStateFlow<String?>(null)
    val selectedComponentUid: StateFlow<String?> = _selectedComponentUid.asStateFlow()

    private val _selectedWireUid = MutableStateFlow<String?>(null)
    val selectedWireUid: StateFlow<String?> = _selectedWireUid.asStateFlow()

    private val _activeTool = MutableStateFlow(EditorTool.SELECT_MOVE)
    val activeTool: StateFlow<EditorTool> = _activeTool.asStateFlow()

    private val _pendingWireStart = MutableStateFlow<PinAnchor?>(null)
    val pendingWireStart: StateFlow<PinAnchor?> = _pendingWireStart.asStateFlow()

    private val _wireLayer = MutableStateFlow("TOP")
    val wireLayer: StateFlow<String> = _wireLayer.asStateFlow()

    private val _wireTraceWidthMm = MutableStateFlow(0.25)
    val wireTraceWidthMm: StateFlow<Double> = _wireTraceWidthMm.asStateFlow()

    private val _syncStatus = MutableStateFlow(CloudSyncStatus.SYNCED)
    val syncStatus: StateFlow<CloudSyncStatus> = _syncStatus.asStateFlow()

    private val _syncErrorMessage = MutableStateFlow<String?>(null)
    val syncErrorMessage: StateFlow<String?> = _syncErrorMessage.asStateFlow()

    private val _quickCheckState = MutableStateFlow<AiTaskState<QuickComponentCheckResult>>(AiTaskState.Idle)
    val quickCheckState: StateFlow<AiTaskState<QuickComponentCheckResult>> = _quickCheckState.asStateFlow()

    private val _deepAuditState = MutableStateFlow<AiTaskState<DeepErcAuditResult>>(AiTaskState.Idle)
    val deepAuditState: StateFlow<AiTaskState<DeepErcAuditResult>> = _deepAuditState.asStateFlow()

    private val _bomAdvisorState = MutableStateFlow<AiTaskState<List<BomSourcingSuggestion>>>(AiTaskState.Idle)
    val bomAdvisorState: StateFlow<AiTaskState<List<BomSourcingSuggestion>>> = _bomAdvisorState.asStateFlow()

    private val undoStack = ArrayDeque<SchematicProject>()
    private val redoStack = ArrayDeque<SchematicProject>()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private var autoSyncJob: Job? = null
    private var dragSnapshotProject: SchematicProject? = null

    fun createProject(
        name: String,
        description: String,
        template: StarterTemplate,
        layerCount: Long,
        onCreated: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val draft = StarterTemplates.buildProject(
                userId = currentUserId,
                name = name,
                description = description,
                template = template,
                layerCount = layerCount
            )
            repository.createSchematic(draft)
                .onSuccess { newId ->
                    val created = draft.copy(id = newId)
                    openProject(created)
                    onCreated(newId)
                }
                .onFailure { err ->
                    onError(err.localizedMessage ?: "Failed to create schematic in cloud")
                }
        }
    }

    fun deleteProject(projectId: String, onError: (String) -> Unit) {
        viewModelScope.launch {
            repository.deleteSchematic(projectId)
                .onSuccess {
                    if (_activeProject.value?.id == projectId) {
                        _activeProject.value = null
                    }
                }
                .onFailure { err ->
                    onError(err.localizedMessage ?: "Failed to delete project")
                }
        }
    }

    fun openProject(project: SchematicProject) {
        autoSyncJob?.cancel()
        undoStack.clear()
        redoStack.clear()
        updateUndoRedoFlags()
        _selectedComponentUid.value = null
        _selectedWireUid.value = null
        _pendingWireStart.value = null
        _activeTool.value = EditorTool.SELECT_MOVE
        _quickCheckState.value = AiTaskState.Idle
        _deepAuditState.value = AiTaskState.Idle
        _bomAdvisorState.value = AiTaskState.Idle
        _syncStatus.value = CloudSyncStatus.SYNCED
        _syncErrorMessage.value = null
        _activeProject.value = project
        _validationResult.value = SchematicValidator.validate(project)
    }

    fun setEditorTool(tool: EditorTool) {
        _activeTool.value = tool
        if (tool != EditorTool.WIRE_ROUTE) {
            _pendingWireStart.value = null
        }
    }

    fun setWireLayer(layer: String) {
        _wireLayer.value = layer
    }

    fun setWireTraceWidth(widthMm: Double) {
        _wireTraceWidthMm.value = widthMm
    }

    fun selectComponent(uid: String?) {
        _selectedComponentUid.value = uid
        if (uid != null) {
            _selectedWireUid.value = null
            _quickCheckState.value = AiTaskState.Idle
        }
    }

    fun selectWire(uid: String?) {
        _selectedWireUid.value = uid
        if (uid != null) {
            _selectedComponentUid.value = null
        }
    }

    fun addComponentAtGrid(type: ComponentType, gridX: Long, gridY: Long) {
        val current = _activeProject.value ?: return
        if (current.components.size >= 120) return
        val nextIndex = nextRefNumber(current.components, type.refPrefix)
        val newComp = SchematicComponent(
            uid = "comp_${UUID.randomUUID().toString().take(8)}",
            refDes = "${type.refPrefix}$nextIndex",
            type = type.name,
            label = type.displayName,
            valueText = type.defaultValue,
            footprint = type.defaultFootprint,
            manufacturerPart = type.defaultMpn,
            unitPriceUsd = type.defaultPriceUsd,
            gridX = gridX.coerceAtLeast(0L),
            gridY = gridY.coerceAtLeast(0L),
            rotationDeg = 0L,
            voltageRatingV = type.defaultVoltageV,
            powerRatingMw = type.defaultPowerMw
        )
        val updated = current.copy(components = current.components + newComp)
        applyProjectMutation(updated, pushUndo = true)
        _selectedComponentUid.value = newComp.uid
        _selectedWireUid.value = null
    }

    fun startComponentDrag() {
        dragSnapshotProject = _activeProject.value
    }

    fun moveComponentToGrid(componentUid: String, newGridX: Long, newGridY: Long) {
        val current = _activeProject.value ?: return
        val maxX = (current.boardWidthMm / current.gridSnapMm).toLong().coerceAtLeast(10L)
        val maxY = (current.boardHeightMm / current.gridSnapMm).toLong().coerceAtLeast(10L)
        val clampedX = newGridX.coerceIn(0L, maxX)
        val clampedY = newGridY.coerceIn(0L, maxY)
        val existing = current.components.firstOrNull { it.uid == componentUid } ?: return
        if (existing.gridX == clampedX && existing.gridY == clampedY) return

        val updatedList = current.components.map { comp ->
            if (comp.uid == componentUid) comp.copy(gridX = clampedX, gridY = clampedY) else comp
        }
        val updated = current.copy(components = updatedList)
        val validation = SchematicValidator.validate(updated)
        _activeProject.value = updated.copy(
            componentCount = updatedList.size.toLong(),
            wireCount = updated.wires.size.toLong(),
            validationErrorCount = validation.errorCount.toLong(),
            validationSummary = validation.summaryText
        )
        _validationResult.value = validation
    }

    fun endComponentDrag() {
        val beforeDrag = dragSnapshotProject
        val current = _activeProject.value
        dragSnapshotProject = null
        if (beforeDrag != null && current != null && beforeDrag.components != current.components) {
            pushUndoState(beforeDrag)
            scheduleAutoSync()
        }
    }

    fun rotateSelectedComponent() {
        val uid = _selectedComponentUid.value ?: return
        val current = _activeProject.value ?: return
        val updated = current.copy(
            components = current.components.map { comp ->
                if (comp.uid == uid) {
                    comp.copy(rotationDeg = (comp.rotationDeg + 90L) % 360L)
                } else comp
            }
        )
        applyProjectMutation(updated, pushUndo = true)
    }

    fun updateSelectedComponent(
        refDes: String,
        label: String,
        valueText: String,
        footprint: String,
        manufacturerPart: String,
        unitPriceUsd: Double,
        voltageRatingV: Double
    ) {
        val uid = _selectedComponentUid.value ?: return
        val current = _activeProject.value ?: return
        val updated = current.copy(
            components = current.components.map { comp ->
                if (comp.uid == uid) {
                    comp.copy(
                        refDes = refDes.trim().take(16).ifBlank { comp.refDes },
                        label = label.trim().take(48).ifBlank { comp.label },
                        valueText = valueText.trim().take(32),
                        footprint = footprint.trim().take(24),
                        manufacturerPart = manufacturerPart.trim().take(48),
                        unitPriceUsd = unitPriceUsd.coerceIn(0.0, 9999.0),
                        voltageRatingV = voltageRatingV.coerceIn(0.0, 1000.0)
                    )
                } else comp
            }
        )
        applyProjectMutation(updated, pushUndo = true)
    }

    fun deleteSelectedElement() {
        val current = _activeProject.value ?: return
        val compUid = _selectedComponentUid.value
        val wireUid = _selectedWireUid.value
        if (compUid != null) {
            val updated = current.copy(
                components = current.components.filterNot { it.uid == compUid },
                wires = current.wires.filterNot { it.fromCompUid == compUid || it.toCompUid == compUid }
            )
            _selectedComponentUid.value = null
            applyProjectMutation(updated, pushUndo = true)
        } else if (wireUid != null) {
            val updated = current.copy(
                wires = current.wires.filterNot { it.uid == wireUid }
            )
            _selectedWireUid.value = null
            applyProjectMutation(updated, pushUndo = true)
        }
    }

    fun tapPinForWire(componentUid: String, pinIndex: Int) {
        val current = _activeProject.value ?: return
        val comp = current.components.firstOrNull { it.uid == componentUid } ?: return
        val pinName = comp.resolvedType().pins.firstOrNull { it.index == pinIndex }?.name ?: "$pinIndex"
        val anchor = PinAnchor(componentUid, pinIndex, comp.refDes, pinName)

        val start = _pendingWireStart.value
        if (start == null) {
            _pendingWireStart.value = anchor
            return
        }

        if (start.componentUid == anchor.componentUid && start.pinIndex == anchor.pinIndex) {
            _pendingWireStart.value = null
            return
        }

        // Prevent duplicate wire between same pins
        val alreadyExists = current.wires.any { w ->
            (w.fromCompUid == start.componentUid && w.fromPinIndex.toInt() == start.pinIndex &&
                w.toCompUid == anchor.componentUid && w.toPinIndex.toInt() == anchor.pinIndex) ||
                (w.toCompUid == start.componentUid && w.toPinIndex.toInt() == start.pinIndex &&
                    w.fromCompUid == anchor.componentUid && w.fromPinIndex.toInt() == anchor.pinIndex)
        }

        if (alreadyExists || current.wires.size >= 300) {
            _pendingWireStart.value = null
            return
        }

        val inferredNet = inferNetName(current, start, anchor)
        val newWire = SchematicWire(
            uid = "wire_${UUID.randomUUID().toString().take(8)}",
            netName = inferredNet,
            fromCompUid = start.componentUid,
            fromPinIndex = start.pinIndex.toLong(),
            toCompUid = anchor.componentUid,
            toPinIndex = anchor.pinIndex.toLong(),
            traceWidthMm = _wireTraceWidthMm.value,
            copperLayer = _wireLayer.value
        )
        _pendingWireStart.value = null
        val updated = current.copy(wires = current.wires + newWire)
        applyProjectMutation(updated, pushUndo = true)
        _selectedWireUid.value = newWire.uid
        _selectedComponentUid.value = null
    }

    fun updateSelectedWire(netName: String, traceWidthMm: Double, copperLayer: String) {
        val wireUid = _selectedWireUid.value ?: return
        val current = _activeProject.value ?: return
        val updated = current.copy(
            wires = current.wires.map { w ->
                if (w.uid == wireUid) {
                    w.copy(
                        netName = netName.trim().take(24).ifBlank { w.netName },
                        traceWidthMm = traceWidthMm.coerceIn(0.1, 5.0),
                        copperLayer = if (copperLayer == "BOTTOM") "BOTTOM" else "TOP"
                    )
                } else w
            }
        )
        applyProjectMutation(updated, pushUndo = true)
    }

    fun updateBoardSettings(
        name: String,
        description: String,
        boardWidthMm: Double,
        boardHeightMm: Double,
        layerCount: Long
    ) {
        val current = _activeProject.value ?: return
        val updated = current.copy(
            name = name.trim().take(80).ifBlank { current.name },
            description = description.trim().take(300),
            boardWidthMm = boardWidthMm.coerceIn(10.0, 500.0),
            boardHeightMm = boardHeightMm.coerceIn(10.0, 500.0),
            layerCount = layerCount.coerceIn(1L, 6L)
        )
        applyProjectMutation(updated, pushUndo = true)
    }

    fun undo() {
        val current = _activeProject.value ?: return
        if (undoStack.isEmpty()) return
        redoStack.addLast(current)
        val previous = undoStack.removeLast()
        updateUndoRedoFlags()
        val validation = SchematicValidator.validate(previous)
        _activeProject.value = previous
        _validationResult.value = validation
        scheduleAutoSync()
    }

    fun redo() {
        val current = _activeProject.value ?: return
        if (redoStack.isEmpty()) return
        undoStack.addLast(current)
        val next = redoStack.removeLast()
        updateUndoRedoFlags()
        val validation = SchematicValidator.validate(next)
        _activeProject.value = next
        _validationResult.value = validation
        scheduleAutoSync()
    }

    fun syncActiveProjectNow() {
        autoSyncJob?.cancel()
        val project = _activeProject.value ?: return
        if (project.id.isBlank()) return
        viewModelScope.launch {
            performCloudSync(project)
        }
    }

    fun runQuickComponentCheck() {
        val project = _activeProject.value ?: return
        val compUid = _selectedComponentUid.value ?: return
        val comp = project.components.firstOrNull { it.uid == compUid } ?: return
        viewModelScope.launch {
            _quickCheckState.value = AiTaskState.Loading
            aiRepository.runQuickComponentCheck(comp, project)
                .onSuccess { res ->
                    _quickCheckState.value = AiTaskState.Success(res)
                }
                .onFailure { err ->
                    _quickCheckState.value = AiTaskState.Error(
                        err.localizedMessage ?: "Quick component check failed."
                    )
                }
        }
    }

    fun applyQuickCheckSuggestion(result: QuickComponentCheckResult) {
        val uid = _selectedComponentUid.value ?: return
        val current = _activeProject.value ?: return
        val comp = current.components.firstOrNull { it.uid == uid } ?: return
        updateSelectedComponent(
            refDes = comp.refDes,
            label = comp.label,
            valueText = result.suggestedValue.ifBlank { comp.valueText },
            footprint = comp.footprint,
            manufacturerPart = result.suggestedMpn.ifBlank { comp.manufacturerPart },
            unitPriceUsd = comp.unitPriceUsd,
            voltageRatingV = comp.voltageRatingV
        )
    }

    fun runDeepSchematicAudit() {
        val project = _activeProject.value ?: return
        viewModelScope.launch {
            _deepAuditState.value = AiTaskState.Loading
            aiRepository.runDeepSchematicAudit(project)
                .onSuccess { res ->
                    _deepAuditState.value = AiTaskState.Success(res)
                }
                .onFailure { err ->
                    _deepAuditState.value = AiTaskState.Error(
                        err.localizedMessage ?: "Deep schematic audit failed."
                    )
                }
        }
    }

    fun enrichBomWithAi() {
        val project = _activeProject.value ?: return
        if (project.components.isEmpty()) return
        viewModelScope.launch {
            _bomAdvisorState.value = AiTaskState.Loading
            aiRepository.enrichBomParts(project.components)
                .onSuccess { suggestions ->
                    _bomAdvisorState.value = AiTaskState.Success(suggestions)
                }
                .onFailure { err ->
                    _bomAdvisorState.value = AiTaskState.Error(
                        err.localizedMessage ?: "BOM sourcing advisor failed."
                    )
                }
        }
    }

    fun applyBomSuggestions(suggestions: List<BomSourcingSuggestion>) {
        val current = _activeProject.value ?: return
        val byRef = suggestions.associateBy { it.refDes.uppercase() }
        val updated = current.copy(
            components = current.components.map { comp ->
                val match = byRef[comp.refDes.uppercase()]
                if (match != null && match.suggestedMpn.isNotBlank()) {
                    comp.copy(
                        manufacturerPart = match.suggestedMpn.take(48),
                        unitPriceUsd = match.estimatedUnitPriceUsd.coerceIn(0.0, 9999.0)
                    )
                } else comp
            }
        )
        applyProjectMutation(updated, pushUndo = true)
    }

    private fun applyProjectMutation(newProject: SchematicProject, pushUndo: Boolean) {
        val previous = _activeProject.value
        if (pushUndo && previous != null) {
            pushUndoState(previous)
        }
        val validation = SchematicValidator.validate(newProject)
        val finalProject = newProject.copy(
            componentCount = newProject.components.size.toLong(),
            wireCount = newProject.wires.size.toLong(),
            validationErrorCount = validation.errorCount.toLong(),
            validationSummary = validation.summaryText
        )
        _activeProject.value = finalProject
        _validationResult.value = validation
        scheduleAutoSync()
    }

    private fun pushUndoState(state: SchematicProject) {
        if (undoStack.size >= 25) {
            undoStack.removeFirst()
        }
        undoStack.addLast(state)
        redoStack.clear()
        updateUndoRedoFlags()
    }

    private fun updateUndoRedoFlags() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    private fun scheduleAutoSync() {
        _syncStatus.value = CloudSyncStatus.PENDING
        _syncErrorMessage.value = null
        autoSyncJob?.cancel()
        autoSyncJob = viewModelScope.launch {
            delay(1100L)
            val latest = _activeProject.value ?: return@launch
            if (latest.id.isNotBlank()) {
                performCloudSync(latest)
            }
        }
    }

    private suspend fun performCloudSync(project: SchematicProject) {
        _syncStatus.value = CloudSyncStatus.SYNCING
        repository.updateSchematic(project)
            .onSuccess {
                _syncStatus.value = CloudSyncStatus.SYNCED
                _syncErrorMessage.value = null
            }
            .onFailure { err ->
                _syncStatus.value = CloudSyncStatus.ERROR
                _syncErrorMessage.value = err.localizedMessage ?: "Cloud sync failed"
            }
    }

    private fun nextRefNumber(components: List<SchematicComponent>, prefix: String): Int {
        val existingNums = components
            .filter { it.refDes.startsWith(prefix, ignoreCase = true) }
            .mapNotNull { it.refDes.drop(prefix.length).toIntOrNull() }
            .toSet()
        var candidate = 1
        while (candidate in existingNums) {
            candidate++
        }
        return candidate
    }

    private fun inferNetName(project: SchematicProject, a: PinAnchor, b: PinAnchor): String {
        val compA = project.components.firstOrNull { it.uid == a.componentUid }
        val compB = project.components.firstOrNull { it.uid == b.componentUid }
        return when {
            compA?.resolvedType() == ComponentType.GND_RAIL || compB?.resolvedType() == ComponentType.GND_RAIL -> "GND"
            compA?.resolvedType() == ComponentType.VCC_RAIL -> "VCC_${compA.valueText.replace(".", "_")}"
            compB?.resolvedType() == ComponentType.VCC_RAIL -> "VCC_${compB.valueText.replace(".", "_")}"
            a.pinName.contains("GND", true) || b.pinName.contains("GND", true) -> "GND"
            else -> "NET_${project.wires.size + 1}"
        }
    }

    private companion object {
        const val TAG = "TraceLabVM"
    }
}
