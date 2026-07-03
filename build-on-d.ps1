#!/usr/bin/env pwsh
# -- SurakshaSathi - D-Drive Build Script --------------------------------------
# All caches, SDK, JDK, and AVD data live on D drive. C drive is FULL.
# Usage:  .\build-on-d.ps1                                 -> assembleNotificationOnlyDebug (default, Play-publishable flavor)
#         .\build-on-d.ps1 -Task testNotificationOnlyDebugUnitTest -> run unit tests
#         .\build-on-d.ps1 -Task lintNotificationOnlyDebug  -> run lint
#         .\build-on-d.ps1 -Task assembleDefaultHandlerDebug -> build the restricted-permission SMS flavor
# -----------------------------------------------------------------------------
param(
    [string]$Task = "assembleNotificationOnlyDebug",
    [switch]$StackTrace,
    [switch]$NoDaemon
)

# -- Toolchain paths (all on D drive) ------------------------------------------
$env:JAVA_HOME         = "D:\.gradle\jdks\eclipse_adoptium-21-amd64-windows.2"
$env:ANDROID_SDK_ROOT  = "D:\AndroidSdk"
$env:ANDROID_HOME      = "D:\AndroidSdk"
$env:ANDROID_AVD_HOME  = "D:\.android\avd"
$env:GRADLE_USER_HOME  = "D:\.gradle"

# Inject JDK bin at front of PATH for this session
$env:Path = "$env:JAVA_HOME\bin;$env:ANDROID_SDK_ROOT\platform-tools;" + $env:Path

# Gradle temp -> D drive (not C)
$env:JAVA_OPTS = "-Djava.io.tmpdir=D:\tmp $env:JAVA_OPTS"

Write-Host ""
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "        SurakshaSathi - Building from D Drive             " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "  JAVA_HOME         = $env:JAVA_HOME"       -ForegroundColor Green
Write-Host "  ANDROID_SDK_ROOT  = $env:ANDROID_SDK_ROOT" -ForegroundColor Green
Write-Host "  GRADLE_USER_HOME  = $env:GRADLE_USER_HOME" -ForegroundColor Green
Write-Host "  Task              = $Task"                  -ForegroundColor Yellow
Write-Host ""

Set-Location "D:\SBI\SurakshaSathi"

# Build args
$args_ = @($Task)
if ($StackTrace) { $args_ += "--stacktrace" }
if ($NoDaemon)   { $args_ += "--no-daemon" }

Write-Host "-> Running: gradlew $($args_ -join ' ')" -ForegroundColor Yellow
.\gradlew.bat @args_ 2>&1

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "SUCCESS: Build succeeded" -ForegroundColor Green
    if ($Task -like "*assemble*" -or $Task -like "*bundle*") {
        $apk = Get-ChildItem "app\build\outputs\apk\*\*.apk" -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($apk) { Write-Host "   APK -> $($apk.FullName)" -ForegroundColor Cyan }
    }
} else {
    Write-Host ""
    Write-Host "FAILED: Build failed (exit code $LASTEXITCODE)" -ForegroundColor Red
    exit $LASTEXITCODE
}
