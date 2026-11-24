package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class ComponentCategory(val title: String) {
    PASSIVE("Passives"),
    SEMICONDUCTOR("Active / ICs"),
    POWER("Power & Rails"),
    CONNECTIVITY("I/O & Timing")
}

enum class PinRole {
    PASSIVE,
    POWER_IN,
    POWER_OUT,
    GROUND,
    DIGITAL_IO,
    ANALOG_IN,
    GATE_BASE,
    DRAIN_COLLECTOR,
    SOURCE_EMITTER
}

data class PinDefinition(
    val index: Int,
    val name: String,
    val role: PinRole,
    val localX: Float,
    val localY: Float
)

enum class ComponentType(
    val displayName: String,
    val category: ComponentCategory,
    val refPrefix: String,
    val defaultValue: String,
    val defaultFootprint: String,
    val defaultMpn: String,
    val defaultPriceUsd: Double,
    val defaultVoltageV: Double,
    val defaultPowerMw: Double,
    val pins: List<PinDefinition>
) {
    RESISTOR(
        displayName = "Resistor",
        category = ComponentCategory.PASSIVE,
        refPrefix = "R",
        defaultValue = "10kΩ",
        defaultFootprint = "0603",
        defaultMpn = "RC0603FR-0710KL",
        defaultPriceUsd = 0.02,
        defaultVoltageV = 50.0,
        defaultPowerMw = 100.0,
        pins = listOf(
            PinDefinition(0, "1", PinRole.PASSIVE, -1.6f, 0f),
            PinDefinition(1, "2", PinRole.PASSIVE, 1.6f, 0f)
        )
    ),
    CAPACITOR(
        displayName = "Capacitor",
        category = ComponentCategory.PASSIVE,
        refPrefix = "C",
        defaultValue = "100nF",
        defaultFootprint = "0603",
        defaultMpn = "CL10B104KB8NNNC",
        defaultPriceUsd = 0.03,
        defaultVoltageV = 50.0,
        defaultPowerMw = 0.0,
        pins = listOf(
            PinDefinition(0, "1", PinRole.PASSIVE, -1.3f, 0f),
            PinDefinition(1, "2", PinRole.PASSIVE, 1.3f, 0f)
        )
    ),
    INDUCTOR(
        displayName = "Inductor",
        category = ComponentCategory.PASSIVE,
        refPrefix = "L",
        defaultValue = "10µH",
        defaultFootprint = "0805",
        defaultMpn = "LQM21PN100MGRD",
        defaultPriceUsd = 0.14,
        defaultVoltageV = 30.0,
        defaultPowerMw = 500.0,
        pins = listOf(
            PinDefinition(0, "1", PinRole.PASSIVE, -1.6f, 0f),
            PinDefinition(1, "2", PinRole.PASSIVE, 1.6f, 0f)
        )
    ),
    DIODE(
        displayName = "Schottky Diode",
        category = ComponentCategory.SEMICONDUCTOR,
        refPrefix = "D",
        defaultValue = "1N5819",
        defaultFootprint = "SOD-123",
        defaultMpn = "1N5819HW-7-F",
        defaultPriceUsd = 0.09,
        defaultVoltageV = 40.0,
        defaultPowerMw = 400.0,
        pins = listOf(
            PinDefinition(0, "A", PinRole.PASSIVE, -1.5f, 0f),
            PinDefinition(1, "K", PinRole.PASSIVE, 1.5f, 0f)
        )
    ),
    LED(
        displayName = "Status LED",
        category = ComponentCategory.SEMICONDUCTOR,
        refPrefix = "D",
        defaultValue = "Green 525nm",
        defaultFootprint = "0603",
        defaultMpn = "LTST-C191KGKT",
        defaultPriceUsd = 0.07,
        defaultVoltageV = 3.3,
        defaultPowerMw = 60.0,
        pins = listOf(
            PinDefinition(0, "A", PinRole.PASSIVE, -1.5f, 0f),
            PinDefinition(1, "K", PinRole.PASSIVE, 1.5f, 0f)
        )
    ),
    MOSFET_N(
        displayName = "N-MOSFET",
        category = ComponentCategory.SEMICONDUCTOR,
        refPrefix = "Q",
        defaultValue = "BSS138",
        defaultFootprint = "SOT-23",
        defaultMpn = "BSS138LT1G",
        defaultPriceUsd = 0.12,
        defaultVoltageV = 50.0,
        defaultPowerMw = 360.0,
        pins = listOf(
            PinDefinition(0, "G", PinRole.GATE_BASE, -1.6f, 0.5f),
            PinDefinition(1, "D", PinRole.DRAIN_COLLECTOR, 1.0f, -1.4f),
            PinDefinition(2, "S", PinRole.SOURCE_EMITTER, 1.0f, 1.4f)
        )
    ),
    MCU(
        displayName = "MCU Module",
        category = ComponentCategory.SEMICONDUCTOR,
        refPrefix = "U",
        defaultValue = "STM32G030",
        defaultFootprint = "TSSOP-20",
        defaultMpn = "STM32G030F6P6",
        defaultPriceUsd = 1.45,
        defaultVoltageV = 3.6,
        defaultPowerMw = 250.0,
        pins = listOf(
            PinDefinition(0, "VDD", PinRole.POWER_IN, -2.0f, -1.0f),
            PinDefinition(1, "GND", PinRole.GROUND, -2.0f, 1.0f),
            PinDefinition(2, "PA0", PinRole.DIGITAL_IO, 2.0f, -1.0f),
            PinDefinition(3, "PA1", PinRole.DIGITAL_IO, 2.0f, 0.0f),
            PinDefinition(4, "NRST", PinRole.DIGITAL_IO, 2.0f, 1.0f),
            PinDefinition(5, "SWDIO", PinRole.DIGITAL_IO, -2.0f, 0.0f)
        )
    ),
    REGULATOR(
        displayName = "3.3V LDO Reg",
        category = ComponentCategory.POWER,
        refPrefix = "U",
        defaultValue = "AMS1117-3.3",
        defaultFootprint = "SOT-223",
        defaultMpn = "AMS1117-3.3",
        defaultPriceUsd = 0.24,
        defaultVoltageV = 15.0,
        defaultPowerMw = 1000.0,
        pins = listOf(
            PinDefinition(0, "VIN", PinRole.POWER_IN, -1.8f, -0.6f),
            PinDefinition(1, "GND", PinRole.GROUND, 0.0f, 1.5f),
            PinDefinition(2, "VOUT", PinRole.POWER_OUT, 1.8f, -0.6f)
        )
    ),
    OPAMP(
        displayName = "Op-Amp",
        category = ComponentCategory.SEMICONDUCTOR,
        refPrefix = "U",
        defaultValue = "LMV358",
        defaultFootprint = "SOIC-8",
        defaultMpn = "LMV358IDR",
        defaultPriceUsd = 0.28,
        defaultVoltageV = 5.5,
        defaultPowerMw = 120.0,
        pins = listOf(
            PinDefinition(0, "IN+", PinRole.ANALOG_IN, -1.8f, -0.7f),
            PinDefinition(1, "IN-", PinRole.ANALOG_IN, -1.8f, 0.7f),
            PinDefinition(2, "OUT", PinRole.DIGITAL_IO, 1.8f, 0.0f),
            PinDefinition(3, "V+", PinRole.POWER_IN, 0.0f, -1.4f),
            PinDefinition(4, "V-", PinRole.GROUND, 0.0f, 1.4f)
        )
    ),
    CRYSTAL(
        displayName = "Crystal Osc",
        category = ComponentCategory.CONNECTIVITY,
        refPrefix = "Y",
        defaultValue = "16MHz",
        defaultFootprint = "HC49-SMD",
        defaultMpn = "ABM3B-16.000MHZ",
        defaultPriceUsd = 0.35,
        defaultVoltageV = 5.0,
        defaultPowerMw = 10.0,
        pins = listOf(
            PinDefinition(0, "X1", PinRole.DIGITAL_IO, -1.5f, 0f),
            PinDefinition(1, "X2", PinRole.DIGITAL_IO, 1.5f, 0f)
        )
    ),
    CONNECTOR(
        displayName = "USB-C / Header",
        category = ComponentCategory.CONNECTIVITY,
        refPrefix = "J",
        defaultValue = "USB-C 6P",
        defaultFootprint = "USB-C-SMD",
        defaultMpn = "TYPE-C-31-M-12",
        defaultPriceUsd = 0.42,
        defaultVoltageV = 20.0,
        defaultPowerMw = 3000.0,
        pins = listOf(
            PinDefinition(0, "VBUS", PinRole.POWER_OUT, 1.8f, -1.0f),
            PinDefinition(1, "D+", PinRole.DIGITAL_IO, 1.8f, -0.3f),
            PinDefinition(2, "D-", PinRole.DIGITAL_IO, 1.8f, 0.3f),
            PinDefinition(3, "GND", PinRole.GROUND, 1.8f, 1.0f)
        )
    ),
    SWITCH(
        displayName = "Tact Switch",
        category = ComponentCategory.CONNECTIVITY,
        refPrefix = "SW",
        defaultValue = "Momentary",
        defaultFootprint = "SMD-6x3.5",
        defaultMpn = "B3U-1000P",
        defaultPriceUsd = 0.18,
        defaultVoltageV = 12.0,
        defaultPowerMw = 50.0,
        pins = listOf(
            PinDefinition(0, "1", PinRole.PASSIVE, -1.5f, 0f),
            PinDefinition(1, "2", PinRole.PASSIVE, 1.5f, 0f)
        )
    ),
    VCC_RAIL(
        displayName = "VCC Power",
        category = ComponentCategory.POWER,
        refPrefix = "PWR",
        defaultValue = "3.3V",
        defaultFootprint = "NET-FLAG",
        defaultMpn = "VIRTUAL-NET",
        defaultPriceUsd = 0.0,
        defaultVoltageV = 3.3,
        defaultPowerMw = 0.0,
        pins = listOf(
            PinDefinition(0, "VCC", PinRole.POWER_OUT, 0f, 1.2f)
        )
    ),
    GND_RAIL(
        displayName = "GND Rail",
        category = ComponentCategory.POWER,
        refPrefix = "GND",
        defaultValue = "0V",
        defaultFootprint = "NET-FLAG",
        defaultMpn = "VIRTUAL-NET",
        defaultPriceUsd = 0.0,
        defaultVoltageV = 0.0,
        defaultPowerMw = 0.0,
        pins = listOf(
            PinDefinition(0, "GND", PinRole.GROUND, 0f, -1.2f)
        )
    );

    companion object {
        fun safeValueOf(raw: String): ComponentType =
            entries.firstOrNull { it.name == raw } ?: RESISTOR
    }
}

