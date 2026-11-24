package com.example.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.DeepErcAuditResult
import com.example.data.QuickComponentCheckResult
import com.example.model.SchematicComponent
import com.example.model.SchematicProject
import com.example.model.SchematicWire
import com.example.model.ValidationIssue
import com.example.model.ValidationResult
import com.example.model.ValidationSeverity
import com.example.viewmodel.AiTaskState

@Composable
fun ComponentInspectorPanel(
    component: SchematicComponent,
    quickCheckState: AiTaskState<QuickComponentCheckResult>,
    onUpdateComponent: (
        refDes: String,
        label: String,
        valueText: String,
        footprint: String,
        mpn: String,
        unitPrice: Double,
        voltageRating: Double
    ) -> Unit,
    onRotate: () -> Unit,
    onDelete: () -> Unit,
    onRunQuickCheck: () -> Unit,
    onApplyQuickCheck: (QuickComponentCheckResult) -> Unit,
    modifier: Modifier = Modifier
) {
    var refDes by remember(component.uid) { mutableStateOf(component.refDes) }
    var label by remember(component.uid) { mutableStateOf(component.label) }
    var valueText by remember(component.uid) { mutableStateOf(component.valueText) }
    var footprint by remember(component.uid) { mutableStateOf(component.footprint) }
    var mpn by remember(component.uid) { mutableStateOf(component.manufacturerPart) }
    var priceStr by remember(component.uid) { mutableStateOf(component.unitPriceUsd.toString()) }
    var voltStr by remember(component.uid) { mutableStateOf(component.voltageRatingV.toString()) }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text(
                    text = "${component.refDes} • ${component.resolvedType().displayName}",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Grid (${component.gridX}, ${component.gridY}) • ${component.rotationDeg}°",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = onRotate,
                    modifier = Modifier.testTag("rotate_component_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = "Rotate 90 degrees", modifier = Modifier.size(18.dp))
                }
                OutlinedButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("delete_component_button")
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete component", modifier = Modifier.size(18.dp))
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = refDes,
                onValueChange = { refDes = it },
                label = { Text("RefDes") },
                singleLine = true,
                modifier = Modifier.weight(0.4f)
            )
            OutlinedTextField(
                value = valueText,
                onValueChange = { valueText = it },
                label = { Text("Value") },
                singleLine = true,
                modifier = Modifier.weight(0.6f)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = footprint,
                onValueChange = { footprint = it },
                label = { Text("Footprint") },
                singleLine = true,
                modifier = Modifier.weight(0.5f)
            )
            OutlinedTextField(
                value = voltStr,
                onValueChange = { voltStr = it },
                label = { Text("Max V") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(0.5f)
            )
        }

        OutlinedTextField(
            value = label,
            onValueChange = { label = it },
            label = { Text("Description") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = mpn,
                onValueChange = { mpn = it },
                label = { Text("Manufacturer Part #") },
                singleLine = true,
                modifier = Modifier.weight(0.65f)
            )
            OutlinedTextField(
                value = priceStr,
                onValueChange = { priceStr = it },
                label = { Text("Unit $") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(0.35f)
            )
        }

        Button(
            onClick = {
                onUpdateComponent(
                    refDes,
                    label,
                    valueText,
                    footprint,
                    mpn,
                    priceStr.toDoubleOrNull() ?: component.unitPriceUsd,
                    voltStr.toDoubleOrNull() ?: component.voltageRatingV
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("apply_component_changes_button")
        ) {
            Text("Apply Properties")
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // Low-latency AI Check (Gemini 3.1 Flash-Lite)
        OutlinedButton(
            onClick = onRunQuickCheck,
            enabled = quickCheckState !is AiTaskState.Loading,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("quick_ai_check_button")
        ) {
            if (quickCheckState is AiTaskState.Loading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Checking Specs...")
            } else {
                Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Instant Part Check")
            }
        }

        when (quickCheckState) {
            is AiTaskState.Success -> {
                val res = quickCheckState.data
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "${res.status}: ${res.headline}",
                            style = MaterialTheme.typography.titleMedium,
                            color = when (res.status) {
                                "PASS" -> MaterialTheme.colorScheme.primary
                                "FAIL" -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.secondary
                            }
                        )
                        Text(res.recommendation, style = MaterialTheme.typography.bodyMedium)
                        if (res.suggestedMpn.isNotBlank() || res.suggestedValue.isNotBlank()) {
                            Text(
                                text = "Suggested: ${res.suggestedValue} (${res.suggestedMpn})",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            TextButton(
                                onClick = {
                                    onApplyQuickCheck(res)
                                    valueText = res.suggestedValue.ifBlank { valueText }
                                    mpn = res.suggestedMpn.ifBlank { mpn }
                                }
                            ) {
                                Text("Apply Suggested MPN & Value")
                            }
                        }
                    }
                }
            }
            is AiTaskState.Error -> {
                Text(
                    text = quickCheckState.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            else -> Unit
        }
    }
}

@Composable
fun WireInspectorPanel(
    wire: SchematicWire,
    onUpdateWire: (netName: String, traceWidthMm: Double, copperLayer: String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var netName by remember(wire.uid) { mutableStateOf(wire.netName) }
    var widthStr by remember(wire.uid) { mutableStateOf(wire.traceWidthMm.toString()) }
    var layer by remember(wire.uid) { mutableStateOf(wire.copperLayer) }

    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Copper Trace Properties", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(
                onClick = onDelete,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete wire", modifier = Modifier.size(18.dp))
            }
        }

        OutlinedTextField(
            value = netName,
            onValueChange = { netName = it },
            label = { Text("Net Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = widthStr,
            onValueChange = { widthStr = it },
            label = { Text("Trace Width (mm)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("TOP", "BOTTOM").forEach { option ->
                val active = layer == option
                Surface(
                    color = if (active) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(
                        1.dp,
                        if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.clickable { layer = option }
                ) {
                    Text(
                        text = "$option COPPER",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }
        }

        Button(
            onClick = {
                onUpdateWire(netName, widthStr.toDoubleOrNull() ?: wire.traceWidthMm, layer)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Update Trace")
        }
    }
}

@Composable
fun ValidationAuditPanel(
    validationResult: ValidationResult,
    deepAuditState: AiTaskState<DeepErcAuditResult>,
    onRunDeepAudit: () -> Unit,
    onFocusComponent: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Real-time local ERC summary banner
        Surface(
            color = if (validationResult.errorCount > 0) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = if (validationResult.errorCount > 0) Icons.Filled.Error else Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = if (validationResult.errorCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
                Column {
                    Text(
                        text = "Real-Time ERC / DRC",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = validationResult.summaryText,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // Deep Thinking Audit CTA (Gemini 4 Carbon with ThinkingLevel.HIGH)
        Button(
            onClick = onRunDeepAudit,
            enabled = deepAuditState !is AiTaskState.Loading,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("run_deep_erc_audit_button")
        ) {
            if (deepAuditState is AiTaskState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = MaterialTheme.colorScheme.onSecondary,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("Deep Thinking ERC Audit Running...")
            } else {
                Icon(Icons.Filled.Psychology, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Run Deep Schematic Audit (High Thinking)")
            }
        }

        LazyColumn(
            modifier = Modifier.heightIn(max = 420.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (deepAuditState is AiTaskState.Success) {
                val audit = deepAuditState.data
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                                    Text("AI Engineering Audit", style = MaterialTheme.typography.titleMedium)
                                }
                                Text(
                                    text = "Score: ${audit.overallScore}/100",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(audit.summary, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                items(audit.findings) { finding ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "[${finding.severity}] ${finding.title}",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (finding.severity == "CRITICAL") MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.secondary
                            )
                            Text(finding.detail, style = MaterialTheme.typography.bodyMedium)
                            if (finding.fixStep.isNotBlank()) {
                                Text(
                                    text = "Fix: ${finding.fixStep}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            } else if (deepAuditState is AiTaskState.Error) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = deepAuditState.message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            items(validationResult.issues, key = { it.id }) { issue ->
                IssueRowCard(issue = issue, onFocusComponent = onFocusComponent)
            }
        }
    }
}

@Composable
private fun IssueRowCard(
    issue: ValidationIssue,
    onFocusComponent: (String) -> Unit
) {
    val (icon, tint) = when (issue.severity) {
        ValidationSeverity.ERROR -> Icons.Filled.Error to MaterialTheme.colorScheme.error
        ValidationSeverity.WARNING -> Icons.Filled.Warning to MaterialTheme.colorScheme.secondary
        ValidationSeverity.INFO -> Icons.Filled.Info to MaterialTheme.colorScheme.tertiary
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (issue.componentUid != null) Modifier.clickable { onFocusComponent(issue.componentUid) }
                else Modifier
            )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(issue.code, style = MaterialTheme.typography.labelMedium, color = tint)
                    Text(issue.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                }
                Text(
                    text = issue.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun BoardSettingsDialog(
    project: SchematicProject,
    onDismiss: () -> Unit,
    onSave: (name: String, desc: String, widthMm: Double, heightMm: Double, layers: Long) -> Unit
) {
    var name by remember(project.id) { mutableStateOf(project.name) }
    var desc by remember(project.id) { mutableStateOf(project.description) }
    var widthStr by remember(project.id) { mutableStateOf(project.boardWidthMm.toString()) }
    var heightStr by remember(project.id) { mutableStateOf(project.boardHeightMm.toString()) }
    var layers by remember(project.id) { mutableStateOf(project.layerCount) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("PCB Board Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Board Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Description") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = widthStr,
                        onValueChange = { widthStr = it },
                        label = { Text("Width (mm)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = heightStr,
                        onValueChange = { heightStr = it },
                        label = { Text("Height (mm)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text("Copper Layers", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(2L, 4L, 6L).forEach { l ->
                        Surface(
                            color = if (layers == l) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(
                                1.dp,
                                if (layers == l) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clickable { layers = l }
                        ) {
                            Text(
                                text = "$l-Layer",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        name,
                        desc,
                        widthStr.toDoubleOrNull() ?: project.boardWidthMm,
                        heightStr.toDoubleOrNull() ?: project.boardHeightMm,
                        layers
                    )
                    onDismiss()
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
