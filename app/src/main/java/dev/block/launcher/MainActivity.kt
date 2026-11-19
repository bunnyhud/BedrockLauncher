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
import android.view.Gravity
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
    
    // Цвета в стиле макета
    private val green = Color.rgb(61, 119, 35)
    private val darkBg = Color.rgb(25, 25, 28)
    private val panelBg = Color.rgb(43, 43, 46)
    private val btnGray = Color.rgb(66, 66, 69)

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun panel(color: Int) = GradientDrawable().apply { setColor(color); setStroke(dp(2), Color.rgb(16,16,16)); cornerRadius = dp(2).toFloat() }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    
    private fun label(text: String, size: Float = 16f, bold: Boolean = false) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(Color.rgb(232,232,226))
        setPadding(dp(8),dp(10),dp(8),dp(10))
        if (bold) typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    }
    
    private fun button(text: String, primary: Boolean = false, action: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        setTextColor(Color.WHITE)
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        background = panel(if(primary) green else btnGray)
        layoutParams = LinearLayout.LayoutParams(-1, dp(54)).apply { setMargins(dp(3),dp(4),dp(3),dp(4)) }
        setOnClickListener { action() }
    }

    override fun onCreate(state: Bundle?) { 
        super.onCreate(state)
        tab = state?.getString("tab") ?: "ИГРАТЬ"
        layout() 
    }
    
    override fun onSaveInstanceState(out: Bundle) { 
        out.putString("tab", tab)
        super.onSaveInstanceState(out) 
    }

    private fun layout() {
        val base = column().apply { setBackgroundColor(darkBg); setPadding(dp(12),0,dp(12),0) }
        base.setOnApplyWindowInsetsListener { v, i -> v.setPadding(dp(12),i.systemWindowInsetTop,dp(12),i.systemWindowInsetBottom); i }
        
        base.addView(label("BLOCK / BEDROCK LAUNCHER", 24f, true).apply { typeface = Typeface.MONOSPACE })
        
        // Горизонтальное меню вкладок
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val scrollNav = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        
        listOf("ИГРАТЬ", "ВЕРСИИ", "МОДЫ", "РЕСУРСЫ", "МИРЫ", "СКИНЫ", "НАСТРОЙКИ").forEach { name ->
            val isActive = (tab == name)
            val btn = Button(this).apply {
                text = name
                isAllCaps = false
                textSize = 12f
                typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
                setTextColor(if (isActive) Color.WHITE else Color.LTGRAY)
                background = panel(if (isActive) green else btnGray)
                layoutParams = LinearLayout.LayoutParams(dp(90), dp(48)).apply { setMargins(dp(2), 0, dp(2), 0) }
                setOnClickListener { 
                    if (tab != name) { tab = name; layout() } 
                }
            }
            nav.addView(btn)
        }
        scrollNav.addView(nav)
        base.addView(scrollNav)

        body = column().apply { setPadding(0,dp(16),0,dp(16)) }
        base.addView(ScrollView(this).apply { addView(body) }, LinearLayout.LayoutParams(-1,0,1f))
        setContentView(base); base.requestApplyInsets()
        
        when(tab) {
            "ИГРАТЬ" -> home()
            "ВЕРСИИ" -> versionsTab()
            "МОДЫ" -> modsTab()
            "РЕСУРСЫ" -> resourcesTab()
            "МИРЫ" -> worldsTab()
            "СКИНЫ" -> skinsTab()
            "НАСТРОЙКИ" -> settingsTab()
        }
    }

    private fun text(s: String) { body.addView(label(s)) }
    private fun act(s: String, primary: Boolean = false, f: () -> Unit) { body.addView(button(s,primary,f)) }
    private fun toast(s: String) { Toast.makeText(this,s,Toast.LENGTH_LONG).show() }
    private fun version(): String? = try { packageManager.getPackageInfo(game,0).versionName } catch (_: Exception) { null }

    private fun home() {
        text("MINECRAFT BEDROCK")
        text(version()?.let { "Установлена версия $it" } ?: "Minecraft не установлен в этом профиле Android")
        act("▶  ИГРАТЬ", true) {
            val launch = packageManager.getLaunchIntentForPackage(game)
            if(launch == null) toast("Сначала установи Minecraft") else try { startActivity(launch) } catch (_: Exception) { toast("Не удалось запустить Minecraft") }
        }
        act("Minecraft в Google Play") { openUrl("https://play.google.com/store/apps/details?id=$game") }
        text("Лаунчер запускает установленную игру. Миры и лицензия управляются самим Minecraft.")
    }

    private fun versionsTab() {
        text("ВЕРСИИ MINECRAFT")
        text("Выберите версию для скачивания или запуска.")
        
        // Пример карточки версии
        val card = column().apply { background = panel(panelBg); setPadding(dp(8), dp(8), dp(8), dp(8)); setMargins(0, dp(4), 0, dp(4)) }
        card.addView(label("Bedrock 1.20.80", 18f, true))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(Button(this).apply { 
            text = "Скачать"; isAllCaps = false; setTextColor(Color.WHITE); background = panel(green)
            layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f).apply { setMargins(0, 0, dp(4), 0) }
            setOnClickListener { openUrl("https://www.google.com/search?q=download+minecraft+bedrock+1.20.80+apk") }
        })
        row.addView(Button(this).apply { 
            text = "Запустить"; isAllCaps = false; setTextColor(Color.WHITE); background = panel(btnGray)
            layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f).apply { setMargins(dp(4), 0, 0, 0) }
            setOnClickListener { 
                val intent = packageManager.getLaunchIntentForPackage(game)
                if(intent != null) startActivity(intent) else toast("Эта версия не установлена")
            }
        })
        card.addView(row)
        body.addView(card)
        
        text("Примечание: Лаунчер не может скачивать и устанавливать APK автоматически из-за ограничений Android. Кнопка 'Скачать' откроет браузер.")
    }

    private fun modsTab() {
        text("МОДЫ И ДОПОЛНЕНИЯ")
        text("Скачивайте моды с проверенных сайтов и импортируйте их.")
        
        act("Открыть CurseForge (Minecraft Bedrock)", true) { openUrl("https://www.curseforge.com/minecraft-bedrock") }
        act("Открыть Modrinth (Bedrock)", true) { openUrl("https://modrinth.com/mods?g=categories:bedrock") }
        
        text("Если у вас есть файл .mcpack или .mcaddon, используйте кнопку ниже:")
        act("＋  Импортировать мод", false) {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" }, 100)
        }
    }

    private fun resourcesTab() {
        text("РЕСУРС-ПАКИ")
        text("Импортируйте файлы .mcpack (ресурсы) для изменения текстур.")
        act("＋  Импортировать ресурсы", true) {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" }, 100)
        }
    }

    private fun worldsTab() {
        text("МИРЫ")
        text("Импортируйте миры .mcworld. Лаунчер передаст файл игре.")
        act("＋  Импортировать мир", true) {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" }, 100)
        }
    }

    private fun skinsTab() {
        text("СКИНЫ")
        text("Импортируйте PNG-скины. В игре перейдите в Гардероб -> Классические скины -> Импорт.")
        act("Скачать скины (Planet Minecraft)", true) { openUrl("https://www.planetminecraft.com/skins/tag/minecraft/") }
    }

    private fun settingsTab() {
        text("НАСТРОЙКИ")
        text("Minecraft: " + (version() ?: "не найден") + "\nЛаунчер: 2.0")
        act("Настройки приложения Minecraft") {
            try { startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$game"))) } catch (_: Exception) { toast("Настройки недоступны") }
        }
        act("Очистить временные файлы") {
            File(cacheDir, "imports").listFiles()?.forEach { it.delete() }
            toast("Кэш очищен")
        }
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
                require(ext in listOf("mcpack","mcaddon","mcworld","png")) { "Нужен файл .mcpack, .mcaddon, .mcworld или .png" }
                val dir = File(cacheDir,"imports").apply { mkdirs() }
                dir.listFiles()?.filter { System.currentTimeMillis()-it.lastModified() > 86400000L }?.forEach { it.delete() }
                val dest = File(dir,"import-${UUID.randomUUID()}.$ext"); temp = dest
                contentResolver.openInputStream(source).use { input ->
                    requireNotNull(input) { "Не удалось прочитать файл" }
                    dest.outputStream().use { output ->
                        val buffer = ByteArray(65536); var total = 0L
                        while(true) { val n = input.read(buffer); if(n < 0) break; total += n; require(total <= 512L*1024*1024) { "Максимум — 512 МБ" }; output.write(buffer,0,n) }
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
                    } catch (_: Exception) { toast("Minecraft не установлен или эта версия не поддерживает импорт.") }
                }
            } catch(e: Exception) { temp?.delete(); runOnUiThread { busy = false; toast(e.message ?: "Ошибка импорта") } }
        }.start()
    }

    private fun openUrl(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) { toast("Не удалось открыть ссылку") }
    }
}
