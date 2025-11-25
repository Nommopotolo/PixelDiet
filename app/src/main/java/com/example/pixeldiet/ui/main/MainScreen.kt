package com.example.pixeldiet.ui.main

// ⭐️ 5. 이 import 2줄이 핵심
import com.example.pixeldiet.model.AppName
import com.example.pixeldiet.model.AppUsage

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pixeldiet.viewmodel.SharedViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MainScreen(viewModel: SharedViewModel = viewModel()) {
    // ... (이하 코드는 이전과 동일) ...
    val appList by viewModel.appUsageList.observeAsState(emptyList())
    val totalUsage by viewModel.totalUsageData.observeAsState(Pair(0, 0))
    var showGoalDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            val dateFormat = SimpleDateFormat("yyyy년 M월 d일", Locale.KOREAN)
            Text(text = dateFormat.format(Date()), fontSize = 16.sp, color = Color.Gray)
        }
        item {
            Button(
                onClick = { showGoalDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text("목표 시간 설정") }
        }
        item { VisualNotification(appList) }
        item { TotalProgress(totalUsage.first, totalUsage.second) }
        items(appList, key = { it.appName.name }) { app ->
            AppUsageCard(app)
        }
    }
    if (showGoalDialog) {
        GoalSettingDialog(
            appList = appList,
            onDismiss = { showGoalDialog = false },
            onSave = { newGoals ->
                viewModel.setGoalTimes(newGoals)
                showGoalDialog = false
            }
        )
    }
}

@Composable
fun VisualNotification(appList: List<AppUsage>) {
    // ... (이하 코드는 이전과 동일) ...
    val appsWithUsage = appList.filter { it.currentUsage > 0 }
    val maxUsage = appsWithUsage.maxOfOrNull { it.currentUsage }?.toFloat() ?: 1f
    Card(elevation = CardDefaults.cardElevation(2.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .horizontalScroll(rememberScrollState())
                .padding(24.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            appsWithUsage.forEach { app ->
                val size = (40 + (app.currentUsage / maxUsage) * 100).dp
                Box(modifier = Modifier.size(size).background(app.appName.composeColor))
            }
        }
    }
}

@Composable
fun TotalProgress(totalUsage: Int, totalGoal: Int) {
    // ... (이하 코드는 이전과 동일) ...
    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("총 사용시간", fontSize = 14.sp, color = Color.Gray)
                Row {
                    Text(formatTime(totalUsage), fontSize = 14.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 16.dp))
                    Text("목표 ${formatTime(totalGoal)}", fontSize = 14.sp, color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            val progress = if (totalGoal > 0) (totalUsage.toFloat() / totalGoal).coerceAtMost(1f) else 0f
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(12.dp))
        }
    }
}

@Composable
fun GoalSettingDialog(
    appList: List<AppUsage>,
    onDismiss: () -> Unit,
    onSave: (Map<AppName, Int>) -> Unit
) {
    // ... (이하 코드는 이전과 동일) ...
    val goalStates = remember {
        mutableStateMapOf<AppName, Pair<String, String>>().apply {
            AppName.values().forEach { appName ->
                val currentMinutes = appList.find { it.appName == appName }?.goalTime ?: 0
                val hours = (currentMinutes / 60).toString()
                val minutes = (currentMinutes % 60).toString()
                put(appName, Pair(hours, minutes))
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("목표 시간 설정") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(AppName.values()) { appName ->
                    Text(appName.displayName, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val (hours, minutes) = goalStates[appName] ?: Pair("0", "0")
                        OutlinedTextField(
                            value = hours,
                            onValueChange = {
                                goalStates[appName] = Pair(it.filter { char -> char.isDigit() }, minutes)
                            },
                            label = { Text("시간") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = minutes,
                            onValueChange = {
                                goalStates[appName] = Pair(hours, it.filter { char -> char.isDigit() })
                            },
                            label = { Text("분") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val newGoals = goalStates.mapValues {
                    val hours = it.value.first.toIntOrNull() ?: 0
                    val minutes = it.value.second.toIntOrNull() ?: 0
                    (hours * 60) + minutes
                }
                onSave(newGoals)
            }) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}

private fun formatTime(minutes: Int): String {
    val hours = minutes / 60
    val mins = minutes % 60
    return String.format("%d시간 %02d분", hours, mins)
}