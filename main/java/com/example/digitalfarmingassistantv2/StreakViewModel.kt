package com.example.digitalfarmingassistantv2

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

class StreakViewModel(app: Application) : AndroidViewModel(app) {

    private val gamificationManager = GamificationManager(app.applicationContext)

    val streakData = gamificationManager.streakDataFlow

    private val _pendingReward = MutableStateFlow<DailyReward?>(null)
    val pendingReward: StateFlow<DailyReward?> = _pendingReward.asStateFlow()

    private var currentDate: LocalDate = LocalDate.now()

    init {
        checkStreak()
        startMidnightChecker()
    }

    fun checkStreak() {
        viewModelScope.launch {
            val today = LocalDate.now()
            currentDate = today
            val reward = gamificationManager.checkAndUpdateDailyStreak(today)
            if (reward != null) {
                _pendingReward.value = reward
            }
        }
    }

    private fun startMidnightChecker() {
        viewModelScope.launch {
            while (true) {
                delay(10_000)
                val now = LocalDate.now()
                if (now != currentDate) {
                    currentDate = now
                    checkStreak()
                }
            }
        }
    }

    fun claimReward() {
        if (_pendingReward.value == null) return
        viewModelScope.launch {
            gamificationManager.markRewardClaimed(currentDate)
            _pendingReward.value = null
        }
    }
}
