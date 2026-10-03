/*
 * VaVsta Messenger (форк Element X): скелет стикеров + импорт из Telegram.
 */

package io.element.android.features.messages.impl.sticker

/**
 * VaVsta: состояние экрана выбора стикера.
 *
 * Две вкладки: [Tab.DevStickers] — вшитый набор для проверки пути, [Tab.MyStickers] — то, что
 * пользователь импортировал (в т.ч. из экспорта Telegram).
 */
data class StickerPickerState(
    val tab: Tab,
    val devStickers: List<DevSticker>,
    val myStickers: List<MySticker>,
    val sendingStickerId: String?,
    val isImporting: Boolean,
    val sendError: String?,
    val importError: String?,
    val eventSink: (StickerPickerEvent) -> Unit,
) {
    enum class Tab { DevStickers, MyStickers }
}

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

    /** Отправка одного из импортированных: presenter сам знает, что грузить. */
    data class SelectMySticker(val sticker: MySticker) : StickerPickerEvent

    data class SelectTab(val tab: StickerPickerState.Tab) : StickerPickerEvent

    /** VaVsta: открыть системный выбор файлов для импорта. */
    data object PickFilesToImport : StickerPickerEvent

    /** VaVsta: файл выбран, дальше разбирает presenter. */
    data class FilesPicked(val uris: List<android.net.Uri>) : StickerPickerEvent

    data class DeleteMySticker(val sticker: MySticker) : StickerPickerEvent

    data object Dismiss : StickerPickerEvent
}
