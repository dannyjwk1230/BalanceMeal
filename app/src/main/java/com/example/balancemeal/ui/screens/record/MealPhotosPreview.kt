package com.example.balancemeal.ui.screens.record

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.balancemeal.model.MealType
import com.example.balancemeal.ui.theme.BalanceMealTheme

@Preview(name = "Optional photos", widthDp = 320, showBackground = true)
@Composable
fun EmptyMealPhotosPreview() {
    BalanceMealTheme {
        MealPhotoSection(MealType.BREAKFAST, emptyList()) { _, _, _ -> }
    }
}

@Preview(name = "Unavailable photos", widthDp = 320, showBackground = true)
@Composable
fun UnavailableMealPhotosPreview() {
    BalanceMealTheme {
        MealPhotoSection(MealType.BREAKFAST, listOf("file:///missing-one", "file:///missing-two")) { _, _, _ -> }
    }
}
