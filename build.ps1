# Compile everything into out/ and run the demos from the repo root.
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

if (Test-Path out) { Remove-Item out -Recurse -Force }
$sources = Get-ChildItem -Path src/imgformats -Filter *.java | ForEach-Object { $_.FullName }
javac -d out $sources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

# ComparisonDemo goes last so it sees the sample files the other demos wrote.
# JpegInspector is the JPEG entry point: it writes the sample, then prints the table.
foreach ($demo in "FoundationsDemo", "PngDemo", "PngFilterDemo", "JpegInspector", "JpegDctDemo", "GifDemo", "GifLzwDemo", "ComparisonDemo") {
    if (Test-Path "out/imgformats/$demo.class") {
        Write-Host "== $demo =="
        java "-Djava.awt.headless=true" -cp out "imgformats.$demo"
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
        Write-Host ""
    }
    else {
        Write-Host "== $demo`: class not found, skipped =="
    }
}