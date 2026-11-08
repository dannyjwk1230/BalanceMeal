package com.example.balancemeal.ui.screens.record

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import java.time.LocalDate

@Preview(widthDp = 272, heightDp = 240, showBackground = true)
@Composable
fun WeeklyDatePreview() {
    MaterialTheme {
        WeeklyDateSelector(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 3),
            LocalDate.of(2026, 6, 4), {}, {})
    }
}
