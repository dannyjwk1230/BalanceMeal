package com.example.balancemeal

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.example.balancemeal.data.LocalMealRepository
import com.example.balancemeal.model.MealKey
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test

/** 실제 앱 기록의 읽기 전용 점검. 파일을 저장하거나 변경하지 않습니다. */
class MealRecordAuditTest {
    @Test fun auditExistingRecordsWithoutMutation() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.filesDir, "meal_records.json")
        val before = if (file.exists()) file.readBytes() else null
        val repo = LocalMealRepository(file)
        repo.initialize()
        val records = repo.observeRecords().first()
        val duplicates = records.groupBy { MealKey(it.date, it.mealType) }.filterValues { it.size > 1 }
        Log.i("MealAudit", "MEAL_AUDIT total=${records.size} duplicateKeys=${duplicates.size} " +
            duplicates.entries.joinToString { (key, rows) ->
                "${key.date}/${key.mealType}: ${rows.size} records, ids=${rows.map { it.recordId }}"
            })
        if (before != null) org.junit.Assert.assertArrayEquals(before, file.readBytes())
        else org.junit.Assert.assertFalse(file.exists())
    }
}
