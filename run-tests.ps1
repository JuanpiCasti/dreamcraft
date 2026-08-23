<#
.SYNOPSIS
  Suite de pruebas local de DreamCraftProtection — punto de entrada unico.

.DESCRIPTION
  Capa A (unitaria): gradlew build corre los tests JUnit puros.
  Capa B (integracion real): levanta el server Docker con el arnes
  DreamCraftTestHarness, que ejecuta escenarios contra los plugins reales
  y escribe data/dreamcraft-test/results.json + report.txt.

.USAGE
  powershell -ExecutionPolicy Bypass -File run-tests.ps1              # todo
  powershell -ExecutionPolicy Bypass -File run-tests.ps1 -UnitOnly    # solo JUnit
  powershell -ExecutionPolicy Bypass -File run-tests.ps1 -NoReset     # conserva datos del plugin

.EXIT CODES
  0 = todo OK   1 = fallos en escenarios/tests   2 = infraestructura (timeout/boot)
#>
param(
    [switch]$UnitOnly,
    [switch]$SkipBuild,
    [switch]$NoReset
)
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot

function Fail($msg) { Write-Host "`n[X] $msg" -ForegroundColor Red; exit 1 }

Write-Host "== 1/4 Compilando (incluye capa unitaria JUnit) ==" -ForegroundColor Cyan
if (-not $SkipBuild) {
    Push-Location $root
    & .\gradlew.bat --console=plain build
    $buildExit = $LASTEXITCODE
    Pop-Location
    if ($buildExit -ne 0) { Fail "La compilacion o los tests unitarios fallaron (exit $buildExit)" }
} else {
    Write-Host "(skip build)"
}
if ($UnitOnly) { Write-Host "`n[OK] Capa unitaria verde." -ForegroundColor Green; exit 0 }

Write-Host "== 2/5 Desplegando jar del plugin ==" -ForegroundColor Cyan
$jar = Get-ChildItem (Join-Path $root "build\libs") -Filter "*.jar" |
    Where-Object { $_.Name -notlike "*-sources*" -and $_.Name -notlike "*-javadoc*" } |
    Select-Object -First 1
if (-not $jar) { Fail "No se encontro el jar del plugin en build\libs (compilo con -SkipBuild sin jar previo?)" }
Copy-Item $jar.FullName (Join-Path $root "data\plugins\DreamCraftProtection.jar") -Force
Write-Host ("  - desplegado {0}" -f $jar.Name)
# El harness se monta directo desde harness/build/libs via docker-compose (volumen ro).

Write-Host "== 3/5 Reseteando fixtures de integracion ==" -ForegroundColor Cyan
$plgData = Join-Path $root "data\plugins\DreamCraftProtection"
if (-not $NoReset) {
    foreach ($f in 'wards.yml','cities.yml','estates.yml','claims.yml','treasuries.yml','nucleus-claims.yml') {
        $p = Join-Path $plgData $f
        if (Test-Path $p) { Remove-Item $p -Force; Write-Host "  - reset $f" }
    }
    $ze = Join-Path $plgData "zone-edits"
    if (Test-Path $ze) { Get-ChildItem $ze | Remove-Item -Force; Write-Host "  - reset zone-edits/" }
} else {
    Write-Host "(skip reset: se conservan wards/ciudades/estates actuales)"
}

$out = Join-Path $root "data\dreamcraft-test"
if (Test-Path $out) { Remove-Item $out -Recurse -Force }

Write-Host "== 4/5 Levantando server de pruebas ==" -ForegroundColor Cyan
Push-Location $root
# PS5.1 convierte el stderr de herramientas nativas en errores fatales con
# ErrorActionPreference=Stop → se relaja solo para esta llamada.
$prevEap = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
docker compose up -d --force-recreate mc | Out-Null
$ErrorActionPreference = $prevEap
Pop-Location

Write-Host "== 5/5 Esperando resultados (timeout 420s) ==" -ForegroundColor Cyan
$deadline = (Get-Date).AddSeconds(420)
while ((Get-Date) -lt $deadline) {
    if ((Test-Path (Join-Path $out "results.json")) -and (Test-Path (Join-Path $out "report.txt"))) { break }
    Start-Sleep -Seconds 5
}
$resultsJson = Join-Path $out "results.json"
if (-not (Test-Path $resultsJson)) {
    Write-Host "`nUltimas lineas del server:" -ForegroundColor Yellow
    $prevEap = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
    docker logs --tail 25 dreamcraft-mc-1 2>&1 | ForEach-Object { ($_ -replace "\x1b\[[0-9;]*m","") }
    $ErrorActionPreference = $prevEap
    Fail "Timeout esperando results.json (el arnes no termino la suite)"
}

$json = Get-Content $resultsJson -Raw | ConvertFrom-Json
$s = $json.summary
Write-Host ""
Write-Host "======================================================" 
Write-Host (" Suite: {0} escenarios -> {1} PASS / {2} FAIL / {3} PROBE" -f $s.total, $s.pass, $s.fail, $s.probe)
Write-Host "======================================================" 
foreach ($sc in $json.scenarios) {
    switch ($sc.status) {
        "PASS"  { Write-Host (" [PASS]  {0}" -f $sc.name) -ForegroundColor Green }
        "PROBE" { Write-Host (" [PROBE] {0}" -f $sc.name) -ForegroundColor DarkCyan }
        default {
            Write-Host (" [FAIL]  {0}" -f $sc.name) -ForegroundColor Red
            if ($sc.expected) { Write-Host ("          esperado : {0}" -f $sc.expected) -ForegroundColor Gray }
            if ($sc.actual)   { Write-Host ("          observado: {0}" -f ($sc.actual -split "\n")[0]) -ForegroundColor Gray }
            if ($sc.hint)     { Write-Host ("          pista    : {0}" -f $sc.hint) -ForegroundColor Yellow }
        }
    }
}
if ($s.fail -gt 0) {
    Write-Host ""
    Write-Host "[X] $($s.fail) escenario(s) fallaron. Detalle completo: data\dreamcraft-test\report.txt" -ForegroundColor Red
    exit 1
}
Write-Host ""
Write-Host "[OK] Suite verde." -ForegroundColor Green
exit 0
