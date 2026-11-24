package com.example.data

import com.example.model.ComponentType
import com.example.model.SchematicComponent
import com.example.model.SchematicProject
import com.example.model.SchematicWire
import com.example.model.StarterTemplate
import com.example.validation.SchematicValidator

object StarterTemplates {

    fun buildProject(
        userId: String,
        name: String,
        description: String,
        template: StarterTemplate,
        layerCount: Long
    ): SchematicProject {
        val (components, wires) = when (template) {
            StarterTemplate.BLANK -> emptyList<SchematicComponent>() to emptyList()
            StarterTemplate.LDO_POWER -> ldoPowerCircuit()
            StarterTemplate.MCU_NODE -> mcuNodeCircuit()
        }

        val draft = SchematicProject(
            userId = userId,
            name = name.trim().ifBlank { template.title },
            description = description.trim().ifBlank { template.subtitle },
            boardWidthMm = template.boardWidthMm,
            boardHeightMm = template.boardHeightMm,
            layerCount = layerCount,
            gridSnapMm = 2.54,
            components = components,
            wires = wires,
            componentCount = components.size.toLong(),
            wireCount = wires.size.toLong()
        )
        val validation = SchematicValidator.validate(draft)
        return draft.copy(
            validationErrorCount = validation.errorCount.toLong(),
            validationSummary = validation.summaryText
        )
    }

    private fun ldoPowerCircuit(): Pair<List<SchematicComponent>, List<SchematicWire>> {
        val j1 = SchematicComponent(
            uid = "c_j1",
            refDes = "J1",
            type = ComponentType.CONNECTOR.name,
            label = "USB-C Input",
            valueText = "5V VBUS",
            footprint = "USB-C-SMD",
            manufacturerPart = "TYPE-C-31-M-12",
            unitPriceUsd = 0.42,
            gridX = 4L,
            gridY = 7L,
            voltageRatingV = 20.0,
            powerRatingMw = 3000.0
        )
        val c1 = SchematicComponent(
            uid = "c_c1",
            refDes = "C1",
            type = ComponentType.CAPACITOR.name,
            label = "Input Filter Cap",
            valueText = "10µF",
            footprint = "0805",
            manufacturerPart = "CL21A106KAYNNNE",
            unitPriceUsd = 0.05,
            gridX = 9L,
            gridY = 4L,
            voltageRatingV = 25.0,
            powerRatingMw = 0.0
        )
        val u1 = SchematicComponent(
            uid = "c_u1",
            refDes = "U1",
            type = ComponentType.REGULATOR.name,
            label = "3.3V LDO Reg",
            valueText = "AMS1117-3.3",
            footprint = "SOT-223",
            manufacturerPart = "AMS1117-3.3",
            unitPriceUsd = 0.24,
            gridX = 13L,
            gridY = 7L,
            voltageRatingV = 15.0,
            powerRatingMw = 1000.0
        )
        val c2 = SchematicComponent(
            uid = "c_c2",
            refDes = "C2",
            type = ComponentType.CAPACITOR.name,
            label = "Output Decoupling",
            valueText = "100nF",
            footprint = "0603",
            manufacturerPart = "CL10B104KB8NNNC",
            unitPriceUsd = 0.03,
            gridX = 18L,
            gridY = 4L,
            voltageRatingV = 50.0,
            powerRatingMw = 0.0
        )
        val r1 = SchematicComponent(
            uid = "c_r1",
            refDes = "R1",
            type = ComponentType.RESISTOR.name,
            label = "LED Current Limit",
            valueText = "470Ω",
            footprint = "0603",
            manufacturerPart = "RC0603FR-07470RL",
            unitPriceUsd = 0.02,
            gridX = 20L,
            gridY = 10L,
            voltageRatingV = 50.0,
            powerRatingMw = 100.0
        )
        val d1 = SchematicComponent(
            uid = "c_d1",
            refDes = "D1",
            type = ComponentType.LED.name,
            label = "Power LED",
            valueText = "Green 525nm",
            footprint = "0603",
            manufacturerPart = "LTST-C191KGKT",
            unitPriceUsd = 0.07,
            gridX = 24L,
            gridY = 10L,
            voltageRatingV = 3.3,
            powerRatingMw = 60.0
        )
        val gnd = SchematicComponent(
            uid = "c_gnd",
            refDes = "GND1",
            type = ComponentType.GND_RAIL.name,
            label = "GND Rail",
            valueText = "0V",
            footprint = "NET-FLAG",
            manufacturerPart = "VIRTUAL-NET",
            unitPriceUsd = 0.0,
            gridX = 13L,
            gridY = 14L,
            voltageRatingV = 0.0,
            powerRatingMw = 0.0
        )

        val wires = listOf(
            SchematicWire("w_1", "VBUS_5V", "c_j1", 0L, "c_u1", 0L, 0.5, "TOP"),
            SchematicWire("w_2", "VBUS_5V", "c_j1", 0L, "c_c1", 0L, 0.4, "TOP"),
            SchematicWire("w_3", "VCC_3V3", "c_u1", 2L, "c_c2", 0L, 0.5, "TOP"),
            SchematicWire("w_4", "VCC_3V3", "c_u1", 2L, "c_r1", 0L, 0.35, "TOP"),
            SchematicWire("w_5", "LED_A", "c_r1", 1L, "c_d1", 0L, 0.25, "TOP"),
            SchematicWire("w_6", "GND", "c_j1", 3L, "c_gnd", 0L, 0.6, "BOTTOM"),
            SchematicWire("w_7", "GND", "c_u1", 1L, "c_gnd", 0L, 0.6, "BOTTOM"),
            SchematicWire("w_8", "GND", "c_c1", 1L, "c_gnd", 0L, 0.35, "BOTTOM"),
            SchematicWire("w_9", "GND", "c_c2", 1L, "c_gnd", 0L, 0.35, "BOTTOM"),
            SchematicWire("w_10", "GND", "c_d1", 1L, "c_gnd", 0L, 0.3, "BOTTOM")
        )
        return listOf(j1, c1, u1, c2, r1, d1, gnd) to wires
    }

