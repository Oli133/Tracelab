package com.example.data

import com.example.model.BomLineItem
import com.example.model.ComponentType
import com.example.model.SchematicProject
import java.util.Locale

object BomExporter {

    fun buildBom(project: SchematicProject): List<BomLineItem> {
        val physicalParts = project.components.filter {
            val t = it.resolvedType()
            t != ComponentType.VCC_RAIL && t != ComponentType.GND_RAIL
        }

        val grouped = physicalParts.groupBy {
            Triple(
                it.label.trim(),
                it.valueText.trim(),
                "${it.footprint.trim()}|${it.manufacturerPart.trim()}"
            )
        }

        return grouped.values.mapIndexed { index, group ->
            val first = group.first()
            val qty = group.size
            val unitPrice = first.unitPriceUsd.coerceAtLeast(0.0)
            BomLineItem(
                itemNumber = index + 1,
                partName = first.label,
                valueText = first.valueText,
                footprint = first.footprint,
                manufacturerPart = first.manufacturerPart.ifBlank { "UNASSIGNED" },
                designators = group.map { it.refDes }.sorted(),
                quantity = qty,
                unitPriceUsd = unitPrice,
                extendedPriceUsd = unitPrice * qty
            )
        }
    }

    fun toCsv(project: SchematicProject, bom: List<BomLineItem>): String {
        val sb = StringBuilder()
        sb.appendLine("# TraceLab Bill of Materials")
        sb.appendLine("# Project: ${escapeCsv(project.name)}")
        sb.appendLine("# Board: ${project.boardWidthMm}mm x ${project.boardHeightMm}mm (${project.layerCount} Layers)")
        sb.appendLine("Item,Designators,Quantity,Part,Value,Footprint,MPN,UnitPriceUSD,ExtendedPriceUSD")
        bom.forEach { row ->
            sb.appendLine(
                listOf(
                    row.itemNumber.toString(),
                    escapeCsv(row.designators.joinToString(" ")),
                    row.quantity.toString(),
                    escapeCsv(row.partName),
                    escapeCsv(row.valueText),
                    escapeCsv(row.footprint),
                    escapeCsv(row.manufacturerPart),
                    String.format(Locale.US, "%.4f", row.unitPriceUsd),
                    String.format(Locale.US, "%.4f", row.extendedPriceUsd)
                ).joinToString(",")
            )
        }
        val total = bom.sumOf { it.extendedPriceUsd }
        sb.appendLine(",,,,,,,TotalUSD,${String.format(Locale.US, "%.4f", total)}")
        return sb.toString()
    }

    fun toJson(project: SchematicProject, bom: List<BomLineItem>): String {
        val totalParts = bom.sumOf { it.quantity }
        val totalCost = bom.sumOf { it.extendedPriceUsd }
        val itemsJson = bom.joinToString(",\n") { row ->
            val designatorsJson = row.designators.joinToString(", ") { "\"${escapeJson(it)}\"" }
            """    {
      "item": ${row.itemNumber},
      "designators": [$designatorsJson],
      "quantity": ${row.quantity},
      "part": "${escapeJson(row.partName)}",
      "value": "${escapeJson(row.valueText)}",
      "footprint": "${escapeJson(row.footprint)}",
      "mpn": "${escapeJson(row.manufacturerPart)}",
      "unitPriceUsd": ${String.format(Locale.US, "%.4f", row.unitPriceUsd)},
      "extendedPriceUsd": ${String.format(Locale.US, "%.4f", row.extendedPriceUsd)}
    }"""
        }
        return """{
  "projectName": "${escapeJson(project.name)}",
  "boardWidthMm": ${project.boardWidthMm},
  "boardHeightMm": ${project.boardHeightMm},
  "layerCount": ${project.layerCount},
  "totalParts": $totalParts,
  "totalEstimatedCostUsd": ${String.format(Locale.US, "%.4f", totalCost)},
  "items": [
$itemsJson
  ]
}"""
    }

    private fun escapeJson(raw: String): String = buildString {
        raw.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
    }

    private fun escapeCsv(raw: String): String {
        val needsQuotes = raw.contains(',') || raw.contains('"') || raw.contains('\n')
        return if (needsQuotes) {
            "\"${raw.replace("\"", "\"\"")}\""
        } else {
            raw
        }
    }
}
