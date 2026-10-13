package app.photoxpo.backup

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import app.photoxpo.core.BatteryInput
import app.photoxpo.core.BatteryStatus

/** Thin adapter: reads the sticky battery broadcast. Unknown (no level) reads as null and never stops a run. */
class AndroidBatteryInput(private val context: Context) : BatteryInput {
    override fun read(): BatteryStatus? {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        return batteryStatusFrom(
            level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
            scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1),
            plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0),
        )
    }
}

internal fun batteryStatusFrom(level: Int, scale: Int, plugged: Int): BatteryStatus? {
    if (level < 0 || scale <= 0) return null
    return BatteryStatus(percent = level * 100 / scale, charging = plugged != 0)
}