@Keep
@IgnoreExtraProperties
data class SchematicComponent(
    val uid: String = "",
    val refDes: String = "R1",
    val type: String = ComponentType.RESISTOR.name,
    val label: String = "Resistor",
    val valueText: String = "10kΩ",
    val footprint: String = "0603",
    val manufacturerPart: String = "",
    val unitPriceUsd: Double = 0.02,
    val gridX: Long = 0L,
    val gridY: Long = 0L,
    val rotationDeg: Long = 0L,
    val voltageRatingV: Double = 50.0,
    val powerRatingMw: Double = 100.0
) {
    fun resolvedType(): ComponentType = ComponentType.safeValueOf(type)

    fun toMap(): Map<String, Any> = mapOf(
        "uid" to uid,
        "refDes" to refDes,
        "type" to type,
        "label" to label,
        "valueText" to valueText,
        "footprint" to footprint,
        "manufacturerPart" to manufacturerPart,
        "unitPriceUsd" to unitPriceUsd,
        "gridX" to gridX,
        "gridY" to gridY,
        "rotationDeg" to rotationDeg,
        "voltageRatingV" to voltageRatingV,
        "powerRatingMw" to powerRatingMw
    )
}

@Keep
@IgnoreExtraProperties
data class SchematicWire(
    val uid: String = "",
    val netName: String = "NET1",
    val fromCompUid: String = "",
    val fromPinIndex: Long = 0L,
    val toCompUid: String = "",
    val toPinIndex: Long = 0L,
    val traceWidthMm: Double = 0.25,
    val copperLayer: String = "TOP"
) {
    fun toMap(): Map<String, Any> = mapOf(
        "uid" to uid,
        "netName" to netName,
        "fromCompUid" to fromCompUid,
        "fromPinIndex" to fromPinIndex,
        "toCompUid" to toCompUid,
        "toPinIndex" to toPinIndex,
        "traceWidthMm" to traceWidthMm,
        "copperLayer" to copperLayer
    )
}

