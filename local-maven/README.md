
### 16 KB page size compatibility

Google Play requires native libraries to be 16 KB page-aligned for apps targeting Android 15+. Piano Weave's release builds meet this requirement for the 64-bit ABIs.

* **`local-maven/`** contains a rebuilt `dev.kotlinds:fluidsynth-kmp` (version `1.1.1-16kb`). The upstream 1.1.1 release ships `libfluidsynth_jni.so` with 4 KB alignment. The rebuild links it with `-Wl,-z,max-page-size=16384`. See [`local-maven/README.md`](local-maven/README.md) for the source and details. This folder will be removed once upstream publishes an aligned release.
* **Native audio bridge** (`app/src/main/cpp`) is linked with the same flag.
* **Release ABIs:** release builds ship `arm64-v8a` and `armeabi-v7a`. `x86_64` is excluded from release builds because the bundled TensorFlow Lite libraries for that ABI are not 16 KB aligned. Debug builds keep `x86_64` so the app runs on standard emulators.

To check alignment yourself (Windows PowerShell), after `assembleRelease`:

```powershell
$objdump = "$env:LOCALAPPDATA\Android\Sdk\ndk\30.0.16248370\toolchains\llvm\prebuilt\windows-x86_64\bin\llvm-objdump.exe"
Copy-Item app\build\outputs\apk\release\app-release.apk $env:TEMP\pw.zip -Force
Expand-Archive $env:TEMP\pw.zip -DestinationPath $env:TEMP\pw -Force
Get-ChildItem $env:TEMP\pw\lib\arm64-v8a -Filter *.so | ForEach-Object {
    Write-Host $_.Name
    & $objdump -p $_.FullName | Select-String -CaseSensitive '^\s+LOAD\s'
}
```

Every `LOAD` line should end in `align 2**14` or higher.
