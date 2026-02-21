package net.adamfoster.headoverwheels.service.ble

import io.mockk.mockk
import io.mockk.verify
import net.adamfoster.headoverwheels.data.RideRepository
import org.junit.Test

class HeartRateManagerTest {

    private val repository: RideRepository = mockk(relaxed = true)
    private val manager = HeartRateManager(repository)

    @Test
    fun `onCharacteristicChanged parses UINT8 heart rate correctly`() {
        val uuid = HeartRateManager.HEART_RATE_MEASUREMENT_CHAR_UUID
        // Flags: 0 (UINT8), HR: 60 (0x3C)
        val value = byteArrayOf(0x00, 0x3C)

        manager.onCharacteristicChanged(uuid, value)

        verify { repository.updateHeartRate(60) }
    }

    @Test
    fun `onCharacteristicChanged parses UINT16 heart rate correctly`() {
        val uuid = HeartRateManager.HEART_RATE_MEASUREMENT_CHAR_UUID
        // Flags: 1 (UINT16), HR: 300 (0x012C -> LSB: 2C, MSB: 01)
        val value = byteArrayOf(0x01, 0x2C, 0x01)

        manager.onCharacteristicChanged(uuid, value)

        verify { repository.updateHeartRate(300) }
    }

    @Test
    fun `onDisconnected resets heart rate and status`() {
        manager.onDisconnected()

        verify { repository.updateHeartRate(0) }
        verify { repository.updateHrSensorStatus("disconnected") }
    }

    @Test
    fun `onCharacteristicChanged ignores truncated UINT8 packet`() {
        val uuid = HeartRateManager.HEART_RATE_MEASUREMENT_CHAR_UUID
        // Only the flag byte present — no HR byte
        val value = byteArrayOf(0x00)

        manager.onCharacteristicChanged(uuid, value)

        verify(exactly = 0) { repository.updateHeartRate(any()) }
    }

    @Test
    fun `onCharacteristicChanged ignores truncated UINT16 packet`() {
        val uuid = HeartRateManager.HEART_RATE_MEASUREMENT_CHAR_UUID
        // Flag says UINT16 but only 2 bytes present — missing the MSB
        val value = byteArrayOf(0x01, 0x2C)

        manager.onCharacteristicChanged(uuid, value)

        verify(exactly = 0) { repository.updateHeartRate(any()) }
    }
}
