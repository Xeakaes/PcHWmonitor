package com.example.pchwmonitor.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SystemStatus(
    val type: String = "status",
    val timestamp: Long,
    val available: Boolean = true,
    val error: String? = null,
    val pc: PcInfo? = null,
    val cpu: CpuInfo? = null,
    val gpu: GpuInfo? = null,
    val igpu: GpuInfo? = null,
    val ram: RamInfo? = null,
)

@Serializable
data class PcInfo(
    val name: String? = null,
    val os: String? = null,
    val source: String? = null,
)

@Serializable
data class CpuInfo(
    val name: String? = null,
    @SerialName("usagePct") val usagePct: Float? = null,
    @SerialName("tempC") val tempC: Float? = null,
    @SerialName("clockMhz") val clockMhz: Float? = null,
    @SerialName("powerW") val powerW: Float? = null,
    val loads: List<Float>? = null,
)

@Serializable
data class GpuInfo(
    val name: String? = null,
    @SerialName("usagePct") val usagePct: Float? = null,
    @SerialName("tempC") val tempC: Float? = null,
    @SerialName("hotspotC") val hotspotC: Float? = null,
    @SerialName("vramUsedMb") val vramUsedMb: Float? = null,
    @SerialName("vramTotalMb") val vramTotalMb: Float? = null,
    @SerialName("coreClockMhz") val coreClockMhz: Float? = null,
    @SerialName("memClockMhz") val memClockMhz: Float? = null,
    @SerialName("powerW") val powerW: Float? = null,
    val fps: Float? = null,
)

@Serializable
data class RamInfo(
    @SerialName("usedGb") val usedGb: Float? = null,
    @SerialName("totalGb") val totalGb: Float? = null,
    @SerialName("usagePct") val usagePct: Float? = null,
    @SerialName("clockMhz") val clockMhz: Float? = null,
)

@Serializable
data class WelcomeInfo(
    val type: String = "welcome",
    @SerialName("intervalMs") val intervalMs: Int,
    @SerialName("serverName") val serverName: String,
    val source: String,
    @SerialName("pcName") val pcName: String? = null,
)

sealed class WsMessage {
    data class Welcome(val info: WelcomeInfo) : WsMessage()
    data class Status(val status: SystemStatus) : WsMessage()
    data class ParseFailure(val raw: String, val reason: String) : WsMessage()
}
