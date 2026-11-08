package com.example.balancemeal.ui.screens.record

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.balancemeal.model.MealType
import com.example.balancemeal.ui.components.MealPhoto

@Composable
fun MealPhotoSection(
    mealType: MealType,
    photoReferences: List<String>,
    enabled: Boolean = true,
    targetDate: String? = null,
    onPhotoRemoved: (String) -> Unit = {},
    onPhotoSelected: (MealType, List<String>, String?) -> Unit
) {
    // 선택기를 연 날짜·끼니를 회전 후에도 유지합니다.
    var pendingDate by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingMeal by rememberSaveable { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        val target = pendingMeal
        val date = pendingDate
        pendingMeal = null
        pendingDate = null
        if (uris.isNotEmpty() && target != null)
            onPhotoSelected(MealType.valueOf(target), uris.map { it.toString() }.distinct(), date)
        // 빈 결과(취소)는 기존 목록을 변경하지 않습니다.
    }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("사진(선택)", style = MaterialTheme.typography.titleMedium)
            Text("사진 없이도 저장할 수 있어요")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(photoReferences, key = { it }) { reference ->
                    Column(Modifier.width(160.dp)) {
                        MealPhoto(reference)
                        TextButton(onClick = { onPhotoRemoved(reference) }, enabled = enabled) {
                            Text("사진 제거")
                        }
                    }
                }
            }
            Button(
                enabled = enabled && pendingMeal == null,
                onClick = {
                    pendingMeal = mealType.name
                    pendingDate = targetDate
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            ) { Text(if (photoReferences.isEmpty()) "사진 선택" else "사진 추가") }
        }
    }
}
