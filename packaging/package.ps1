# Builds a self-contained TankArena for Windows: the game plus its own Java runtime, so players need
# nothing installed. The Windows twin of package.sh; normally run inside packaging/windows.Dockerfile
# (see packaging/build-windows.sh).
#
#   packaging\package.ps1           app image + .zip in build\dist
#   packaging\package.ps1 msi       also an .msi installer (needs WiX 3 on the PATH)
#
# VERSION sets the version (build-windows.sh passes the one from git), KOTLIN_SHARED_CACHE moves the Kotlin toolchain's download cache. jpackage
# comes from JAVA_HOME (a JDK 25).
param([string[]]$Types = @())

$ErrorActionPreference = 'Stop'
Set-Location (Split-Path -Parent $PSScriptRoot)

$name = 'TankArena'
$version = if ($env:VERSION) { $env:VERSION } else { '0.0.0' }
$dist = 'build\dist'
$cache = if ($env:KOTLIN_SHARED_CACHE) { $env:KOTLIN_SHARED_CACHE } else { "$env:LOCALAPPDATA\JetBrains\Kotlin" }
# Keeps upgrades of the installed game in place instead of installing a second copy.
$upgradeUuid = '66c59037-1297-45ff-997d-7d29779ccd7c'

function Invoke-Kotlin {
    & .\kotlin.bat "--shared-cache-dir=$cache" @args
    if ($LASTEXITCODE -ne 0) { throw "kotlin $args failed with exit code $LASTEXITCODE" }
}

$jpackage = Join-Path $env:JAVA_HOME 'bin\jpackage.exe'
if (-not (Test-Path $jpackage)) { throw "jpackage not found: set JAVA_HOME to a JDK 25" }
Write-Host "Using $jpackage ($(& $jpackage --version))"

Write-Host 'Building the game...'
# Packaging the executable jar is what writes the module jars (build\tasks\_<module>_jarJvm); the
# executable jar itself is not used, see below.
Invoke-Kotlin package -m app-desktop -f executable-jar

# Every runtime library jar, named by group as well: Compose 1.12 has artifacts with the same file
# name in two groups (androidx.compose.runtime:runtime-desktop and its empty JetBrains redirect),
# which is why the toolchain's executable jar loses the real ones.
$inputDir = 'build\package-input'
Remove-Item -Recurse -Force $inputDir, "$dist\$name" -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $inputDir, $dist | Out-Null
$pattern = '([A-Za-z0-9_.-]+):([A-Za-z0-9_.-]+):([0-9][A-Za-z0-9_.+-]*)(?: -> ([0-9][A-Za-z0-9_.+-]*))?(?: \(\*\))?$'
$seen = @{}
foreach ($line in (Invoke-Kotlin show dependencies -m app-desktop --scope runtime -p jvm)) {
    if ($line -notmatch $pattern) { continue }
    $group = $Matches[1]
    $artifact = $Matches[2]
    $ver = if ($Matches[4]) { $Matches[4] } else { $Matches[3] }
    $key = "${group}:${artifact}:$ver"
    if ($seen.ContainsKey($key)) { continue }
    $seen[$key] = $true
    $jar = Join-Path $cache (".m2.cache\" + $group.Replace('.', '\') + "\$artifact\$ver\$artifact-$ver.jar")
    # Umbrella artifacts have no jar of their own, only metadata.
    if (Test-Path $jar) { Copy-Item $jar "$inputDir\$group.$artifact-$ver.jar" }
}
foreach ($module in 'app-desktop', 'game-ui', 'game-net', 'game-sim', 'game-content', 'game-protocol') {
    Copy-Item "build\tasks\_${module}_jarJvm\$module-jvm.jar" $inputDir
}
Write-Host "$((Get-ChildItem $inputDir).Count) jars on the class path"

$common = @(
    '--name', $name,
    # jpackage takes numbers only: "0.7.5-dirty" installs as 0.7.5 (the file names keep it).
    '--app-version', ($version -replace '-dirty$', ''),
    '--vendor', 'TankArena',
    '--description', 'Tank Arena, rewritten in Kotlin',
    '--input', $inputDir,
    '--main-jar', 'app-desktop-jvm.jar',
    '--main-class', 'nl.tankarena.app.desktop.MainKt',
    '--icon', 'packaging\tankarena.ico',
    # Only the Java modules the game uses (jdeps --print-module-deps over the jars).
    '--add-modules', 'java.base,java.desktop,java.instrument,java.management,jdk.unsupported',
    # Skia and the audio backend load native code.
    '--java-options', '--enable-native-access=ALL-UNNAMED',
    '--dest', $dist
)

function Invoke-JPackage {
    & $jpackage @args
    if ($LASTEXITCODE -ne 0) { throw "jpackage failed with exit code $LASTEXITCODE" }
}

Write-Host 'Building the app image...'
Invoke-JPackage --type app-image @common
$archive = "$dist\$name-$version-windows-x64.zip"
Remove-Item -Force $archive -ErrorAction SilentlyContinue
# Windows' own tar (bsdtar) writes a standard zip; Compress-Archive in Windows PowerShell 5 stores
# backslashes in the entry names.
& tar.exe -a -c -f $archive -C $dist $name
if ($LASTEXITCODE -ne 0) { throw "tar failed with exit code $LASTEXITCODE" }
Write-Host "Wrote $archive (run $name\$name.exe)"

foreach ($type in $Types) {
    Write-Host "Building the $type installer..."
    Invoke-JPackage --type $type @common --win-menu --win-menu-group $name --win-shortcut --win-upgrade-uuid $upgradeUuid
}
