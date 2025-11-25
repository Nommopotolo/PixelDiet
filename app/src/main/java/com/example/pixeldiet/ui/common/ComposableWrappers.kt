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
import com.github.mikephil.charting.components.XAxis
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
    decoratorData: List<CalendarDecoratorData>
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
                // ✅ today 로 이동 (프로퍼티가 아니라 메서드 호출)
                setCurrentDate(CalendarDay.today())
            }
        },
        update = { view ->
            // 기존 데코레이터 제거
            view.removeDecorators()

            // 상태별로 날짜를 분리
            val successDays = decoratorData
                .filter { it.status == DayStatus.SUCCESS }
                .map { it.date }
                .toSet()

            val warningDays = decoratorData
                .filter { it.status == DayStatus.WARNING }
                .map { it.date }
                .toSet()

            val failDays = decoratorData
                .filter { it.status == DayStatus.FAIL }
                .map { it.date }
                .toSet()

            if (successDays.isNotEmpty()) {
                view.addDecorator(StatusDecorator(successDays, Color.BLUE))
            }
            if (warningDays.isNotEmpty()) {
                view.addDecorator(
                    StatusDecorator(
                        warningDays,
                        Color.parseColor("#FFC107") // 노랑
                    )
                )
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
    chartData: List<Entry>
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            BarChart(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(true)
                    granularity = 1f      // 하루 간격
                }
                axisRight.isEnabled = false
                description.isEnabled = false
                setDrawValueAboveBar(false)
            }
        },
        update = { view ->
            if (chartData.isEmpty()) {
                view.clear()
                view.invalidate()
                return@AndroidView
            }

            val barEntries = chartData.map { BarEntry(it.x, it.y) }

            val dataSet = BarDataSet(barEntries, "사용시간(분)").apply {
                color = Color.BLUE
                setDrawValues(false)
            }
            view.data = BarData(dataSet)
            view.invalidate()
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

    // 여기서 day가 칠해야 할 날짜인지 확인
    override fun shouldDecorate(day: CalendarDay): Boolean = dates.contains(day)

    // 실제 꾸미기 (날짜 정보는 이미 위에서 필터링됨)
    override fun decorate(view: DayViewFacade) {
        view.addSpan(DotSpan(10f, color))
    }
}
