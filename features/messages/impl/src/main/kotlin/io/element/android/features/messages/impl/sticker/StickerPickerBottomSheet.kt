/*
 * VaVsta Messenger (форк Element X): скелет стикеров.
 */

package io.element.android.features.messages.impl.sticker

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import io.element.android.features.messages.impl.R
import io.element.android.features.messages.impl.timeline.components.customreaction.ExpandSheetWhenImeIsVisible
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.designsystem.theme.components.ModalBottomSheet
import io.element.android.libraries.designsystem.theme.components.Text

/**
 * VaVsta: скелет панели стикеров — сетка вшитых стикеров, тап = отправка `m.sticker`.
 *
 * Дальше по плану (уровень 2 из сметы): вкладки паков, «недавние», поиск, анимации.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StickerPickerBottomSheet(
    state: StickerPickerState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
        scrollable = false,
    ) {
        ExpandSheetWhenImeIsVisible(sheetState)
        StickerPickerContents(state = state, modifier = Modifier.navigationBarsPadding())
    }
}

@Composable
private fun StickerPickerContents(
    state: StickerPickerState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .heightIn(max = 420.dp),
    ) {
        Text(
            text = stringResource(R.string.vavsta_sticker_picker_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.vavsta_sticker_picker_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        state.sendError?.let { error ->
            Text(
                text = stringResource(R.string.vavsta_sticker_send_failed, error),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 72.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(state.stickers, key = { it.id }) { sticker ->
                val isSending = state.sendingStickerId == sticker.id
                val sendDescription = stringResource(R.string.a11y_vavsta_sticker_send, sticker.label)
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(16.dp),
                        )
                        .semantics { contentDescription = sendDescription }
                        .clickable(enabled = state.sendingStickerId == null) {
                            val bytes = context.resources.openRawResource(sticker.drawableRes).use { it.readBytes() }
                            val bitmap = bytes.toImageBitmapOrNull()
                            state.eventSink(
                                StickerPickerEvent.SelectSticker(
                                    sticker = sticker,
                                    pngBytes = bytes,
                                    width = bitmap?.width ?: 0,
                                    height = bitmap?.height ?: 0,
                                )
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    painterResource(sticker.drawableRes).let { painter ->
                        Image(
                            painter = painter,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(60.dp)
                                .then(if (isSending) Modifier.padding(2.dp) else Modifier),
                        )
                    }
                    if (isSending) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}

/** Размере�� PNG нужен для поля `info` в MSC3916, а декодер живёт в res/drawable — читаем заголовок. */
private fun ByteArray.toImageBitmapOrNull(): ImageBitmap? = runCatching {
    val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeByteArray(this, 0, size, options)
    if (options.outWidth <= 0 || options.outHeight <= 0) return null
    val decoded = android.graphics.BitmapFactory.decodeByteArray(this, 0, size)
    decoded?.asImageBitmap()
}.getOrNull()

@PreviewsDayNight
@Composable
private fun StickerPickerContentsPreview() = ElementPreview {
    StickerPickerContents(
        state = StickerPickerState(
            stickers = devStickers,
            sendingStickerId = "like",
            sendError = null,
            eventSink = {},
        )
    )
}