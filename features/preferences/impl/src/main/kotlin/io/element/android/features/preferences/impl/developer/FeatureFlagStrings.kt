/*
 * Copyright (c) 2026 VaVsta
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package io.element.android.features.preferences.impl.developer

import androidx.annotation.StringRes
import io.element.android.features.preferences.impl.R
import io.element.android.libraries.featureflag.api.Feature
import io.element.android.libraries.featureflag.api.FeatureFlags

/**
 * Локализация названий и описаний feature flags.
 *
 * Апстрим держит эти тексты прямо в enum [FeatureFlags] и сознательно их не
 * переводит (в `Feature.title` написано «not needed to be translated, it's only
 * dev accessible»). Для нашего форка этого аргумента недостаточно: меню
 * «Для разработчиков» — обычный видимый экран, и пользователь просил его на
 * русском.
 *
 * Поэтому здесь маппим флаг на строковый ресурс. Если для флага строки нет —
 * возвращаем null, и вызывающий код падает назад на английский текст из enum.
 * Так новые флаги апстрима продолжат работать без правок.
 */
internal object FeatureFlagStrings {

    @StringRes
    fun titleRes(feature: Feature): Int? = when (feature) {
        FeatureFlags.ShowBlockedUsersDetails -> R.string.vavsta_ff_show_blocked_users_details_title
        FeatureFlags.SyncOnPush -> R.string.vavsta_ff_sync_on_push_title
        FeatureFlags.OnlySignedDeviceIsolationMode -> R.string.vavsta_ff_only_signed_device_isolation_mode_title
        FeatureFlags.Knock -> R.string.vavsta_ff_knock_title
        FeatureFlags.PrintLogsToLogcat -> R.string.vavsta_ff_print_logs_to_logcat_title
        FeatureFlags.SelectableMediaQuality -> R.string.vavsta_ff_selectable_media_quality_title
        FeatureFlags.Threads -> R.string.vavsta_ff_threads_title
        FeatureFlags.MultiAccount -> R.string.vavsta_ff_multi_account_title
        FeatureFlags.QrCodeLogin -> R.string.vavsta_ff_qr_code_login_title
        FeatureFlags.AllowBlackTheme -> R.string.vavsta_ff_allow_black_theme_title
        FeatureFlags.ValidateNetworkWhenSchedulingNotificationFetching ->
            R.string.vavsta_ff_validate_network_when_scheduling_notification_fetching_title
        FeatureFlags.JumpToUnread -> R.string.vavsta_ff_jump_to_unread_title
        FeatureFlags.SlashCommand -> R.string.vavsta_ff_slash_command_title
        FeatureFlags.RoomThreadList -> R.string.vavsta_ff_room_thread_list_title
        FeatureFlags.AutomaticBackPagination -> R.string.vavsta_ff_automatic_back_pagination_title
        FeatureFlags.UnreadIndicatorCount -> R.string.vavsta_ff_unread_indicator_count_title
        FeatureFlags.SendGalleryMessages -> R.string.vavsta_ff_send_gallery_messages_title
        FeatureFlags.MessageSearch -> R.string.vavsta_ff_message_search_title
        // Новые флаги апстрима: строки ещё нет — вызывающий код возьмёт английский текст.
        else -> null
    }

    @StringRes
    fun descriptionRes(feature: Feature): Int? = when (feature) {
        FeatureFlags.ShowBlockedUsersDetails -> R.string.vavsta_ff_show_blocked_users_details_description
        FeatureFlags.SyncOnPush -> R.string.vavsta_ff_sync_on_push_description
        FeatureFlags.OnlySignedDeviceIsolationMode -> R.string.vavsta_ff_only_signed_device_isolation_mode_description
        FeatureFlags.Knock -> R.string.vavsta_ff_knock_description
        FeatureFlags.PrintLogsToLogcat -> R.string.vavsta_ff_print_logs_to_logcat_description
        FeatureFlags.SelectableMediaQuality -> R.string.vavsta_ff_selectable_media_quality_description
        FeatureFlags.Threads -> R.string.vavsta_ff_threads_description
        FeatureFlags.MultiAccount -> R.string.vavsta_ff_multi_account_description
        FeatureFlags.QrCodeLogin -> R.string.vavsta_ff_qr_code_login_description
        FeatureFlags.AllowBlackTheme -> R.string.vavsta_ff_allow_black_theme_description
        FeatureFlags.ValidateNetworkWhenSchedulingNotificationFetching ->
            R.string.vavsta_ff_validate_network_when_scheduling_notification_fetching_description
        FeatureFlags.JumpToUnread -> R.string.vavsta_ff_jump_to_unread_description
        FeatureFlags.SlashCommand -> R.string.vavsta_ff_slash_command_description
        FeatureFlags.RoomThreadList -> R.string.vavsta_ff_room_thread_list_description
        FeatureFlags.AutomaticBackPagination -> R.string.vavsta_ff_automatic_back_pagination_description
        FeatureFlags.UnreadIndicatorCount -> R.string.vavsta_ff_unread_indicator_count_description
        FeatureFlags.SendGalleryMessages -> R.string.vavsta_ff_send_gallery_messages_description
        FeatureFlags.MessageSearch -> R.string.vavsta_ff_message_search_description
        else -> null
    }
}
