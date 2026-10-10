/*
 * VaVsta Messenger (форк Element X): импорт стикеров (в т.ч. из Telegram).
 */

package io.element.android.features.messages.impl.sticker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.LottieCompositionFactory
import com.airbnb.lottie.LottieDrawable
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.di.annotations.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream
import kotlin.math.roundToInt

/**
 * Итог импорта одного файла. Отдельное имя, а не `Result`: иначе конфликтует с `kotlin.Result`.
 */
sealed interface ImportResult {
    data class Imported(val stickers: List<MySticker>) : ImportResult
    data class Failed(val reason: String) : ImportResult
}

/**
 * VaVsta: разбор выбранных пользователем файлов в стикеры.
 *
 * Что понимаем на входе:
 * - `.webp` / `.png` / `.gif` — копируем как есть (Telegram отдаёт такие при экспорте паков);
 * - `.tgs` — это **не картинка**, а Lottie-анимация (gzipped JSON). Матрикс их не понимает,
 *   поэтому рендерим покадры и отдаём анимированный GIF: на прошивке нашего телефона animated
 *   WebP показывается только первым кадром, а GIF проигрывается полностью;
 * - `.zip` — распаковываем (экспорт Telegram Desktop) и обрабатываем содержимое.
 *
 * Ограничения MSC3916: 512×512 и до 300 КБ — приводим к ним, чтобы сервер и клиенты
 * не начали капризничать на крупных кадрах.
 */
interface StickerImporter {
    /** Импортирует один выбранный файл (или архив) и возвращает, что получилось. */
    suspend fun import(uri: Uri): ImportResult
}

