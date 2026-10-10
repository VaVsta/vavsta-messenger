/*
 * VaVsta Messenger (форк Element X): свой GIF-энкодер для анимированных стикеров.
 */

package io.element.android.features.messages.impl.sticker

import java.io.OutputStream

/**
 * Минимальный GIF89a-энкодер: палитра до 256 цветов (median cut) + LZW.
 *
 * Зачем свой, если есть библиотеки:
 * - На прошивке нашего телефона `ImageDecoder` отдаёт от animated WebP и APNG только первый кадр,
 *   а `GifDecoder` из coil-gif анимирует **только GIF**. GIF — единственный формат, который тут
 *   реально проигрывается, и тащить ради него мегабайты зависимостей смысла нет.
 * - LSB-порядок бит и таблица LZW в GIF нестандартны, и любая ошибка даёт «битый файл, который
 *   получатель не покажет» — поэтому код намеренно прямолинейный.
 *
 * Ограничения (те же, что и в MSC3916):
 * - общая палитра на всю анимацию, до 256 цветов;
 * - прозрачность бинарная (альфа < 128 → прозрачный индекс). Альфа в GIF по определению 1 бит,
 *   поэтому градиенты в прозрачных зонах пойдут ступенями.
 */
internal class GifEncoder(
    private val width: Int,
    private val height: Int,
    private val delayMs: Int,
    private val loopCount: Int = 0,
) {
    private val frames = mutableListOf<IntArray>()

    /** Добавляет кадр из ARGB-пикселей (раскладка как у Bitmap.getPixels).
     *
     * ВАЖНО: массив КОПИРУЕТСЯ. Вызывающий код переиспользует один буфер `IntArray` на все
     * кадры (`bitmap.getPixels(pixels, …)` в цикле), и без копии все кадры ссылались бы на один
     * и тот же массив — в GIF попадало 25 одинаковых кадров. Симптом обманчивый: файл весит
     * десятки килобайт, duration правильный, разбирается без ошибок — а картинка не двигается.
     */
    fun addFrame(pixels: IntArray) {
        require(pixels.size == width * height) { "кадр ${pixels.size} != ${width * height}" }
        frames += pixels.copyOf()
    }

    val frameCount: Int get() = frames.size

    fun write(out: OutputStream) {
        if (frames.isEmpty()) return
        val palette = buildPalette()
        // Таблицу квантования строим один раз на весь файл: ускоряет подбор цвета в разы.
        val lookup = buildLookup(palette)

        out.write("GIF89a".toByteArray(Charsets.US_ASCII))
        // Logical Screen Descriptor
        out.writeLsbShort(width)
        out.writeLsbShort(height)
        out.write(0xF7) // глобальная палитра есть, её размер 256, сортировки нет
        // Фоновый индекс обязан совпадать с прозрачным: при disposal = 2 декодер заливает область
        // кадра именно этим цветом, и если здесь 0, прозрачные зоны анимации наливаются цветом
        // нулевого элемента палитры (произвольным — хоть бы и чёрным).
        out.write(palette.transparentIndex)
        out.write(0) // pixel aspect ratio

        val transparent = palette.transparentIndex
        for (index in 0 until 256) {
            val rgb = if (index == transparent) 0 else palette.colors.getOrElse(index) { 0 }
            out.write((rgb shr 16) and 0xFF)
            out.write((rgb shr 8) and 0xFF)
            out.write(rgb and 0xFF)
        }

        // NETSCAPE2.0 — без этого блока GIF показывается ровно один раз.
        out.write(0x21)
        out.write(0xFF)
        out.write(11)
        out.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
        out.write(3)
        out.write(1)
        out.writeLsbShort(loopCount)
        out.write(0)

        frames.forEach { pixels ->
            // Graphic Control Extension. Порядок полей по спецификации GIF89a:
            // [packed: reserved(3) | disposal(3) | userInput(1) | transparencyFlag(1)]
            // [delayLo] [delayHi] [transparentColorIndex] [terminator=0]
            //
            // ГРАБЛЯ: индекс прозрачности идёт ПОСЛЕ задержки, а не перед ней. Если переставить,
            // файл остаётся «открывающимся», но прозрачность молча теряется: круг на прозрачном
            // фоне превращается в круг на чёрном. Порядок сверен побайтово с эталоном Pillow.
            //
            // Disposal = 2 (restore to background), иначе кадры наслаиваются и полупрозрачные
            // области наследуют предыдущий кадр.
            out.write(0x21)
            out.write(0xF9)
            out.write(4)
            out.write(DISPOSAL_RESTORE_BACKGROUND or TRANSPARENT_FLAG)
            out.writeLsbShort(delayMs / 10)
            out.write(transparent)
            out.write(0) // block terminator
            // Image Descriptor
            out.write(0x2C)
            out.writeLsbShort(0)
            out.writeLsbShort(0)
            out.writeLsbShort(width)
            out.writeLsbShort(height)
            out.write(0) // без локальной палитры, без чередования
            writeLzw(out, quantise(pixels, palette, lookup))
        }

        out.write(0x3B) // trailer
        out.flush()
    }

    // ---------------------------------------------------------------- квантование

    private fun quantise(pixels: IntArray, palette: Palette, lookup: IntArray): ByteArray {
        val transparent = palette.transparentIndex
        val out = ByteArray(pixels.size)
        // Кэш на кадр: в анимации один и тот же цвет встречается сотни раз
        val cache = HashMap<Int, Byte>(1024)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val index: Byte = if ((pixel ushr 24) < ALPHA_THRESHOLD) {
                transparent.toByte()
            } else {
                cache.getOrPut(pixel) {
                    val key = quantiseKey(pixel)
                    val value = if (palette.colors.isEmpty()) transparent else lookup[key]
                    value.toByte()
                }
            }
            out[i] = index
        }
        return out
    }

    /** Квант в пространство 5 бит на канал (32³ = 32768 ячеек). */
    private fun quantiseKey(pixel: Int): Int {
        val r = ((pixel shr 16) and 0xFF) shr 3
        val g = ((pixel shr 8) and 0xFF) shr 3
        val b = (pixel and 0xFF) shr 3
        return (r shl 10) or (g shl 5) or b
    }

    /**
     * Готовая таблица «квант → индекс палитры», чтобы не искать ближайший цвет на каждый пиксель.
     *
     * ВАЖНО: квантовое пространство 32³ = 32768 ячеек, а цветов в палитре максимум 255, поэтому
     * таблица всегда покрыта дырами. Без их заливки пиксель, попавший в пустую ячейку, уезжал в
     * индекс прозрачного цвета — то есть градиент местами становился дырявым. Поэтому после
     * раскладки палитры проходим по всем ячейкам и закрываем пустые ближайшим цветом.
     */
    private fun buildLookup(palette: Palette): IntArray {
        val lookup = IntArray(LUT_SIZE) { EMPTY }
        if (palette.colors.isEmpty()) {
            // Совсем нет цветов (все кадры пустые) — всё отдаём прозрачному индексу
            lookup.fill(palette.transparentIndex)
            return lookup
        }
        // Сначала раскладываем цвета палитры: при коллизии выигрывает записанный раньше,
        // а в палитре частые цвета стоят первыми.
        palette.colors.forEachIndexed { index, rgb ->
            val key = quantiseKey(rgb)
            if (lookup[key] == EMPTY) lookup[key] = index
        }
        // Теперь закрываем дыры. 32768 ячеек × 255 цветов — около 8 млн сравнений, для импорта
        // одного стикера это незаметно.
        for (cell in 0 until LUT_SIZE) {
            if (lookup[cell] != EMPTY) continue
            val r = ((cell shr 10) and 0x1F) * 8 + 4
            val g = ((cell shr 5) and 0x1F) * 8 + 4
            val b = (cell and 0x1F) * 8 + 4
            var best = 0
            var bestDistance = Long.MAX_VALUE
            for (index in palette.colors.indices) {
                val color = palette.colors[index]
                val dr = r - ((color shr 16) and 0xFF)
                val dg = g - ((color shr 8) and 0xFF)
                val db = b - (color and 0xFF)
                val distance = (dr * dr + dg * dg + db * db).toLong()
                if (distance < bestDistance) {
                    bestDistance = distance
                    best = index
                }
            }
            lookup[cell] = best
        }
        return lookup
    }

    // ---------------------------------------------------------------- палитра

    /** Median cut по взвешенной гистограмме: до 255 цветов + один прозрачный. */
    private fun buildPalette(): Palette {
        val histogram = HashMap<Int, Int>(4096)
        var seen = 0L
        // Reservoir sampling: если цветов больше потолка, держим равномерную выборку, а не
        // «первые N» — иначе один громкий участок съест половину палитры, а шумовые кадры
        // (анимированный WebP-шум, сжатие) уедут в один-единственный цвет.
        val random = kotlin.random.Random(0x5EED)
        for (pixels in frames) {
            for (pixel in pixels) {
                if ((pixel ushr 24) < ALPHA_THRESHOLD) continue
                val rgb = pixel and 0xFFFFFF
                seen++
                val count = histogram[rgb]
                if (count == null) {
                    if (histogram.size < MAX_UNIQUE_COLORS) {
                        histogram[rgb] = 1
                    } else if (random.nextLong(seen) < MAX_UNIQUE_COLORS) {
                        histogram[rgb] = 1
                    }
                } else if (count < Int.MAX_VALUE) {
                    histogram[rgb] = count + 1
                }
            }
        }
        if (histogram.isEmpty()) return Palette(IntArray(0), 0)

        var boxes = listOf(ColorBox(histogram))
        while (boxes.size < PALETTE_COLORS) {
            // Делим НЕ первый подходящий бокс, а самый «весомый»: наибольший размах цветов,
            // помноженный на их количество. Иначе редкий, но важный цвет (например, красный
            // круг рядом с градиентом) попадает в группу из 30 соседей и растворяется в среднем.
            val target = boxes.indices.maxByOrNull { boxes[it].splitWeight() } ?: break
            if (boxes[target].colors.size <= 1) break
            val (left, right) = boxes[target].split()
            boxes = boxes.toMutableList().also {
                it[target] = left
                it += right
            }
        }

        val colors = IntArray(boxes.size) { boxes[it].averageColor() }
        return Palette(colors, colors.size) // прозрачный индекс — сразу за палитрой
    }

    private class ColorBox(private val histogram: Map<Int, Int>) {
        val colors: List<Int> = histogram.keys.toList()

        /** Размах по самому «длинному» каналу — по нему и будем делить. */
        private fun widestChannel(): Int = (0..2).maxBy { range ->
            colors.maxOf { (it shr range) and 0xFF } - colors.minOf { (it shr range) and 0xFF }
        } ?: 0

        /**
         * Насколько бокс «достоин» деления. Размах × число цветов: иначе бокс из двух почти
         * одинаковых цветов окажется важнее бокса, где 40 цветов разложены по всей оси.
         */
        fun splitWeight(): Long {
            if (colors.size <= 1) return 0L
            val channel = widestChannel()
            val range = colors.maxOf { (it shr channel) and 0xFF } - colors.minOf { (it shr channel) and 0xFF }
            return range.toLong() * colors.size
        }

        fun split(): Pair<ColorBox, ColorBox> {
            val channel = widestChannel()
            val sorted = colors.sortedBy { (it shr channel) and 0xFF }
            val middle = sorted.size / 2
            // ВНИМАНИЕ: скобки обязательны. Без них Kotlin разбирает это как `(return a) to b`.
            return (ColorBox(subMap(sorted.subList(0, middle))) to
                ColorBox(subMap(sorted.subList(middle, sorted.size))))
        }

        /** Взвешенное среднее: редкие цвета не должны вытеснять массовые. */
        fun averageColor(): Int {
            var r = 0L
            var g = 0L
            var b = 0L
            var weight = 0L
            for (color in colors) {
                val w = histogram[color]?.toLong() ?: 1L
                r += (((color shr 16) and 0xFF) * w)
                g += (((color shr 8) and 0xFF) * w)
                b += ((color and 0xFF) * w)
                weight += w
            }
            if (weight == 0L) return colors.firstOrNull() ?: 0
            return (((r / weight).toInt() shl 16) or ((g / weight).toInt() shl 8) or (b / weight).toInt()) and 0xFFFFFF
        }

        private fun subMap(keys: List<Int>): Map<Int, Int> {
            val result = HashMap<Int, Int>(keys.size * 2)
            keys.forEach { result[it] = histogram[it] ?: 1 }
            return result
        }
    }

    // ---------------------------------------------------------------- LZW

    /**
     * LZW-упаковка индексов. Что отличает GIF-LZW от «обычного» LZW и что ломает файл, если забыть:
     * - биты идут **LSB-first** (в PNG наоборот);
     * - коды начинаются с 9 бит и растут на бит по мере заполнения таблицы, максимум 12;
     * - при заполнении (4096) пишется clear, таблица сбрасывается;
     * - размер минимума кодов 8, clear = 256, EOI = 257, первые «новые» коды с 258.
     */
    private fun writeLzw(out: OutputStream, indices: ByteArray) {
        val clearCode = 1 shl MIN_CODE_SIZE // 256
        val eoiCode = clearCode + 1 // 257
        var nextCode = clearCode + 2 // 258
        var codeSize = MIN_CODE_SIZE + 1 // 9
        val table = HashMap<Int, Int>(4096)
        val bits = BitWriter(out)

        out.write(MIN_CODE_SIZE)
        bits.write(clearCode, codeSize)

        var current = indices.firstOrNull()?.toInt()?.and(0xFF) ?: -1
        for (position in 1 until indices.size) {
            val index = indices[position].toInt() and 0xFF
            val key = (current shl 8) or index
            val existing = table[key]
            if (existing != null) {
                current = existing
                continue
            }
            bits.write(current, codeSize)
            if (nextCode >= 4096) {
                // Таблица переполнена — сбрасываем её и начинаем заново
                bits.write(clearCode, codeSize)
                table.clear()
                nextCode = clearCode + 2
                codeSize = MIN_CODE_SIZE + 1
            } else {
                // ПОРЯДОК ВАЖЕН: сначала растим размер кода по текущему nextCode, и только потом
                // выдаём сам код. Если наоборот, энкодер уйдёт на бит вперёд декодера, и тот
                // начнёт читать коды, которых в его таблице ещё нет («код 802 отсутствует»).
                if (nextCode >= (1 shl codeSize) && codeSize < 12) codeSize++
                table[key] = nextCode++
            }
            current = index
        }

        if (current >= 0) bits.write(current, codeSize)
        bits.write(eoiCode, codeSize)
        bits.flush()
    }

    /**
     * Пишет биты LSB-first в GIF-подблоки.
     *
     * ГЛАВНАЯ ГРАБЛЯ формата: поток обязан быть нарезан на блоки по 255 байт, перед каждым —
     * байт длины, а в конце — ноль. Без нарезки декодер читает первый байт данных как длину
     * блока и получает пустой кадр («broken data stream»).
     */
    private class BitWriter(private val out: OutputStream) {
        private var accumulator = 0
        private var bitCount = 0
        private val block = ByteArray(MAX_BLOCK_SIZE)
        private var blockSize = 0

        fun write(code: Int, size: Int) {
            accumulator = accumulator or ((code and ((1 shl size) - 1)) shl bitCount)
            bitCount += size
            while (bitCount >= 8) {
                pushByte(accumulator and 0xFF)
                accumulator = accumulator shr 8
                bitCount -= 8
            }
        }

        fun flush() {
            while (bitCount > 0) {
                pushByte(accumulator and 0xFF)
                accumulator = accumulator shr 8
                bitCount -= 8
            }
            if (blockSize > 0) {
                out.write(blockSize)
                out.write(block, 0, blockSize)
                blockSize = 0
            }
            out.write(0) // block terminator
            accumulator = 0
            bitCount = 0
        }

        private fun pushByte(value: Int) {
            block[blockSize++] = value.toByte()
            if (blockSize == MAX_BLOCK_SIZE) {
                out.write(MAX_BLOCK_SIZE)
                out.write(block, 0, MAX_BLOCK_SIZE)
                blockSize = 0
            }
        }
    }

    // ---------------------------------------------------------------- утилиты

    private fun OutputStream.writeLsbShort(value: Int) {
        write(value and 0xFF)
        write((value shr 8) and 0xFF)
    }

    private class Palette(val colors: IntArray, val transparentIndex: Int)

    private companion object {
        const val MIN_CODE_SIZE = 8
        const val PALETTE_COLORS = 255
        const val MAX_UNIQUE_COLORS = 8192
        const val ALPHA_THRESHOLD = 128
        const val LUT_SIZE = 32 * 32 * 32

        /** Максимальный размер подблока LZW-данных в GIF. */
        const val MAX_BLOCK_SIZE = 255

        /** Метка «ячейка таблицы ещё не заполнена». -1 не встречается среди индексов палитры. */
        const val EMPTY = -1

        /** Disposal method 2 (restore to background) занимает биты 2..4 байта packed. */
        const val DISPOSAL_RESTORE_BACKGROUND = 0b0000_1000

        /** Флаг «использовать индекс прозрачного цвета» — самый младший бит packed. */
        const val TRANSPARENT_FLAG = 0b0000_0001
    }
}
