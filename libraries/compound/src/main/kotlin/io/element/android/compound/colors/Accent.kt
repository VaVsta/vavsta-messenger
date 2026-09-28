/*
 * VaVsta Messenger — выбираемые цветовые акценты.
 * AGPL-3.0-or-later.
 */

package io.element.android.compound.colors

import androidx.compose.ui.graphics.Color

/**
 * Выбираемый пользователем цветовой акцент темы.
 * Допляет всю базовую палитру к неоновому стилю VaVsta.
 */
enum class Accent(
    val primary: Color,
    val primaryHover: Color,
    val primaryPressed: Color,
    val primarySelected: Color,
    val primarySubtle: Color,
    val tertiary: Color,
    val badge: Color,
    val info: Color,
    val onSolid: Color,
) {
    Neon(
        primary = Color(0xffd63dbb),
        primaryHover = Color(0xffe556c9),
        primaryPressed = Color(0xffc030a0),
        primarySelected = Color(0x33d63dbb),
        primarySubtle = Color(0xff2a1535),
        tertiary = Color(0xffa02a93),
        badge = Color(0xff6a1f5c),
        info = Color(0xff3a2486),
        onSolid = Color(0xff120a26),
    ),
    Cyan(
        primary = Color(0xff21c7d8),
        primaryHover = Color(0xff4fd6e5),
        primaryPressed = Color(0xff1599ab),
        primarySelected = Color(0x3321c7d8),
        primarySubtle = Color(0xff0e2b33),
        tertiary = Color(0xff10899b),
        badge = Color(0xff0e4552),
        info = Color(0xff2a5fb8),
        onSolid = Color(0xff04181d),
    ),
    Green(
        primary = Color(0xff35d07f),
        primaryHover = Color(0xff5de09a),
        primaryPressed = Color(0xff1ea35f),
        primarySelected = Color(0x3335d07f),
        primarySubtle = Color(0xff0c2b1d),
        tertiary = Color(0xff188a4d),
        badge = Color(0xff0c3b28),
        info = Color(0xff2b9d54),
        onSolid = Color(0xff041f11),
    ),
    Purple(
        primary = Color(0xff9d6bff),
        primaryHover = Color(0xffb58bff),
        primaryPressed = Color(0xff7d4fe0),
        primarySelected = Color(0x339d6bff),
        primarySubtle = Color(0xff1f1336),
        tertiary = Color(0xff6d3fd0),
        badge = Color(0xff36265c),
        info = Color(0xff5b3fa8),
        onSolid = Color(0xff150c26),
    ),
    Orange(
        primary = Color(0xffff9142),
        primaryHover = Color(0xffffab6b),
        primaryPressed = Color(0xffe0712a),
        primarySelected = Color(0x33ff9142),
        primarySubtle = Color(0xff331f10),
        tertiary = Color(0xffbf5d1f),
        badge = Color(0xff452a12),
        info = Color(0xff9b6b2a),
        onSolid = Color(0xff261105),
    ),
}

/** Акцент из строки настроек, либо [Accent.Neon] по умолчанию. */
fun accentFromName(name: String?): Accent = when (name) {
    null -> Accent.Neon
    else -> runCatching { Accent.valueOf(name) }.getOrDefault(Accent.Neon)
}