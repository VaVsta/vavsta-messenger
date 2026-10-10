/*
 * VaVsta Messenger (форк Element X): свои (импортированные) стикеры.
 */

package io.element.android.features.messages.impl.sticker

import android.content.Context
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.di.annotations.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File

/**
 * VaVsta: стикер, импортированный пользователем (например, из экспорта Telegram).
 *
 * Лежит локально на устройстве: общего пака в комнате пока нет. Поле [mediaUrl] заполняется
 * после первой отправки — дальше отправляем без повторной загрузки.
 */
data class MySticker(
    val id: String,
    val fileName: String,
    val localPath: String,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    /** `true`, если файл пришёл из `.tgs`: у нас остался только статичный кадр. */
    val fromTgs: Boolean = false,
    /** mxc-ссылка после первой успешной отправки, `null` до неё. */
    val mediaUrl: String? = null,
)

/**
 * VaVsta: хранилище импортированных стикеров. Файлы лежат в `filesDir/vavsta-stickers/`,
 * список — в SharedPreferences `vavsta_my_stickers` (JSON собран руками, чтобы не подключать
 * плагин сериализации ради одной строки).
 */
interface MyStickerStore {
    fun list(): List<MySticker>
    fun add(sticker: MySticker)
    fun remove(id: String)

    /** Помнить mxc-ссылку, чтобы второй раз не грузить тот же файл. */
    fun markUploaded(id: String, mediaUrl: String)

    /** Каталог под импортированные файлы (создаётся при первом обращении). */
    fun importDirectory(): File
}

@ContributesBinding(AppScope::class)
class DefaultMyStickerStore(
    @ApplicationContext private val context: Context,
) : MyStickerStore {
    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    override fun list(): List<MySticker> = runCatching {
        val raw = prefs.getString(KEY_LIST, null) ?: return emptyList()
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            MySticker(
                id = item.optString("id"),
                fileName = item.optString("fileName"),
                localPath = item.optString("localPath"),
                mimeType = item.optString("mimeType"),
                width = item.optInt("width"),
                height = item.optInt("height"),
                sizeBytes = item.optLong("sizeBytes"),
                fromTgs = item.optBoolean("fromTgs"),
                mediaUrl = item.optString("mediaUrl").takeIf { it.isNotEmpty() && it != "null" },
            )
        }.filter { it.id.isNotEmpty() && File(it.localPath).exists() }
    }.onFailure {
        Timber.e(it, "MyStickerStore: не прочитали список стикеров")
    }.getOrDefault(emptyList())

    override fun add(sticker: MySticker) {
        persist(list().filterNot { it.id == sticker.id } + sticker)
        Timber.i("MyStickerStore: добавили %s (%s, %d Б)", sticker.fileName, sticker.mimeType, sticker.sizeBytes)
    }

    override fun remove(id: String) {
        val current = list()
        current.firstOrNull { it.id == id }?.let { runCatching { File(it.localPath).delete() } }
        persist(current.filterNot { it.id == id })
        Timber.i("MyStickerStore: удалили %s", id)
    }

    /** Помнить mxc-ссылку, чтобы второй раз не грузить тот же файл. */
    override fun markUploaded(id: String, mediaUrl: String) {
        persist(list().map { if (it.id == id) it.copy(mediaUrl = mediaUrl) else it })
    }

    /** Каталог под импортированные файлы (создаётся при первом обращении). */
    override fun importDirectory(): File = File(context.filesDir, DIR_NAME).apply { mkdirs() }

    private fun persist(stickers: List<MySticker>) {
        val array = JSONArray()
        stickers.forEach { sticker ->
            array.put(
                JSONObject().apply {
                    put("id", sticker.id)
                    put("fileName", sticker.fileName)
                    put("localPath", sticker.localPath)
                    put("mimeType", sticker.mimeType)
                    put("width", sticker.width)
                    put("height", sticker.height)
                    put("sizeBytes", sticker.sizeBytes)
                    put("fromTgs", sticker.fromTgs)
                    put("mediaUrl", sticker.mediaUrl ?: "")
                }
            )
        }
        prefs.edit().putString(KEY_LIST, array.toString()).apply()
    }

    private companion object {
        const val PREFS_NAME = "vavsta_my_stickers"
        const val KEY_LIST = "list"
        const val DIR_NAME = "vavsta-stickers"
    }
}
