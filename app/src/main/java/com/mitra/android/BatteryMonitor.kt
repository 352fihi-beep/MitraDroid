package com.mitra.android

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

class BatteryMonitor(
    private val context: Context
) {
    data class BatteryState(
        val isCharging: Boolean,
        val level: Float,
        val temperatureCelsius: Float,
        val status: String
    ) {
        fun isLowPower(threshold: Int = 20): Boolean = !isCharging && level < threshold
        fun isOverheating(thresholdCelsius: Float = 40f): Boolean = temperatureCelsius > thresholdCelsius
    }

    fun getBatteryState(): BatteryState {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            context.registerReceiver(null, ifilter)
        }

        val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging: Boolean = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val currentLevel: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct: Float = if (currentLevel >= 0 && scale > 0) {
            (currentLevel * 100f) / scale.toFloat()
        } else {
            100f
        }

        val temperatureInt: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val temperatureCelsius: Float = if (temperatureInt > 0) temperatureInt / 10f else 25.0f

        val statusStr = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "CHARGING"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "DISCHARGING"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "NOT_CHARGING"
            BatteryManager.BATTERY_STATUS_FULL -> "FULL"
            else -> "NOMINAL"
        }

        return BatteryState(
            isCharging = isCharging,
            level = batteryPct.coerceIn(0f, 100f),
            temperatureCelsius = temperatureCelsius,
            status = statusStr
        )
    }
}
