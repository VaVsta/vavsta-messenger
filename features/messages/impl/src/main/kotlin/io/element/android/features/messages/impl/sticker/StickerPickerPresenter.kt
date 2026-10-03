/*
 * VaVsta Messenger (форк Element X): скелет стикеров.
 */

package io.element.android.features.messages.impl.sticker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.media.ImageInfo
import io.element.android.libraries.matrix.api.room.JoinedRoom
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * VaVsta: скелет отправки стикера — загрузка байтов в медиахранилище + событие `m.sticker` (MSC3916).
 *
 * Специального API для стикеров в Rust SDK нет, поэтому загрузка идёт через общий
 * [MatrixClient.uploadMedia], а само событие — через `JoinedRoom.sendSticker`, который внутри
 * зовёт `sendRaw`. Для скелета это нормально, но на будущее: собственный метод в FFI,
 * чтобы не собирать JSON руками.
 */
@AssistedInject
class StickerPickerPresenter(
    @SessionCoroutineScope private val sessionCoroutineScope: CoroutineScope,
    private val matrixClient: MatrixClient,
    @Assisted private val room: JoinedRoom,
    @Assisted private val onStickerSent: () -> Unit,
) {
    @AssistedFactory
    interface Factory {
        fun create(room: JoinedRoom, onStickerSent: () -> Unit): StickerPickerPresenter
    }

    @Composable
    fun present(): StickerPickerState {
        var sendingStickerId by remember { mutableStateOf<String?>(null) }
        var sendError by remember { mutableStateOf<String?>(null) }

        fun send(event: StickerPickerEvent.SelectSticker) {
            if (sendingStickerId != null) return
            sendingStickerId = event.sticker.id
            sendError = null
            sessionCoroutineScope.launch {
                runCatchingExceptions {
                    val mediaUrl = matrixClient.uploadMedia(mimeType = PNG_MIME_TYPE, data = event.pngBytes).getOrThrow()
                    val imageInfo = ImageInfo(
                        height = event.height.toLong(),
                        width = event.width.toLong(),
                        mimetype = PNG_MIME_TYPE,
                        size = event.pngBytes.size.toLong(),
                        thumbnailInfo = null,
                        thumbnailSource = null,
                        blurhash = null,
                    )
                    room.sendSticker(
                        mediaUrl = mediaUrl,
                        body = event.sticker.fileName,
                        info = imageInfo,
                    ).getOrThrow()
                }.onSuccess {
                    Timber.i("Sticker: %s отправлен", event.sticker.id)
                    onStickerSent()
                }.onFailure { throwable ->
                    Timber.e(throwable, "Sticker: не отправился %s", event.sticker.id)
                    sendError = throwable.message ?: throwable::class.simpleName ?: "unknown error"
                }
                sendingStickerId = null
            }
        }

        val eventSink: (StickerPickerEvent) -> Unit = { event ->
            when (event) {
                is StickerPickerEvent.SelectSticker -> send(event)
                StickerPickerEvent.Dismiss -> onStickerSent()
            }
        }

        return StickerPickerState(
            stickers = devStickers,
            sendingStickerId = sendingStickerId,
            sendError = sendError,
            eventSink = eventSink,
        )
    }

    private companion object {
        const val PNG_MIME_TYPE = "image/png"
    }
}