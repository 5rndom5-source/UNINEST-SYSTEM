$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot "java-env.ps1")

$mvn = Join-Path $env:MAVEN_HOME "bin\mvn.cmd"
$war = Join-Path $root "target\uninest.war"
$webapps = Join-Path $env:CATALINA_HOME "webapps"
$targetWar = Join-Path $webapps "uninest.war"
$localRepo = Join-Path $root ".m2\repository"

if (-not (Test-Path -LiteralPath $mvn)) {
    throw "Maven is not installed. Run .\tools\install-java-stack.ps1 first."
}

if (-not (Test-Path -LiteralPath $env:CATALINA_HOME)) {
    throw "Tomcat is not installed. Run .\tools\install-java-stack.ps1 first."
}

& $mvn `
    "-Dmaven.repo.local=$localRepo" `
    "-Dmaven.resolver.transport=wagon" `
    -f (Join-Path $root "pom.xml") clean package
if ($LASTEXITCODE -ne 0) {
    throw "Maven build failed with exit code $LASTEXITCODE"
}

New-Item -ItemType Directory -Force -Path $webapps | Out-Null
Copy-Item -LiteralPath $war -Destination $targetWar -Force

& cmd.exe /c "`"$env:CATALINA_HOME\bin\startup.bat`""

Write-Host ""
Write-Host "UniNest is starting at: http://localhost:8080/uninest/"
Write-Host "Admin direct page:      http://localhost:8080/uninest/Admin/web/jsp/Login.html"
Write-Host "Home page:              http://localhost:8080/uninest/HomePage/home.html"
