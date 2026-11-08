package com.example.balancemeal.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/** 기록·내역·상세가 동일한 로딩/읽기 실패 표시를 사용합니다. */
@Composable
fun MealPhoto(reference: String, modifier: Modifier = Modifier) {
    key(reference) {
        var loading by remember { mutableStateOf(true) }
        var failed by remember { mutableStateOf(false) }
        Column(modifier) {
            if (!failed) AsyncImage(
                model = reference, contentDescription = "식사 사진", contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(128.dp),
                onLoading = { loading = true; failed = false },
                onSuccess = { loading = false; failed = false },
                onError = { loading = false; failed = true }
            )
            if (loading) {
                CircularProgressIndicator(Modifier.size(24.dp))
                Text("사진을 불러오는 중입니다.")
            }
            if (failed) Surface(Modifier.fillMaxWidth().heightIn(min = 96.dp),
                color = MaterialTheme.colorScheme.surfaceVariant) {
                Text("사진을 읽을 수 없습니다. 다시 선택해 주세요.", Modifier.padding(12.dp))
            }
        }
    }
}
