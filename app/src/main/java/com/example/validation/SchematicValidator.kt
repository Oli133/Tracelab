package com.example.validation

import com.example.model.ComponentType
import com.example.model.PinRole
import com.example.model.SchematicComponent
import com.example.model.SchematicProject
import com.example.model.SchematicWire
import com.example.model.ValidationIssue
import com.example.model.ValidationResult
import com.example.model.ValidationSeverity
import kotlin.math.abs

object SchematicValidator {

    fun validate(project: SchematicProject): ValidationResult {
        val issues = mutableListOf<ValidationIssue>()
        val components = project.components
        val wires = project.wires
        val compMap = components.associateBy { it.uid }

        // 1. Duplicate Reference Designator check
        val refGroups = components
            .filter { it.resolvedType() != ComponentType.VCC_RAIL && it.resolvedType() != ComponentType.GND_RAIL }
            .groupBy { it.refDes.trim().uppercase() }
        refGroups.forEach { (ref, group) ->
            if (ref.isBlank()) {
                group.forEach { comp ->
                    issues += ValidationIssue(
                        id = "blank_ref_${comp.uid}",
                        severity = ValidationSeverity.ERROR,
                        code = "ERC-001",
                        title = "Missing Reference Designator",
                        message = "${comp.label} has no RefDes assigned.",
                        componentUid = comp.uid
                    )
                }
            } else if (group.size > 1) {
                group.forEach { comp ->
                    issues += ValidationIssue(
                        id = "dup_ref_${comp.uid}",
                        severity = ValidationSeverity.ERROR,
                        code = "ERC-002",
                        title = "Duplicate RefDes '$ref'",
                        message = "${group.size} components share reference designator $ref.",
                        componentUid = comp.uid
                    )
                }
            }
        }

        // 2. Build pin connectivity adjacency
        val connectedPins = mutableSetOf<Pair<String, Int>>()
        val adj = mutableMapOf<Pair<String, Int>, MutableList<Pair<String, Int>>>()
        wires.forEach { wire ->
            val a = wire.fromCompUid to wire.fromPinIndex.toInt()
            val b = wire.toCompUid to wire.toPinIndex.toInt()
            if (compMap.containsKey(wire.fromCompUid) && compMap.containsKey(wire.toCompUid)) {
                connectedPins += a
                connectedPins += b
                adj.getOrPut(a) { mutableListOf() }.add(b)
                adj.getOrPut(b) { mutableListOf() }.add(a)
            } else {
                issues += ValidationIssue(
                    id = "dangling_wire_${wire.uid}",
                    severity = ValidationSeverity.ERROR,
                    code = "ERC-003",
                    title = "Dangling Trace '${wire.netName}'",
                    message = "Wire ${wire.netName} references a removed component.",
                    componentUid = null
                )
            }

            if (wire.traceWidthMm < 0.15) {
                issues += ValidationIssue(
                    id = "thin_trace_${wire.uid}",
                    severity = ValidationSeverity.WARNING,
                    code = "DRC-001",
                    title = "Trace Width Below 0.15mm",
                    message = "Net ${wire.netName} trace width (${wire.traceWidthMm}mm) is below standard 6-mil fab tolerance.",
                    componentUid = wire.fromCompUid
                )
            }
        }

        // 3. Connected-component net traversal for VCC-to-GND short circuit detection
        val visited = mutableSetOf<Pair<String, Int>>()
        for (node in adj.keys) {
            if (node in visited) continue
            val netNodes = mutableListOf<Pair<String, Int>>()
            val queue = ArrayDeque<Pair<String, Int>>()
            queue.add(node)
            visited.add(node)
            while (queue.isNotEmpty()) {
                val curr = queue.removeFirst()
                netNodes.add(curr)
                adj[curr]?.forEach { next ->
                    if (next !in visited) {
                        visited.add(next)
                        queue.add(next)
                    }
                }
            }

            var hasPowerSource = false
            var hasGround = false
            val railVoltages = mutableListOf<Double>()

            netNodes.forEach { (compUid, pinIdx) ->
                val comp = compMap[compUid] ?: return@forEach
                val type = comp.resolvedType()
                val pin = type.pins.firstOrNull { it.index == pinIdx }
                if (pin != null) {
                    if (pin.role == PinRole.POWER_OUT || type == ComponentType.VCC_RAIL) {
                        hasPowerSource = true
                        parseVoltage(comp.valueText)?.let { railVoltages += it }
                    }
                    if (pin.role == PinRole.GROUND || type == ComponentType.GND_RAIL) {
                        hasGround = true
                    }
                }
            }

            if (hasPowerSource && hasGround) {
                val firstComp = netNodes.firstOrNull()?.first
                issues += ValidationIssue(
                    id = "short_vcc_gnd_${node.first}_${node.second}",
                    severity = ValidationSeverity.ERROR,
                    code = "ERC-010",
                    title = "Power-to-Ground Short Circuit",
                    message = "A power rail is directly shorted to GND through connected traces.",
                    componentUid = firstComp
                )
            }

            // Voltage rating check against rail voltage
            val maxRailV = railVoltages.maxOrNull()
            if (maxRailV != null) {
                netNodes.forEach { (compUid, _) ->
                    val comp = compMap[compUid] ?: return@forEach
                    val type = comp.resolvedType()
                    if (type != ComponentType.VCC_RAIL && type != ComponentType.GND_RAIL &&
                        comp.voltageRatingV > 0.0 && maxRailV > comp.voltageRatingV + 0.1
                    ) {
                        issues += ValidationIssue(
                            id = "overvolt_${comp.uid}",
                            severity = ValidationSeverity.ERROR,
                            code = "ERC-011",
                            title = "Over-Voltage on ${comp.refDes}",
                            message = "${comp.refDes} rated ${comp.voltageRatingV}V is on a ${maxRailV}V rail.",
                            componentUid = comp.uid
                        )
                    }
                }
            }
        }

        // 4. Component-level checks (floating critical pins, board bounds, LED series resistor, MCU decoupling)
        val maxGridX = (project.boardWidthMm / project.gridSnapMm).toLong()
        val maxGridY = (project.boardHeightMm / project.gridSnapMm).toLong()

        val hasCapacitor = components.any { it.resolvedType() == ComponentType.CAPACITOR }

        components.forEach { comp ->
            val type = comp.resolvedType()

            // Board boundary DRC
            if (comp.gridX < 0 || comp.gridY < 0 || comp.gridX > maxGridX || comp.gridY > maxGridY) {
                issues += ValidationIssue(
                    id = "oob_${comp.uid}",
                    severity = ValidationSeverity.WARNING,
                    code = "DRC-002",
                    title = "${comp.refDes} Outside Board Edge",
                    message = "${comp.refDes} at grid (${comp.gridX}, ${comp.gridY}) extends past ${project.boardWidthMm.toInt()}×${project.boardHeightMm.toInt()}mm outline.",
                    componentUid = comp.uid
                )
            }

            // Unconnected pins check
            val floatingPins = type.pins.filter { (comp.uid to it.index) !in connectedPins }
            if (floatingPins.size == type.pins.size && components.size > 1) {
                issues += ValidationIssue(
                    id = "unwired_${comp.uid}",
                    severity = ValidationSeverity.WARNING,
                    code = "ERC-004",
                    title = "Unconnected ${comp.refDes}",
                    message = "${comp.refDes} (${comp.valueText}) has no routed wires.",
                    componentUid = comp.uid
                )
            } else if (floatingPins.isNotEmpty() && (type == ComponentType.REGULATOR || type == ComponentType.MCU)) {
                val criticalUnwired = floatingPins.filter {
                    it.role == PinRole.POWER_IN || it.role == PinRole.GROUND || it.role == PinRole.POWER_OUT
                }
                if (criticalUnwired.isNotEmpty()) {
                    issues += ValidationIssue(
                        id = "pwr_float_${comp.uid}",
                        severity = ValidationSeverity.ERROR,
                        code = "ERC-005",
                        title = "Unwired Power Pin on ${comp.refDes}",
                        message = "Pin(s) ${criticalUnwired.joinToString { it.name }} on ${comp.refDes} must be connected.",
                        componentUid = comp.uid
                    )
                }
            }

            // Missing MPN warning for BOM readiness
            if (type != ComponentType.VCC_RAIL && type != ComponentType.GND_RAIL && comp.manufacturerPart.isBlank()) {
                issues += ValidationIssue(
                    id = "mpn_missing_${comp.uid}",
                    severity = ValidationSeverity.INFO,
                    code = "BOM-001",
                    title = "Missing MPN on ${comp.refDes}",
                    message = "Add a Manufacturer Part Number for turnkey BOM assembly.",
                    componentUid = comp.uid
                )
            }

            // LED without resistor nearby / in circuit
            if (type == ComponentType.LED) {
                val ledConnectedToResistor = wires.any { w ->
                    (w.fromCompUid == comp.uid && compMap[w.toCompUid]?.resolvedType() == ComponentType.RESISTOR) ||
                        (w.toCompUid == comp.uid && compMap[w.fromCompUid]?.resolvedType() == ComponentType.RESISTOR)
                }
                if (!ledConnectedToResistor && connectedPins.any { it.first == comp.uid }) {
                    issues += ValidationIssue(
                        id = "led_res_${comp.uid}",
                        severity = ValidationSeverity.WARNING,
                        code = "ERC-008",
                        title = "LED ${comp.refDes} Needs Series Resistor",
                        message = "No series current-limiting resistor detected directly on ${comp.refDes}.",
                        componentUid = comp.uid
                    )
                }
            }

            // MCU/Regulator decoupling capacitor suggestion
            if ((type == ComponentType.MCU || type == ComponentType.REGULATOR) && !hasCapacitor) {
                issues += ValidationIssue(
                    id = "decoupling_${comp.uid}",
                    severity = ValidationSeverity.WARNING,
                    code = "ERC-009",
                    title = "No Decoupling Capacitor",
                    message = "Add a 100nF ceramic capacitor near ${comp.refDes} power pins.",
                    componentUid = comp.uid
                )
            }
        }

        // 5. Component collision check (two components on exact same grid coordinate)
        components.groupBy { it.gridX to it.gridY }.forEach { (coord, list) ->
            if (list.size > 1) {
                issues += ValidationIssue(
                    id = "overlap_${coord.first}_${coord.second}",
                    severity = ValidationSeverity.ERROR,
                    code = "DRC-003",
                    title = "Footprint Collision",
                    message = "${list.joinToString { it.refDes }} overlap at grid (${coord.first}, ${coord.second}).",
                    componentUid = list.first().uid
                )
            }
        }

        val errorCount = issues.count { it.severity == ValidationSeverity.ERROR }
        val warningCount = issues.count { it.severity == ValidationSeverity.WARNING }
        val summary = when {
            components.isEmpty() -> "Empty board — drag components onto the grid"
            errorCount == 0 && warningCount == 0 -> "ERC/DRC Passed • ${components.size} parts"
            errorCount == 0 -> "$warningCount warning(s) • ${components.size} parts"
            else -> "$errorCount error(s), $warningCount warning(s)"
        }.take(490)

        return ValidationResult(
            issues = issues.sortedBy { it.severity.ordinal },
            errorCount = errorCount,
            warningCount = warningCount,
            summaryText = summary
        )
    }

    private fun parseVoltage(text: String): Double? {
        val match = Regex("""(\d+(?:\.\d+)?)\s*[vV]""").find(text) ?: return null
        return match.groupValues[1].toDoubleOrNull()
    }
}
