param([Parameter(Mandatory=$true)][string]$DatabaseName)
$ErrorActionPreference='Stop'
if ($DatabaseName -notmatch '^uninest_upgrade_check_[a-f0-9]{12}$') { throw 'Disposable test database required' }
. (Join-Path $PSScriptRoot 'java-env.ps1')
$root=Split-Path -Parent $PSScriptRoot
$preview=Join-Path $root 'test-runtime\funding-preview'
if(Get-NetTCPConnection -LocalPort 8092 -State Listen -ErrorAction SilentlyContinue){throw 'Port 8092 already in use'}
$uri=[Uri]($env:UNINEST_DB_URL -replace '^jdbc:','')
$env:MYSQL_PWD=$env:UNINEST_DB_PASSWORD
try {
    "INSERT INTO rooms(room_id,landlord_id,room_number,room_type,location,price,capacity,availability_status,approval_status) VALUES(70,1,'Journey self-funded','single_room','Test',1000,1,'available','approved'),(80,1,'Journey funded','single_room','Test',1000,1,'available','approved');" | & 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe' "--host=$($uri.Host)" "--port=$($uri.Port)" "--user=$env:UNINEST_DB_USER" "--database=$DatabaseName"
    if($LASTEXITCODE -ne 0){throw 'Test fixtures failed'}
} finally {Remove-Item Env:MYSQL_PWD}
foreach($folder in @('conf','webapps','logs','temp','work')){New-Item -ItemType Directory -Force -Path (Join-Path $preview $folder)|Out-Null}
Copy-Item -LiteralPath "$env:CATALINA_HOME\conf\web.xml" -Destination "$preview\conf\web.xml" -Force
[xml]$server=Get-Content -LiteralPath "$root\test-runtime\upgrade-preview\conf\server.xml"
$server.Server.Service.Connector.SetAttribute('port','8092')
$server.Save("$preview\conf\server.xml")
Copy-Item -LiteralPath "$root\target\uninest.war" -Destination "$preview\webapps\uninest.war" -Force
$env:UNINEST_DB_URL="jdbc:mysql://$($uri.Host):$($uri.Port)/${DatabaseName}?serverTimezone=Africa/Johannesburg"
$env:UNINEST_DEMO_PAYMENTS='true'
$arguments=@('-Dcatalina.home="'+$env:CATALINA_HOME+'"','-Dcatalina.base="'+$preview+'"','-Djava.io.tmpdir="'+$preview+'\temp"','-cp','"'+$env:CATALINA_HOME+'\bin\bootstrap.jar;'+$env:CATALINA_HOME+'\bin\tomcat-juli.jar"','org.apache.catalina.startup.Bootstrap','start')
$process=Start-Process -FilePath "$env:JAVA_HOME\bin\java.exe" -ArgumentList $arguments -WindowStyle Hidden -PassThru -RedirectStandardOutput "$preview\logs\stdout.log" -RedirectStandardError "$preview\logs\stderr.log"
Write-Output "Disposable funding preview PID: $($process.Id); http://localhost:8092/uninest/"
