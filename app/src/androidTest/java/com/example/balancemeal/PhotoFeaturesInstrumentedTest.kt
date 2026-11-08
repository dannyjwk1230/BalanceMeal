package com.example.balancemeal

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.balancemeal.data.FilePhotoStorage
import com.example.balancemeal.model.MealType
import com.example.balancemeal.ui.screens.record.MealPhotoSection
import java.io.File
import java.net.URI
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PhotoFeaturesInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun png(file: File) {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun pickerResultUsesOpeningMealAndCancelRetainsPhoto() {
        val photo = File.createTempFile("picker-test-", ".png", context.cacheDir)
        png(photo)
        try {
            var requestCode = 0
            val registry = object : ActivityResultRegistry() {
                override fun <I, O> onLaunch(code: Int, contract: ActivityResultContract<I, O>,
                    input: I, options: ActivityOptionsCompat?) {
                    assertTrue(contract is ActivityResultContracts.PickVisualMedia)
                    requestCode = code
                }
            }
            val owner = object : ActivityResultRegistryOwner {
                override val activityResultRegistry = registry
            }
            val meal = mutableStateOf(MealType.BREAKFAST)
            val references = mutableStateOf<Map<MealType, String>>(emptyMap())
            compose.setContent {
                CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                    MealPhotoSection(meal.value, references.value[meal.value]) { target, reference ->
                        references.value = references.value + (target to reference)
                    }
                }
            }
            compose.onNodeWithText("사진 선택").performClick()
            compose.runOnIdle {
                meal.value = MealType.LUNCH
                registry.dispatchResult(requestCode, Uri.fromFile(photo))
            }
            assertNull(references.value[MealType.LUNCH])
            assertEquals(Uri.fromFile(photo).toString(), references.value[MealType.BREAKFAST])
            compose.runOnIdle { meal.value = MealType.BREAKFAST }
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("사진을 불러오는 중입니다.").fetchSemanticsNodes().isEmpty()
            }
            compose.onNodeWithContentDescription("식사 사진").assertIsDisplayed()
            compose.onNodeWithText("사진을 읽을 수 없습니다. 다시 선택해 주세요.").assertDoesNotExist()
            compose.onNodeWithText("사진 다시 선택").performClick()
            val before = references.value
            compose.runOnIdle { registry.dispatchResult<Uri?>(requestCode, null) }
            assertEquals(before, references.value)
        } finally { photo.delete() }
    }

    @Test fun inaccessiblePhotoShowsReselectionMessage() {
        compose.setContent {
            MealPhotoSection(MealType.BREAKFAST, "content://invalid.test/missing") { _, _ -> }
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("사진을 읽을 수 없습니다. 다시 선택해 주세요.").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("사진 다시 선택").assertIsEnabled()
    }

    @Test fun contentResolverCopyRemainsReadableAfterSourceRemoval() = runBlocking {
        val directory = Files.createTempDirectory(context.cacheDir.toPath(), "photo-storage-").toFile()
        try {
            val source = File(directory, "original.png")
            png(source)
            val storage = FilePhotoStorage(File(directory, "images"), context.contentResolver)
            val reference = storage.copyPhoto(Uri.fromFile(source).toString())
            source.delete()
            val copied = File(URI(reference))
            assertTrue(copied.exists())
            val bitmap = BitmapFactory.decodeFile(copied.absolutePath)
            assertNotNull(bitmap)
            bitmap?.recycle()
            assertEquals(1, copied.parentFile!!.listFiles()!!.size)
        } finally { directory.deleteRecursively() }
    }
}
