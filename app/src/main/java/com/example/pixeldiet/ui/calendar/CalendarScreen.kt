package com.example.pixeldiet.ui.calendar

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
// ⭐️ 1. [수정됨] WrappedLineChart -> WrappedBarChart
import com.example.pixeldiet.ui.common.WrappedBarChart
import com.example.pixeldiet.ui.common.WrappedMaterialCalendar
import com.example.pixeldiet.viewmodel.SharedViewModel

@Composable
fun CalendarScreen(viewModel: SharedViewModel = viewModel()) {

    val decoratorData by viewModel.calendarDecoratorData.observeAsState(emptyList())
    val statsText by viewModel.calendarStatsText.observeAsState("")
    val streakText by viewModel.streakText.observeAsState("")
    val chartData by viewModel.chartData.observeAsState(emptyList())

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. 스피너
        item { FilterSpinner(onFilterSelected = { viewModel.setCalendarFilter(it) }) }
        // 2. 캘린더
        item {
            Card(elevation = CardDefaults.cardElevation(2.dp)) {
                WrappedMaterialCalendar(Modifier.fillMaxWidth(), decoratorData)
            }
        }
        // 3. 안내 문구
        item {
            Text(statsText, Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 16.sp, color = MaterialTheme.colorScheme.secondary)
            Text(streakText, Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
        }
        // 4. 그래프
        item {
            Card(Modifier.fillMaxWidth().height(300.dp), elevation = CardDefaults.cardElevation(2.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("이번 달 사용 시간", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 16.sp)
                    Spacer(Modifier.height(16.dp))

                    // ⭐️ 2. [수정됨] WrappedLineChart -> WrappedBarChart
                    WrappedBarChart(
                        modifier = Modifier.fillMaxSize(),
                        chartData = chartData
                    )
                }
            }
        }
    }
}

// ----------------------
// FilterSpinner (이 부분은 동일)
// ----------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSpinner(onFilterSelected: (String) -> Unit) {
    val options = listOf("전체", "네이버 웹툰", "인스타그램", "유튜브")
    var expanded by remember { mutableStateOf(false) }
    var selectedText by remember { mutableStateOf(options[0]) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedText,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        selectedText = option
                        expanded = false
                        onFilterSelected(option)
                    }
                )
            }
        }
    }
}