param(
    [switch]$Clean,
    [switch]$Debuggable
)

$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$build = Join-Path $root '.build'
$sdk = Join-Path $build 'ext'
$bt = Join-Path $sdk 'android-15'
$androidJar = Join-Path $sdk 'android-35\android.jar'
$app = Join-Path $root 'app'
$res = Join-Path $app 'res'
$src = Join-Path $app 'src'
$manifest = Join-Path $app 'AndroidManifest.xml'
$manifestToUse = $manifest
if ($Debuggable) {
    $raw = [System.IO.File]::ReadAllText($manifest)
    $raw = $raw.Replace('android:allowBackup="true"',
        'android:allowBackup="true" android:debuggable="true"')
    $manifestToUse = Join-Path $build 'manifest-debug.xml'
    [System.IO.File]::WriteAllText($manifestToUse, $raw, (New-Object System.Text.UTF8Encoding($false)))
}

$env:JAVA_HOME = 'C:\Program Files\Java\jdk-23'
$javac = Join-Path $env:JAVA_HOME 'bin\javac.exe'
$jarTool = Join-Path $env:JAVA_HOME 'bin\jar.exe'
$keytool = Join-Path $env:JAVA_HOME 'bin\keytool.exe'

$aapt2 = Join-Path $bt 'aapt2.exe'
$d8 = Join-Path $bt 'd8.bat'
$zipalign = Join-Path $bt 'zipalign.exe'
$apksigner = Join-Path $bt 'apksigner.bat'

$classes = Join-Path $build 'classes'
$gen = Join-Path $build 'gen'
$dexdir = Join-Path $build 'dex'
$out = Join-Path $build 'output'
$compiled = Join-Path $build 'compiled.zip'
$keystore = Join-Path $build 'debug.keystore'

Remove-Item -Recurse -Force $classes, $gen, $dexdir, $out -ErrorAction SilentlyContinue
Remove-Item -Force $compiled -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $classes, $gen, $dexdir, $out | Out-Null

Write-Host '[1/7] compile resources'
& $aapt2 compile --dir $res -o $compiled
if ($LASTEXITCODE -ne 0) { throw 'aapt2 compile failed' }

Write-Host '[2/7] link resources'
& $aapt2 link -o (Join-Path $out 'unsigned.apk') -I $androidJar --manifest $manifestToUse -R $compiled --java $gen --auto-add-overlay
if ($LASTEXITCODE -ne 0) { throw 'aapt2 link failed' }

Write-Host '[3/7] compile java'
$javaFiles = @(Get-ChildItem -Path $src -Recurse -Filter *.java | ForEach-Object { $_.FullName })
$rFiles = @(Get-ChildItem -Path $gen -Recurse -Filter *.java | ForEach-Object { $_.FullName })
$lambdaStubs = Join-Path $bt 'core-lambda-stubs.jar'
& $javac -source 1.8 -target 1.8 -bootclasspath "$androidJar;$lambdaStubs" -encoding UTF-8 -Xlint:-options -d $classes @javaFiles @rFiles
if ($LASTEXITCODE -ne 0) { throw 'javac failed' }

Write-Host '[4/7] dex'
$classFiles = @(Get-ChildItem -Path $classes -Recurse -Filter *.class | ForEach-Object { $_.FullName })
& $d8 --min-api 26 --lib $androidJar --output $dexdir @classFiles
if ($LASTEXITCODE -ne 0) { throw 'd8 failed' }

Write-Host '[5/7] package dex'
& $jarTool uf (Join-Path $out 'unsigned.apk') -C $dexdir 'classes.dex'
if ($LASTEXITCODE -ne 0) { throw 'jar failed' }

Write-Host '[6/7] align'
& $zipalign -f 4 (Join-Path $out 'unsigned.apk') (Join-Path $out 'aligned.apk')
if ($LASTEXITCODE -ne 0) { throw 'zipalign failed' }

Write-Host '[7/7] sign'
if (-not (Test-Path $keystore)) {
    & $keytool -genkeypair -keystore $keystore -storetype JKS -storepass android -keypass android `
        -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 `
        -dname 'CN=Android Debug,O=Android,C=US'
    if ($LASTEXITCODE -ne 0) { throw 'keytool failed' }
}
& $apksigner sign --ks $keystore --ks-pass pass:android --key-pass pass:android `
    --out (Join-Path $out 'app-release.apk') (Join-Path $out 'aligned.apk')
if ($LASTEXITCODE -ne 0) { throw 'apksigner sign failed' }

& $apksigner verify --print-certs (Join-Path $out 'app-release.apk')
if ($LASTEXITCODE -ne 0) { throw 'apksigner verify failed' }

& $aapt2 dump badging (Join-Path $out 'app-release.apk') | Select-Object -First 6

$finalName = if ($Debuggable) { 'WeightDiary-test.apk' } else { 'WeightDiary-v1.2.apk' }
$final = Join-Path $root $finalName
Copy-Item (Join-Path $out 'app-release.apk') $final -Force
Write-Host ''
Write-Host ('DONE -> ' + $final)
