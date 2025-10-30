Issue: APK contains native libraries with LOAD segments not aligned to 16 KB

Details:
- Google Play will require support for 16 KB page sizes starting Nov 1, 2025.
- Your current APK contains native libraries with LOAD segments not aligned to 16 KB for the following files:
  - lib/x86_64/libimage_processing_util_jni.so
  - lib/x86_64/libxeno_native.so

Temporary workaround applied:
- app/build.gradle.kts: added jniLibs excludes to prevent packaging x86/x86_64 native libraries into the APK. This reduces the ABIs included in the APK and may allow publishing while you obtain corrected native libraries.

Long-term fixes (recommended):
1) Rebuild the native libraries with NDK r25b or later and ensure the linker produces 16 KB-aligned PT_LOAD segments. Newer NDK toolchains produce aligned segments by default.
2) Update Android Gradle Plugin (AGP) and NDK to the latest stable supported versions.
   - In some cases, setting `android.defaultConfig.ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }` and building universal APKs / app bundles that only include ARM ABIs is acceptable.
3) Work with upstream library maintainers to publish updated .so binaries that are compatible with 16 KB page size.
4) Use Android App Bundle (.aab) and rely on Play's split delivery to only serve compatible ABIs to devices.

How to revert temporary workaround:
- Remove the `jniLibs.excludes` entries from `app/build.gradle.kts` once you have corrected libraries or have updated your build to produce correctly aligned .so files.

Steps to find which dependency provides the problematic .so files:

1) Build an APK locally (assembleDebug or assembleRelease).
2) Use `unzip -l app/build/outputs/apk/debug/app-debug.apk` to list files and find `lib/x86_64/libxeno_native.so` etc.
3) If using Android Studio, open the `Build` -> `Analyze APK...` tool and inspect the `lib/` directory to see which AAR packaged the native library.
4) Once you identify the dependency, search for newer versions of that library or reach out to the maintainer for a fixed .so built with NDK r25b+.

Using the included Gradle helper task

I added a Gradle task `scanNativeLibs` to the root `build.gradle.kts`. It attempts to scan resolved dependency AAR/JAR files for the target native library filenames.

Run it from project root (PowerShell):

```powershell
.\gradlew.bat scanNativeLibs
```

This prints any matches and which AAR / dependency they came from. If nothing is found, build the app first (assembleDebug) and then run the task.

Note: The task resolves configurations and may download artifacts; run it after a build for best results.

References:
- https://developer.android.com/16kb-page-size

If you want, I can:
- Add `ndk.abiFilters` to the Gradle config to restrict ABIs to arm-only.
- Search the repo for where those .so files come from and try to update the library versions.
- Suggest an updated AGP/NDK matrix for your compileSdk=34 setup.

If you want, I can try to automate searching AARs in Gradle caches, but I need either build artifacts or permission to run local commands.
