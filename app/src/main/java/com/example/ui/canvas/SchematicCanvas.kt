package com.example.ui.canvas

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import com.example.model.ComponentType
import com.example.model.EditorTool
import com.example.model.SchematicComponent
import com.example.model.SchematicProject
import com.example.model.SchematicWire
import com.example.model.ValidationIssue
import com.example.model.ValidationSeverity
import com.example.ui.theme.CadGridMajor
import com.example.ui.theme.CadGridMinor
import com.example.ui.theme.CopperAmber
import com.example.ui.theme.DiagnosticRed
import com.example.ui.theme.SolderEmerald
import com.example.ui.theme.TraceCyan
import com.example.viewmodel.PinAnchor
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToLong
import kotlin.math.sin

data class CanvasViewportState(
    val scale: Float = 1.0f,
    val offsetX: Float = 60f,
    val offsetY: Float = 80f
)

@Composable
fun SchematicCanvas(
    project: SchematicProject,
    issues: List<ValidationIssue>,
    selectedComponentUid: String?,
    selectedWireUid: String?,
    activeTool: EditorTool,
    pendingWireStart: PinAnchor?,
    viewport: CanvasViewportState,
    onViewportChange: (CanvasViewportState) -> Unit,
    onSelectComponent: (String?) -> Unit,
    onSelectWire: (String?) -> Unit,
    onStartDragComponent: () -> Unit,
    onDragComponentToGrid: (String, Long, Long) -> Unit,
    onEndDragComponent: () -> Unit,
    onTapPin: (String, Int) -> Unit,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    val baseGridPx = 32f

    // Keep latest callbacks/state accessible inside pointerInput without restarting gesture detector
    val latestProject by rememberUpdatedState(project)
    val latestTool by rememberUpdatedState(activeTool)
    val latestViewport by rememberUpdatedState(viewport)

    val bgColor = MaterialTheme.colorScheme.background
    val silkColor = MaterialTheme.colorScheme.onBackground
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary
    val copperColor = CopperAmber
    val bottomCopperColor = TraceCyan
    val errorColor = DiagnosticRed
    val gridMinorColor = if (isDarkTheme) CadGridMinor else Color(0xFFDCE4EF)
    val gridMajorColor = if (isDarkTheme) CadGridMajor else Color(0xFFB8C7DB)
    val boardFill = if (isDarkTheme) Color(0xFF0A141D) else Color(0xFFEDF3FA)

    val issueMap = remember(issues) {
        issues.filter { it.componentUid != null }.groupBy { it.componentUid!! }
    }

    val refPaint = remember(silkColor) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = silkColor.toArgb()
            textSize = 26f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
    }
    val valPaint = remember(mutedColor) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = mutedColor.toArgb()
            textSize = 21f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }
    }
    val netPaint = remember(copperColor) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = copperColor.toArgb()
            textSize = 19f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
    }

    var draggingCompUid by remember { mutableStateOf<String?>(null) }
    var dragStartOffsetWorldX by remember { mutableFloatStateOf(0f) }
    var dragStartOffsetWorldY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val firstDown = awaitFirstDown(requireUnconsumed = false)
                        val downPos = firstDown.position
                        val vp = latestViewport
                        val worldDown = Offset(
                            (downPos.x - vp.offsetX) / vp.scale,
                            (downPos.y - vp.offsetY) / vp.scale
                        )

                        val hitPin = findHitPin(latestProject, worldDown, baseGridPx)
                        val hitComp = findHitComponent(latestProject, worldDown, baseGridPx)

                        var isDraggingComponent = false
                        var movedBeyondSlop = false
                        var isMultiTouch = false
                        val touchSlop = 10f

                        if (latestTool == EditorTool.SELECT_MOVE && hitComp != null && hitPin == null) {
                            draggingCompUid = hitComp.uid
                            dragStartOffsetWorldX = worldDown.x - hitComp.gridX * baseGridPx
                            dragStartOffsetWorldY = worldDown.y - hitComp.gridY * baseGridPx
                        } else {
                            draggingCompUid = null
                        }

                        var currentVp = vp
                        while (true) {
                            val event = awaitPointerEvent()
                            val activePointers = event.changes.filter { it.pressed }
                            if (activePointers.isEmpty()) {
                                // Finger(s) lifted
                                if (isDraggingComponent) {
                                    onEndDragComponent()
                                } else if (!movedBeyondSlop && !isMultiTouch) {
                                    // Clean single tap
                                    val tapWorld = Offset(
                                        (downPos.x - currentVp.offsetX) / currentVp.scale,
                                        (downPos.y - currentVp.offsetY) / currentVp.scale
                                    )
                                    val tappedPin = findHitPin(latestProject, tapWorld, baseGridPx)
                                    val tappedComp = findHitComponent(latestProject, tapWorld, baseGridPx)
                                    val tappedWire = findHitWire(latestProject, tapWorld, baseGridPx)

                                    when {
                                        latestTool == EditorTool.WIRE_ROUTE && tappedPin != null -> {
                                            onTapPin(tappedPin.first, tappedPin.second)
                                        }
                                        tappedComp != null -> {
                                            onSelectComponent(tappedComp.uid)
                                        }
                                        tappedWire != null -> {
                                            onSelectWire(tappedWire.uid)
                                        }
                                        tappedPin != null -> {
                                            onTapPin(tappedPin.first, tappedPin.second)
                                        }
                                        else -> {
                                            onSelectComponent(null)
                                            onSelectWire(null)
                                        }
                                    }
                                }
                                draggingCompUid = null
                                break
                            }

                            if (activePointers.size >= 2) {
                                if (isDraggingComponent) {
                                    onEndDragComponent()
                                    isDraggingComponent = false
                                    draggingCompUid = null
                                }
                                isMultiTouch = true
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()
                                val centroid = event.calculateCentroid(useCurrent = true)
                                val newScale = (currentVp.scale * zoomChange).coerceIn(0.45f, 3.0f)
                                val scaleRatio = newScale / currentVp.scale
                                val newOffX = centroid.x - (centroid.x - currentVp.offsetX) * scaleRatio + panChange.x
                                val newOffY = centroid.y - (centroid.y - currentVp.offsetY) * scaleRatio + panChange.y
                                currentVp = CanvasViewportState(newScale, newOffX, newOffY)
                                onViewportChange(currentVp)
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            } else {
                                val change: PointerInputChange = activePointers.first()
                                val deltaFromDown = change.position - downPos
                                if (!movedBeyondSlop && hypot(deltaFromDown.x, deltaFromDown.y) > touchSlop) {
                                    movedBeyondSlop = true
                                    if (draggingCompUid != null) {
                                        isDraggingComponent = true
                                        onStartDragComponent()
                                        onSelectComponent(draggingCompUid)
                                    }
                                }
                                if (movedBeyondSlop) {
                                    if (isDraggingComponent && draggingCompUid != null) {
                                        val worldNow = Offset(
                                            (change.position.x - currentVp.offsetX) / currentVp.scale,
                                            (change.position.y - currentVp.offsetY) / currentVp.scale
                                        )
                                        val gx = ((worldNow.x - dragStartOffsetWorldX) / baseGridPx).roundToLong()
                                        val gy = ((worldNow.y - dragStartOffsetWorldY) / baseGridPx).roundToLong()
                                        onDragComponentToGrid(draggingCompUid!!, gx, gy)
                                    } else {
                                        val pan = change.position - change.previousPosition
                                        currentVp = currentVp.copy(
                                            offsetX = currentVp.offsetX + pan.x,
                                            offsetY = currentVp.offsetY + pan.y
                                        )
                                        onViewportChange(currentVp)
                                    }
                                    change.consume()
                                }
                            }
                        }
                    }
                }
        ) {
            val scale = viewport.scale
            val stepPx = baseGridPx * scale

            // 1. Infinite Engineering Grid
            val startX = ((viewport.offsetX % stepPx) + stepPx) % stepPx
            val startY = ((viewport.offsetY % stepPx) + stepPx) % stepPx

            var gx = startX
            var colIdx = ((startX - viewport.offsetX) / stepPx).roundToLong()
            while (gx < size.width) {
                val isMajor = colIdx % 5L == 0L
                drawLine(
                    color = if (isMajor) gridMajorColor else gridMinorColor,
                    start = Offset(gx, 0f),
                    end = Offset(gx, size.height),
                    strokeWidth = if (isMajor) 1.5f else 0.8f
                )
                gx += stepPx
                colIdx++
            }

            var gy = startY
            var rowIdx = ((startY - viewport.offsetY) / stepPx).roundToLong()
            while (gy < size.height) {
                val isMajor = rowIdx % 5L == 0L
                drawLine(
                    color = if (isMajor) gridMajorColor else gridMinorColor,
                    start = Offset(0f, gy),
                    end = Offset(size.width, gy),
                    strokeWidth = if (isMajor) 1.5f else 0.8f
                )
                gy += stepPx
                rowIdx++
            }

            // 2. PCB Board Outline & Mounting Holes
            val boardCols = (project.boardWidthMm / project.gridSnapMm).toFloat()
            val boardRows = (project.boardHeightMm / project.gridSnapMm).toFloat()
            val boardTopLeft = Offset(viewport.offsetX, viewport.offsetY)
            val boardSize = Size(boardCols * stepPx, boardRows * stepPx)

            drawRoundRect(
                color = boardFill.copy(alpha = 0.65f),
                topLeft = boardTopLeft,
                size = boardSize,
                cornerRadius = CornerRadius(10f * scale, 10f * scale)
            )
            drawRoundRect(
                color = SolderEmerald.copy(alpha = 0.75f),
                topLeft = boardTopLeft,
                size = boardSize,
                cornerRadius = CornerRadius(10f * scale, 10f * scale),
                style = Stroke(width = 2.5f * scale)
            )

            // 4 corner mounting holes
            val holeInset = 14f * scale
            val holeCorners = listOf(
                Offset(boardTopLeft.x + holeInset, boardTopLeft.y + holeInset),
                Offset(boardTopLeft.x + boardSize.width - holeInset, boardTopLeft.y + holeInset),
                Offset(boardTopLeft.x + holeInset, boardTopLeft.y + boardSize.height - holeInset),
                Offset(boardTopLeft.x + boardSize.width - holeInset, boardTopLeft.y + boardSize.height - holeInset)
            )
            holeCorners.forEach { center ->
                drawCircle(color = copperColor, radius = 6f * scale, center = center)
                drawCircle(color = bgColor, radius = 3f * scale, center = center)
            }

            val compMap = project.components.associateBy { it.uid }

            // 3. Draw Wires / Copper Traces
            project.wires.forEach { wire ->
                val fromComp = compMap[wire.fromCompUid] ?: return@forEach
                val toComp = compMap[wire.toCompUid] ?: return@forEach
                val p1 = pinScreenPos(fromComp, wire.fromPinIndex.toInt(), baseGridPx, viewport)
                val p2 = pinScreenPos(toComp, wire.toPinIndex.toInt(), baseGridPx, viewport)
                val isSelected = wire.uid == selectedWireUid
                val traceColor = when {
                    isSelected -> Color.White
                    wire.copperLayer == "BOTTOM" -> bottomCopperColor
                    else -> copperColor
                }
                val strokeW = ((wire.traceWidthMm.toFloat() * 14f) * scale).coerceIn(2.5f, 16f)
                val midX = (p1.x + p2.x) / 2f

                val path = Path().apply {
                    moveTo(p1.x, p1.y)
                    lineTo(midX, p1.y)
                    lineTo(midX, p2.y)
                    lineTo(p2.x, p2.y)
                }

                if (isSelected) {
                    drawPath(
                        path = path,
                        color = primaryColor.copy(alpha = 0.4f),
                        style = Stroke(width = strokeW + 8f * scale, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }

                drawPath(
                    path = path,
                    color = traceColor,
                    style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                // Solder junctions at endpoints
                drawCircle(color = traceColor, radius = 4.5f * scale, center = p1)
                drawCircle(color = traceColor, radius = 4.5f * scale, center = p2)

                // Net label near midpoint
                if (scale >= 0.65f) {
                    netPaint.color = traceColor.toArgb()
                    netPaint.textSize = (16f * scale).coerceIn(11f, 26f)
                    drawContext.canvas.nativeCanvas.drawText(
                        wire.netName,
                        midX + 4f * scale,
                        ((p1.y + p2.y) / 2f) - 5f * scale,
                        netPaint
                    )
                }
            }

            // 4. Draw Components & Pins
            project.components.forEach { comp ->
                val isSelected = comp.uid == selectedComponentUid
                val compIssues = issueMap[comp.uid].orEmpty()
                val hasError = compIssues.any { it.severity == ValidationSeverity.ERROR }
                val hasWarning = compIssues.any { it.severity == ValidationSeverity.WARNING }

                val center = Offset(
                    viewport.offsetX + comp.gridX * stepPx,
                    viewport.offsetY + comp.gridY * stepPx
                )

                // Selection Halo / Validation Aura
                if (isSelected) {
                    drawRoundRect(
                        color = primaryColor.copy(alpha = 0.16f),
                        topLeft = Offset(center.x - 52f * scale, center.y - 38f * scale),
                        size = Size(104f * scale, 76f * scale),
                        cornerRadius = CornerRadius(12f * scale, 12f * scale)
                    )
                    drawRoundRect(
                        color = primaryColor,
                        topLeft = Offset(center.x - 52f * scale, center.y - 38f * scale),
                        size = Size(104f * scale, 76f * scale),
                        cornerRadius = CornerRadius(12f * scale, 12f * scale),
                        style = Stroke(width = 2f * scale)
                    )
                } else if (hasError || hasWarning) {
                    val badgeColor = if (hasError) errorColor else copperColor
                    drawRoundRect(
                        color = badgeColor.copy(alpha = 0.6f),
                        topLeft = Offset(center.x - 48f * scale, center.y - 34f * scale),
                        size = Size(96f * scale, 68f * scale),
                        cornerRadius = CornerRadius(10f * scale, 10f * scale),
                        style = Stroke(width = 1.6f * scale)
                    )
                }

                // Component Symbol Geometry
                drawComponentSymbol(
                    comp = comp,
                    center = center,
                    scale = scale,
                    baseGridPx = baseGridPx,
                    silkColor = silkColor,
                    accentColor = primaryColor,
                    copperColor = copperColor,
                    ledColor = SolderEmerald
                )

                // Pins
                comp.resolvedType().pins.forEach { pin ->
                    val pinPos = pinScreenPos(comp, pin.index, baseGridPx, viewport)
                    val isPendingStart = pendingWireStart?.componentUid == comp.uid &&
                        pendingWireStart.pinIndex == pin.index
                    val pinRadius = when {
                        isPendingStart -> 8.5f * scale
                        activeTool == EditorTool.WIRE_ROUTE -> 6.5f * scale
                        else -> 4.5f * scale
                    }
                    val pinColor = when {
                        isPendingStart -> primaryColor
                        activeTool == EditorTool.WIRE_ROUTE -> copperColor
                        else -> copperColor.copy(alpha = 0.9f)
                    }
                    drawCircle(color = pinColor, radius = pinRadius, center = pinPos)
                    drawCircle(color = bgColor, radius = pinRadius * 0.42f, center = pinPos)
                }

                // RefDes & Value Silkscreen Labels
                refPaint.color = if (isSelected) primaryColor.toArgb() else silkColor.toArgb()
                refPaint.textSize = (20f * scale).coerceIn(12f, 34f)
                valPaint.color = mutedColor.toArgb()
                valPaint.textSize = (16f * scale).coerceIn(10f, 28f)

                drawContext.canvas.nativeCanvas.drawText(
                    comp.refDes,
                    center.x - 28f * scale,
                    center.y - 24f * scale,
                    refPaint
                )
                if (scale >= 0.55f) {
                    drawContext.canvas.nativeCanvas.drawText(
                        comp.valueText,
                        center.x - 28f * scale,
                        center.y + 38f * scale,
                        valPaint
                    )
                }

                // Error/Warning dot badge on top-right
                if (hasError || hasWarning) {
                    val dotCenter = Offset(center.x + 38f * scale, center.y - 28f * scale)
                    drawCircle(
                        color = if (hasError) errorColor else copperColor,
                        radius = 7f * scale,
                        center = dotCenter
                    )
                }
            }
        }
    }
}

private fun rotateLocal(x: Float, y: Float, rotationDeg: Long): Offset {
    val rad = Math.toRadians((rotationDeg % 360L).toDouble())
    val c = cos(rad).toFloat()
    val s = sin(rad).toFloat()
    return Offset(x * c - y * s, x * s + y * c)
}

private fun pinWorldPos(comp: SchematicComponent, pinIndex: Int, baseGridPx: Float): Offset {
    val pin = comp.resolvedType().pins.firstOrNull { it.index == pinIndex }
        ?: return Offset(comp.gridX * baseGridPx, comp.gridY * baseGridPx)
    val rot = rotateLocal(pin.localX * baseGridPx, pin.localY * baseGridPx, comp.rotationDeg)
    return Offset(comp.gridX * baseGridPx + rot.x, comp.gridY * baseGridPx + rot.y)
}

private fun pinScreenPos(
    comp: SchematicComponent,
    pinIndex: Int,
    baseGridPx: Float,
    vp: CanvasViewportState
): Offset {
    val world = pinWorldPos(comp, pinIndex, baseGridPx)
    return Offset(vp.offsetX + world.x * vp.scale, vp.offsetY + world.y * vp.scale)
}

private fun findHitPin(
    project: SchematicProject,
    worldPos: Offset,
    baseGridPx: Float
): Pair<String, Int>? {
    val hitRadius = baseGridPx * 0.52f
    for (comp in project.components.asReversed()) {
        for (pin in comp.resolvedType().pins) {
            val pos = pinWorldPos(comp, pin.index, baseGridPx)
            if (hypot(worldPos.x - pos.x, worldPos.y - pos.y) <= hitRadius) {
                return comp.uid to pin.index
            }
        }
    }
    return null
}

private fun findHitComponent(
    project: SchematicProject,
    worldPos: Offset,
    baseGridPx: Float
): SchematicComponent? {
    val hitRadius = baseGridPx * 1.45f
    return project.components.asReversed().firstOrNull { comp ->
        val cx = comp.gridX * baseGridPx
        val cy = comp.gridY * baseGridPx
        hypot(worldPos.x - cx, worldPos.y - cy) <= hitRadius
    }
}

private fun findHitWire(
    project: SchematicProject,
    worldPos: Offset,
    baseGridPx: Float
): SchematicWire? {
    val compMap = project.components.associateBy { it.uid }
    val threshold = 18f
    return project.wires.asReversed().firstOrNull { wire ->
        val fromC = compMap[wire.fromCompUid] ?: return@firstOrNull false
        val toC = compMap[wire.toCompUid] ?: return@firstOrNull false
        val p1 = pinWorldPos(fromC, wire.fromPinIndex.toInt(), baseGridPx)
        val p2 = pinWorldPos(toC, wire.toPinIndex.toInt(), baseGridPx)
        val midX = (p1.x + p2.x) / 2f
        val d1 = distToSegment(worldPos, p1, Offset(midX, p1.y))
        val d2 = distToSegment(worldPos, Offset(midX, p1.y), Offset(midX, p2.y))
        val d3 = distToSegment(worldPos, Offset(midX, p2.y), p2)
        minOf(d1, d2, d3) <= threshold
    }
}

private fun distToSegment(p: Offset, a: Offset, b: Offset): Float {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val lenSq = dx * dx + dy * dy
    if (lenSq == 0f) return hypot(p.x - a.x, p.y - a.y)
    val t = (((p.x - a.x) * dx + (p.y - a.y) * dy) / lenSq).coerceIn(0f, 1f)
    val projX = a.x + t * dx
    val projY = a.y + t * dy
    return hypot(p.x - projX, p.y - projY)
}

private fun DrawScope.drawComponentSymbol(
    comp: SchematicComponent,
    center: Offset,
    scale: Float,
    baseGridPx: Float,
    silkColor: Color,
    accentColor: Color,
    copperColor: Color,
    ledColor: Color
) {
    val strokeW = 2.4f * scale
    val g = baseGridPx * scale
    val rot = comp.rotationDeg

    fun toScreen(lx: Float, ly: Float): Offset {
        val r = rotateLocal(lx * g, ly * g, rot)
        return Offset(center.x + r.x, center.y + r.y)
    }

    when (comp.resolvedType()) {
        ComponentType.RESISTOR -> {
            val pts = listOf(
                toScreen(-1.6f, 0f),
                toScreen(-0.9f, 0f),
                toScreen(-0.7f, -0.4f),
                toScreen(-0.35f, 0.4f),
                toScreen(0f, -0.4f),
                toScreen(0.35f, 0.4f),
                toScreen(0.7f, -0.4f),
                toScreen(0.9f, 0f),
                toScreen(1.6f, 0f)
            )
            val path = Path().apply {
                moveTo(pts.first().x, pts.first().y)
                pts.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(path, color = silkColor, style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        ComponentType.CAPACITOR -> {
            drawLine(silkColor, toScreen(-1.3f, 0f), toScreen(-0.22f, 0f), strokeWidth = strokeW)
            drawLine(silkColor, toScreen(0.22f, 0f), toScreen(1.3f, 0f), strokeWidth = strokeW)
            drawLine(copperColor, toScreen(-0.22f, -0.65f), toScreen(-0.22f, 0.65f), strokeWidth = strokeW * 1.3f)
            drawLine(copperColor, toScreen(0.22f, -0.65f), toScreen(0.22f, 0.65f), strokeWidth = strokeW * 1.3f)
        }

        ComponentType.INDUCTOR -> {
            drawLine(silkColor, toScreen(-1.6f, 0f), toScreen(-0.9f, 0f), strokeWidth = strokeW)
            drawLine(silkColor, toScreen(0.9f, 0f), toScreen(1.6f, 0f), strokeWidth = strokeW)
            for (i in 0 until 3) {
                val cx = -0.6f + i * 0.6f
                drawCircle(
                    color = silkColor,
                    radius = 0.3f * g,
                    center = toScreen(cx, -0.15f),
                    style = Stroke(width = strokeW)
                )
            }
        }

        ComponentType.DIODE, ComponentType.LED -> {
            val col = if (comp.resolvedType() == ComponentType.LED) ledColor else silkColor
            drawLine(silkColor, toScreen(-1.5f, 0f), toScreen(-0.45f, 0f), strokeWidth = strokeW)
            drawLine(silkColor, toScreen(0.45f, 0f), toScreen(1.5f, 0f), strokeWidth = strokeW)
            val tri = Path().apply {
                val a = toScreen(-0.45f, -0.55f)
                val b = toScreen(-0.45f, 0.55f)
                val c = toScreen(0.45f, 0f)
                moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); close()
            }
            drawPath(tri, color = col, style = Stroke(width = strokeW, join = StrokeJoin.Round))
            drawLine(col, toScreen(0.45f, -0.55f), toScreen(0.45f, 0.55f), strokeWidth = strokeW * 1.2f)
            if (comp.resolvedType() == ComponentType.LED) {
                drawLine(ledColor, toScreen(0.1f, -0.7f), toScreen(0.55f, -1.1f), strokeWidth = strokeW * 0.8f)
                drawLine(ledColor, toScreen(0.5f, -0.6f), toScreen(0.95f, -1.0f), strokeWidth = strokeW * 0.8f)
            }
        }

        ComponentType.MOSFET_N -> {
            drawLine(silkColor, toScreen(-1.6f, 0.5f), toScreen(-0.4f, 0.5f), strokeWidth = strokeW)
            drawLine(copperColor, toScreen(-0.4f, -0.7f), toScreen(-0.4f, 0.7f), strokeWidth = strokeW * 1.2f)
            drawLine(silkColor, toScreen(0f, -0.7f), toScreen(0f, 0.7f), strokeWidth = strokeW)
            drawLine(silkColor, toScreen(0f, -0.5f), toScreen(1.0f, -0.5f), strokeWidth = strokeW)
            drawLine(silkColor, toScreen(1.0f, -0.5f), toScreen(1.0f, -1.4f), strokeWidth = strokeW)
            drawLine(silkColor, toScreen(0f, 0.5f), toScreen(1.0f, 0.5f), strokeWidth = strokeW)
            drawLine(silkColor, toScreen(1.0f, 0.5f), toScreen(1.0f, 1.4f), strokeWidth = strokeW)
        }

        ComponentType.MCU, ComponentType.REGULATOR, ComponentType.OPAMP, ComponentType.CONNECTOR -> {
            val halfW = if (comp.resolvedType() == ComponentType.MCU) 1.3f else 1.15f
            val halfH = if (comp.resolvedType() == ComponentType.MCU) 1.3f else 1.05f
            val tl = Offset(center.x - halfW * g, center.y - halfH * g)
            val sz = Size(halfW * 2f * g, halfH * 2f * g)
            drawRoundRect(
                color = accentColor.copy(alpha = 0.14f),
                topLeft = tl,
                size = sz,
                cornerRadius = CornerRadius(6f * scale, 6f * scale)
            )
            drawRoundRect(
                color = silkColor,
                topLeft = tl,
                size = sz,
                cornerRadius = CornerRadius(6f * scale, 6f * scale),
                style = Stroke(width = strokeW)
            )
            // Pin-1 dot
            drawCircle(
                color = copperColor,
                radius = 3.5f * scale,
                center = Offset(tl.x + 8f * scale, tl.y + 8f * scale)
            )
            // Draw pin stubs
            comp.resolvedType().pins.forEach { pin ->
                val pScreen = toScreen(pin.localX, pin.localY)
                val bodyAnchorX = pin.localX.coerceIn(-halfW, halfW)
                val bodyAnchorY = pin.localY.coerceIn(-halfH, halfH)
                drawLine(silkColor, toScreen(bodyAnchorX, bodyAnchorY), pScreen, strokeWidth = strokeW * 0.9f)
            }
        }

        ComponentType.CRYSTAL -> {
            drawLine(silkColor, toScreen(-1.5f, 0f), toScreen(-0.55f, 0f), strokeWidth = strokeW)
            drawLine(silkColor, toScreen(0.55f, 0f), toScreen(1.5f, 0f), strokeWidth = strokeW)
            drawLine(silkColor, toScreen(-0.55f, -0.55f), toScreen(-0.55f, 0.55f), strokeWidth = strokeW)
            drawLine(silkColor, toScreen(0.55f, -0.55f), toScreen(0.55f, 0.55f), strokeWidth = strokeW)
            drawRect(
                color = copperColor,
                topLeft = Offset(center.x - 0.25f * g, center.y - 0.5f * g),
                size = Size(0.5f * g, 1.0f * g),
                style = Stroke(width = strokeW)
            )
        }

        ComponentType.SWITCH -> {
            drawLine(silkColor, toScreen(-1.5f, 0f), toScreen(-0.6f, 0f), strokeWidth = strokeW)
            drawLine(silkColor, toScreen(0.6f, 0f), toScreen(1.5f, 0f), strokeWidth = strokeW)
            drawCircle(silkColor, radius = 3.5f * scale, center = toScreen(-0.55f, 0f))
            drawCircle(silkColor, radius = 3.5f * scale, center = toScreen(0.55f, 0f))
            drawLine(copperColor, toScreen(-0.5f, -0.1f), toScreen(0.55f, -0.65f), strokeWidth = strokeW)
        }

        ComponentType.VCC_RAIL -> {
            drawLine(copperColor, toScreen(0f, 1.2f), toScreen(0f, -0.4f), strokeWidth = strokeW)
            val arrow = Path().apply {
                val a = toScreen(-0.55f, -0.1f)
                val b = toScreen(0f, -0.8f)
                val c = toScreen(0.55f, -0.1f)
                moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y)
            }
            drawPath(arrow, color = copperColor, style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        ComponentType.GND_RAIL -> {
            drawLine(accentColor, toScreen(0f, -1.2f), toScreen(0f, 0f), strokeWidth = strokeW)
            drawLine(accentColor, toScreen(-0.75f, 0f), toScreen(0.75f, 0f), strokeWidth = strokeW)
            drawLine(accentColor, toScreen(-0.48f, 0.35f), toScreen(0.48f, 0.35f), strokeWidth = strokeW)
            drawLine(accentColor, toScreen(-0.22f, 0.7f), toScreen(0.22f, 0.7f), strokeWidth = strokeW)
        }
    }
}
