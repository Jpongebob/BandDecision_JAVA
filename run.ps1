param([switch]$Demo, [switch]$Gui)
$ErrorActionPreference = 'Stop'
# Gui is retained as a compatibility switch; all runs now open the GUI.
& (Join-Path $PSScriptRoot 'build.ps1')
if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed' }
$javaArguments = @('-Dfile.encoding=UTF-8', "-Dbanddecision.dataDir=$(Join-Path $PSScriptRoot 'data')", '-cp', (Join-Path $PSScriptRoot 'out'), 'Main')
if ($Demo) { $javaArguments += '--demo' }
& java @javaArguments
