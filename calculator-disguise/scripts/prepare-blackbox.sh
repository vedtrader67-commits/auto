#!/usr/bin/env bash
set -euo pipefail

ENGINE_DIR="${1:?BlackBox engine directory required}"
PROJECT_DIR="${2:?Calculator project directory required}"

python3 - "$ENGINE_DIR" "$PROJECT_DIR" <<'PY'
from pathlib import Path
import re
import shutil
import sys

engine = Path(sys.argv[1]).resolve()
project = Path(sys.argv[2]).resolve()
app = engine / "app"
gradle_file = app / "build.gradle"
manifest_file = app / "src/main/AndroidManifest.xml"
strings_file = app / "src/main/res/values/strings.xml"
gate_input = project / "CalculatorGateActivity.kt"
icon_input = project / "app/src/main/res/drawable/ic_calculator.xml"

for required in (gradle_file, manifest_file, strings_file, gate_input, icon_input):
    if not required.is_file():
        raise SystemExit("Required integration file missing: " + str(required))

gradle = gradle_file.read_text()
gradle = gradle.replace('applicationId "top.niunaijun.blackbox"', 'applicationId "com.calculator.simple"')
gradle = gradle.replace('versionCode 400', 'versionCode 4')
gradle = gradle.replace('versionName "4.0.0"', 'versionName "1.1.0"')
if 'applicationId "com.calculator.simple"' not in gradle:
    raise SystemExit("Could not set Calculator applicationId safely")
gradle_file.write_text(gradle)

manifest_file = app / "src/main/AndroidManifest.xml"
manifest = manifest_file.read_text()
welcome_pattern = re.compile(
    r'(<activity\s+android:name="\.view\.main\.WelcomeActivity"[^>]*>)'
    r'\s*<intent-filter>.*?</intent-filter>\s*(</activity>)',
    re.S
)
manifest, count = welcome_pattern.subn(r'\1\n        \2', manifest, count=1)
if count != 1:
    raise SystemExit("Could not safely remove launcher filter from WelcomeActivity")

if '.view.main.CalculatorGateActivity' not in manifest:
    gate_activity = """
        <activity
            android:name=".view.main.CalculatorGateActivity"
            android:exported="true"
            android:screenOrientation="portrait">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
"""
    manifest = manifest.replace('</application>', gate_activity + '    </application>')
manifest = manifest.replace('android:icon="@mipmap/ic_launcher"', 'android:icon="@drawable/ic_calculator"')
manifest = manifest.replace('android:roundIcon="@mipmap/ic_launcher_round"', 'android:roundIcon="@drawable/ic_calculator"')
manifest_file.write_text(manifest)

strings = strings_file.read_text()
strings, count = re.subn(
    r'<string name="app_name">.*?</string>',
    '<string name="app_name">Calculator</string>',
    strings, count=1, flags=re.S
)
if count != 1:
    raise SystemExit("Could not rename app label")
if 'name="install_apk_to_virtual"' not in strings:
    strings = strings.replace(
        '<resources>',
        '<resources>\n    <string name="install_apk_to_virtual">Install APK to Virtual Space</string>',
        1
    )
strings_file.write_text(strings)

gate_source = app / "src/main/java/top/niunaijun/blackboxa/view/main/CalculatorGateActivity.kt"
gate_source.parent.mkdir(parents=True, exist_ok=True)
shutil.copy2(gate_input, gate_source)
icon_dest = app / "src/main/res/drawable/ic_calculator.xml"
icon_dest.parent.mkdir(parents=True, exist_ok=True)
shutil.copy2(icon_input, icon_dest)

main_activity = app / "src/main/java/top/niunaijun/blackboxa/view/main/MainActivity.kt"
main_menu = app / "src/main/res/menu/menu_main.xml"
list_menu = app / "src/main/res/menu/menu_list.xml"
repo_file = app / "src/main/java/top/niunaijun/blackboxa/data/AppsRepository.kt"
native_utils = engine / "Bcore/src/main/java/top/niunaijun/blackbox/utils/NativeUtils.java"
for required in (main_activity, main_menu, list_menu, repo_file, native_utils):
    if not required.is_file():
        raise SystemExit("BlackBox source file missing: " + str(required))

