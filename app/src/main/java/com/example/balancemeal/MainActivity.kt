package com.example.balancemeal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.balancemeal.ui.theme.BalanceMealTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BalanceMealTheme {
                com.example.balancemeal.ui.meal.MealAppRoute(
                    (application as BalanceMealApplication).appContainer
                )
            }
        }
    }
}
