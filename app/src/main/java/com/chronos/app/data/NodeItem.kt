package com.chronos.app.data

enum class DimensionCategory {
    FISICO, MENTAL, EXPRESIVO, OCUPACIONAL
}

data class NodeItem(
    val id: String,
    val title: String,
    val timeInfo: String,
    val dimension: DimensionCategory,
    var isActivated: Boolean = false,
    var activationText: String = "> SE ACTIVA EN: 08:00",
    var statusState: Int = 0 // 0: Nulo/Incompleto, 1: Cumplido (Chulito), 2: En Proceso
)
