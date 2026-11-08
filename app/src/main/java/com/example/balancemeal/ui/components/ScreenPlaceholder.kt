package com.example.balancemeal.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ScreenPlaceholder(
    title: String,
    areas: List<String>,
    modifier: Modifier = Modifier,
    actions: @Composable ColumnScope.() -> Unit = {}
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        areas.forEach { area ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().heightIn(min = 88.dp).padding(16.dp)) {
                    Text(area, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        actions()
    }
}
