package com.example.balancemeal

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Process
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.example.balancemeal.data.*
import com.example.balancemeal.model.MealType
import com.example.balancemeal.ui.meal.*
import java.io.File
import java.net.URI
import java.time.Clock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * 프로세스 종료를 사이에 두고 prepare와 verify를 개별 실행하는 개발 검증입니다.
 * 실제 사용자 기록과 분리된 filesDir/connection-verification만 사용합니다.
 */
class ProcessRecoveryVerification {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val root get() = File(context.filesDir, "connection-verification")

    @Test fun prepare() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("recoveryPhase") == "prepare")
        assertFalse("이전 검증 폴더를 먼저 정리해야 합니다.", root.exists())
        root.mkdirs()
        val source = File(root, "source.png")
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val store = ViewModelStore()
        lateinit var vm: MealViewModel
        try {
            instrumentation.runOnMainSync {
                vm = MealViewModel(
                    AssetFoodRepository(context.assets),
                    LocalMealRepository(File(root, "records.json")),
                    Clock.systemDefaultZone(),
                    FilePhotoStorage(File(root, "images"), context.contentResolver)
                )
                store.put("verification", vm)
            }
            withTimeout(10_000) { vm.uiState.first {
                it.foodReadState == OperationState.Success && it.recordReadState == OperationState.Success
            } }
            instrumentation.runOnMainSync {
                vm.onEvent(MealEvent.AddItem)
                val id = vm.uiState.value.draftsByMealType.getValue(MealType.BREAKFAST).items.single().itemId
                vm.onEvent(MealEvent.SelectFood(id, vm.uiState.value.foods.first().code, MealType.BREAKFAST))
                vm.onEvent(MealEvent.SetAmount(id, "125.5"))
                vm.onEvent(MealEvent.SetPhoto(Uri.fromFile(source).toString(), MealType.BREAKFAST))
                repeat(5) { vm.saveMeal() }
            }
            withTimeout(10_000) { vm.uiState.first { it.saveState == OperationState.Success } }
            val saved = withTimeout(10_000) { vm.uiState.first { it.records.size == 1 } }.records.single()
            File(root, "record.id").writeText(saved.recordId)
            File(root, "process.pid").writeText(Process.myPid().toString())
            assertEquals(1, File(root, "images").listFiles()!!.size)
            source.delete()
            println("RECOVERY_PREPARED pid=${Process.myPid()} records=1 amount=125.5")
        } finally { instrumentation.runOnMainSync { store.clear() } }
    }

    @Test fun verify() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("recoveryPhase") == "verify")
        assertTrue(root.exists())
        val previousPid = File(root, "process.pid").readText().toInt()
        assertNotEquals(previousPid, Process.myPid())
        val store = ViewModelStore()
        lateinit var vm: MealViewModel
        try {
            instrumentation.runOnMainSync {
                vm = MealViewModel(
                    AssetFoodRepository(context.assets),
                    LocalMealRepository(File(root, "records.json")),
                    Clock.systemDefaultZone(),
                    FilePhotoStorage(File(root, "images"), context.contentResolver)
                )
                store.put("verification", vm)
            }
            val loaded = withTimeout(10_000) { vm.uiState.first {
                it.recordReadState == OperationState.Success
            } }.records
            assertEquals(1, loaded.size)
            val record = loaded.single()
            assertEquals(File(root, "record.id").readText(), record.recordId)
            assertEquals(125.5, record.items.single().amount, 0.0)
            val photo = File(URI(record.photoReference!!))
            assertTrue(photo.exists())
            assertFalse(File(root, "source.png").exists())
            val bitmap = BitmapFactory.decodeFile(photo.absolutePath)
            assertNotNull(bitmap)
            bitmap?.recycle()
            assertTrue(vm.uiState.value.draftsByMealType.values.all { it.items.isEmpty() })
            println("RECOVERY_VERIFIED previousPid=$previousPid pid=${Process.myPid()} records=1 photoDecoded=true")
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            root.deleteRecursively()
        }
    }

    @Test fun cleanup() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("recoveryPhase") == "cleanup")
        root.deleteRecursively()
        assertFalse(root.exists())
    }
}
