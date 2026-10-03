/*
 * VaVsta Messenger (форк Element X): скелет стикеров.
 */

package io.element.android.features.messages.impl.sticker

import androidx.annotation.DrawableRes
import io.element.android.features.messages.impl.R

/**
 * VaVsta: скелетный набор стикеров для dev-сборки.
 *
 * Настоящие паки (MSC3916/3912) с сервера, загрузкой и «недавними» — следующий этап,
 * пока это вшитые в APK картинки, чтобы проверить весь путь: загрузка → `m.sticker` → отрисовка.
 */
data class DevSticker(
    val id: String,
    val fileName: String,
    @DrawableRes val drawableRes: Int,
    val label: String,
)

internal val devStickers = listOf(
    DevSticker("like", "vavsta_like.png", R.drawable.sticker_dev_like, "LIKE"),
    DevSticker("lol", "vavsta_lol.png", R.drawable.sticker_dev_lol, "LOL"),
    DevSticker("wow", "vavsta_wow.png", R.drawable.sticker_dev_wow, "WOW"),
    DevSticker("cry", "vavsta_cry.png", R.drawable.sticker_dev_cry, "CRY"),
    DevSticker("fire", "vavsta_fire.png", R.drawable.sticker_dev_fire, "FIRE"),
    DevSticker("ok", "vavsta_ok.png", R.drawable.sticker_dev_ok, "OK"),
    DevSticker("think", "vavsta_think.png", R.drawable.sticker_dev_think, "?"),
    DevSticker("party", "vavsta_party.png", R.drawable.sticker_dev_party, "YAY"),
)