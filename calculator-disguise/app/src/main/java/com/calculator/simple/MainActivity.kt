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
import android.view.View
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
    private lateinit var status: TextView
    private var selectedApk: Uri? = null
    private var selectedIcon: Drawable? = null
    private var selectedPackage: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
    }

    private fun buildScreen() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(24), dp(22), dp(24))
            setBackgroundColor(Color.rgb(12, 15, 24))
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val title = TextView(this).apply {
            text = "APK CUSTOMIZER"
            textSize = 25f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }
        content.addView(title)
        content.addView(label("Change an app's display name and icon"))
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(22), dp(18), dp(22))
            setBackgroundColor(Color.rgb(25, 31, 47))
        }
        appIcon = ImageView(this).apply {
            setImageResource(android.R.drawable.sym_def_app_icon)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        card.addView(appIcon, LinearParams(dp(76), dp(76)))
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
            text = "Import an APK file to begin"
            textSize = 12f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
        }
        card.addView(packageName)
        content.addView(card, LinearParams(-1, -2).apply { topMargin = dp(24) })

        val import = actionButton("＋   IMPORT APK")
        import.setOnClickListener { chooseApk() }
        content.addView(import, LinearParams(-1, dp(54)).apply { topMargin = dp(22) })

        content.addView(label("CUSTOM APP NAME"))
        customName = EditText(this).apply {
            hint = "Example: Vedant"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            textSize = 17f
            setSingleLine(true)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setBackgroundColor(Color.rgb(31, 38, 55))
            isEnabled = false
        }
        content.addView(customName, LinearParams(-1, dp(54)))

        val preview = actionButton("PREVIEW CHANGES")
        preview.setOnClickListener {
            if (selectedApk == null) toast("Import an APK first.")
            else {
                val name = customName.text.toString().trim().ifEmpty { originalName.text.toString() }
                originalName.text = name
                toast("Preview updated. Original APK is unchanged.")
            }
        }
        content.addView(preview, LinearParams(-1, dp(50)).apply { topMargin = dp(14) })

        status = TextView(this).apply {
            text = "READY • Import an APK to inspect its app name, package and icon."
            textSize = 13f
            setTextColor(Color.rgb(174, 190, 215))
            setPadding(0, dp(20), 0, dp(8))
        }
        content.addView(status)

        val note = TextView(this).apply {
            text = "BUILD STATUS\nThis first test build imports APKs and previews custom names. Actual APK repacking and signing is not enabled yet. Changing an app's package identity can break Play Store updates and app security checks."
            textSize = 12f
            setTextColor(Color.rgb(190, 196, 208))
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setBackgroundColor(Color.rgb(25, 31, 47))
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

    @Deprecated("Legacy activity result is used for broad Android compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 41 || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            val flags = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            contentResolver.takePersistableUriPermission(uri, flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val path = cacheApk(uri)
            val info: PackageInfo? = packageManager.getPackageArchiveInfo(path, 0)
            if (info == null) {
                toast("This APK could not be read.")
                return
            }
            info.applicationInfo?.let { app ->
                app.sourceDir = path
                app.publicSourceDir = path
                selectedIcon = app.loadIcon(packageManager)
                appIcon.setImageDrawable(selectedIcon)
                val name = app.loadLabel(packageManager).toString()
                originalName.text = name
                customName.setText(name)
                customName.isEnabled = true
                selectedPackage = info.packageName
                packageName.text = info.packageName
                selectedApk = uri
                status.text = "IMPORTED • APK details loaded. You can edit the preview name."
            }
        } catch (e: Exception) {
            status.text = "Could not inspect this APK: ${e.message ?: "unknown error"}"
            toast("Could not read this APK.")
        }
    }

    private fun cacheApk(uri: Uri): String {
        val file = java.io.File(cacheDir, "selected-input.apk")
        contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to open selected file" }.use { source ->
                file.outputStream().use { target -> source.copyTo(target) }
            }
        }
        return file.absolutePath
    }

    private fun label(value: String) = TextView(this).apply {
        text = value
        textSize = 11f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.rgb(130, 165, 220))
        setPadding(0, dp(22), 0, dp(8))
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
