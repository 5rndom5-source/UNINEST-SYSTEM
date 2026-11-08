param(
    [Parameter(Mandatory = $true)][string]$Email
)
# Approves ONE pending admin request by email, the same way an active admin's approval does.
# Use it for the first admin on a database that has no active admin yet; afterwards approve
# new admins from the portal's "Admin access requests" page instead.
# Only a pending admin account with an admins row can be activated; students and landlords cannot.
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
if (-not $env:UNINEST_DB_PASSWORD) { . (Join-Path $PSScriptRoot 'java-env.ps1') }
foreach ($name in 'UNINEST_DB_URL', 'UNINEST_DB_USER', 'UNINEST_DB_PASSWORD') {
    if (-not [Environment]::GetEnvironmentVariable($name)) { throw "$name is not set. Set the database settings first." }
}
if ($env:UNINEST_DB_URL -notmatch '^jdbc:mysql://([^:/?]+)(?::(\d+))?/([^?]+)') { throw 'UNINEST_DB_URL is not a MySQL JDBC URL.' }
$dbHost = $matches[1]; $port = if ($matches[2]) { $matches[2] } else { '3306' }; $database = $matches[3]
$mysql = (Get-Command mysql.exe -ErrorAction SilentlyContinue).Source
if (-not $mysql) { $mysql = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe' }
if (-not (Test-Path -LiteralPath $mysql)) { throw 'mysql.exe was not found. Install the MySQL client or add it to PATH.' }

$safeEmail = $Email.Trim().Replace("'", "''")
$adminState = "SELECT u.status FROM users u JOIN admins a ON a.user_id = u.user_id WHERE u.email = '$safeEmail' AND u.role = 'admin';"
function Invoke-Sql([string]$statement) {
    $output = & $mysql -h $dbHost -P $port -u $env:UNINEST_DB_USER -D $database -N -B -e $statement
    if ($LASTEXITCODE -ne 0) { throw 'The database command failed.' }
    return "$output".Trim()
}
$env:MYSQL_PWD = $env:UNINEST_DB_PASSWORD
try {
    $before = Invoke-Sql $adminState
    if ($before -ne 'pending_verification') {
        if ($before -eq 'active') { Write-Host "Nothing changed: $Email is already an active admin." }
        elseif ($before) { Write-Host "Nothing changed: $Email is '$before', not a pending request." }
        else { Write-Host "Nothing changed: no admin request exists for $Email. Register it first at Admin/web/jsp/Register.html." }
        return
    }
    Invoke-Sql @"
UPDATE users u JOIN admins a ON a.user_id = u.user_id
SET u.status = 'active', a.admin_position = 'Administrator'
WHERE u.email = '$safeEmail' AND u.role = 'admin' AND u.status = 'pending_verification';
"@ | Out-Null
    if ((Invoke-Sql $adminState) -eq 'active') { Write-Host "Approved: $Email can now sign in as an admin." }
    else { throw "The approval did not take effect for $Email." }
} finally { Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue }
