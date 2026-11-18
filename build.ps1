param([switch]$Test)
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    New-Item -ItemType Directory -Path out -Force | Out-Null
    $sources = @(Get-ChildItem -LiteralPath src -Filter '*.java' | ForEach-Object { $_.FullName })
    if ($Test) { $sources += @(Get-ChildItem -LiteralPath test -Filter '*.java' | ForEach-Object { $_.FullName }) }
    & javac --release 17 -encoding UTF-8 -Xlint:all -d out @sources
    if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed' }
    if ($Test) {
        & java '-Dfile.encoding=UTF-8' -cp out AcceptanceTest
        if ($LASTEXITCODE -ne 0) { throw 'Tests failed' }
        & java '-Dfile.encoding=UTF-8' -cp out GuiSmokeTest
        if ($LASTEXITCODE -ne 0) { throw 'GUI tests failed' }
    }
} finally { Pop-Location }
