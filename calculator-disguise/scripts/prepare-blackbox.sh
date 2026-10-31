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
source_file = app / "src/main/java/top/niunaijun/blackboxa/view/main/CalculatorGateActivity.kt"
source_input = project / "CalculatorGateActivity.kt"
icon_input = project / "app/src/main/res/drawable/ic_calculator.xml"

for required in (gradle_file, manifest_file, strings_file, source_input, icon_input):
    if not required.is_file():
        raise SystemExit(f"Required integration file missing: {required}")

gradle = gradle_file.read_text()
gradle = gradle.replace('applicationId "top.niunaijun.blackbox"', 'applicationId "com.calculator.simple"')
gradle = gradle.replace('versionCode 400', 'versionCode 3')
gradle = gradle.replace('versionName "4.0.0"', 'versionName "1.0.0"')
if 'applicationId "com.calculator.simple"' not in gradle:
    raise SystemExit("Could not set Calculator applicationId safely")
gradle_file.write_text(gradle)

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
    strings,
    count=1,
    flags=re.S
)
if count != 1:
    raise SystemExit("Could not rename app label")
strings_file.write_text(strings)

source_file.parent.mkdir(parents=True, exist_ok=True)
shutil.copy2(source_input, source_file)
icon_dest = app / "src/main/res/drawable/ic_calculator.xml"
icon_dest.parent.mkdir(parents=True, exist_ok=True)
shutil.copy2(icon_input, icon_dest)

print("Calculator gate, app identity, icon and BlackBox launcher handoff configured.")
PY
