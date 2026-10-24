param([switch]$DemoPayments)
$ErrorActionPreference = 'Stop'
$env:UNINEST_DEMO_PAYMENTS = if ($DemoPayments) { 'true' } else { 'false' }
. (Join-Path $PSScriptRoot 'java-env.ps1')
$root = Split-Path -Parent $PSScriptRoot
$preview = Join-Path $root 'test-runtime\upgrade-preview'
if (Get-NetTCPConnection -LocalPort 8091 -State Listen -ErrorAction SilentlyContinue) { throw 'Port 8091 is already in use' }
foreach ($folder in @('webapps','logs','temp','work')) { New-Item -ItemType Directory -Force -Path (Join-Path $preview $folder) | Out-Null }
Copy-Item -LiteralPath "$env:CATALINA_HOME\conf\web.xml" -Destination "$preview\conf\web.xml" -Force
Copy-Item -LiteralPath "$root\target\uninest.war" -Destination "$preview\webapps\uninest.war" -Force
$arguments = @('-Dcatalina.home="'+$env:CATALINA_HOME+'"', '-Dcatalina.base="'+$preview+'"', '-Djava.io.tmpdir="'+$preview+'\temp"', '-cp', '"'+$env:CATALINA_HOME+'\bin\bootstrap.jar;'+$env:CATALINA_HOME+'\bin\tomcat-juli.jar"', 'org.apache.catalina.startup.Bootstrap', 'start')
$process = Start-Process -FilePath "$env:JAVA_HOME\bin\java.exe" -ArgumentList $arguments -WindowStyle Hidden -PassThru -RedirectStandardOutput "$preview\logs\stdout.log" -RedirectStandardError "$preview\logs\stderr.log"
Write-Output "Preview PID: $($process.Id); URL: http://localhost:8091/uninest/"
