package com.example.balancemeal.data

import android.content.ContentResolver
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class FilePhotoStorage internal constructor(
    private val directory: File,
    private val openInput: (String) -> InputStream,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : PhotoStorage {
    constructor(
        directory: File,
        resolver: ContentResolver,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    ) : this(directory, { reference ->
        resolver.openInputStream(Uri.parse(reference))
            ?: throw IOException("사진 스트림을 열 수 없습니다.")
    }, ioDispatcher)

    override suspend fun deletePhoto(reference: String): Unit = withContext(ioDispatcher) {
        val uri = java.net.URI(reference)
        require(uri.scheme == "file") { "내부 파일 참조만 정리할 수 있습니다." }
        val file = File(uri).canonicalFile
        require(file.parentFile == directory.canonicalFile && file.extension == "img") {
            "이 사진 저장소에서 생성한 파일만 정리할 수 있습니다."
        }
        UUID.fromString(file.nameWithoutExtension)
        Files.deleteIfExists(file.toPath())
        Unit
    }

    override suspend fun copyPhoto(sourceReference: String): String = withContext(ioDispatcher) {
        require(sourceReference.isNotBlank()) { "사진 참조가 비어 있습니다." }
        Files.createDirectories(directory.toPath())
        val temporary = File.createTempFile("photo-", ".tmp", directory)
        val destination = File(directory, "${UUID.randomUUID()}.img")
        try {
            currentCoroutineContext().ensureActive()
            openInput(sourceReference).use { input ->
                FileOutputStream(temporary).use { output ->
                    val count = input.copyTo(output)
                    if (count == 0L) throw IOException("사진 파일이 비어 있습니다.")
                    output.fd.sync()
                }
            }
            currentCoroutineContext().ensureActive()
            Files.move(temporary.toPath(), destination.toPath(), ATOMIC_MOVE)
            destination.toURI().toString()
        } finally {
            temporary.delete()
        }
    }
}
