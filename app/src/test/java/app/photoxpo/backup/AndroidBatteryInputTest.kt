package app.photoxpo.backup

import app.photoxpo.core.BatteryStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AndroidBatteryInputTest {
    @Test
    fun levelAndScaleBecomeAPercent_andAnyPowerSourceMeansCharging() {
        assertEquals(BatteryStatus(15, charging = false), batteryStatusFrom(15, 100, 0))
        assertEquals(BatteryStatus(15, charging = true), batteryStatusFrom(30, 200, 2))
    }

    @Test
    fun missingLevelIsUnknown() {
        assertNull(batteryStatusFrom(-1, 100, 0))
        assertNull(batteryStatusFrom(50, 0, 0))
    }
}
