$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'java-env.ps1')
$root = Split-Path -Parent $PSScriptRoot
$databaseUri = [Uri]($env:UNINEST_DB_URL -replace '^jdbc:', '')
$env:MYSQL_PWD = $env:UNINEST_DB_PASSWORD
$testName = 'uninest_upgrade_check_' + [Guid]::NewGuid().ToString('N').Substring(0,12)
$mysql = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'
$mysqlArgs = @("--host=$($databaseUri.Host)","--port=$($databaseUri.Port)","--user=$env:UNINEST_DB_USER")
$schema = Get-Content -LiteralPath "$root\Database\Uninest_DB.sql" -Raw
$schema = $schema.Replace('CREATE DATABASE IF NOT EXISTS uninest_db','CREATE DATABASE IF NOT EXISTS '+$testName).Replace('USE uninest_db;', 'USE '+$testName+';')
$schema | & $mysql @mysqlArgs
if ($LASTEXITCODE -ne 0) { throw 'Disposable database setup failed' }
$originalUrl = $env:UNINEST_DB_URL
try {
    $builder = [UriBuilder]$databaseUri
    $builder.Path = '/'+$testName
    $env:UNINEST_DB_URL = 'jdbc:'+$builder.Uri.AbsoluteUri
    $classpath = "$root\target\classes;$root\target\test-classes;$root\target\uninest\WEB-INF\lib\*;$root\.m2\repository\javax\servlet\javax.servlet-api\4.0.1\javax.servlet-api-4.0.1.jar"
    & "$env:JAVA_HOME\bin\java.exe" -ea -cp $classpath com.uninest.DatabaseUpgradeChecks
    if ($LASTEXITCODE -ne 0) { throw 'Database integration checks failed' }
    Write-Output "Disposable test database retained: $testName"
} finally { $env:UNINEST_DB_URL = $originalUrl; Remove-Item Env:MYSQL_PWD }
