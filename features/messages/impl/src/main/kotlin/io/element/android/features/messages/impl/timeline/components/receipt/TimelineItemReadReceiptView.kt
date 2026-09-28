/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.receipt

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.messages.impl.timeline.model.ReadReceiptData
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.components.avatar.getBestName
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.matrix.api.timeline.item.event.LocalEventSendState
import io.element.android.libraries.testtags.TestTags
import io.element.android.libraries.ui.strings.CommonPlurals
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.collections.immutable.ImmutableList

@Composable
fun TimelineItemReadReceiptView(
    state: ReadReceiptViewState,
    onReadReceiptsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.receipts.isNotEmpty()) {
        ReadReceiptsRow(modifier = modifier) {
            ReadReceiptsDoubleCheck(
                receipts = state.receipts,
                onClick = onReadReceiptsClick,
            )
        }
    } else {
        when (state.sendState) {
            is LocalEventSendState.Sending -> {
                ReadReceiptsRow(modifier) {
                    Icon(
                        modifier = Modifier.padding(2.dp),
                        imageVector = CompoundIcons.Circle(),
                        contentDescription = stringResource(id = CommonStrings.common_sending),
                        tint = ElementTheme.colors.iconSecondary
                    )
                }
            }
            is LocalEventSendState.Failed -> {
                // Error? The timestamp is already displayed in red
            }
            null,
            is LocalEventSendState.Sent -> {
                if (state.isLastOutgoingMessage) {
                    ReadReceiptsRow(modifier = modifier) {
                        Icon(
                            modifier = Modifier.padding(2.dp),
                            imageVector = CompoundIcons.Check(),
                            contentDescription = stringResource(id = CommonStrings.common_sent),
                            tint = ElementTheme.colors.iconSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadReceiptsRow(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(AvatarSize.TimelineReadReceipt.dp + 8.dp)
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 4.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun ReadReceiptsDoubleCheck(
    receipts: ImmutableList<ReadReceiptData>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val receiptDescription = computeReceiptDescription(receipts)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable(
                onClick = onClick,
                onClickLabel = stringResource(id = CommonStrings.a11y_read_receipts_tap_to_show_all_android),
            )
            .padding(2.dp)
            .clearAndSetSemantics {
                testTag = TestTags.messageReadReceipts.value
                contentDescription = receiptDescription
                role = Role.Button
            }
            .padding(start = 2.dp),
        horizontalArrangement = Arrangement.spacedBy((-2).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = CompoundIcons.Check(),
            contentDescription = null,
            tint = ElementTheme.colors.iconAccentPrimary
        )
        Icon(
            modifier = Modifier.offset(x = (-6).dp, y = 1.dp),
            imageVector = CompoundIcons.Check(),
            contentDescription = null,
            tint = ElementTheme.colors.iconAccentPrimary
        )
    }
}

@Composable
private fun computeReceiptDescription(receipts: ImmutableList<ReadReceiptData>): String {
    return when (receipts.size) {
        0 -> "" // Cannot happen
        1 -> stringResource(
            id = CommonStrings.a11y_read_receipts_single,
            receipts[0].avatarData.getBestName()
        )
        2 -> stringResource(
            id = CommonStrings.a11y_read_receipts_multiple,
            receipts[0].avatarData.getBestName(),
            receipts[1].avatarData.getBestName(),
        )
        else -> pluralStringResource(
            id = CommonPlurals.a11y_read_receipts_multiple_with_others,
            count = receipts.size - 1,
            receipts[0].avatarData.getBestName(),
            receipts.size - 1
        )
    }
}

@PreviewsDayNight
@Composable
internal fun TimelineItemReadReceiptViewPreview(
    @PreviewParameter(ReadReceiptViewStatePreviewParam::class) state: ReadReceiptViewState,
) = ElementPreview {
    TimelineItemReadReceiptView(
        state = state,
        onReadReceiptsClick = {},
    )
}
