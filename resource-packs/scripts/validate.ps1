$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$packRoot = Join-Path $root "packs\dreamcraft"
$packMeta = Join-Path $packRoot "pack.mcmeta"
$assetsRoot = Join-Path $packRoot "assets\dreamcraft"

if (-not (Test-Path -LiteralPath $packRoot -PathType Container)) {
  throw "Missing resource pack root: $packRoot"
}

if (-not (Test-Path -LiteralPath $assetsRoot -PathType Container)) {
  throw "Missing assets namespace: $assetsRoot"
}

if (-not (Test-Path -LiteralPath $packMeta -PathType Leaf)) {
  throw "Missing pack.mcmeta. Confirm the Minecraft version and pack_format before validation."
}

# pack.mcmeta must parse
Get-Content -LiteralPath $packMeta -Raw | ConvertFrom-Json | Out-Null

# every JSON in the namespace must parse
$jsonFiles = Get-ChildItem -LiteralPath $assetsRoot -Recurse -Filter *.json -File
foreach ($file in $jsonFiles) {
  try {
    Get-Content -LiteralPath $file.FullName -Raw | ConvertFrom-Json | Out-Null
  } catch {
    throw "Invalid JSON: $($file.FullName): $_"
  }
}

# item textures must be square and at most 32x32;
# font glyph textures may be non-square, at most 1024 wide and 256 tall
Add-Type -AssemblyName System.Drawing
$fontTextureRoot = Join-Path $assetsRoot "textures\font"
$pngFiles = Get-ChildItem -LiteralPath $assetsRoot -Recurse -Filter *.png -File
foreach ($file in $pngFiles) {
  $image = [System.Drawing.Image]::FromFile($file.FullName)
  try {
    $w = $image.Width
    $h = $image.Height
  } finally {
    $image.Dispose()
  }
  $isFontGlyph = $file.FullName.StartsWith("$fontTextureRoot\", [System.StringComparison]::OrdinalIgnoreCase)
  if ($isFontGlyph) {
    if ($h -gt 256) { throw "Font glyph taller than 256px: $($file.FullName) (${w}x${h})" }
    if ($w -gt 1024) { throw "Font glyph wider than 1024px: $($file.FullName) (${w}x${h})" }
  } else {
    if ($w -ne $h) { throw "Non-square texture: $($file.FullName) (${w}x${h})" }
    if ($w -gt 32 -or $h -gt 32) { throw "Texture larger than 32x32: $($file.FullName) (${w}x${h})" }
  }
}

Write-Output "Resource pack structure is valid."
Write-Output "JSON files checked: $($jsonFiles.Count). Textures checked: $($pngFiles.Count)."
