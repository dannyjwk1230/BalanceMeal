package com.example.balancemeal.data

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URI
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FilePhotoStorageTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun copiesStreamOnIoAndReturnsUniqueReadableReferences() = runBlocking {
        val caller = Thread.currentThread()
        val bytes = ByteArray(100_000) { (it % 255).toByte() }
        val directory = File(temporary.root, "images")
        val storage = FilePhotoStorage(directory, {
            assertNotSame(caller, Thread.currentThread())
            ByteArrayInputStream(bytes)
        })
        val first = storage.copyPhoto("source")
        val second = storage.copyPhoto("source")
        assertNotEquals(first, second)
        assertArrayEquals(bytes, File(URI(first)).readBytes())
        assertArrayEquals(bytes, File(URI(second)).readBytes())
        assertTrue(directory.listFiles()!!.all { it.extension == "img" })
    }

    @Test fun failedStreamClosesAndRemovesPartialTemporaryFile() = runBlocking {
        var closed = false
        val storage = FilePhotoStorage(temporary.root, {
            object : InputStream() {
                var reads = 0
                override fun read(): Int {
                    if (reads++ > 10) throw IOException("read failed")
                    return 1
                }
                override fun close() { closed = true }
            }
        })
        try { storage.copyPhoto("source"); fail("Expected stream failure") }
        catch (_: IOException) { }
        assertTrue(closed)
        assertTrue(temporary.root.listFiles()!!.isEmpty())
    }

    @Test fun deletionOnlyRemovesTheRequestedOwnedFile() = runBlocking {
        val images = File(temporary.root, "images")
        val storage = FilePhotoStorage(images, { ByteArrayInputStream(byteArrayOf(1, 2, 3)) })
        val first = storage.copyPhoto("one")
        val second = storage.copyPhoto("two")
        val outside = File(temporary.root, "original.jpg").apply { writeText("original") }
        try { storage.deletePhoto(outside.toURI().toString()); fail("Outside file must be protected") }
        catch (_: IllegalArgumentException) { }
        storage.deletePhoto(first)
        assertFalse(File(URI(first)).exists())
        assertTrue(File(URI(second)).exists())
        assertEquals("original", outside.readText())
    }

    @Test fun emptyOrUnreadablePhotoLeavesNoFiles() = runBlocking {
        val empty = FilePhotoStorage(temporary.root, { ByteArrayInputStream(byteArrayOf()) })
        try { empty.copyPhoto("source"); fail("Expected empty file error") }
        catch (_: IOException) { }
        val unreadable = FilePhotoStorage(temporary.root, { throw IOException("denied") })
        try { unreadable.copyPhoto("source"); fail("Expected read error") }
        catch (_: IOException) { }
        assertTrue(temporary.root.listFiles()!!.isEmpty())
    }
}
