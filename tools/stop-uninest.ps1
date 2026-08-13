$ErrorActionPreference = "Stop"

. (Join-Path $PSScriptRoot "java-env.ps1")

if (Test-Path -LiteralPath $env:CATALINA_HOME) {
    & cmd.exe /c "`"$env:CATALINA_HOME\bin\shutdown.bat`""
}
