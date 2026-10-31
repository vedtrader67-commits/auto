# VirtualXposed engine import

This folder is tracked as a Git submodule, pinned to upstream commit:

- Repository: https://github.com/imsourcandy/VirtualXposed_12
- Pinned commit: 546bf1e37b0687db666673bba34da570aeba209d
- License: GNU General Public License v3.0 (GPL-3.0)

## Important integration status

This commit imports and pins the complete upstream engine source as a submodule. It does **not** yet merge the engine into the Calculator app's Gradle build or make guest APK installation/launch functional.

VirtualXposed is a complete legacy Android application/runtime, not a drop-in library. Before wiring it into the Calculator host, its Gradle/Android plugin versions, native ABIs, manifest components, package identity, and Android 12 behavior must be audited and adapted. Do not claim virtual APK execution works until a device build and runtime test confirm it.

To fetch the engine source after cloning this repository:

```bash
git submodule update --init --recursive
```

The upstream project is GPL-3.0. Preserve its license and notices when modifying or distributing it.
