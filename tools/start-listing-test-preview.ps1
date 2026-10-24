param([Parameter(Mandatory=$true)][string]$DatabaseName)
$ErrorActionPreference='Stop'
if ($DatabaseName -notmatch '^uninest_upgrade_check_[a-f0-9]{12}$') { throw 'Disposable test database required' }
. (Join-Path $PSScriptRoot 'java-env.ps1')
$root=Split-Path -Parent $PSScriptRoot
$preview=Join-Path $root 'test-runtime\listing-preview'
if(Get-NetTCPConnection -LocalPort 8093 -State Listen -ErrorAction SilentlyContinue){throw 'Port 8093 already in use'}
$uri=[Uri]($env:UNINEST_DB_URL -replace '^jdbc:','')
foreach($folder in @('conf','webapps','logs','temp','work','uploads')){New-Item -ItemType Directory -Force -Path (Join-Path $preview $folder)|Out-Null}
Copy-Item -LiteralPath "$env:CATALINA_HOME\conf\web.xml" -Destination "$preview\conf\web.xml" -Force
[xml]$server=Get-Content -LiteralPath "$root\test-runtime\upgrade-preview\conf\server.xml"
$server.Server.Service.Connector.SetAttribute('port','8093')
$server.Save("$preview\conf\server.xml")
Copy-Item -LiteralPath "$root\target\uninest.war" -Destination "$preview\webapps\uninest.war" -Force
$builder=[UriBuilder]$uri
$builder.Path='/'+$DatabaseName
$env:UNINEST_DB_URL='jdbc:'+$builder.Uri.AbsoluteUri
$env:UNINEST_UPLOAD_DIR=Join-Path $preview 'uploads'
$env:UNINEST_DEMO_PAYMENTS='false'
$arguments=@('-Dcatalina.home="'+$env:CATALINA_HOME+'"','-Dcatalina.base="'+$preview+'"','-Djava.io.tmpdir="'+$preview+'\temp"','-cp','"'+$env:CATALINA_HOME+'\bin\bootstrap.jar;'+$env:CATALINA_HOME+'\bin\tomcat-juli.jar"','org.apache.catalina.startup.Bootstrap','start')
$process=Start-Process -FilePath "$env:JAVA_HOME\bin\java.exe" -ArgumentList $arguments -WindowStyle Hidden -PassThru -RedirectStandardOutput "$preview\logs\stdout.log" -RedirectStandardError "$preview\logs\stderr.log"
Write-Output "Disposable listing preview PID: $($process.Id); http://localhost:8093/uninest/"