@Keep
@IgnoreExtraProperties
data class SchematicProject(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "Untitled Board",
    val description: String = "",
    val boardWidthMm: Double = 80.0,
    val boardHeightMm: Double = 60.0,
    val layerCount: Long = 2L,
    val gridSnapMm: Double = 2.54,
    val components: List<SchematicComponent> = emptyList(),
    val wires: List<SchematicWire> = emptyList(),
    val componentCount: Long = 0L,
    val wireCount: Long = 0L,
    val validationErrorCount: Long = 0L,
    val validationSummary: String = "Ready",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

enum class ValidationSeverity {
    ERROR,
    WARNING,
    INFO
}

data class ValidationIssue(
    val id: String,
    val severity: ValidationSeverity,
    val code: String,
    val title: String,
    val message: String,
    val componentUid: String? = null
)

data class ValidationResult(
    val issues: List<ValidationIssue>,
    val errorCount: Int,
    val warningCount: Int,
    val summaryText: String
)

data class BomLineItem(
    val itemNumber: Int,
    val partName: String,
    val valueText: String,
    val footprint: String,
    val manufacturerPart: String,
    val designators: List<String>,
    val quantity: Int,
    val unitPriceUsd: Double,
    val extendedPriceUsd: Double
)

enum class EditorTool {
    SELECT_MOVE,
    WIRE_ROUTE
}

enum class StarterTemplate(
    val title: String,
    val subtitle: String,
    val boardWidthMm: Double,
    val boardHeightMm: Double
) {
    BLANK(
        title = "Blank 2-Layer Board",
        subtitle = "Clean 80×60mm canvas ready for custom design",
        boardWidthMm = 80.0,
        boardHeightMm = 60.0
    ),
    LDO_POWER(
        title = "5V → 3.3V USB LDO Supply",
        subtitle = "USB-C input, AMS1117 regulator, decoupling caps & status LED",
        boardWidthMm = 70.0,
        boardHeightMm = 50.0
    ),
    MCU_NODE(
        title = "STM32 Sensor Controller",
        subtitle = "MCU with 16MHz crystal, pull-up reset switch & 3.3V rail",
        boardWidthMm = 90.0,
        boardHeightMm = 65.0
    )
}
