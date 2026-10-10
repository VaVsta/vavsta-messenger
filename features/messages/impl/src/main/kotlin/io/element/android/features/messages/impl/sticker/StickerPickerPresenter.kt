/*
 * VaVsta Messenger (форк Element X): отправка стикеров и импорт (в т.ч. из Telegram).
 */

package io.element.android.features.messages.impl.sticker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import java.io.File

/**
 * VaVsta: отправка стикера — загрузка в медиахранилище + событие `m.sticker` (MSC3916).
 *
 * Специального API для стикеров в Rust SDK нет, поэтому загрузка идёт через общий
 * [MatrixClient.uploadMedia], а само событие — через `JoinedRoom.sendSticker`, который внутри
 * зовёт `sendRaw`. Для своих (импортированных) стикеров mxc-ссылка запоминается, чтобы не грузить
 * один и тот же файл дважды.
 */
@AssistedInject
class StickerPickerPresenter(
    @SessionCoroutineScope private val sessionCoroutineScope: CoroutineScope,
    private val matrixClient: MatrixClient,
    private val store: MyStickerStore,
    private val importer: StickerImporter,
    @Assisted private val room: JoinedRoom,
    @Assisted private val onStickerSent: () -> Unit,
) {
    @AssistedFactory
    interface Factory {
        fun create(room: JoinedRoom, onStickerSent: () -> Unit): StickerPickerPresenter
    }

    @Composable
    fun present(): StickerPickerState {
        var tab by remember { mutableStateOf(StickerPickerState.Tab.DevStickers) }
        var myStickers by remember { mutableStateOf(store.list()) }
        var sendingStickerId by remember { mutableStateOf<String?>(null) }
        var isImporting by remember { mutableStateOf(false) }
        var sendError by remember { mutableStateOf<String?>(null) }
        var importError by remember { mutableStateOf<String?>(null) }

        fun send(
            name: String,
            mimeType: String,
            width: Int,
            height: Int,
            sizeBytes: Long,
            mediaUrlOrNull: String?,
            readBytes: () -> ByteArray,
            onUploaded: ((String) -> Unit)? = null,
        ) {
            if (sendingStickerId != null) return
            sendingStickerId = name
            sendError = null
            sessionCoroutineScope.launch {
                runCatchingExceptions {
                    val mediaUrl = mediaUrlOrNull ?: matrixClient
                        .uploadMedia(mimeType = mimeType, data = readBytes())
                        .getOrThrow()
                        .also { onUploaded?.invoke(it) }
                    room.sendSticker(
                        mediaUrl = mediaUrl,
                        body = name,
                        info = ImageInfo(
                            height = height.toLong(),
                            width = width.toLong(),
                            mimetype = mimeType,
                            size = sizeBytes,
                            thumbnailInfo = null,
                            thumbnailSource = null,
                            blurhash = null,
                        ),
                    ).getOrThrow()
                }.onSuccess {
                    Timber.i("Sticker: %s отправлен", name)
                    onStickerSent()
                }.onFailure { throwable ->
                    Timber.e(throwable, "Sticker: не отправился %s", name)
                    sendError = throwable.message ?: throwable::class.simpleName ?: "unknown error"
                }
                sendingStickerId = null
            }
        }

        fun sendDevSticker(event: StickerPickerEvent.SelectSticker) {
            send(
                name = event.sticker.fileName,
                mimeType = PNG_MIME_TYPE,
                width = event.width,
                height = event.height,
                sizeBytes = event.pngBytes.size.toLong(),
                mediaUrlOrNull = null,
                readBytes = { event.pngBytes },
            )
        }

        fun sendMySticker(sticker: MySticker) {
            send(
                name = sticker.fileName,
                mimeType = sticker.mimeType,
                width = sticker.width,
                height = sticker.height,
                sizeBytes = sticker.sizeBytes,
                mediaUrlOrNull = sticker.mediaUrl,
                readBytes = { File(sticker.localPath).readBytes() },
                onUploaded = { store.markUploaded(sticker.id, it) },
            )
        }

        fun importUris(uris: List<android.net.Uri>) {
            if (uris.isEmpty()) return
            isImporting = true
            importError = null
            sessionCoroutineScope.launch {
                val failures = mutableListOf<String>()
                uris.forEach { uri ->
                    when (val result = importer.import(uri)) {
                        is ImportResult.Imported -> Timber.i("Sticker: импортировали %d шт.", result.stickers.size)
                        is ImportResult.Failed -> failures += result.reason
                    }
                }
                myStickers = store.list()
                isImporting = false
                if (failures.isNotEmpty()) {
                    importError = failures.joinToString("; ")
                } else if (myStickers.isNotEmpty()) {
                    tab = StickerPickerState.Tab.MyStickers
                }
            }
        }

        val eventSink: (StickerPickerEvent) -> Unit = { event ->
            when (event) {
                is StickerPickerEvent.SelectSticker -> sendDevSticker(event)
                is StickerPickerEvent.SelectMySticker -> sendMySticker(event.sticker)
                is StickerPickerEvent.SelectTab -> tab = event.tab
                StickerPickerEvent.PickFilesToImport -> Unit // экран сам запустит системный пикер
                is StickerPickerEvent.FilesPicked -> importUris(event.uris)
                is StickerPickerEvent.DeleteMySticker -> {
                    store.remove(event.sticker.id)
                    myStickers = store.list()
                }
                StickerPickerEvent.Dismiss -> onStickerSent()
            }
        }

        return StickerPickerState(
            tab = tab,
            devStickers = devStickers,
            myStickers = myStickers,
            sendingStickerId = sendingStickerId,
            isImporting = isImporting,
            sendError = sendError,
            importError = importError,
            eventSink = eventSink,
        )
    }

    private companion object {
        const val PNG_MIME_TYPE = "image/png"
    }
}
