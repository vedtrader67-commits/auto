package com.calculator.simple

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var appIcon: ImageView
    private lateinit var originalName: TextView
    private lateinit var packageName: TextView
    private lateinit var customName: EditText
    private lateinit var customPackage: EditText
    private lateinit var status: TextView
    private var selectedApk: Uri? = null
    private var selectedLogo: Uri? = null
    private var selectedPackage: String? = null
    private var selectedApkPath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
    }

    private fun buildScreen() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(28))
            setBackgroundColor(Color.rgb(10, 13, 23))
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(TextView(this).apply {
            text = "APK CUSTOMIZER"
            textSize = 26f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        })
        content.addView(label("PERSONALIZE YOUR APK"))
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(22), dp(18), dp(22))
            setBackgroundColor(Color.rgb(25, 32, 49))
        }
        appIcon = ImageView(this).apply {
            setImageResource(android.R.drawable.sym_def_app_icon)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        card.addView(appIcon, LinearParams(dp(88), dp(88)))
        originalName = TextView(this).apply {
            text = "No APK selected"
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(4))
        }
        card.addView(originalName)
        packageName = TextView(this).apply {
            text = "Import an APK to get started"
            textSize = 12f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }
        card.addView(packageName)
        content.addView(card, LinearParams(-1, -2).apply { topMargin = dp(18) })

        val import = actionButton("＋  IMPORT APK")
        import.setOnClickListener { chooseApk() }
        content.addView(import, LinearParams(-1, dp(54)).apply { topMargin = dp(18) })

        content.addView(label("NEW APP NAME"))
        customName = input("Enter the name you want")
        content.addView(customName, LinearParams(-1, dp(54)))

        content.addView(label("NEW APP LOGO"))
        val logoButton = actionButton("▣  CHOOSE NEW APP LOGO")
        logoButton.setOnClickListener { chooseLogo() }
        content.addView(logoButton, LinearParams(-1, dp(52)))

        content.addView(label("NEW PACKAGE ID"))
        customPackage = input("example.custom.app")
        content.addView(customPackage, LinearParams(-1, dp(54)))

        val preview = actionButton("PREVIEW CUSTOMIZATION")
        preview.setOnClickListener { previewChanges() }
        content.addView(preview, LinearParams(-1, dp(52)).apply { topMargin = dp(18) })

        status = TextView(this).apply {
            text = "READY • Import an APK, edit its name and choose a replacement logo."
            textSize = 13f
            setTextColor(Color.rgb(180, 195, 220))
            setPadding(0, dp(18), 0, dp(8))
        }
        content.addView(status)

        val note = TextView(this).apply {
            text = "CURRENT BUILD: NAME, LOGO AND PACKAGE ID PREVIEW\nThis build lets you select a replacement image and preview your settings. It does not yet rewrite, sign or export the APK. The original APK remains unchanged."
            textSize = 12f
            setTextColor(Color.rgb(205, 210, 222))
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setBackgroundColor(Color.rgb(25, 32, 49))
        }
        content.addView(note, LinearParams(-1, -2).apply { topMargin = dp(12) })
        scroll.addView(content)
        page.addView(scroll, LinearParams(-1, -1))
        setContentView(page)
    }

    private fun chooseApk() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/vnd.android.package-archive"
        }
        startActivityForResult(intent, 41)
    }

    private fun chooseLogo() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        startActivityForResult(intent, 42)
    }

    @Deprecated("Legacy activity result supports the project's minimum Android version")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            val readFlag = data.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION
            val persistableFlag = data.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            if (readFlag != 0 && persistableFlag != 0) {
                contentResolver.takePersistableUriPermission(uri, readFlag)
            }
            when (requestCode) {
                41 -> importApk(uri)
                42 -> {
                    contentResolver.openInputStream(uri).use { stream ->
                        requireNotNull(stream) { "Could not open selected image" }.use {
                            val bitmap = android.graphics.BitmapFactory.decodeStream(it)
                            requireNotNull(bitmap) { "Unsupported image file" }
                            appIcon.setImageBitmap(bitmap)
                        }
                    }
                    selectedLogo = uri
                    status.text = "LOGO SELECTED • Preview updated. APK file has not been modified."
                }
            }
        } catch (e: Exception) {
            status.text = "Could not open selection: ${e.message ?: "unknown error"}"
            toast("Could not open that file.")
        }
    }

    private fun importApk(uri: Uri) {
        val path = cacheApk(uri)
        val info: PackageInfo? = packageManager.getPackageArchiveInfo(path, 0)
        if (info == null) {
            toast("This APK could not be read.")
            return
        }
        val app = info.applicationInfo ?: run {
            toast("APK application details are missing.")
            return
        }
        app.sourceDir = path
        app.publicSourceDir = path
        appIcon.setImageDrawable(app.loadIcon(packageManager))
        val name = app.loadLabel(packageManager).toString()
        originalName.text = name
        customName.setText(name)
        selectedPackage = info.packageName
        customPackage.setText(info.packageName)
        packageName.text = info.packageName
        selectedApk = uri
        selectedApkPath = path
        selectedLogo = null
        status.text = "IMPORTED • Edit the name, choose a new logo and preview."
    }

    private fun previewChanges() {
        if (selectedApk == null) {
            toast("Import an APK first.")
            return
        }
        val name = customName.text.toString().trim()
        val pkg = customPackage.text.toString().trim()
        if (name.isEmpty()) {
            toast("Enter a new app name.")
            return
        }
        if (!pkg.matches(Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+"))) {
            toast("Enter a valid package ID, e.g. example.custom.app")
            return
        }
        originalName.text = name
        packageName.text = pkg
        status.text = "PREVIEW READY • Name, logo and package ID selected. Original APK is unchanged."
    }

    private fun cacheApk(uri: Uri): String {
        val file = java.io.File(cacheDir, "selected-input.apk")
        contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to open selected APK" }.use { source ->
                file.outputStream().use { target -> source.copyTo(target) }
            }
        }
        return file.absolutePath
    }

    private fun input(hintText: String) = EditText(this).apply {
        hint = hintText
        setHintTextColor(Color.GRAY)
        setTextColor(Color.WHITE)
        textSize = 16f
        setSingleLine(true)
        setPadding(dp(14), dp(10), dp(14), dp(10))
        setBackgroundColor(Color.rgb(31, 39, 57))
    }

    private fun label(value: String) = TextView(this).apply {
        text = value
        textSize = 11f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.rgb(132, 169, 235))
        setPadding(0, dp(20), 0, dp(8))
    }

    private fun actionButton(value: String) = Button(this).apply {
        text = value
        textSize = 14f
        isAllCaps = false
        setTextColor(Color.WHITE)
        backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(67, 112, 220))
    }

    private fun LinearParams(w: Int, h: Int) = LinearLayout.LayoutParams(w, h)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun toast(value: String) = Toast.makeText(this, value, Toast.LENGTH_SHORT).show()
}
