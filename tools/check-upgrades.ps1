$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'java-env.ps1')
$root = Split-Path -Parent $PSScriptRoot
& "$env:MAVEN_HOME\bin\mvn.cmd" "-Dmaven.repo.local=$root\.m2\repository" -o -f "$root\pom.xml" package
if ($LASTEXITCODE -ne 0) { throw 'Build failed' }
$classpath = "$root\target\classes;$root\target\test-classes;$root\target\uninest\WEB-INF\lib\*;$root\.m2\repository\javax\servlet\javax.servlet-api\4.0.1\javax.servlet-api-4.0.1.jar"
& "$env:JAVA_HOME\bin\java.exe" -ea -cp $classpath com.uninest.UpgradeChecks
if ($LASTEXITCODE -ne 0) { throw 'Regression checks failed' }
& "$env:JAVA_HOME\bin\java.exe" -ea -cp $classpath com.uninest.SignoutChecks
if ($LASTEXITCODE -ne 0) { throw 'Sign-out checks failed' }
& "$env:JAVA_HOME\bin\java.exe" -ea -cp $classpath com.uninest.BookingCapacityChecks
if ($LASTEXITCODE -ne 0) { throw 'Booking capacity checks failed' }
& "$env:JAVA_HOME\bin\java.exe" -ea -cp $classpath com.uninest.LoginThrottleChecks
if ($LASTEXITCODE -ne 0) { throw 'Login throttle checks failed' }
& "$env:JAVA_HOME\bin\java.exe" -ea -cp $classpath com.uninest.CsrfChecks
if ($LASTEXITCODE -ne 0) { throw 'CSRF checks failed' }
& "$env:JAVA_HOME\bin\java.exe" -ea -cp $classpath com.uninest.MessageDeliveryChecks
if ($LASTEXITCODE -ne 0) { throw 'Messaging checks failed' }
& "$env:JAVA_HOME\bin\java.exe" -ea -cp $classpath com.uninest.RecoveryLogChecks
if ($LASTEXITCODE -ne 0) { throw 'Password-recovery log checks failed' }
$originalUploads = $env:UNINEST_UPLOAD_DIR
try {
    $env:UNINEST_UPLOAD_DIR = Join-Path $root 'test-runtime\attachment-checks'
    & "$env:JAVA_HOME\bin\java.exe" -ea -cp $classpath com.uninest.AttachmentChecks
    if ($LASTEXITCODE -ne 0) { throw 'Attachment checks failed' }
} finally { $env:UNINEST_UPLOAD_DIR = $originalUploads }