    private fun mcuNodeCircuit(): Pair<List<SchematicComponent>, List<SchematicWire>> {
        val vcc = SchematicComponent(
            uid = "m_vcc",
            refDes = "PWR1",
            type = ComponentType.VCC_RAIL.name,
            label = "VCC Power",
            valueText = "3.3V",
            footprint = "NET-FLAG",
            manufacturerPart = "VIRTUAL-NET",
            unitPriceUsd = 0.0,
            gridX = 6L,
            gridY = 3L,
            voltageRatingV = 3.3,
            powerRatingMw = 0.0
        )
        val gnd = SchematicComponent(
            uid = "m_gnd",
            refDes = "GND1",
            type = ComponentType.GND_RAIL.name,
            label = "GND Rail",
            valueText = "0V",
            footprint = "NET-FLAG",
            manufacturerPart = "VIRTUAL-NET",
            unitPriceUsd = 0.0,
            gridX = 6L,
            gridY = 15L,
            voltageRatingV = 0.0,
            powerRatingMw = 0.0
        )
        val c1 = SchematicComponent(
            uid = "m_c1",
            refDes = "C1",
            type = ComponentType.CAPACITOR.name,
            label = "VDD Decoupling",
            valueText = "100nF",
            footprint = "0603",
            manufacturerPart = "CL10B104KB8NNNC",
            unitPriceUsd = 0.03,
            gridX = 10L,
            gridY = 4L,
            voltageRatingV = 50.0,
            powerRatingMw = 0.0
        )
        val u1 = SchematicComponent(
            uid = "m_u1",
            refDes = "U1",
            type = ComponentType.MCU.name,
            label = "STM32G030 MCU",
            valueText = "STM32G030",
            footprint = "TSSOP-20",
            manufacturerPart = "STM32G030F6P6",
            unitPriceUsd = 1.45,
            gridX = 15L,
            gridY = 9L,
            voltageRatingV = 3.6,
            powerRatingMw = 250.0
        )
        val r1 = SchematicComponent(
            uid = "m_r1",
            refDes = "R1",
            type = ComponentType.RESISTOR.name,
            label = "NRST Pull-Up",
            valueText = "10kΩ",
            footprint = "0603",
            manufacturerPart = "RC0603FR-0710KL",
            unitPriceUsd = 0.02,
            gridX = 23L,
            gridY = 5L,
            voltageRatingV = 50.0,
            powerRatingMw = 100.0
        )
        val sw1 = SchematicComponent(
            uid = "m_sw1",
            refDes = "SW1",
            type = ComponentType.SWITCH.name,
            label = "Reset Tact Switch",
            valueText = "Momentary",
            footprint = "SMD-6x3.5",
            manufacturerPart = "B3U-1000P",
            unitPriceUsd = 0.18,
            gridX = 23L,
            gridY = 13L,
            voltageRatingV = 12.0,
            powerRatingMw = 50.0
        )
        val y1 = SchematicComponent(
            uid = "m_y1",
            refDes = "Y1",
            type = ComponentType.CRYSTAL.name,
            label = "HSE Crystal",
            valueText = "16MHz",
            footprint = "HC49-SMD",
            manufacturerPart = "ABM3B-16.000MHZ",
            unitPriceUsd = 0.35,
            gridX = 23L,
            gridY = 9L,
            voltageRatingV = 5.0,
            powerRatingMw = 10.0
        )
        val wires = listOf(
            SchematicWire("mw_1", "VCC_3V3", "m_vcc", 0L, "m_u1", 0L, 0.4, "TOP"),
            SchematicWire("mw_2", "VCC_3V3", "m_vcc", 0L, "m_c1", 0L, 0.3, "TOP"),
            SchematicWire("mw_3", "VCC_3V3", "m_vcc", 0L, "m_r1", 0L, 0.25, "TOP"),
            SchematicWire("mw_4", "GND", "m_gnd", 0L, "m_u1", 1L, 0.5, "BOTTOM"),
            SchematicWire("mw_5", "GND", "m_gnd", 0L, "m_c1", 1L, 0.3, "BOTTOM"),
            SchematicWire("mw_6", "GND", "m_gnd", 0L, "m_sw1", 1L, 0.3, "BOTTOM"),
            SchematicWire("mw_7", "NRST", "m_u1", 4L, "m_r1", 1L, 0.25, "TOP"),
            SchematicWire("mw_8", "NRST", "m_u1", 4L, "m_sw1", 0L, 0.25, "TOP"),
            SchematicWire("mw_9", "OSC_IN", "m_u1", 2L, "m_y1", 0L, 0.2, "TOP"),
            SchematicWire("mw_10", "OSC_OUT", "m_u1", 3L, "m_y1", 1L, 0.2, "TOP")
        )
        return listOf(vcc, gnd, c1, u1, r1, sw1, y1) to wires
    }
}
