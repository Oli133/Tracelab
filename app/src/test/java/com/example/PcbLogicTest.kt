package com.example

import com.example.data.BomExporter
import com.example.data.StarterTemplates
import com.example.model.ComponentType
import com.example.model.SchematicComponent
import com.example.model.SchematicProject
import com.example.model.SchematicWire
import com.example.model.StarterTemplate
import com.example.model.ValidationSeverity
import com.example.validation.SchematicValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PcbLogicTest {

    @Test
    fun `starter LDO power template passes ERC with zero errors`() {
        val project = StarterTemplates.buildProject(
            userId = "user_1",
            name = "LDO Board",
            description = "Test",
            template = StarterTemplate.LDO_POWER,
            layerCount = 2L
        )
        val result = SchematicValidator.validate(project)
        assertEquals(0, result.errorCount)
        assertTrue(project.components.isNotEmpty())
        assertTrue(project.wires.isNotEmpty())
    }

    @Test
    fun `validator detects VCC to GND short circuit and duplicate RefDes`() {
        val vcc = SchematicComponent(
            uid = "v1",
            refDes = "PWR1",
            type = ComponentType.VCC_RAIL.name,
            valueText = "5.0V",
            gridX = 1L,
            gridY = 1L
        )
        val gnd = SchematicComponent(
            uid = "g1",
            refDes = "GND1",
            type = ComponentType.GND_RAIL.name,
            valueText = "0V",
            gridX = 4L,
            gridY = 1L
        )
        val r1a = SchematicComponent(
            uid = "r1",
            refDes = "R1",
            type = ComponentType.RESISTOR.name,
            gridX = 2L,
            gridY = 4L
        )
        val r1b = SchematicComponent(
            uid = "r2",
            refDes = "R1",
            type = ComponentType.RESISTOR.name,
            gridX = 6L,
            gridY = 4L
        )
        val shortWire = SchematicWire(
            uid = "w1",
            netName = "SHORT",
            fromCompUid = "v1",
            fromPinIndex = 0L,
            toCompUid = "g1",
            toPinIndex = 0L
        )
        val project = SchematicProject(
            userId = "u1",
            name = "Faulty",
            components = listOf(vcc, gnd, r1a, r1b),
            wires = listOf(shortWire)
        )

        val res = SchematicValidator.validate(project)
        assertTrue(res.issues.any { it.code == "ERC-010" && it.severity == ValidationSeverity.ERROR })
        assertTrue(res.issues.any { it.code == "ERC-002" && it.severity == ValidationSeverity.ERROR })
    }

    @Test
    fun `BOM exporter groups identical parts and excludes virtual power rails`() {
        val project = StarterTemplates.buildProject(
            userId = "u1",
            name = "MCU Node",
            description = "Test",
            template = StarterTemplate.MCU_NODE,
            layerCount = 2L
        )
        val bom = BomExporter.buildBom(project)
        // MCU_NODE has 5 physical parts (C1, U1, R1, SW1, Y1) + 2 virtual rails (PWR1, GND1)
        assertEquals(5, bom.sumOf { it.quantity })
        val csv = BomExporter.toCsv(project, bom)
        assertTrue(csv.contains("STM32G030"))
        assertTrue(csv.contains("TotalUSD"))
        val json = BomExporter.toJson(project, bom)
        assertTrue(json.contains("\"projectName\": \"MCU Node\""))
    }
}