@ContributesBinding(AppScope::class)
class DefaultStickerImporter(
    @ApplicationContext private val context: Context,
    private val store: MyStickerStore,
) : StickerImporter {
    override suspend fun import(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val name = displayName(uri)
        Timber.i("StickerImport: берём %s", name)
        when (extensionOf(name)) {
            "zip" -> {
                val extracted = runCatching { unzip(uri) }
                    .getOrElse {
                        Timber.e(it, "StickerImport: не распаковали %s", name)
                        return@withContext ImportResult.Failed("не смог распаковать архив")
                    }
                val imported = mutableListOf<MySticker>()
                extracted.forEach { file -> importFile(file)?.let { imported += it } }
                extracted.forEach { it.delete() }
                if (imported.isEmpty()) {
                    ImportResult.Failed("в архиве не нашлось webp/png/tgs")
                } else {
                    ImportResult.Imported(imported)
                }
            }

            "tgs" -> {
                val sticker = importFile(materialise(uri, name))
                if (sticker == null) {
                    ImportResult.Failed("не смог прочитать анимацию .tgs")
                } else {
                    ImportResult.Imported(listOf(sticker))
                }
            }

            "webp", "png", "gif" -> {
                val sticker = importFile(materialise(uri, name))
                if (sticker == null) {
                    ImportResult.Failed("не смог прочитать картинку")
                } else {
                    ImportResult.Imported(listOf(sticker))
                }
            }

            else -> ImportResult.Failed("формат ${extensionOf(name).ifEmpty { "?" }} не поддерживается — нужны webp, png или tgs")
        }
    }

    private fun importFile(source: File): MySticker? {
        var extension = extensionOf(source.name)
        val id = UUID.randomUUID().toString()
        val target: File
        val mimeType: String
        val fromTgs = extension == "tgs"

        if (fromTgs) {
            // Анимация целиком: Lottie → кадры → GIF. Иначе от Telegram остаётся
            // один-единственный кадр, а Telegram-стикеры почти все анимированные.
            // Раньше здесь был animated WebP, но на прошивке нашего телефона он показывался
            // только первым кадром.
            target = renderTgsAnimation(source, id) ?: run {
                Timber.e("StickerImport: .tgs не дал анимации (%s)", source.name)
                return null
            }
            mimeType = "image/gif"
            // Имя и расширение — из gif, а не из tgs: иначе сохраним файл с чужим расширением
            extension = "gif"
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            target = store.importDirectory().resolve("$id.$extension")
            source.copyTo(target, overwrite = true)
            mimeType = if (extension == "png") "image/png" else "image/$extension"
        }

        // Анимацию НЕ трогаем: fit() пережал бы её в статичный PNG и убил движение
        val final = if (fromTgs) target else fit(target)
        val sticker = MySticker(
            id = id,
            fileName = "${id.take(8)}.$extension",
            localPath = final.absolutePath,
            mimeType = mimeType,
            width = final.readWidth(),
            height = final.readHeight(),
            sizeBytes = final.length(),
            fromTgs = fromTgs,
        )
        if (final != target) target.delete()
        store.add(sticker)
        return sticker
    }

    /**
     * Приводит файл к «стикерному» виду: не больше 512×512 и, если получится, не тяжелее 300 КБ.
     * Если файл уже в норме — возвращаем тот же путь.
     */
    private fun fit(source: File): File {
        val (width, height) = source.readWidth() to source.readHeight()
        val longest = maxOf(width, height)
        if (longest <= TARGET_SIZE && source.length() <= MAX_SIZE_BYTES) return source

        val bitmap = BitmapFactory.decodeFile(source.absolutePath) ?: return source
        val scale = if (longest > TARGET_SIZE) TARGET_SIZE.toDouble() / longest else 1.0
        val scaled = if (scale < 1.0) {
            Bitmap.createScaledBitmap(bitmap, (width * scale).roundToInt(), (height * scale).roundToInt(), true)
        } else {
            bitmap
        }
        val out = store.importDirectory().resolve("${source.nameWithoutExtension}.png")
        FileOutputStream(out).use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
        if (scaled !== bitmap) scaled.recycle()
        bitmap.recycle()
        Timber.i("StickerImport: ужали %sx%s (%d Б) до %sx%s (%d Б)", width, height, source.length(), out.readWidth(), out.readHeight(), out.length())
        return out
    }

    /**
     * `.tgs` = gzipped Lottie JSON → набор кадров → анимированный GIF.
     *
     * Почему GIF, а не animated WebP: на прошивке нашего телефона системный `ImageDecoder`
     * отдаёт от animated WebP только первый кадр, а `GifDecoder` из coil-gif анимирует только
     * GIF. Проверено на устройстве — WebP показывается как статика. GIF — единственный формат,
     * который тут реально проигрывается, поэтому пишем своим энкодером (GifEncoder).
     *
     * Размер: лимит MSC3916 — 300 КБ. 25 кадров 512×512 дают около 740 КБ, поэтому перебираем
     * варианты от лучшего к худшему, пока не влезем.
     */
    private fun renderTgsAnimation(source: File, id: String): File? = runCatching {
        val composition: LottieComposition = GZIPInputStream(FileInputStream(source)).use { jsonStream ->
            LottieCompositionFactory
                .fromJsonInputStreamSync(jsonStream, "$id.json")
                .value
        } ?: return null

        // ВАЖНО: Lottie отдаёт duration во Float СЕКУНДАХ, а нам нужны миллисекунды.
        val durationMs = (composition.duration * 1000f).toLong().coerceAtLeast(1L)

        val target = store.importDirectory().resolve("$id.gif")
        for (attempt in RENDER_ATTEMPTS) {
            val size = attempt.size
            val frameCount = attempt.frames.coerceAtMost(
                (durationMs / 1000 * MAX_FPS).toInt().coerceAtLeast(2)
            )
            val delayMs = (durationMs / frameCount).coerceAtLeast(40L)

            val encoder = GifEncoder(width = size, height = size, delayMs = delayMs.toInt())
            val drawable = LottieDrawable().apply { setComposition(composition) }
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, size, size)
            val pixels = IntArray(size * size)
            val step = durationMs.toFloat() / frameCount
            repeat(frameCount) { index ->
                bitmap.eraseColor(Color.TRANSPARENT)
                drawable.progress = index * step / durationMs
                drawable.draw(canvas)
                bitmap.getPixels(pixels, 0, size, 0, 0, size, size)
                encoder.addFrame(pixels)
            }
            bitmap.recycle()

            target.outputStream().buffered().use { encoder.write(it) }
            val bytes = target.length()
            Timber.i("StickerImport: .tgs → GIF %d×%d, %d кадров, %d Б", size, size, frameCount, bytes)
            if (bytes <= MAX_SIZE_BYTES) return@runCatching target
            // Не влезло — следующая попытка перезапишет файл меньшим вариантом
        }
        // Ни один вариант не влез в лимит: отдаём последний (самый мелкий), а не молча мимо
        target
    }.onFailure {
        Timber.e(it, "StickerImport: .tgs не собрался в анимацию")
    }.getOrNull()

    /**
     * `.tgs` = gzipped Lottie JSON. Рендерим кадр из середины анимации: первый кадр у Telegram
     * часто почти пустой (замаскированный fade-in).
     */
    private fun renderTgsFrame(source: File): Bitmap? = runCatching {
        // ГЛАВНАЯ ГРАБЛЯ `.tgs`: это gzip-архив с Lottie-JSON, а Lottie ждёт обычный
        // текстовый поток. Без распаковки он молча не разбирает анимацию.
        // `.getOrThrow()` у LottieResult конфликтует с kotlin.Result, поэтому берём значение явно.
        val composition: LottieComposition = GZIPInputStream(FileInputStream(source)).use { jsonStream ->
            LottieCompositionFactory
                .fromJsonInputStreamSync(jsonStream, "${source.name}.json")
                .value
        } ?: error("Lottie не распознал ${source.name}")
        val drawable = LottieDrawable().apply { setComposition(composition) }
        // На середине анимации кадр обычно самый показательный: у Telegram первый кадр
        // часто почти пустой, из-за fade-in.
        drawable.progress = 0.5f
        val bitmap = Bitmap.createBitmap(TARGET_SIZE, TARGET_SIZE, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
        drawable.setBounds(0, 0, TARGET_SIZE, TARGET_SIZE)
        drawable.draw(android.graphics.Canvas(bitmap))
        bitmap
    }.onFailure {
        Timber.e(it, "StickerImport: .tgs не отрендерился")
    }.getOrNull()

    private fun unzip(uri: Uri): List<File> {
        val target = File(context.cacheDir, "sticker-import-${UUID.randomUUID().toString().take(8)}")
        target.mkdirs()
        val files = mutableListOf<File>()
        runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()?.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val name = File(entry.name).name
                        if (extensionOf(name) in SUPPORTED_EXTENSIONS) {
                            val out = File(target, name)
                            FileOutputStream(out).use { zip.copyTo(it) }
                            files += out
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        Timber.i("StickerImport: из архива взяли %d файлов", files.size)
        return files
    }

    /** Копирует `content://`-uri во временный файл, чтобы работать с ним как с обычным. */
    private fun materialise(uri: Uri, name: String): File {
        val target = File(context.cacheDir, "sticker-pick-${UUID.randomUUID().toString().take(8)}-$name")
        // Именно contentResolver, а не getSystemService<ContentResolver>(): на этой
        // прошивке getSystemService вернул null, и импорт молча падал с «не смог прочитать».
        val stream = runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
        if (stream == null) {
            Timber.e("StickerImport: система не дала прочитать %s (%s)", name, uri)
            throw IllegalStateException("Android не дал доступ к $name")
        }
        stream.use { input -> FileOutputStream(target).use { output -> input.copyTo(output) } }
        return target
    }

    private fun displayName(uri: Uri): String =
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)
        }.getOrNull()
            ?.use { cursor ->
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
            ?: uri.lastPathSegment
            ?: "sticker"

    private fun File.readWidth(): Int = BitmapFactory.Options().run {
        inJustDecodeBounds = true
        BitmapFactory.decodeFile(absolutePath, this)
        outWidth
    }

    private fun File.readHeight(): Int = BitmapFactory.Options().run {
        inJustDecodeBounds = true
        BitmapFactory.decodeFile(absolutePath, this)
        outHeight
    }

    private fun extensionOf(name: String): String = name.substringAfterLast('.', "").lowercase()

    private companion object {
        val SUPPORTED_EXTENSIONS = setOf("webp", "png", "gif", "tgs")
        const val TARGET_SIZE = 512
        const val MAX_SIZE_BYTES = 300L * 1024

        /** Сколько кадров кладём в анимацию (Telegram обычно 60 fps, но нам хватит 25). */
        const val FRAME_COUNT = 25

        /**
         * Попытки попасть в лимит MSC3916: от лучшего качества к худшему.
         * 25 кадров 512×512 дают около 740 КБ — многовато, поэтому уменьшаем.
         */
        val RENDER_ATTEMPTS = listOf(
            RenderAttempt(size = 512, frames = 25),
            RenderAttempt(size = 512, frames = 14),
            RenderAttempt(size = 384, frames = 14),
            RenderAttempt(size = 320, frames = 10),
        )

        data class RenderAttempt(val size: Int, val frames: Int)

        /** Не дёргаем Lottie чаще, чем он реально умеет. */
        const val MAX_FPS = 25

        const val QUALITY = 80f
        const val LOOP_FOREVER = 0
    }
}
