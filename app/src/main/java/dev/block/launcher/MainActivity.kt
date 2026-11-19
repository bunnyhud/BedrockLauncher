package dev.block.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class MainActivity : Activity() {
    private val game = "com.mojang.minecraftpe"
    private val prefs by lazy { getSharedPreferences("launcher", MODE_PRIVATE) }
    private lateinit var body: LinearLayout
    private var tab = "ИГРАТЬ"
    private var busy = false
    private val green = Color.rgb(61, 119, 35)

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun panel(color: Int) = GradientDrawable().apply { setColor(color); setStroke(dp(2), Color.rgb(16,16,16)); cornerRadius = dp(2).toFloat() }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun label(text: String, size: Float = 16f) = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(Color.rgb(232,232,226)); setPadding(dp(8),dp(10),dp(8),dp(10))
    }
    private fun button(text: String, primary: Boolean = false, action: () -> Unit) = Button(this).apply {
        this.text = text; isAllCaps = false; setTextColor(Color.WHITE); typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        background = panel(if(primary) green else Color.rgb(66,66,69))
        layoutParams = LinearLayout.LayoutParams(-1, dp(54)).apply { setMargins(dp(3),dp(4),dp(3),dp(4)) }
        setOnClickListener { action() }
    }

    override fun onCreate(state: Bundle?) { super.onCreate(state); tab = state?.getString("tab") ?: "ИГРАТЬ"; layout() }
    override fun onSaveInstanceState(out: Bundle) { out.putString("tab", tab); super.onSaveInstanceState(out) }

    private fun layout() {
        val base = column().apply { setBackgroundColor(Color.rgb(25,25,28)); setPadding(dp(12),0,dp(12),0) }
        base.setOnApplyWindowInsetsListener { v, i -> v.setPadding(dp(12),i.systemWindowInsetTop,dp(12),i.systemWindowInsetBottom); i }
        base.addView(label("BLOCK / BEDROCK LAUNCHER", 24f).apply { typeface = Typeface.MONOSPACE })
        val nav = LinearLayout(this)
        listOf("ИГРАТЬ","ПАКЕТЫ","СЕРВЕРЫ","НАСТРОЙКИ").forEach { name ->
            nav.addView(button(name, tab == name) { tab = name; layout() }.apply { layoutParams = LinearLayout.LayoutParams(dp(152),dp(52)) })
        }
        base.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(nav) })
        body = column().apply { setPadding(0,dp(12),0,dp(12)) }
        base.addView(ScrollView(this).apply { addView(body) }, LinearLayout.LayoutParams(-1,0,1f))
        setContentView(base); base.requestApplyInsets()
        when(tab) { "ИГРАТЬ" -> home(); "ПАКЕТЫ" -> packs(); "СЕРВЕРЫ" -> servers(); else -> settings() }
    }

    private fun text(s: String) { body.addView(label(s)) }
    private fun act(s: String, primary: Boolean = false, f: () -> Unit) { body.addView(button(s,primary,f)) }
    private fun version(): String? = try { packageManager.getPackageInfo(game,0).versionName } catch (_: Exception) { null }
    private fun toast(s: String) { Toast.makeText(this,s,Toast.LENGTH_LONG).show() }

    private fun home() {
        text("MINECRAFT BEDROCK")
        text(version()?.let { "Установлена версия $it" } ?: "Minecraft не установлен в этом профиле Android")
        act("▶  ИГРАТЬ",true) {
            val launch = packageManager.getLaunchIntentForPackage(game)
            if(launch == null) toast("Сначала установи Minecraft из официального магазина") else try { startActivity(launch) } catch (_: Exception) { toast("Не удалось запустить Minecraft") }
        }
        act("Minecraft в Google Play") { open(Uri.parse("https://play.google.com/store/apps/details?id=$game")) }
        text("Лаунчер запускает установленную игру. Миры, лицензия и вход Microsoft управляются самим Minecraft.")
        text("Дизайн вдохновлён твоим макетом: тёмные панели, квадратные кнопки и зелёные акценты. Это независимое приложение, не продукт Mojang или Microsoft.")
    }

    private fun packs() {
        text("МОДЫ • РЕСУРСЫ • МИРЫ")
        text("Выбери .mcpack, .mcaddon или .mcworld. Лаунчер передаст файл Minecraft; результат импорта проверяй в игре. Java-моды .jar не поддерживаются.")
        act("＋  Импортировать файл",true) {
            if(!busy) startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" },100)
        }
        text("Скины: открой в Minecraft «Гардероб» → классические скины → импорт и выбери PNG. Лаунчер не меняет защищённые файлы игры.")
    }

    @Deprecated("Platform activity result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode != 100 || resultCode != RESULT_OK) return
        val source = data?.data ?: return
        if(busy) return
        busy = true
        toast("Подготовка файла…")
        Thread {
            var temp: File? = null
            try {
                val name = contentResolver.query(source,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use { if(it.moveToFirst()) it.getString(0) else null } ?: ""
                val ext = name.substringAfterLast('.',"").lowercase()
                require(ext in listOf("mcpack","mcaddon","mcworld")) { "Нужен файл .mcpack, .mcaddon или .mcworld" }
                val dir = File(cacheDir,"imports").apply { mkdirs() }
                dir.listFiles()?.filter { System.currentTimeMillis()-it.lastModified() > 86400000L }?.forEach { it.delete() }
                val dest = File(dir,"import-${UUID.randomUUID()}.$ext"); temp = dest
                contentResolver.openInputStream(source).use { input ->
                    requireNotNull(input) { "Не удалось прочитать файл" }
                    dest.outputStream().use { output ->
                        val buffer = ByteArray(65536); var total = 0L
                        while(true) { val n = input.read(buffer); if(n < 0) break; total += n; require(total <= 512L*1024*1024) { "Максимальный размер — 512 МБ" }; output.write(buffer,0,n) }
                    }
                }
                val uri = Uri.parse("content://$packageName.imports/${dest.name}")
                runOnUiThread {
                    busy = false
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri,"application/octet-stream"); setPackage(game)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); clipData = ClipData.newRawUri("Import",uri)
                        })
                    } catch (_: Exception) { toast("Minecraft не установлен или эта версия не поддерживает такой импорт. Попробуй открыть файл через системный файловый менеджер.") }
                }
            } catch(e: Exception) { temp?.delete(); runOnUiThread { busy = false; toast(e.message ?: "Ошибка импорта") } }
        }.start()
    }

    private fun saved(): JSONArray = try { JSONArray(prefs.getString("servers","[]")) } catch (_: Exception) { JSONArray() }
    private fun store(a: JSONArray) { prefs.edit().putString("servers",a.toString()).apply() }

    private fun servers() {
        text("СЕРВЕРЫ BEDROCK")
        act("＋  Добавить сервер",true) { serverDialog() }
        text("Кнопка передаёт адрес в Minecraft через minecraft://. Поддержка зависит от версии игры; подключение выполняется в игре.")
        val a = saved()
        if(a.length() == 0) text("Пока пусто — добавь свой сервер.")
        for(i in 0 until a.length()) {
            val s = a.getJSONObject(i)
            val card = column().apply { background = panel(Color.rgb(43,43,46)); setPadding(dp(8),dp(8),dp(8),dp(8)) }
            card.addView(label(s.getString("name"),20f))
            card.addView(label("${s.getString("host")}:${s.getInt("port")}"))
            card.addView(button("Открыть в Minecraft",true) {
                val payload = "${s.getString("name")}|${s.getString("host")}:${s.getInt("port")}"
                open(Uri.parse("minecraft://?addExternalServer=${Uri.encode(payload)}"),true)
            })
            card.addView(button("Удалить") {
                AlertDialog.Builder(this).setMessage("Удалить сервер из списка?").setPositiveButton("Удалить") { _,_ -> a.remove(i); store(a); layout() }.setNegativeButton("Отмена",null).show()
            })
            body.addView(card)
        }
    }

    private fun serverDialog() {
        val form = column().apply { setPadding(dp(20),dp(8),dp(20),dp(8)) }
        fun field(hint: String) = EditText(this).apply { this.hint = hint; isSingleLine = true; form.addView(this) }
        val name = field("Название"); val host = field("Адрес: play.example.org"); val port = field("Порт").apply { inputType = 2; setText("19132") }
        val dialog = AlertDialog.Builder(this).setTitle("Новый сервер").setView(form).setPositiveButton("Сохранить",null).setNegativeButton("Отмена",null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val n = name.text.toString().trim(); val h = host.text.toString().trim(); val p = port.text.toString().toIntOrNull()
                if(n.isEmpty() || n.length > 64 || n.contains('|') || !h.matches(Regex("[a-zA-Z0-9.-]{1,253}")) || p == null || p !in 1..65535) { toast("Проверь название, адрес IPv4/домен и порт 1–65535"); return@setOnClickListener }
                val a = saved(); a.put(JSONObject().put("name",n).put("host",h).put("port",p)); store(a); dialog.dismiss(); layout()
            }
        }; dialog.show()
    }

    private fun settings() {
        text("НАСТРОЙКИ И ВЕРСИИ")
        text("Minecraft: " + (version() ?: "не найден") + "\nЛаунчер: 1.0")
        text("На Android сторонний лаунчер не может менять RAM, графику и аккаунт Minecraft. Эти параметры настраиваются в игре. Переключение версий здесь не поддерживается.")
        act("Настройки приложения Minecraft") { try { startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:$game"))) } catch (_: Exception) { toast("Настройки недоступны") } }
        act("Очистить копии импортированных файлов") {
            if(busy) toast("Дождись завершения копирования") else AlertDialog.Builder(this).setMessage("Убедись, что Minecraft закончил импорт. Удалить временные копии? Исходные файлы сохранятся.").setPositiveButton("Очистить") { _,_ -> File(cacheDir,"imports").listFiles()?.forEach { it.delete() }; toast("Кэш очищен") }.setNegativeButton("Отмена",null).show()
        }
        text("Приватность: нет аналитики, рекламы и сбора паролей. Список серверов хранится локально. Для запуска игры нужен легально установленный Minecraft.")
    }

    private fun open(uri: Uri, minecraft: Boolean = false) {
        try { startActivity(Intent(Intent.ACTION_VIEW,uri).apply { if(minecraft) setPackage(game) }) } catch (_: Exception) { toast("Нет приложения, которое может открыть эту ссылку") }
    }
}
