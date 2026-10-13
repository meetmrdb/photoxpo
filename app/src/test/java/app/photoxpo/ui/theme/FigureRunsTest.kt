package app.photoxpo.ui.theme

import org.junit.Test
import org.junit.Assert.assertEquals

class FigureRunsTest {
    @Test
    fun aCountInASentence_isOneRun() {
        assertEquals(listOf(0..2), figureRuns("502 photos backed up"))
    }

    @Test
    fun aThousandsSeparator_staysInsideTheRun() {
        val text = "Size limit reached. 3,660 files left."
        assertEquals(listOf("3,660"), figureRuns(text).map { text.substring(it) })
    }

    @Test
    fun aDecimalPoint_staysInsideTheRun() {
        val text = "41.2 GB to back up"
        assertEquals(listOf("41.2"), figureRuns(text).map { text.substring(it) })
    }

    @Test
    fun aSentenceEndingFullStop_isNotPartOfTheRun() {
        val text = "Only 1."
        assertEquals(listOf("1"), figureRuns(text).map { text.substring(it) })
    }

    @Test
    fun aYearInAMonthLabel_isARun_soItsOnesAreNotSetInTheSerif() {
        val text = "October 2021"
        assertEquals(listOf("2021"), figureRuns(text).map { text.substring(it) })
    }

    @Test
    fun textWithoutDigits_hasNoRuns() {
        assertEquals(emptyList<IntRange>(), figureRuns("No photos yet"))
    }
}
