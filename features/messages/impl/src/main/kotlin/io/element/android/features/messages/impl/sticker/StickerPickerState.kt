/*
 * VaVsta Messenger (форк Element X): скелет стикеров.
 */

package io.element.android.features.messages.impl.sticker

/**
 * VaVsta: состояние экрана выбора стикера.
 *
 * Скелет: набор стикеров вшит в APK, отправка идёт через загрузку медиа и событие `m.sticker`.
 */
data class StickerPickerState(
    val stickers: List<DevSticker>,
    val sendingStickerId: String?,
    val sendError: String?,
    val eventSink: (StickerPickerEvent) -> Unit,
)

sealed interface StickerPickerEvent {
    /**
     * Стикер выбран для отправки.
     *
     * Байты и размеры тащим из view-слоя: там есть ресурс ассета, а presenter-у контекст
     * не положен. Файл отдаётся в комнату как есть, без пережатия.
     */
    data class SelectSticker(
        val sticker: DevSticker,
        val pngBytes: ByteArray,
        val width: Int,
        val height: Int,
    ) : StickerPickerEvent

    data object Dismiss : StickerPickerEvent
}