/*
 * VaVsta Messenger (форк Element X): рендер анимированных картинок (GIF) в таймлайне.
 */

package io.element.android.features.messages.impl.timeline.components.event

import android.graphics.Movie
import android.graphics.drawable.Animatable
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import coil3.DrawableImage
import coil3.compose.AsyncImagePainter
import kotlin.math.roundToInt

/**
 * Рисует результат Coil-паинтера и, если это анимированный Drawable, **запускает анимацию**.
 *
 * Зачем это нужно (цепочка багов, все проверены на устройстве):
 *
 * 1. **Coil 3.5 вообще не анимирует DrawableImage.** `AsyncImagePainter.onDraw` →
 *    `DrawableImage.draw(canvas)` → `drawable.draw(canvas)`, при этом **ни `Drawable.Callback`
 *    не устанавливается, ни `Animatable.start()` не вызывается** (проверено байткодом
 *    `coil-compose-core-3.5.0` и `coil-core-3.5.0`). `MovieDrawable.start()` зовёт
 *    `invalidateSelf()`, но без callback это вызов в пустоту: GIF навсегда на первом кадре.
 *
 * 2. **Своего рендера с `start()` тоже мало.** Мы ставили свой `Drawable.Callback` и гоняли
 *    перерисовку по кадрам Compose — перерисовка шла (60 раз/с), но картинка не менялась:
 *    `MovieDrawable.updateFrameTime()` в связке с нашим способом отрисовки не переключает кадры,
 *    хотя **сам `android.graphics.Movie` кадры переключает** (проверено пробой: у нашего файла
 *    Movie видит 5 разных кадров из 6, у эталонного — 10 из 25).
 *
 * Поэтому для MovieDrawable мы достаём из него внутренний `Movie` и рисуем **сами**:
 * `Movie.setTime()` + `Movie.draw()` работают надёжно, проверено пробой.
 *
 * Грабля первой попытки: `remember(painter)` для вытаскивания Drawable кэширует `null` навсегда —
 * painter один и тот же, меняется его `state`. Нужен `collectAsState`.
 */
