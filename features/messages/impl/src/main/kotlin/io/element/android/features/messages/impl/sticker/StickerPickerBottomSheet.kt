/*
 * VaVsta Messenger (форк Element X): панель стикеров.
 */

package io.element.android.features.messages.impl.sticker

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.element.android.features.messages.impl.R
import io.element.android.features.messages.impl.timeline.components.customreaction.ExpandSheetWhenImeIsVisible
import io.element.android.libraries.core.mimetype.MimeTypes
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.designsystem.theme.components.ModalBottomSheet
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.mediapickers.api.PickerProvider
import java.io.File

/**
 * VaVsta: панель стикеров — вкладка с вшитым набором (проверка пути) и вкладка «Мои»,
 * куда попадает импорт из Telegram (`.webp`/`.png`) и `.tgs` (рендерится кадром).
 *
 * Дальше по плану (уровень 2 из сметы): паки, «недавние», поиск, анимации.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StickerPickerBottomSheet(
    state: StickerPickerState,
    pickerProvider: PickerProvider,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)

    // Импорт: системный выбор файлов. Регистрировать надо во время композиции, поэтому
    // launcher живёт тут, а не в обработчике кнопки.
    val filePicker = pickerProvider.registerFileMultiPicker(mimeType = MimeTypes.Any) { uris ->
        state.eventSink(StickerPickerEvent.FilesPicked(uris))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
        scrollable = false,
    ) {
        ExpandSheetWhenImeIsVisible(sheetState)
        StickerPickerContents(
            state = state,
            onImportClick = { filePicker.launch() },
            modifier = Modifier.navigationBarsPadding(),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun StickerPickerContents(
    state: StickerPickerState,
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .heightIn(max = 460.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.vavsta_sticker_picker_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = stringResource(R.string.vavsta_sticker_picker_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onImportClick, enabled = !state.isImporting) {
                Text(
                    text = if (state.isImporting) {
                        stringResource(R.string.vavsta_sticker_importing)
                    } else {
                        stringResource(R.string.vavsta_sticker_import_button)
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }

        state.importError?.let { error ->
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        state.sendError?.let { error ->
            Text(
                text = stringResource(R.string.vavsta_sticker_send_failed, error),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }

        TabRow(selectedTabIndex = if (state.tab == StickerPickerState.Tab.DevStickers) 0 else 1) {
            Tab(
                selected = state.tab == StickerPickerState.Tab.DevStickers,
                onClick = { state.eventSink(StickerPickerEvent.SelectTab(StickerPickerState.Tab.DevStickers)) },
                text = {
                    Text(
                        text = stringResource(R.string.vavsta_sticker_tab_dev),
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                    )
                },
            )
            Tab(
                selected = state.tab == StickerPickerState.Tab.MyStickers,
                onClick = { state.eventSink(StickerPickerEvent.SelectTab(StickerPickerState.Tab.MyStickers)) },
                text = {
                    Text(
                        text = stringResource(R.string.vavsta_sticker_tab_mine),
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                    )
                },
            )
        }

        when (state.tab) {
            StickerPickerState.Tab.DevStickers -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 72.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(state.devStickers, key = { it.id }) { sticker ->
                    StickerGridCell(
                        contentDescription = stringResource(R.string.a11y_vavsta_sticker_send, sticker.label),
                        isSending = state.sendingStickerId == sticker.id,
                        enabled = state.sendingStickerId == null,
                        onClick = {
                            val bytes = context.resources.openRawResource(sticker.drawableRes).use { it.readBytes() }
                            val size = bytes.toImageSizeOrZero()
                            state.eventSink(
                                StickerPickerEvent.SelectSticker(
                                    sticker = sticker,
                                    pngBytes = bytes,
                                    width = size.first,
                                    height = size.second,
                                )
                            )
                        },
                    ) {
                        painterResource(sticker.drawableRes).let { painter ->
                            Image(
                                painter = painter,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.size(60.dp),
                            )
                        }
                    }
                }
            }

            StickerPickerState.Tab.MyStickers -> {
                if (state.myStickers.isEmpty()) {
                    EmptyStickersHint()
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 72.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(state.myStickers, key = { it.id }) { sticker ->
                            StickerGridCell(
                                contentDescription = stringResource(R.string.a11y_vavsta_sticker_send, sticker.fileName),
                                isSending = state.sendingStickerId == sticker.id,
                                enabled = state.sendingStickerId == null,
                                onClick = { state.eventSink(StickerPickerEvent.SelectMySticker(sticker)) },
                                onLongClick = { state.eventSink(StickerPickerEvent.DeleteMySticker(sticker)) },
                            ) {
                                val bitmap = remember(sticker.localPath) {
                                    runCatching {
                                        File(sticker.localPath).inputStream().use { android.graphics.BitmapFactory.decodeStream(it) }
                                    }.getOrNull()
                                }
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.size(60.dp),
                                    )
                                } else {
                                    Text(text = "?", style = MaterialTheme.typography.titleLarge)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StickerGridCell(
    contentDescription: String,
    isSending: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp),
            )
            .semantics { this.contentDescription = contentDescription }
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(enabled = enabled, onClick = onClick, onLongClick = onLongClick)
                } else {
                    Modifier.clickable(enabled = enabled, onClick = onClick)
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
        if (isSending) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun EmptyStickersHint() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.vavsta_sticker_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

/** Размеры PNG из заголовка файла — нужны для поля `info` в MSC3916. */
private fun ByteArray.toImageSizeOrZero(): Pair<Int, Int> {
    val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeByteArray(this, 0, size, options)
    return options.outWidth to options.outHeight
}

@PreviewsDayNight
@Composable
private fun StickerPickerContentsPreview() = ElementPreview {
    var state by remember {
        mutableStateOf(
            StickerPickerState(
                tab = StickerPickerState.Tab.DevStickers,
                devStickers = devStickers,
                myStickers = emptyList(),
                sendingStickerId = "like",
                isImporting = false,
                sendError = null,
                importError = null,
                eventSink = {},
            )
        )
    }
    StickerPickerContents(state = state, onImportClick = {})
}

@PreviewsDayNight
@Composable
private fun StickerPickerMyStickersPreview() = ElementPreview {
    StickerPickerContents(
        state = StickerPickerState(
            tab = StickerPickerState.Tab.MyStickers,
            devStickers = devStickers,
            myStickers = emptyList(),
            sendingStickerId = null,
            isImporting = false,
            sendError = null,
            importError = null,
            eventSink = {},
        ),
        onImportClick = {},
    )
}
