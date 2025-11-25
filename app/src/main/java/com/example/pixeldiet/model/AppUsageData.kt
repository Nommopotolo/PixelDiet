package com.example.pixeldiet.model

import android.graphics.Color
// ⭐️ 아이콘이 없는 버전이므로 Drawable import 제거
import androidx.compose.ui.graphics.Color as ComposeColor
import com.prolificinteractive.materialcalendarview.CalendarDay

// 앱 이름 Enum (패키지 이름, 표시 이름, 색상 관리)
enum class AppName(
    val packageName: String,
    val displayName: String,
    val color: Int, // ⭐️ Android View용
    val composeColor: ComposeColor // ⭐️ Compose용
) {
    NAVER_WEBTOON(
        "com.nhn.android.webtoon", "네이버 웹툰",
        Color.parseColor("#00D564"), ComposeColor(0xFF00D564)
    ),
    INSTAGRAM(
        "com.instagram.android", "인스타그램",
        Color.parseColor("#E1306C"), ComposeColor(0xFFE1306C)
    ),
    YOUTUBE(
        "com.google.android.youtube", "유튜브",
        Color.parseColor("#FF0000"), ComposeColor(0xFFFF0000)
    )
}

// 메인화면에 표시될 개별 앱 데이터
// ⭐️ 아이콘이 없는, 파라미터 4개짜리 원본입니다.
data class AppUsage(
    val appName: AppName,
    var currentUsage: Int = 0, // 분 단위
    var goalTime: Int = 0, // 분 단위
    var streak: Int = 0 // 양수(달성), 음수(실패)
)

// 일별 전체 사용 데이터 (캘린더용)
data class DailyUsage(
    val date: String, // YYYY-MM-DD
    val appUsages: Map<AppName, Int> // <앱, 사용 시간(분)>
)

// 알림 설정
data class NotificationSettings(
    var individualApp50: Boolean = true,
    var individualApp70: Boolean = true,
    var individualApp100: Boolean = true,
    var total50: Boolean = true,
    var total70: Boolean = true,
    var total100: Boolean = true,
    // ⭐️ [신규] 100% 초과 시 반복 알림 간격 (기본값 5분)
    var repeatIntervalMinutes: Int = 5
)

// 캘린더 데코레이터용 데이터
data class CalendarDecoratorData(
    val date: CalendarDay,
    val status: DayStatus
)

enum class DayStatus {
    SUCCESS, // 파랑
    WARNING, // 노랑 (70% 초과)
    FAIL     // 빨강
}