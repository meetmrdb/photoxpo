package app.photoxpo.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.photoxpo.backup.BackupController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/**
 * Maps [BackupController.state] into [HomeUiModel] and forwards start and pause.
 * [homeUiModel] receives the time from here.
 */
class HomeViewModel(
    private val controller: BackupController,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    val model: StateFlow<HomeUiModel> = combine(controller.state, clockTicks()) { state, _ ->
        homeUiModel(state, nowEpochMillis())
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = homeUiModel(controller.state.value, nowEpochMillis()),
    )

    fun start() {
        controller.start()
    }

    fun pause() {
        controller.pause()
    }

    private fun clockTicks(): Flow<Long> = flow {
        while (true) {
            emit(nowEpochMillis())
            delay(CLOCK_PERIOD_MS)
        }
    }

    private companion object {
        const val CLOCK_PERIOD_MS = 30_000L
    }
}