@Composable
internal fun AnimatedImagePainter(
    painter: AsyncImagePainter,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val state by painter.state.collectAsState()
    val drawable = remember(state) {
        (state as? AsyncImagePainter.State.Success)
            ?.let { (it.result.image as? DrawableImage)?.drawable }
    }

    // Внутренний Movie достаём рефлексией: coil-gif не подключён к этому модулю как api,
    // а тип назвать нельзя. Работает и с MovieDrawable, и с любым другой обёрткой с полем `movie`.
    val movie = remember(drawable) {
        drawable?.let { d ->
            runCatching {
                var type: Class<*>? = d.javaClass
                while (type != null) {
                    val field = runCatching { type!!.getDeclaredField("movie") }.getOrNull()
                    if (field != null) {
                        field.isAccessible = true
                        return@runCatching field.get(d) as? Movie
                    }
                    type = type.superclass
                }
                null
            }.getOrNull()
        }
    }
    val animatable = drawable as? Animatable

    // Читается в draw-скоупе: изменение заставляет Compose перерисовать кадр
    var frame by remember { mutableIntStateOf(0) }

    // Остальное держим НЕ в Compose state, а в мутируемых контейнерах:
    // всё это перечитывается из draw-лямбды, а писать state из draw-скоупа нельзя
    // (это ломает снапшоты и плодит лишние перерисовки).
    val startTime = remember { longArrayOf(0L) }
    val buffer = remember { arrayOfNulls<android.graphics.Bitmap>(1) }
    val bufferCanvas = remember { arrayOfNulls<android.graphics.Canvas>(1) }

    // Ссылки на актуальные значения: цикл запускается ОДИН раз на каждую сменившуюся
    // картинку, иначе composable, который пересоздаётся каждый кадр (список чатов),
    // перезапускает эффект 2000+ раз в минуту и анимация не успевает сделать даже одного шага.
    val currentDrawable by rememberUpdatedState(drawable)
    val currentMovie by rememberUpdatedState(movie)
    val currentAnimatable by rememberUpdatedState(animatable)

    // Ключ — сама картинка: смена стикера перезапускает цикл, а рекомпозиции таймлайна нет.
    // Для НЕ анимированных картинок (обычные PNG) эффект сразу выходит и не жжёт батарею
    // на пустом `while (true)` с 60 итерациями в секунду.
    LaunchedEffect(drawable) {
        if (currentMovie == null) return@LaunchedEffect
        while (true) {
            withFrameNanos { }
            val d = currentDrawable
            val m = currentMovie
            if (d == null) continue
            val a = currentAnimatable
            if (a != null && !a.isRunning) {
                runCatching {
                    d.javaClass.getMethod("setRepeatCount", Int::class.javaPrimitiveType).invoke(d, 0)
                }
                a.start()
                startTime[0] = SystemClock.uptimeMillis()
            }
            if (m != null && a?.isRunning == true) frame++
        }
    }

    DisposableEffect(drawable) {
        onDispose {
            animatable?.stop()
            buffer[0]?.recycle()
            buffer[0] = null
            bufferCanvas[0] = null
        }
    }

    if (drawable == null) {
        // Обычная (не анимированная) картинка — штатный путь
        Image(
            painter = painter,
            contentDescription = null,
            modifier = modifier,
            contentScale = contentScale,
        )
        return
    }

    Canvas(modifier = modifier) {
        frame // читаем, чтобы Compose инвалидировал этот draw
        val canvasWidth = size.width
        val canvasHeight = size.height
        if (canvasWidth <= 0f || canvasHeight <= 0f) return@Canvas

        if (movie != null) {
            val duration = movie.duration()
            if (duration <= 0) return@Canvas
            val elapsed = ((SystemClock.uptimeMillis() - startTime[0]) % duration).toInt()
            movie.setTime(elapsed)
            // ВАЖНО: Movie.draw() рисует только на программный канва. На hardware-канве Compose
            // (то, что даёт DrawScope) кадр молча не рисуется — проверено пробой на устройстве:
            // в offscreen Bitmap Movie отдаёт все кадры, прямо в Compose-канве — ноль изменений.
            val movieWidth = movie.width().coerceAtLeast(1)
            val movieHeight = movie.height().coerceAtLeast(1)
            val existing = buffer[0]
            if (existing == null || existing.isRecycled ||
                existing.width != movieWidth || existing.height != movieHeight
            ) {
                existing?.recycle()
                val created = android.graphics.Bitmap.createBitmap(
                    movieWidth,
                    movieHeight,
                    android.graphics.Bitmap.Config.ARGB_8888,
                )
                buffer[0] = created
                bufferCanvas[0] = android.graphics.Canvas(created)
            }
            val target = buffer[0] ?: return@Canvas
            val targetCanvas = bufferCanvas[0] ?: return@Canvas
            target.eraseColor(0)
            movie.draw(targetCanvas, 0f, 0f)

            val scale = when (contentScale) {
                ContentScale.Fit -> minOf(canvasWidth / movieWidth, canvasHeight / movieHeight)
                ContentScale.Crop -> maxOf(canvasWidth / movieWidth, canvasHeight / movieHeight)
                else -> 1f
            }
            val targetWidth = (movieWidth * scale).toInt()
            val targetHeight = (movieHeight * scale).toInt()
            val offsetX = ((canvasWidth - targetWidth) / 2f).roundToInt()
            val offsetY = ((canvasHeight - targetHeight) / 2f).roundToInt()
            drawImage(
                image = target.asImageBitmap(),
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(movieWidth, movieHeight),
                dstOffset = IntOffset(offsetX, offsetY),
                dstSize = IntSize(targetWidth, targetHeight),
            )
            return@Canvas
        }

        val drawableWidth = drawable.intrinsicWidth.coerceAtLeast(1)
        val drawableHeight = drawable.intrinsicHeight.coerceAtLeast(1)

        val scale = when (contentScale) {
            ContentScale.Fit -> minOf(canvasWidth / drawableWidth, canvasHeight / drawableHeight)
            ContentScale.Crop -> maxOf(canvasWidth / drawableWidth, canvasHeight / drawableHeight)
            else -> 1f
        }
        val scaledWidth = drawableWidth * scale
        val scaledHeight = drawableHeight * scale
        val left = (canvasWidth - scaledWidth) / 2f
        val top = (canvasHeight - scaledHeight) / 2f

        drawable.setBounds(
            left.toInt(),
            top.toInt(),
            (left + scaledWidth).toInt().coerceAtMost(canvasWidth.toInt()),
            (top + scaledHeight).toInt().coerceAtMost(canvasHeight.toInt()),
        )

        drawIntoCanvas { canvas ->
            drawable.draw(canvas.nativeCanvas)
        }
    }
}
