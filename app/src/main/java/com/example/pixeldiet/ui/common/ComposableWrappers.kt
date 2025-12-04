package com.example.pixeldiet.ui.common

import android.graphics.Color
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.pixeldiet.model.CalendarDecoratorData
import com.example.pixeldiet.model.DayStatus
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.prolificinteractive.materialcalendarview.CalendarDay
import com.prolificinteractive.materialcalendarview.DayViewDecorator
import com.prolificinteractive.materialcalendarview.DayViewFacade
import com.prolificinteractive.materialcalendarview.MaterialCalendarView
import com.prolificinteractive.materialcalendarview.spans.DotSpan

// ----------------------
// MaterialCalendarView 래퍼
// ----------------------
@Composable
fun WrappedMaterialCalendar(
    modifier: Modifier = Modifier,
    decoratorData: List<CalendarDecoratorData>,
    onMonthChanged: (year: Int, month: Int) -> Unit = { _, _ -> }
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            MaterialCalendarView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                topbarVisible = true
                selectionMode = MaterialCalendarView.SELECTION_MODE_NONE
                // ✅ 오늘 날짜로 이동
                setCurrentDate(CalendarDay.today())

                // ⭐ 월 변경 리스너
                setOnMonthChangedListener { _, date ->
                    onMonthChanged(date.year, date.month)
                }
            }
        },
        update = { view ->
            // 기존 데코레이터 제거
            view.removeDecorators()

            // 상태별 날짜 분리
            val successDays = decoratorData.filter { it.status == DayStatus.SUCCESS }.map { it.date }.toSet()
            val warningDays = decoratorData.filter { it.status == DayStatus.WARNING }.map { it.date }.toSet()
            val failDays = decoratorData.filter { it.status == DayStatus.FAIL }.map { it.date }.toSet()

            if (successDays.isNotEmpty()) {
                view.addDecorator(StatusDecorator(successDays, Color.GREEN))
            }
            if (warningDays.isNotEmpty()) {
                view.addDecorator(StatusDecorator(warningDays, Color.parseColor("#FFC107")))
            }
            if (failDays.isNotEmpty()) {
                view.addDecorator(StatusDecorator(failDays, Color.RED))
            }
        }
    )
}

// ----------------------
// BarChart 래퍼
// ----------------------
@Composable
fun WrappedBarChart(
    modifier: Modifier = Modifier,
    chartData: List<Entry>,       // x = 일, y = 사용시간(분)
    goalLine: Float? = null       // 🔹 목표 상한선 (분 단위), 없으면 null
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            BarChart(context).apply {
                description.isEnabled = false
                axisRight.isEnabled = false
                axisLeft.axisMinimum = 0f
                xAxis.granularity = 1f
                xAxis.setDrawGridLines(false)
                axisLeft.setDrawGridLines(true)
                legend.isEnabled = false
            }
        },
        update = { barChart ->
            // 1) BarEntry 변환
            val entries = chartData.map { e -> BarEntry(e.x, e.y) }

            val dataSet = BarDataSet(entries, "사용 시간(분)").apply {
                valueTextSize = 10f
            }

            barChart.data = BarData(dataSet).apply {
                barWidth = 0.6f
            }

            // 2) 기존 LimitLine 제거
            val leftAxis = barChart.axisLeft
            leftAxis.removeAllLimitLines()

            // 3) 목표 상한선 추가
            if (goalLine != null) {
                val limit = LimitLine(goalLine, "목표").apply {
                    lineWidth = 2f
                    enableDashedLine(10f, 10f, 0f)
                    textSize = 10f
                }
                leftAxis.addLimitLine(limit)
            }

            // 4) Y축 최대값 설정
            val maxUsage = (entries.maxOfOrNull { it.y } ?: 0f)
            val maxValue = listOf(maxUsage, goalLine ?: 0f).maxOrNull() ?: 0f
            leftAxis.axisMaximum = (maxValue * 1.1f).coerceAtLeast(10f)

            barChart.invalidate()
        }
    )
}

// ----------------------
// 캘린더 데코레이터
// ----------------------
private class StatusDecorator(
    private val dates: Set<CalendarDay>,
    private val color: Int
) : DayViewDecorator {

    override fun shouldDecorate(day: CalendarDay): Boolean = dates.contains(day)

    override fun decorate(view: DayViewFacade) {
        view.addSpan(DotSpan(10f, color))
    }
}