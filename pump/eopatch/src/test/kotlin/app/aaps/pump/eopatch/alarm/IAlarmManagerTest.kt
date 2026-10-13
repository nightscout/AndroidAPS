package app.aaps.pump.eopatch.alarm

import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class IAlarmManagerTest {

    @Test
    fun `interface should define init method`() {
        val alarmManager = mock(IAlarmManager::class.java)

        alarmManager.init()

        verify(alarmManager).init()
    }

    @Test
    fun `interface should define restartAll method`() {
        val alarmManager = mock(IAlarmManager::class.java)

        alarmManager.restartAll()

        verify(alarmManager).restartAll()
    }
}
