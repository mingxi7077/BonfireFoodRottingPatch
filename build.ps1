param(
    [string]$ServerDir = "..\server"
)

$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$SourceDir = Join-Path $ProjectRoot "src\main\java"
$ResourceDir = Join-Path $ProjectRoot "src\main\resources"
$BuildDir = Join-Path $ProjectRoot "build"
$ClassesDir = Join-Path $BuildDir "classes"
$OutputJar = Join-Path $BuildDir "BonfireFoodRotting-1.1.0-fix3.jar"

$ServerPath = Resolve-Path (Join-Path $ProjectRoot $ServerDir)
$PluginsDir = Join-Path $ServerPath "plugins"

$ApiJar = (Get-ChildItem -Path (Join-Path $ServerPath "libraries\org\purpurmc\purpur\purpur-api") -Filter "purpur-api-*.jar" -File -Recurse -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1).FullName
$NbtApiJar = (Get-ChildItem -Path $PluginsDir -File -ErrorAction SilentlyContinue | Where-Object { $_.Name -match "(?i)(nbt.*api|item-nbt-api-plugin)" } | Sort-Object LastWriteTime -Descending | Select-Object -First 1).FullName
$TargetPluginJar = (Get-ChildItem -Path $PluginsDir -File -ErrorAction SilentlyContinue | Where-Object { $_.Name -like "*BonfireFoodRotting*.jar" } | Sort-Object LastWriteTime -Descending | Select-Object -First 1).FullName
$KyoriJars = @(Get-ChildItem -Path (Join-Path $ServerPath "libraries\net\kyori") -Filter "*.jar" -File -Recurse -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName)
$Md5Jars = @(Get-ChildItem -Path (Join-Path $ServerPath "libraries\net\md-5") -Filter "*.jar" -File -Recurse -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName)

if ([string]::IsNullOrWhiteSpace($ApiJar) -or !(Test-Path $ApiJar)) {
    throw "Missing API jar: $ApiJar"
}
if ([string]::IsNullOrWhiteSpace($NbtApiJar) -or !(Test-Path $NbtApiJar)) {
    throw "Missing NBTAPI jar: $NbtApiJar"
}
if ([string]::IsNullOrWhiteSpace($TargetPluginJar) -or !(Test-Path $TargetPluginJar)) {
    throw "Missing target BonfireFoodRotting plugin jar in: $PluginsDir"
}
if ($KyoriJars.Count -eq 0) {
    throw "Missing Kyori Adventure jars in server libraries."
}
if ($Md5Jars.Count -eq 0) {
    throw "Missing md-5 jars in server libraries."
}

New-Item -ItemType Directory -Path $ClassesDir -Force | Out-Null
Get-ChildItem -Path $ClassesDir -Force -Recurse -ErrorAction SilentlyContinue | Remove-Item -Force -Recurse -ErrorAction SilentlyContinue

$JavaFiles = @(Get-ChildItem -Path $SourceDir -Filter *.java -File -Recurse | Select-Object -ExpandProperty FullName)
if ($JavaFiles.Count -eq 0) {
    throw "No Java source files found in: $SourceDir"
}

$Classpath = @($ApiJar, $NbtApiJar) + $KyoriJars + $Md5Jars -join ";"
javac --release 21 -encoding UTF-8 -cp $Classpath -d $ClassesDir $JavaFiles
if ($LASTEXITCODE -ne 0) {
    throw "javac failed with exit code $LASTEXITCODE"
}

Copy-Item -Path (Join-Path $ResourceDir "*") -Destination $ClassesDir -Recurse -Force

New-Item -ItemType Directory -Path $BuildDir -Force | Out-Null
if (Test-Path $OutputJar) {
    Remove-Item -Path $OutputJar -Force
}
jar --create --file $OutputJar -C $ClassesDir .
if ($LASTEXITCODE -ne 0) {
    throw "jar packaging failed with exit code $LASTEXITCODE"
}

$BackupPath = "$TargetPluginJar.bak"
Copy-Item -Path $TargetPluginJar -Destination $BackupPath -Force
Copy-Item -Path $OutputJar -Destination $TargetPluginJar -Force

Write-Host "Build completed: $OutputJar"
Write-Host "Backed up old jar to: $BackupPath"
Write-Host "Patched plugin copied to: $TargetPluginJar"
