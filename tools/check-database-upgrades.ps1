$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'java-env.ps1')
$root = Split-Path -Parent $PSScriptRoot
$localPassword = $env:UNINEST_LOCAL_DB_PASSWORD
if (-not $localPassword) { $localPassword = $env:UNINEST_DB_PASSWORD }
$originalMysqlPassword = $env:MYSQL_PWD
$testName = 'uninest_upgrade_check_' + [Guid]::NewGuid().ToString('N').Substring(0,12)
$mysql = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'
# Never derive the integration-test server from production configuration.
$mysqlArgs = @('--host=127.0.0.1','--port=3306','--user=root')
$schema = Get-Content -LiteralPath "$root\Database\Uninest_DB.sql" -Raw
$schema = $schema.Replace('CREATE DATABASE IF NOT EXISTS uninest_db','CREATE DATABASE IF NOT EXISTS '+$testName).Replace('USE uninest_db;', 'USE '+$testName+';')
try {
    $env:MYSQL_PWD = $localPassword
    $schema | & $mysql @mysqlArgs
} finally { $env:MYSQL_PWD = $originalMysqlPassword }
if ($LASTEXITCODE -ne 0) { throw 'Disposable database setup failed' }
$originalUrl = $env:UNINEST_DB_URL
$originalUser = $env:UNINEST_DB_USER
$originalPassword = $env:UNINEST_DB_PASSWORD
$originalUploads = $env:UNINEST_UPLOAD_DIR
try {
    $env:UNINEST_DB_URL = "jdbc:mysql://127.0.0.1:3306/${testName}?serverTimezone=UTC"
    $env:UNINEST_DB_USER = 'root'
    $env:UNINEST_DB_PASSWORD = $localPassword
    $env:UNINEST_UPLOAD_DIR = Join-Path $root "test-runtime\$testName\uploads"
    $classpath = "$root\target\classes;$root\target\test-classes;$root\target\uninest\WEB-INF\lib\*;$root\.m2\repository\javax\servlet\javax.servlet-api\4.0.1\javax.servlet-api-4.0.1.jar"
    & "$env:JAVA_HOME\bin\java.exe" -ea -cp $classpath com.uninest.DatabaseUpgradeChecks
    if ($LASTEXITCODE -ne 0) { throw 'Database integration checks failed' }
    Write-Output "Disposable test database retained: $testName"
} finally {
    $env:UNINEST_DB_URL = $originalUrl
    $env:UNINEST_DB_USER = $originalUser
    $env:UNINEST_DB_PASSWORD = $originalPassword
    $env:UNINEST_UPLOAD_DIR = $originalUploads
}
