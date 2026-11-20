package com.example.digitalfarmingassistantv2

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "gamification_prefs")

data class DailyReward(
    val dayCount: Int,
    val coins: Int,
    val messageEn: String,
    val messageBn: String
)

data class StreakData(
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val lastLoginDate: String? = null,
    val loggedInDates: Set<String> = emptySet(),
    val lastRewardClaimedDate: String? = null
)

class GamificationManager(private val context: Context) {

    private companion object {
        val KEY_LAST_LOGIN_DATE = stringPreferencesKey("last_login_date")
        val KEY_CURRENT_STREAK = intPreferencesKey("current_streak")
        val KEY_LONGEST_STREAK = intPreferencesKey("longest_streak")
        val KEY_LOGGED_IN_DATES = stringSetPreferencesKey("logged_in_dates")
        val KEY_LAST_REWARD_CLAIMED_DATE = stringPreferencesKey("last_reward_claimed_date")
    }

    val streakDataFlow: Flow<StreakData> = context.dataStore.data.map { prefs ->
        StreakData(
            currentStreak = prefs[KEY_CURRENT_STREAK] ?: 0,
            longestStreak = prefs[KEY_LONGEST_STREAK] ?: 0,
            lastLoginDate = prefs[KEY_LAST_LOGIN_DATE],
            loggedInDates = prefs[KEY_LOGGED_IN_DATES] ?: emptySet(),
            lastRewardClaimedDate = prefs[KEY_LAST_REWARD_CLAIMED_DATE]
        )
    }

    suspend fun checkAndUpdateDailyStreak(today: LocalDate = LocalDate.now()): DailyReward? {
        var reward: DailyReward? = null

        context.dataStore.edit { prefs ->
            val lastDateStr = prefs[KEY_LAST_LOGIN_DATE]
            var currentStreak = prefs[KEY_CURRENT_STREAK] ?: 0
            var longestStreak = prefs[KEY_LONGEST_STREAK] ?: 0
            val loggedInDates = (prefs[KEY_LOGGED_IN_DATES] ?: emptySet()).toMutableSet()
            val lastRewardClaimedDate = prefs[KEY_LAST_REWARD_CLAIMED_DATE]

            val todayStr = today.toString()

            if (lastDateStr == null) {
                // First ever login: streak = 1
                currentStreak = 1
                if (currentStreak > longestStreak) {
                    longestStreak = currentStreak
                }
                loggedInDates.add(todayStr)
                prefs[KEY_LAST_LOGIN_DATE] = todayStr
                prefs[KEY_CURRENT_STREAK] = currentStreak
                prefs[KEY_LONGEST_STREAK] = longestStreak
                prefs[KEY_LOGGED_IN_DATES] = loggedInDates

                if (lastRewardClaimedDate != todayStr) {
                    val coins = currentStreak * 10
                    reward = DailyReward(
                        dayCount = currentStreak,
                        coins = coins,
                        messageEn = "Day $currentStreak streak! +$coins coins",
                        messageBn = "দিন $currentStreak স্ট্রিক! +$coins কয়েন"
                    )
                }
            } else {
                val lastDate = LocalDate.parse(lastDateStr)
                val daysBetween = ChronoUnit.DAYS.between(lastDate, today)

                if (daysBetween == 0L) {
                    // Already logged in today: no streak change
                    if (lastRewardClaimedDate != todayStr && currentStreak > 0) {
                        val coins = currentStreak * 10
                        reward = DailyReward(
                            dayCount = currentStreak,
                            coins = coins,
                            messageEn = "Day $currentStreak streak! +$coins coins",
                            messageBn = "দিন $currentStreak স্ট্রিক! +$coins কয়েন"
                        )
                    }
                } else if (daysBetween == 1L) {
                    // Last login was yesterday: streak + 1
                    currentStreak += 1
                    if (currentStreak > longestStreak) {
                        longestStreak = currentStreak
                    }
                    loggedInDates.add(todayStr)
                    prefs[KEY_LAST_LOGIN_DATE] = todayStr
                    prefs[KEY_CURRENT_STREAK] = currentStreak
                    prefs[KEY_LONGEST_STREAK] = longestStreak
                    prefs[KEY_LOGGED_IN_DATES] = loggedInDates

                    if (lastRewardClaimedDate != todayStr) {
                        val coins = currentStreak * 10
                        reward = DailyReward(
                            dayCount = currentStreak,
                            coins = coins,
                            messageEn = "Day $currentStreak streak! +$coins coins",
                            messageBn = "দিন $currentStreak স্ট্রিক! +$coins কয়েন"
                        )
                    }
                } else if (daysBetween > 1L) {
                    // Last login was before yesterday: streak resets to 1
                    currentStreak = 1
                    if (currentStreak > longestStreak) {
                        longestStreak = currentStreak
                    }
                    loggedInDates.add(todayStr)
                    prefs[KEY_LAST_LOGIN_DATE] = todayStr
                    prefs[KEY_CURRENT_STREAK] = currentStreak
                    prefs[KEY_LONGEST_STREAK] = longestStreak
                    prefs[KEY_LOGGED_IN_DATES] = loggedInDates

                    if (lastRewardClaimedDate != todayStr) {
                        val coins = currentStreak * 10
                        reward = DailyReward(
                            dayCount = currentStreak,
                            coins = coins,
                            messageEn = "Day $currentStreak streak! +$coins coins",
                            messageBn = "দিন $currentStreak স্ট্রিক! +$coins কয়েন"
                        )
                    }
                }
            }
        }
        return reward
    }

    suspend fun markRewardClaimed(today: LocalDate = LocalDate.now()) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_REWARD_CLAIMED_DATE] = today.toString()
        }
    }
}
