package com.example.balancemeal.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.balancemeal.ui.theme.BalanceMealTheme

@Preview(name = "Phone", widthDp = 400, heightDp = 850, showBackground = true)
@Preview(name = "Tablet", widthDp = 1200, heightDp = 800, showBackground = true)
@Composable
fun NavigationPreview() {
    BalanceMealTheme { BalanceMealApp() }
}

@Preview(name = "History tablet", widthDp = 1200, heightDp = 800, showBackground = true)
@Composable
fun HistoryNavigationPreview() {
    BalanceMealTheme { BalanceMealApp(startDestination = History) }
}
