package net.adamfoster.headoverwheels.service.ble

import io.mockk.mockk
import io.mockk.verify
import net.adamfoster.headoverwheels.data.RideRepository
import org.junit.Test

class RadarManagerTest {

    private val repository: RideRepository = mockk(relaxed = true)
    private val manager = RadarManager(repository)

    @Test
    fun `onCharacteristicChanged reports no threats for header-only packet`() {
        val uuid = RadarManager.RADAR_DATA_CHAR_UUID
        manager.onCharacteristicChanged(uuid, byteArrayOf(0x00))
        verify { repository.updateRadarDistance(-1) }
    }

    @Test
    fun `onCharacteristicChanged reports distance from a single complete triplet`() {
        val uuid = RadarManager.RADAR_DATA_CHAR_UUID
        // header=0x01, triplet=[ID=0x01, dist=50, speed=0x00]
        val value = byteArrayOf(0x01, 0x01, 50, 0x00)
        manager.onCharacteristicChanged(uuid, value)
        verify { repository.updateRadarDistance(50) }
    }

    @Test
    fun `onCharacteristicChanged ignores incomplete trailing triplet`() {
        val uuid = RadarManager.RADAR_DATA_CHAR_UUID
        // header + one complete triplet (dist=50) + 2-byte stub (incomplete)
        val value = byteArrayOf(0x01, 0x01, 50, 0x00, 0x02, 0x14)
        manager.onCharacteristicChanged(uuid, value)
        // Only the complete triplet is read — distance should be 50, not 0x02 (the stub's ID byte)
        verify { repository.updateRadarDistance(50) }
    }

    @Test
    fun `onCharacteristicChanged reports closest of multiple complete triplets`() {
        val uuid = RadarManager.RADAR_DATA_CHAR_UUID
        // header + triplet 1 (dist=100) + triplet 2 (dist=40)
        val value = byteArrayOf(0x01, 0x01, 100, 0x00, 0x02, 40, 0x00)
        manager.onCharacteristicChanged(uuid, value)
        verify { repository.updateRadarDistance(40) }
    }
}
