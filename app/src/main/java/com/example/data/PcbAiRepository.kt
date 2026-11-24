package com.example.data

import android.util.Log
import com.example.model.SchematicComponent
import com.example.model.SchematicProject
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.ThinkingLevel
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.thinkingConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class QuickComponentCheckResult(
    val status: String,
    val headline: String,
    val recommendation: String,
    val suggestedMpn: String,
    val suggestedValue: String
)

data class DeepErcFinding(
    val severity: String,
    val title: String,
    val detail: String,
    val fixStep: String
)

data class DeepErcAuditResult(
    val overallScore: Int,
    val summary: String,
    val findings: List<DeepErcFinding>
)

data class BomSourcingSuggestion(
    val refDes: String,
    val suggestedMpn: String,
    val estimatedUnitPriceUsd: Double,
    val note: String
)

class PcbAiRepository {

    private companion object {
        const val TAG = "PcbAiRepository"
        const val MODEL_FAST_LITE = "gemini-3.1-flash-lite"
        const val MODEL_DEEP_THINKING = "gemini-4-carbon"
        const val MODEL_GENERAL = "gemini-4-hydrogen"
    }

    private val quickCheckSchema = Schema.obj(
        properties = mapOf(
            "status" to Schema.enumeration(
                values = listOf("PASS", "WARN", "FAIL"),
                description = "Validation status of the selected component"
            ),
            "headline" to Schema.string(description = "Short 5-8 word diagnostic headline"),
            "recommendation" to Schema.string(description = "Actionable electrical recommendation"),
            "suggestedMpn" to Schema.string(description = "Recommended manufacturer part number"),
            "suggestedValue" to Schema.string(description = "Recommended component value or rating")
        )
    )

