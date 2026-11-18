param([switch]$Demo)
$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot 'run.ps1') -Demo:$Demo
