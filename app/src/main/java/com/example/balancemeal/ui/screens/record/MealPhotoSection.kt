package com.example.balancemeal.ui.screens.record

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.balancemeal.model.MealType

@Composable
fun MealPhotoSection(
    mealType: MealType,
    photoReference: String?,
    enabled: Boolean = true,
    error: String? = null,
    onPhotoSelected: (MealType, String) -> Unit
) {
    // 현재 끼니와 분리해 선택기를 열 당시의 대상을 Activity 재생성 중에도 보관합니다.
    var pendingMeal by rememberSaveable { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val target = pendingMeal
        pendingMeal = null
        if (uri != null && target != null) onPhotoSelected(MealType.valueOf(target), uri.toString())
        // 취소(null)는 이벤트를 보내지 않아 기존 참조를 유지합니다.
    }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("사진", style = MaterialTheme.typography.titleMedium)
            if (photoReference == null) {
                Text("식사 사진 한 장을 선택해 주세요.")
            } else {
                key(photoReference) {
                    var loading by remember { mutableStateOf(true) }
                    var failed by remember { mutableStateOf(false) }
                    AsyncImage(
                        model = photoReference,
                        contentDescription = "식사 사진",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(200.dp).testTag("meal-photo"),
                        onLoading = { loading = true; failed = false },
                        onSuccess = { loading = false; failed = false },
                        onError = { loading = false; failed = true }
                    )
                    if (loading) {
                        CircularProgressIndicator()
                        Text("사진을 불러오는 중입니다.")
                    }
                    if (failed) Text("사진을 읽을 수 없습니다. 다시 선택해 주세요.")
                }
            }
            Button(
                enabled = enabled && pendingMeal == null,
                onClick = {
                    pendingMeal = mealType.name
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            ) { Text(if (photoReference == null) "사진 선택" else "사진 다시 선택") }
            if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
        }
    }
}
