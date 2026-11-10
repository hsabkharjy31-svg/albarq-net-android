package net.albarq.hotspotsimulator

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var files: HotspotFiles
    private lateinit var folderStatus: TextView
    private lateinit var scanStatus: TextView
    private lateinit var previewButton: TextView
    private val fileExecutor = Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences("preview", MODE_PRIVATE) }
    private val navy = Color.rgb(7, 19, 41)
    private val surface = Color.rgb(17, 34, 64)
    private val cyan = Color.rgb(56, 213, 255)
    private val muted = Color.rgb(164, 181, 210)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = navy
        window.navigationBarColor = navy
        window.decorView.setBackgroundColor(navy)

        files = HotspotFiles(this, prefs.getString("tree", null)?.let { Uri.parse(it) })
        buildDashboard()

        if (files.tree != null) {
            folderStatus.text = "تم حفظ مجلد القالب سابقاً"
            scanStatus.text = "جارٍ التحقق من ملفات القالب..."
            scanFolder(openWhenReady = false)
        }
    }

    private fun buildDashboard() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(26))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(navy, Color.rgb(10, 28, 57), navy)
            )
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(page)
        }

        val brandRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        brandRow.addView(ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }, LinearLayout.LayoutParams(dp(58), dp(58)))
        val brandText = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        brandText.addView(label("AL-BARQ NET", 19f, Color.WHITE, true))
        brandText.addView(label("HOTSPOT STUDIO  •  ANDROID", 10f, cyan, true))
        brandRow.addView(brandText, LinearLayout.LayoutParams(0, -2, 1f))
        page.addView(brandRow, bottomParams(24))

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(22))
            background = rounded(
                intArrayOf(Color.rgb(30, 77, 139), Color.rgb(16, 43, 84)),
                dp(22)
            )
        }
        hero.addView(label("معاينة قوالب MikroTik", 24f, Color.WHITE, true), bottomParams(8))
        hero.addView(label(
            "افتح صفحات الهوتسبوت كصفحات ويب تفاعلية، مع تحميل ملفات CSS وJavaScript والصور المحلية.",
            14f, Color.rgb(220, 232, 249), false
        ))
        page.addView(hero, bottomParams(18))

        val folderCard = card()
        folderCard.addView(label("مجلد ملفات الهوتسبوت", 16f, Color.WHITE, true), bottomParams(8))
        folderStatus = label("لم يتم اختيار مجلد بعد", 13f, muted, false)
        folderCard.addView(folderStatus, bottomParams(10))
        folderCard.addView(actionButton("اختيار مجلد القالب", true) { chooseHotspotFolder() })
        page.addView(folderCard, bottomParams(14))

        val previewCard = card()
        previewCard.addView(label("نافذة المعاينة", 16f, Color.WHITE, true), bottomParams(8))
        previewCard.addView(label(
            "ستُفتح المعاينة في شاشة مستقلة كاملة، وليست داخل لوحة التحكم.",
            13f, muted, false
        ), bottomParams(14))
        previewButton = actionButton("فتح صفحة الدخول  ↗", true) { openPreview() }
        previewButton.isEnabled = false
        previewButton.alpha = 0.45f
        previewCard.addView(previewButton, bottomParams(9))
        previewCard.addView(actionButton("إعادة فحص الملفات", false) {
            scanFolder(openWhenReady = false)
        })
        page.addView(previewCard, bottomParams(14))

        val statusCard = card()
        statusCard.addView(label("حالة الفحص", 15f, Color.WHITE, true), bottomParams(7))
        scanStatus = label("اختر مجلد القالب لبدء الفحص.", 12f, muted, false)
        statusCard.addView(scanStatus)
        page.addView(statusCard, bottomParams(14))

        page.addView(actionButton("عرض التشخيصات", false) { showDiagnostics() }, bottomParams(16))
        page.addView(label(
            "المحاكي يعرض صفحات القالب محلياً. لا ينفّذ أوامر RouterOS ولا ينشئ جلسة اتصال حقيقية.",
            11f, muted, false
        ))
        setContentView(scroll)
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = rounded(surface, dp(18))
    }

    private fun label(textValue: String, size: Float, color: Int, bold: Boolean) =
        TextView(this).apply {
            text = textValue
            textSize = size
            setTextColor(color)
            typeface = if (bold) Typeface.create("sans-serif", Typeface.BOLD)
                else Typeface.create("sans-serif", Typeface.NORMAL)
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            textDirection = TextView.TEXT_DIRECTION_FIRST_STRONG
            setLineSpacing(dp(3).toFloat(), 1f)
        }

    private fun actionButton(textValue: String, primary: Boolean, action: () -> Unit) =
        TextView(this).apply {
            text = textValue
            textSize = 14f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(if (primary) navy else Color.WHITE)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = rounded(
                if (primary) intArrayOf(cyan, Color.rgb(106, 231, 255))
                else intArrayOf(Color.rgb(36, 57, 91), Color.rgb(27, 44, 73)),
                dp(13)
            )
            isClickable = true
            isFocusable = true
            setOnClickListener { if (isEnabled) action() }
        }

    private fun rounded(color: Int, radius: Int) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius.toFloat()
        }

    private fun rounded(colors: IntArray, radius: Int) =
        GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors).apply {
            cornerRadius = radius.toFloat()
        }

    private fun bottomParams(bottom: Int) =
        LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(bottom) }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun chooseHotspotFolder() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }
        try {
            startActivityForResult(intent, REQUEST_HOTSPOT_FOLDER)
        } catch (e: Exception) {
            scanStatus.text = "تعذر فتح منتقي المجلدات: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    @Deprecated("Using the platform document picker for broad SAF provider compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_HOTSPOT_FOLDER || resultCode != RESULT_OK) return
        val uri = data?.data ?: run {
            scanStatus.text = "لم يُرجع منتقي الملفات عنوان المجلد."
            return
        }

        var permissionWarning = ""
        val readGrant = data.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION
        if (readGrant != 0) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                prefs.edit().putString("tree", uri.toString()).apply()
            } catch (e: SecurityException) {
                permissionWarning = "إذن القراءة مؤقت؛ قد تحتاج إلى اختيار المجلد مجدداً بعد إغلاق التطبيق."
                prefs.edit().remove("tree").apply()
            } catch (e: IllegalArgumentException) {
                permissionWarning = "مزود الملفات لا يدعم حفظ الإذن الدائم؛ الإذن الحالي مؤقت."
                prefs.edit().remove("tree").apply()
            }
        } else {
            permissionWarning = "لم يمنح مزود الملفات إذن قراءة واضحاً."
            prefs.edit().remove("tree").apply()
        }

        files.tree = uri
        folderStatus.text = "تم تحديد مجلد القالب"
        scanFolder(openWhenReady = true, permissionWarning = permissionWarning)
    }

    private fun scanFolder(openWhenReady: Boolean, permissionWarning: String = "") {
        if (files.tree == null) {
            scanStatus.text = "يرجى اختيار مجلد الهوتسبوت أولاً."
            previewButton.isEnabled = false
            previewButton.alpha = 0.45f
            return
        }
        scanStatus.text = "جارٍ فحص ملفات HTML وCSS وJavaScript والصور..."
        fileExecutor.execute {
            val paths = try { files.index() } catch (e: Exception) { emptyList() }
            val hasLogin = paths.any { it.substringAfterLast('/').equals("login.html", true) }
            val count = paths.size
            val warnings = files.scanWarnings.toList()
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                previewButton.isEnabled = hasLogin
                previewButton.alpha = if (hasLogin) 1f else 0.45f
                scanStatus.text = buildString {
                    append("اكتمل الفحص • ").append(count).append(" ملف")
                    append(if (hasLogin) "\nتم العثور على login.html — المعاينة جاهزة."
                        else "\nلم يتم العثور على login.html. اختر المجلد الذي يحتوي على صفحة الدخول.")
                    if (permissionWarning.isNotBlank()) append("\n").append(permissionWarning)
                    if (warnings.isNotEmpty()) append("\nتحذيرات: ").append(warnings.size)
                }
                if (openWhenReady && hasLogin) openPreview()
            }
        }
    }

    private fun openPreview() {
        if (files.tree == null) return
        val uri = files.tree ?: return\n        startActivity(Intent(this, PreviewActivity::class.java).putExtra(PreviewActivity.EXTRA_TREE, uri.toString()))
    }

    private fun showDiagnostics() {
        val warnings = files.scanWarnings
        val message = if (warnings.isEmpty()) {
            "لم تُسجل تحذيرات فهرسة.\n\nإذا لم تظهر الصفحة، تأكد من أن المجلد المختار يحتوي على login.html ومجلدات css وjs وimg."
        } else warnings.joinToString("\n")
        AlertDialog.Builder(this)
            .setTitle("تشخيص ملفات القالب")
            .setMessage(message)
            .setPositiveButton("إغلاق", null)
            .show()
    }

    override fun onDestroy() {
        fileExecutor.shutdownNow()
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_HOTSPOT_FOLDER = 100
    }
}
