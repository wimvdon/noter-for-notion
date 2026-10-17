package be.wimvandonck.noter.logic

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

class NotionDatesTest {
    private val brussels = ZoneId.of("Europe/Brussels")

    @Test
    fun `a picked day without time is a plain date`() {
        val millis = NotionDates.pickerMillis("2026-10-15", brussels)!!
        assertEquals("2026-10-15", NotionDates.fromPicker(millis, null, brussels))
    }

    @Test
    fun `a picked day with time gets the phone's offset`() {
        val millis = NotionDates.pickerMillis("2026-10-15", brussels)!!
        assertEquals("2026-10-15T09:30:00.000+02:00", NotionDates.fromPicker(millis, LocalTime.of(9, 30), brussels))
    }

    @Test
    fun `times from Notion are shown in the phone's time zone`() {
        assertEquals(LocalTime.of(11, 0), NotionDates.parseTime("2026-10-15T09:00:00.000Z", brussels))
        assertEquals("Oct 15, 2026", NotionDates.display("2026-10-15", Locale.US, brussels))
    }

    @Test
    fun `changing the time keeps the day`() {
        assertEquals("2026-10-15T18:00:00.000+02:00", NotionDates.withTime("2026-10-15", LocalTime.of(18, 0), brussels))
    }

    @Test
    fun `unknown text is shown as it is`() {
        assertEquals("soon", NotionDates.display("soon", Locale.US, brussels))
    }
}
