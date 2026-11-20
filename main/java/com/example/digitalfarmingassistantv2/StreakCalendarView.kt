package com.example.digitalfarmingassistantv2

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.app.Application
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun StreakCalendarCard(
    isBn: Boolean,
    modifier: Modifier = Modifier,
    viewModel: StreakViewModel? = null
) {
    val context = LocalContext.current
    val streakViewModel = viewModel ?: remember {
        StreakViewModel(context.applicationContext as Application)
    }
    val streakData by streakViewModel.streakData.collectAsState(initial = StreakData())
    val pendingReward by streakViewModel.pendingReward.collectAsState()

    val today = LocalDate.now()
    val yearMonth = YearMonth.now()

    if (pendingReward != null) {
        DailyRewardDialog(
            reward = pendingReward!!,
            isBn = isBn,
            onClaim = { streakViewModel.claimReward() }
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            // Header with streak stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🔥", fontSize = 22.sp)
                    Spacer(Modifier.width(6.dp))
                    Column {
                        Text(
                            text = if (isBn) "বর্তমান স্ট্রিক" else "Current Streak",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                        Text(
                            text = if (isBn) "${streakData.currentStreak} দিন" else "${streakData.currentStreak} Days",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🏆", fontSize = 22.sp)
                    Spacer(Modifier.width(6.dp))
                    Column {
                        Text(
                            text = if (isBn) "সর্বোচ্চ স্ট্রিক" else "Longest Streak",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                        Text(
                            text = if (isBn) "${streakData.longestStreak} দিন" else "${streakData.longestStreak} Days",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE65100)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Month title
            val monthName = yearMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
            Text(
                text = "$monthName ${yearMonth.year}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(8.dp))

            // Days of week header
            val dayHeaders = if (isBn) {
                listOf("র", "সো", "মঁ", "বু", "বৃ", "শু", "শ")
            } else {
                listOf("S", "M", "T", "W", "T", "F", "S")
            }

            Row(Modifier.fillMaxWidth()) {
                dayHeaders.forEach { header ->
                    Text(
                        text = header,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            // Calendar grid
            val daysInMonth = yearMonth.lengthOfMonth()
            val firstDayOfWeek = yearMonth.atDay(1).dayOfWeek.value % 7

            val totalGridCells = firstDayOfWeek + daysInMonth
            val rows = (totalGridCells + 6) / 7

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (r in 0 until rows) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for (c in 0 until 7) {
                            val cellIndex = r * 7 + c
                            val dayNum = cellIndex - firstDayOfWeek + 1

                            if (cellIndex < firstDayOfWeek || dayNum > daysInMonth) {
                                Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                            } else {
                                val date = yearMonth.atDay(dayNum)
                                val dateStr = date.toString()
                                val isToday = (date == today)
                                val isLoggedIn = streakData.loggedInDates.contains(dateStr)

                                Box(
                                    modifier = Modifier.weight(1f).aspectRatio(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val cellModifier = Modifier
                                        .size(28.dp)
                                        .then(
                                            when {
                                                isLoggedIn -> Modifier
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF2E7D32))

                                                isToday -> Modifier
                                                    .clip(CircleShape)
                                                    .border(2.dp, Color(0xFFFF9800), CircleShape)
                                                    .background(Color(0xFFFFF3E0))

                                                else -> Modifier
                                            }
                                        )

                                    Box(
                                        modifier = cellModifier,
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = dayNum.toString(),
                                            fontSize = 11.sp,
                                            fontWeight = if (isToday || isLoggedIn) FontWeight.Bold else FontWeight.Normal,
                                            color = when {
                                                isLoggedIn -> Color.White
                                                isToday -> Color(0xFFE65100)
                                                else -> MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DailyRewardDialog(
    reward: DailyReward,
    isBn: Boolean,
    onClaim: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onClaim,
        title = {
            Text(
                text = if (isBn) "🎉 দৈনিক পুরস্কার!" else "🎉 Daily Reward!",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "🪙", fontSize = 48.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (isBn) reward.messageBn else reward.messageEn,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onClaim,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
            ) {
                Text(if (isBn) "পুরস্কার সংগ্রহ করুন" else "Claim Reward")
            }
        }
    )
}
