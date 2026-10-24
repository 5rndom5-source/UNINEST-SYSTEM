param([string]$BaseUrl = 'http://localhost:8091/uninest')
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http
$handler = New-Object System.Net.Http.HttpClientHandler
$handler.AllowAutoRedirect = $false
$client = New-Object System.Net.Http.HttpClient($handler)
$client.Timeout = [TimeSpan]::FromSeconds(20)
$checks = 0
try {
    foreach ($path in @('/LogoutServlet?portal=admin','/LogoutServlet?portal=landlord','/LogoutServlet?portal=student','/LogoutServlet?portal=invalid','/LandlordLogoutServlet')) {
        $response = $client.GetAsync($BaseUrl+$path).GetAwaiter().GetResult()
        $html = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        if ([int]$response.StatusCode -ne 200 -or $html -notmatch "name='_csrf' value='([A-Za-z0-9_-]{43})'") { throw "Missing sign-out confirmation: $path" }
        $token=$Matches[1]
        $response.Dispose()
        $content=New-Object System.Net.Http.StringContent("_csrf=$token",[Text.Encoding]::UTF8,'application/x-www-form-urlencoded')
        $response = $client.PostAsync($BaseUrl+$path,$content).GetAwaiter().GetResult()
        $content.Dispose()
        if ([int]$response.StatusCode -ne 302 -or $response.Headers.Location.ToString() -notlike '*/createAccount/login.html?signedOut=1&*') { throw "Unexpected sign-out redirect: $path" }
        if (-not $response.Headers.CacheControl.NoStore) { throw "Missing no-store: $path" }
        $response.Dispose()
        $checks++
    }
    foreach ($path in @('/Admin/web/jsp/Logout.html','/Landlord/logout.html','/Logout.html','/LogoutPageServlet')) {
        $response = $client.GetAsync($BaseUrl+$path).GetAwaiter().GetResult()
        if ([int]$response.StatusCode -ne 302 -or $response.Headers.Location.ToString() -notlike '*/LogoutServlet') { throw "Legacy sign-out failed: $path" }
        $response.Dispose()
        $checks++
    }
    foreach ($path in @('/UserDashboard/index.html','/Landlord/listings.html','/Admin/web/jsp/Profile.html')) {
        $response = $client.GetAsync($BaseUrl+$path).GetAwaiter().GetResult()
        if ([int]$response.StatusCode -ne 302 -or $response.Headers.Location.ToString() -notlike '*/createAccount/login.html?*') { throw "Anonymous portal not blocked: $path" }
        $response.Dispose()
        $checks++
    }
    $response = $client.GetAsync($BaseUrl+'/createAccount/login.html?signedOut=1').GetAwaiter().GetResult()
    $html = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    if ([int]$response.StatusCode -ne 200) { throw 'Role chooser unavailable' }
    foreach ($option in @('Choose your role','value="student"','value="landlord"','value="admin"')) {
        if (-not $html.Contains($option)) { throw "Missing role choice: $option" }
        $checks++
    }
    $response.Dispose()
    Write-Output "PASS: $checks live HTTP sign-out checks"
} finally { $client.Dispose(); $handler.Dispose() }