# Add a direct APK picker on the main screen. It hands the selected content URI
# to the virtual install method and never invokes Android's device PackageInstaller.
main_text = main_activity.read_text()
if "directInstallApkResult" not in main_text:
    if "import android.widget.Toast" not in main_text:
        main_text = main_text.replace(
            "import android.view.MenuItem",
            "import android.view.MenuItem\nimport android.widget.Toast", 1
        )
    anchor = "    override fun onCreateOptionsMenu(menu: Menu?): Boolean {"
    direct_picker = """    private val directInstallApkResult =
            registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                if (uri != null) {
                    val pageIndex = viewBinding.viewPager.currentItem
                    val fragment = fragmentList.getOrNull(pageIndex)
                    if (fragment != null) {
                        fragment.installApk(uri.toString())
                    } else {
                        Toast.makeText(this, "Select a virtual space first.", Toast.LENGTH_SHORT).show()
                    }
                }
            }

"""
    if anchor not in main_text:
        raise SystemExit("Could not find main menu insertion point in MainActivity.kt")
    main_text = main_text.replace(anchor, direct_picker + anchor, 1)

when_anchor = "            when (item.itemId) {"
if "R.id.main_install_apk" not in main_text:
    if when_anchor not in main_text:
        raise SystemExit("Could not find MainActivity menu switch")
    main_text = main_text.replace(
        when_anchor,
        when_anchor + '\n                R.id.main_install_apk -> {\n'
        + '                    directInstallApkResult.launch("application/vnd.android.package-archive")\n'
        + '                }',
        1
    )
main_activity.write_text(main_text)

menu_text = main_menu.read_text()
if "main_install_apk" not in menu_text:
    menu_match = re.search(r'(<menu\b[^>]*>)', menu_text)
    if not menu_match:
        raise SystemExit("Could not find menu root in menu_main.xml")
    item = """
    <item
            android:id="@+id/main_install_apk"
            android:title="@string/install_apk_to_virtual"
            app:showAsAction="never" />
"""
    menu_text = menu_text[:menu_match.end()] + item + menu_text[menu_match.end():]
main_menu.write_text(menu_text)

list_text = list_menu.read_text()
list_text = list_text.replace('android:title="@string/choose"', 'android:title="@string/install_apk_to_virtual"', 1)
list_menu.write_text(list_text)

# Stage content URIs to a private temporary APK before installation. Package-name
# selections still use BlackBox's host-app clone path.
repo_text = repo_file.read_text()
repo_start = repo_text.find("    fun installApk(")
repo_end = repo_text.find("\n    fun unInstall(", repo_start)
if repo_start < 0 or repo_end < 0:
    raise SystemExit("Could not locate AppsRepository.installApk() safely")
install_function = r'''    fun installApk(source: String, userId: Int, resultLiveData: MutableLiveData<String>) {
        var stagedApk: File? = null
        try {
            val blackBoxCore = BlackBoxCore.get()
            val sourceUri = Uri.parse(source)
            val installResult = when (sourceUri.scheme?.lowercase()) {
                "content" -> {
                    val staged = File.createTempFile(
                            "blackbox-import-", ".apk", BlackBoxCore.getContext().cacheDir
                    )
                    stagedApk = staged
                    val input = BlackBoxCore.getContext().contentResolver.openInputStream(sourceUri)
                            ?: throw IllegalArgumentException("Android could not read the selected APK.")
                    input.use { sourceStream ->
                        staged.outputStream().use { targetStream ->
                            sourceStream.copyTo(targetStream)
                        }
                    }
                    if (!staged.isFile || staged.length() == 0L) {
                        throw IllegalArgumentException("The selected APK is empty or could not be copied.")
                    }
                    blackBoxCore.installPackageAsUser(staged, userId)
                }
                "file" -> {
                    val path = sourceUri.path
                            ?: throw IllegalArgumentException("The selected file path is missing.")
                    val file = File(path)
                    if (!file.isFile || file.length() == 0L) {
                        throw IllegalArgumentException("The selected APK file cannot be read.")
                    }
                    blackBoxCore.installPackageAsUser(file, userId)
                }
                else -> {
                    val file = File(source)
                    if (file.isFile) {
                        if (file.length() == 0L) {
                            throw IllegalArgumentException("The selected APK file is empty.")
                        }
                        blackBoxCore.installPackageAsUser(file, userId)
                    } else {
                        blackBoxCore.installPackageAsUser(source, userId)
                    }
                }
            }

            if (installResult.success) {
                updateAppSortList(userId, installResult.packageName, true)
                resultLiveData.postValue(getString(R.string.install_success))
                scanUser()
            } else {
                resultLiveData.postValue(getString(R.string.install_fail, installResult.msg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error installing APK into virtual space: " + e.message, e)
            resultLiveData.postValue(
                    "Virtual installation failed: " + (e.localizedMessage ?: e.javaClass.simpleName)
            )
        } finally {
            stagedApk?.let {
                try {
                    if (it.exists()) it.delete()
                } catch (e: Exception) {
                    Log.w(TAG, "Could not remove temporary imported APK: " + e.message)
                }
            }
        }
    }
'''
repo_text = repo_text[:repo_start] + install_function + repo_text[repo_end:]
repo_text = repo_text.replace("import android.webkit.URLUtil\n", "")
repo_file.write_text(repo_text)

