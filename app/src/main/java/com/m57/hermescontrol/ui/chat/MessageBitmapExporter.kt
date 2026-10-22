package com.m57.hermescontrol.ui.chat

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** Outcome of [MessageBitmapExporter.saveMessageToPictures] so the UI can toast accurately. */
enum class MessageImageSaveResult { SAVED, UNSUPPORTED_SDK, FAILED }

/**
 * Saves a captured message-bubble bitmap to the shared Pictures collection
 * (batch-8 F1 — Save-as-image action). Pure MediaStore, no network.
 *
 * API contract: MediaStore RELATIVE_PATH / IS_PENDING need API 29+. On the
 * app's 26–28 floor Path A would need the legacy WRITE_EXTERNAL_STORAGE
 * permission the manifest does not declare, so the exporter declines with
 * [MessageImageSaveResult.UNSUPPORTED_SDK] and the UI toasts instead of
 * crashing on a SecurityException.
 */
object MessageBitmapExporter {
    suspend fun saveMessageToPictures(
        context: Context,
        bitmap: Bitmap,
    ): MessageImageSaveResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return MessageImageSaveResult.UNSUPPORTED_SDK
        }
        return withContext(Dispatchers.IO) {
            runCatching {
                val displayName = "hermes-message-${System.currentTimeMillis()}.png"
                val values =
                    ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Hermes")
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                val resolver = context.contentResolver
                val collection =
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val uri =
                    resolver.insert(collection, values)
                        ?: throw IOException("MediaStore insert failed")
                var published = false
                try {
                    val out =
                        resolver.openOutputStream(uri)
                            ?: throw IOException("MediaStore openOutputStream failed")
                    out.use { stream ->
                        if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                            throw IOException("PNG encode failed")
                        }
                    }
                    val publish = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                    if (resolver.update(uri, publish, null, null) <= 0) {
                        throw IOException("MediaStore publish failed")
                    }
                    published = true
                } finally {
                    if (!published) runCatching { resolver.delete(uri, null, null) }
                }
            }.fold(
                onSuccess = { MessageImageSaveResult.SAVED },
                onFailure = { MessageImageSaveResult.FAILED },
            )
        }
    }
}
