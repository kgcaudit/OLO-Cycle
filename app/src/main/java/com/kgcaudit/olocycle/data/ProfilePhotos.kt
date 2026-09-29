package com.kgcaudit.olocycle.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * 프로필 사진을 앱 내부 저장소로 복사한다. 원본 content URI 권한은 일시적이라 그대로 저장할 수 없으므로,
 * 고른 이미지를 축소·재압축해 filesDir 안에 두고 그 절대경로만 DB에 기록한다. 인터넷으로 나가지 않고
 * 기기 안에만 남는다는 원칙을 지키면서, 큰 원본으로 저장소가 부풀지 않게 최대 변 512px로 줄인다.
 */
object ProfilePhotos {

    private const val MAX_EDGE = 512

    /** [uri]의 이미지를 내부 저장소로 복사하고 저장 경로를 돌려준다. 실패하면 null. */
    fun copyToInternal(context: Context, uri: Uri): String? = runCatching {
        val dir = File(context.filesDir, "profile_photos").apply { mkdirs() }
        val decoded = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            ?: return null
        val scaled = downscale(decoded)
        val file = File(dir, "p_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { scaled.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        if (scaled !== decoded) scaled.recycle()
        decoded.recycle()
        file.absolutePath
    }.getOrNull()

    /** 백업 복원용: 이미 축소된 사진 바이트를 내부 저장소에 그대로 써 두고 새 절대경로를 돌려준다. */
    fun writeInternal(context: Context, bytes: ByteArray): String? = runCatching {
        val dir = File(context.filesDir, "profile_photos").apply { mkdirs() }
        val file = File(dir, "p_${System.currentTimeMillis()}_${bytes.size}.jpg")
        file.writeBytes(bytes)
        file.absolutePath
    }.getOrNull()

    /** 프로필 삭제·사진 교체 시 남는 파일을 지운다(있으면). */
    fun delete(path: String?) {
        path ?: return
        runCatching { File(path).delete() }
    }

    private fun downscale(bmp: Bitmap): Bitmap {
        val longest = maxOf(bmp.width, bmp.height)
        if (longest <= MAX_EDGE) return bmp
        val ratio = MAX_EDGE.toFloat() / longest
        return Bitmap.createScaledBitmap(bmp, (bmp.width * ratio).toInt(), (bmp.height * ratio).toInt(), true)
    }
}