# Use ABIs supported by the BlackBox host process. The old extractor only tried
# Build.CPU_ABI and armeabi, which can miss armeabi-v7a or the process ABI.
native_code = r'''package top.niunaijun.blackbox.utils;

import android.os.Build;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import top.niunaijun.blackbox.BlackBoxCore;

public final class NativeUtils {
    public static final String TAG = "VirtualM";

    private NativeUtils() {
    }

    public static void copyNativeLib(File apk, File nativeLibDir) throws Exception {
        if (apk == null || !apk.isFile()) {
            throw new IOException("APK file is missing while extracting native libraries.");
        }
        if (nativeLibDir.exists()) {
            FileUtils.deleteDir(nativeLibDir);
        }
        if (!nativeLibDir.mkdirs() && !nativeLibDir.isDirectory()) {
            throw new IOException("Unable to create virtual native-library directory.");
        }

        long started = System.currentTimeMillis();
        try (ZipFile zip = new ZipFile(apk)) {
            String[] supported = BlackBoxCore.is64Bit()
                    ? Build.SUPPORTED_64_BIT_ABIS : Build.SUPPORTED_32_BIT_ABIS;
            if (supported == null || supported.length == 0) {
                supported = new String[]{Build.CPU_ABI, Build.CPU_ABI2};
            }

            String selectedAbi = null;
            boolean apkHasNativeLibs = false;
            for (String abi : supported) {
                if (abi == null || abi.length() == 0) continue;
                String prefix = "lib/" + abi + "/";
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (name.startsWith("lib/") && name.endsWith(".so")) {
                        apkHasNativeLibs = true;
                    }
                    if (!entry.isDirectory() && name.startsWith(prefix) && name.endsWith(".so")) {
                        selectedAbi = abi;
                        break;
                    }
                }
                if (selectedAbi != null) break;
            }

            if (selectedAbi == null) {
                if (apkHasNativeLibs) {
                    throw new IOException("This APK contains native libraries, but none match the "
                            + (BlackBoxCore.is64Bit() ? "64-bit" : "32-bit")
                            + " ABI supported by this virtual environment.");
                }
                return;
            }

            String prefix = "lib/" + selectedAbi + "/";
            Enumeration<? extends ZipEntry> entries = zip.entries();
            int copied = 0;
            byte[] buffer = new byte[16 * 1024];
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || !name.startsWith(prefix) || !name.endsWith(".so")) continue;

                String fileName = name.substring(prefix.length());
                if (fileName.length() == 0 || fileName.contains("/") || fileName.contains("\\")
                        || fileName.equals(".") || fileName.equals("..")) continue;

                File outputFile = new File(nativeLibDir, fileName);
                try (InputStream input = new BufferedInputStream(zip.getInputStream(entry));
                     BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(outputFile))) {
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        output.write(buffer, 0, count);
                    }
                    output.flush();
                }
                outputFile.setReadable(true, false);
                outputFile.setExecutable(true, false);
                copied++;
            }

            if (copied == 0 && apkHasNativeLibs) {
                throw new IOException("No native libraries could be extracted for ABI " + selectedAbi + ".");
            }
            Log.i(TAG, "Extracted " + copied + " native libraries for " + selectedAbi);
        } finally {
            Log.d(TAG, "Native library extraction finished in "
                    + (System.currentTimeMillis() - started) + "ms");
        }
    }
}
'''
native_utils.write_text(native_code)

print("Calculator gate, launcher icon, direct virtual APK import, staged document URIs, and ABI-aware native library extraction configured.")
PY
