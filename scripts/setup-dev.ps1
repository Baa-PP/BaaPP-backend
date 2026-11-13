<#
.SYNOPSIS
Instala las dependencias locales de desarrollo para BaaPP (JDK 21, PostgreSQL 16, Node 22 y Android SDK 35).
#>
param(
    [switch]$SkipAndroid
)

$ErrorActionPreference = "Stop"

function Add-UserPath([string]$Dir) {
    $current = [Environment]::GetEnvironmentVariable("Path", "User")
    if ($current -notlike "*$Dir*") {
        [Environment]::SetEnvironmentVariable("Path", "$current;$Dir", "User")
    }
    if ($env:Path -notlike "*$Dir*") {
        $env:Path = "$Dir;$env:Path"
    }
}

Write-Host "==> 1/4 Verificando JDK 21 (Amazon Corretto)..."
$jdkDir = Get-ChildItem "C:\Program Files\Amazon Corretto\jdk21*" -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $jdkDir) {
    winget install --id Amazon.Corretto.21.JDK -e --accept-package-agreements --accept-source-agreements --silent
    $jdkDir = Get-ChildItem "C:\Program Files\Amazon Corretto\jdk21*" -Directory | Select-Object -First 1
}
[Environment]::SetEnvironmentVariable("JAVA_HOME", $jdkDir.FullName, "User")
$env:JAVA_HOME = $jdkDir.FullName
Add-UserPath "$($jdkDir.FullName)\bin"

Write-Host "==> 2/4 Verificando Node.js 22 LTS (via fnm)..."
$env:Path = [Environment]::GetEnvironmentVariable("Path", "Machine") + ";" + [Environment]::GetEnvironmentVariable("Path", "User")
if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
    if (-not (Get-Command fnm -ErrorAction SilentlyContinue)) {
        winget install --id Schniz.fnm -e --accept-package-agreements --accept-source-agreements --silent
        $env:Path = [Environment]::GetEnvironmentVariable("Path", "Machine") + ";" + [Environment]::GetEnvironmentVariable("Path", "User")
    }
    fnm install 22
    fnm default 22
    Add-UserPath "$env:APPDATA\fnm\aliases\default"
}

Write-Host "==> 3/4 Verificando PostgreSQL 16 y base de datos 'oviapp'..."
$pgBase = "$env:LOCALAPPDATA\Programs\PostgreSQL\16"
$pgBin = "$pgBase\pgsql\bin"
$pgData = "$pgBase\data"
if (-not (Test-Path "$pgBase\pgsql\share\postgres.bki")) {
    Remove-Item -Recurse -Force "$pgBase\pgsql" -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Force -Path $pgBase | Out-Null
    $pgZip = "$env:TEMP\pgsql-16.16.zip"
    curl.exe -sSL "https://get.enterprisedb.com/postgresql/postgresql-16.16-1-windows-x64-binaries.zip" -o $pgZip
    tar.exe -xf $pgZip -C $pgBase
    Remove-Item $pgZip -Force
}
Add-UserPath $pgBin
if (-not (Test-Path "$pgData\PG_VERSION")) {
    $pwFile = "$env:TEMP\pgpass.txt"
    Set-Content -Path $pwFile -Value "postgres" -NoNewline
    & "$pgBin\initdb.exe" -D $pgData -U postgres -A trust -E UTF8 --pwfile="$pwFile"
    Remove-Item $pwFile -Force
}
& "$pgBin\pg_ctl.exe" -D $pgData -l "$pgBase\postgres.log" status *> $null
if ($LASTEXITCODE -ne 0) {
    & "$pgBin\pg_ctl.exe" -D $pgData -l "$pgBase\postgres.log" start
}
$dbExists = & "$pgBin\psql.exe" -U postgres -tAc "SELECT 1 FROM pg_database WHERE datname='oviapp'"
if ($dbExists -ne "1") {
    & "$pgBin\createdb.exe" -U postgres oviapp
}

if (-not $SkipAndroid) {
    Write-Host "==> 4/4 Verificando Android SDK 35..."
    $sdkRoot = "$env:LOCALAPPDATA\Android\Sdk"
    $cmdlineDir = "$sdkRoot\cmdline-tools\latest"
    if (-not (Test-Path "$cmdlineDir\bin\sdkmanager.bat")) {
        New-Item -ItemType Directory -Force -Path "$sdkRoot\cmdline-tools" | Out-Null
        $sdkZip = "$env:TEMP\android-cmdline-tools.zip"
        $sdkTmp = "$env:TEMP\android-cmdline-extract"
        curl.exe -sSL "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip" -o $sdkZip
        Remove-Item -Recurse -Force $sdkTmp -ErrorAction SilentlyContinue
        New-Item -ItemType Directory -Force -Path $sdkTmp | Out-Null
        tar.exe -xf $sdkZip -C $sdkTmp
        Move-Item -Path "$sdkTmp\cmdline-tools" -Destination $cmdlineDir -Force
        Remove-Item -Recurse -Force $sdkZip, $sdkTmp -ErrorAction SilentlyContinue
    }
    [Environment]::SetEnvironmentVariable("ANDROID_HOME", $sdkRoot, "User")
    $env:ANDROID_HOME = $sdkRoot
    if (-not (Test-Path "$sdkRoot\platforms\android-35")) {
        cmd /c "for /L %i in (1,1,10) do @echo y" | & "$cmdlineDir\bin\sdkmanager.bat" --sdk_root="$sdkRoot" --licenses | Out-Null
        & "$cmdlineDir\bin\sdkmanager.bat" --sdk_root="$sdkRoot" "platform-tools" "platforms;android-35" "build-tools;35.0.0"
    }
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$webappDir = Join-Path (Split-Path -Parent $repoRoot) "BaaPP_webapp"
if (Test-Path (Join-Path $webappDir "package.json")) {
    Write-Host "==> Instalando dependencias de BaaPP_webapp..."
    Push-Location $webappDir
    npm install
    Pop-Location
}

Write-Host "==> Verificando backend (.\gradlew.bat test)..."
Push-Location $repoRoot
.\gradlew.bat test --no-daemon
Pop-Location

Write-Host "`nListo. Abre una nueva terminal y ejecuta '.\gradlew.bat bootRun' en BaaPP-backend."
