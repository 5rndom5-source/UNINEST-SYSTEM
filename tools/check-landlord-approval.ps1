param([switch]$Browser)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'java-env.ps1')
$root=Split-Path -Parent $PSScriptRoot
$name='uninest_landlord_check_'+[Guid]::NewGuid().ToString('N').Substring(0,12)
$mysql='C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'
$localPassword=$env:UNINEST_LOCAL_DB_PASSWORD
if(-not $localPassword){$localPassword=$env:UNINEST_DB_PASSWORD}
# Always loopback and a fresh disposable database, never the configured production URL.
$schema=Get-Content -LiteralPath "$root\Database\Uninest_DB.sql" -Raw
$schema=$schema.Replace('CREATE DATABASE IF NOT EXISTS uninest_db','CREATE DATABASE IF NOT EXISTS '+$name).Replace('USE uninest_db;','USE '+$name+';')
$oldMysqlPassword=$env:MYSQL_PWD
try {
    $env:MYSQL_PWD=$localPassword
    $schema | & $mysql --host=127.0.0.1 --port=3306 --user=root
} finally {$env:MYSQL_PWD=$oldMysqlPassword}
if($LASTEXITCODE -ne 0){throw 'Disposable local database creation failed'}
$oldUrl=$env:UNINEST_DB_URL; $oldUser=$env:UNINEST_DB_USER; $oldPassword=$env:UNINEST_DB_PASSWORD
$oldUploads=$env:UNINEST_UPLOAD_DIR
try {
    $env:UNINEST_UPLOAD_DIR=Join-Path $root "test-runtime\$name\uploads"
    $env:UNINEST_DB_URL="jdbc:mysql://127.0.0.1:3306/$name`?serverTimezone=UTC"
    $env:UNINEST_DB_USER='root';$env:UNINEST_DB_PASSWORD=$localPassword
    $cp="$root\target\classes;$root\target\test-classes;$root\target\uninest\WEB-INF\lib\*;$root\.m2\repository\javax\servlet\javax.servlet-api\4.0.1\javax.servlet-api-4.0.1.jar"
    & "$env:JAVA_HOME\bin\java.exe" -ea -cp $cp com.uninest.LandlordApprovalChecks
    if($LASTEXITCODE -ne 0){throw 'Landlord checks failed'}
    & "$env:JAVA_HOME\bin\java.exe" -ea -cp $cp com.uninest.LandlordRegistrationChecks
    if($LASTEXITCODE -ne 0){throw 'Landlord registration checks failed'}
    if($Browser){ & (Join-Path $PSScriptRoot 'check-landlord-http.ps1') -TestDatabase $name }
    Write-Output "Disposable database retained: $name"
} finally {$env:UNINEST_DB_URL=$oldUrl;$env:UNINEST_DB_USER=$oldUser;$env:UNINEST_DB_PASSWORD=$oldPassword;$env:UNINEST_UPLOAD_DIR=$oldUploads}
