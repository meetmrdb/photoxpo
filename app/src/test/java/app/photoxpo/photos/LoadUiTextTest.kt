package app.photoxpo.photos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoadUiTextTest {
    @Test
    fun loadButtonShowsTheOriginalSize() {
        val text = loadUiText(
            state = LoadUiState.Idle,
            sizeBytes = FOUR_POINT_TWO_MB,
            driveOnline = true,
            originalOnPhone = false,
        )
        assertEquals("Load · 4.2 MB", text.loadLabel)
        assertEquals("Load · 4.2 MB", loadButtonLabel(FOUR_POINT_TWO_MB))
        assertTrue(text.loadEnabled)
        assertNull(text.message)
        assertFalse(text.showCancel)
        assertFalse(text.showRetry)
        assertFalse(text.showLoadedOriginal)
    }

    @Test
    fun driveOffline_disablesLoadAndExplains() {
        val text = loadUiText(
            state = LoadUiState.Idle,
            sizeBytes = FOUR_POINT_TWO_MB,
            driveOnline = false,
            originalOnPhone = false,
        )
        assertEquals(LOAD_NEEDS_DRIVE, text.message)
        assertEquals("Load · 4.2 MB", text.loadLabel)
        assertFalse(text.loadEnabled)
        assertFalse(text.showRetry)
    }

    @Test
    fun disabledReason_isTheMessageWhileTheDriveIsOut() {
        val text = loadUiText(
            state = LoadUiState.Disabled(LOAD_NEEDS_DRIVE),
            sizeBytes = 1024,
            driveOnline = false,
            originalOnPhone = false,
        )
        assertEquals(LOAD_NEEDS_DRIVE, text.message)
        assertEquals("Load · 1 KB", text.loadLabel)
        assertFalse(text.loadEnabled)
    }

    @Test
    fun driveBack_enablesLoadAfterADisabledState() {
        val text = loadUiText(
            state = LoadUiState.Disabled(LOAD_NEEDS_DRIVE),
            sizeBytes = FOUR_POINT_TWO_MB,
            driveOnline = true,
            originalOnPhone = false,
        )
        assertEquals("Load · 4.2 MB", text.loadLabel)
        assertTrue(text.loadEnabled)
        assertNull(text.message)
    }

    @Test
    fun loading_showsProgressAndCancel() {
        val copied = 2_202_010L
        val text = loadUiText(
            state = LoadUiState.Loading(copied),
            sizeBytes = FOUR_POINT_TWO_MB,
            driveOnline = true,
            originalOnPhone = false,
        )
        assertEquals("2.1 MB of 4.2 MB", text.message)
        assertNull(text.loadLabel)
        assertTrue(text.showCancel)
        assertFalse(text.showRetry)
        assertEquals(0.5f, text.progressFraction!!, 0.01f)
    }

    @Test
    fun loaded_swapsInTheOriginal() {
        val text = loadUiText(
            state = LoadUiState.Loaded("content://photoxpo/1"),
            sizeBytes = FOUR_POINT_TWO_MB,
            driveOnline = false,
            originalOnPhone = false,
        )
        assertEquals(LOAD_ON_PHONE_AND_DRIVE, text.message)
        assertTrue(text.showLoadedOriginal)
        assertNull(text.loadLabel)
        assertFalse(text.showCancel)
    }

    @Test
    fun originalAlreadyOnPhone_saysSoWithoutAButton() {
        val text = loadUiText(
            state = LoadUiState.Idle,
            sizeBytes = FOUR_POINT_TWO_MB,
            driveOnline = false,
            originalOnPhone = true,
        )
        assertEquals(LOAD_ON_PHONE_AND_DRIVE, text.message)
        assertFalse(text.showLoadedOriginal)
        assertNull(text.loadLabel)
    }

    @Test
    fun driveUnplugged_offersRetry() {
        val text = loadUiText(
            state = LoadUiState.Failed(LOAD_DRIVE_UNPLUGGED, canRetry = true),
            sizeBytes = FOUR_POINT_TWO_MB,
            driveOnline = false,
            originalOnPhone = false,
        )
        assertEquals("Your drive was unplugged. Plug it back in and try again.", text.message)
        assertTrue(text.showRetry)
        assertNull(text.loadLabel)
        assertFalse(text.loadEnabled)
    }

    @Test
    fun failedWithoutRetry_hidesTheButton() {
        val text = loadUiText(
            state = LoadUiState.Failed(LOAD_NOT_ON_DRIVE, canRetry = false),
            sizeBytes = 10,
            driveOnline = true,
            originalOnPhone = false,
        )
        assertEquals(LOAD_NOT_ON_DRIVE, text.message)
        assertFalse(text.showRetry)
        assertNull(text.loadLabel)
    }

    private companion object {
        const val FOUR_POINT_TWO_MB = 4_404_019L
    }
}