    private val deepAuditSchema = Schema.obj(
        properties = mapOf(
            "overallScore" to Schema.integer(description = "Design readiness score from 0 to 100"),
            "summary" to Schema.string(description = "Executive engineering summary of the PCB schematic"),
            "findings" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "severity" to Schema.enumeration(listOf("CRITICAL", "WARNING", "OPTIMIZATION")),
                        "title" to Schema.string(description = "Concise engineering issue title"),
                        "detail" to Schema.string(description = "Detailed electrical/PCB reasoning"),
                        "fixStep" to Schema.string(description = "Concrete fix step in the schematic")
                    )
                ),
                description = "List of circuit validation findings"
            )
        )
    )

    private val bomAdvisorSchema = Schema.obj(
        properties = mapOf(
            "suggestions" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "refDes" to Schema.string(description = "Reference designator e.g. R1"),
                        "suggestedMpn" to Schema.string(description = "Real standard MPN e.g. RC0603FR-0710KL"),
                        "estimatedUnitPriceUsd" to Schema.double(description = "Typical 10-qty unit price in USD"),
                        "note" to Schema.string(description = "Brief sourcing/package note")
                    )
                ),
                description = "Component sourcing recommendations"
            )
        )
    )

    // Low-latency model for instant component validation
    private val fastComponentModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_FAST_LITE,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = quickCheckSchema
            }
        )
    }

    // High-thinking reasoning model (ThinkingLevel.HIGH, no maxOutputTokens)
    private val deepAuditModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_DEEP_THINKING,
            generationConfig = generationConfig {
                thinkingConfig = thinkingConfig {
                    thinkingLevel = ThinkingLevel.HIGH
                }
                responseMimeType = "application/json"
                responseSchema = deepAuditSchema
            }
        )
    }

    // General model for BOM MPN & sourcing recommendations
    private val bomAdvisorModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_GENERAL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = bomAdvisorSchema
            }
        )
    }

    suspend fun runQuickComponentCheck(
        component: SchematicComponent,
        project: SchematicProject
    ): Result<QuickComponentCheckResult> {
        return try {
            val connectedNets = project.wires
                .filter { it.fromCompUid == component.uid || it.toCompUid == component.uid }
                .joinToString(", ") { "${it.netName} (${it.traceWidthMm}mm ${it.copperLayer})" }
                .ifBlank { "No nets connected yet" }

            val prompt = """
                Validate this PCB schematic component placement in real time:
                Board: ${project.name} (${project.layerCount}-layer, ${project.boardWidthMm}x${project.boardHeightMm}mm)
                Component: ${component.refDes} (${component.label}, type=${component.type})
                Value: ${component.valueText}, Footprint: ${component.footprint}, MPN: ${component.manufacturerPart.ifBlank { "unset" }}
                Ratings: ${component.voltageRatingV}V, ${component.powerRatingMw}mW
                Connected nets: $connectedNets
                Total board parts: ${project.components.size}
                Give a crisp engineering validation check and suggest an ideal MPN/value if needed.
            """.trimIndent()

            val response = withTimeout(30_000L) {
                fastComponentModel.generateContent(prompt)
            }
            val text = response.text ?: throw IllegalStateException("Empty AI response")
            val json = JSONObject(text)
            Result.success(
                QuickComponentCheckResult(
                    status = json.optString("status", "PASS"),
                    headline = json.optString("headline", "Component checked"),
                    recommendation = json.optString("recommendation", ""),
                    suggestedMpn = json.optString("suggestedMpn", component.manufacturerPart),
                    suggestedValue = json.optString("suggestedValue", component.valueText)
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Quick component check timed out", e)
            Result.failure(Exception("Quick validation timed out. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Quick component check failed", e)
            Result.failure(e)
        }
    }

    suspend fun runDeepSchematicAudit(project: SchematicProject): Result<DeepErcAuditResult> {
        return try {
            val netlistDesc = buildString {
                appendLine("Board: ${project.name} | ${project.boardWidthMm}x${project.boardHeightMm}mm | ${project.layerCount} layers")
                appendLine("Components (${project.components.size}):")
                project.components.forEach { c ->
                    appendLine("- ${c.refDes} [${c.type}] val='${c.valueText}' pkg=${c.footprint} mpn=${c.manufacturerPart} rating=${c.voltageRatingV}V/${c.powerRatingMw}mW pos=(${c.gridX},${c.gridY})")
                }
                appendLine("Wires/Traces (${project.wires.size}):")
                val compByUid = project.components.associateBy { it.uid }
                project.wires.forEach { w ->
                    val fromRef = compByUid[w.fromCompUid]?.refDes ?: w.fromCompUid
                    val toRef = compByUid[w.toCompUid]?.refDes ?: w.toCompUid
                    appendLine("- Net ${w.netName}: $fromRef.pin${w.fromPinIndex} -> $toRef.pin${w.toPinIndex} [width=${w.traceWidthMm}mm layer=${w.copperLayer}]")
                }
            }

            val prompt = """
                Perform a deep Electrical Rule Check (ERC) and PCB Design-for-Manufacturing (DFM) audit on this schematic.
                Inspect power rails, decoupling, current-limiting, trace widths, missing pull-ups/ESD, thermal limits, and signal integrity.
                $netlistDesc
            """.trimIndent()

            val response = withTimeout(60_000L) {
                deepAuditModel.generateContent(prompt)
            }
            val text = response.text ?: throw IllegalStateException("Empty AI response")
            val json = JSONObject(text)
            val findingsArray = json.optJSONArray("findings")
            val findings = buildList {
                if (findingsArray != null) {
                    for (i in 0 until findingsArray.length()) {
                        val obj = findingsArray.getJSONObject(i)
                        add(
                            DeepErcFinding(
                                severity = obj.optString("severity", "WARNING"),
                                title = obj.optString("title", "Design Finding"),
                                detail = obj.optString("detail", ""),
                                fixStep = obj.optString("fixStep", "")
                            )
                        )
                    }
                }
            }
            Result.success(
                DeepErcAuditResult(
                    overallScore = json.optInt("overallScore", 75).coerceIn(0, 100),
                    summary = json.optString("summary", "Audit complete."),
                    findings = findings
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Deep ERC audit timed out", e)
            Result.failure(Exception("Deep ERC audit timed out after 60 seconds."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Deep ERC audit failed", e)
            Result.failure(e)
        }
    }

    suspend fun enrichBomParts(components: List<SchematicComponent>): Result<List<BomSourcingSuggestion>> {
        return try {
            val partList = components
                .filter { it.type != "VCC_RAIL" && it.type != "GND_RAIL" }
                .distinctBy { "${it.refDes}|${it.valueText}|${it.footprint}" }
                .joinToString("\n") { c ->
                    "- ${c.refDes}: ${c.label}, val=${c.valueText}, footprint=${c.footprint}, currentMpn=${c.manufacturerPart.ifBlank { "MISSING" }}"
                }

            val prompt = """
                Suggest real, widely stocked electronic Manufacturer Part Numbers (MPNs) and realistic unit prices (USD) for this PCB Bill of Materials:
                $partList
            """.trimIndent()

            val response = withTimeout(45_000L) {
                bomAdvisorModel.generateContent(prompt)
            }
            val text = response.text ?: throw IllegalStateException("Empty AI response")
            val json = JSONObject(text)
            val arr = json.optJSONArray("suggestions")
            val suggestions = buildList {
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        add(
                            BomSourcingSuggestion(
                                refDes = obj.optString("refDes", ""),
                                suggestedMpn = obj.optString("suggestedMpn", ""),
                                estimatedUnitPriceUsd = obj.optDouble("estimatedUnitPriceUsd", 0.05),
                                note = obj.optString("note", "")
                            )
                        )
                    }
                }
            }
            Result.success(suggestions)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "BOM sourcing timed out", e)
            Result.failure(Exception("BOM sourcing request timed out."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "BOM sourcing failed", e)
            Result.failure(e)
        }
    }
}
