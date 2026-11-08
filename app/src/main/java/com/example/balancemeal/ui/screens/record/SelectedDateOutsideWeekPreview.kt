package com.example.balancemeal.ui.screens.record

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import java.time.LocalDate

@Preview(widthDp = 272, heightDp = 240, showBackground = true)
@Composable
fun SelectedDateOutsideWeekPreview() {
    MaterialTheme {
        // 표시 주를 이전 주로 넘겨도 편집 대상은 6월 3일입니다.
        WeeklyDateSelector(LocalDate.of(2026, 5, 25), LocalDate.of(2026, 6, 3),
            LocalDate.of(2026, 6, 4), {}, {})
    }
}
