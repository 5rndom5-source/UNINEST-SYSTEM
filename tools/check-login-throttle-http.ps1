param([string]$BaseUrl = 'http://localhost:8091/uninest')
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http
$handler = New-Object System.Net.Http.HttpClientHandler
$handler.AllowAutoRedirect = $false
$client = New-Object System.Net.Http.HttpClient($handler)
$client.Timeout = [TimeSpan]::FromSeconds(20)
# Invalid-format synthetic identifier avoids password checks and never changes an account.
$identifier = 'throttle-check-' + [Guid]::NewGuid().ToString('N')
$paths = @('/LoginServlet','/AccountLoginServlet','/LandlordLoginServlet')
try {
    $tokenResponse=$client.GetAsync($BaseUrl+'/CsrfTokenServlet').GetAwaiter().GetResult()
    $token=($tokenResponse.Content.ReadAsStringAsync().GetAwaiter().GetResult() | ConvertFrom-Json).token
    $tokenResponse.Dispose()
    $client.DefaultRequestHeaders.Add('X-CSRF-Token',$token)
    for ($i=0; $i -lt 11; $i++) {
        $path=$paths[$i % 3]
        $field=if ($path -eq '/LandlordLoginServlet') { 'landlordEmail' } else { 'email' }
        $content=New-Object System.Net.Http.StringContent("$field=$identifier&password=invalid&role=student",[Text.Encoding]::UTF8,'application/x-www-form-urlencoded')
        $response=$client.PostAsync($BaseUrl+$path,$content).GetAwaiter().GetResult()
        try {
            $expected=if($i -lt 10){302}else{429}
            if([int]$response.StatusCode -ne $expected){throw "Attempt $i expected $expected, received $([int]$response.StatusCode)"}
            if($i -eq 10 -and -not $response.Headers.Contains('Retry-After')){throw 'Missing Retry-After'}
        } finally { $response.Dispose();$content.Dispose() }
    }
    Write-Output 'PASS: 11 live cross-route login throttle attempts'
} finally { $client.Dispose();$handler.Dispose() }
