param(
    [Parameter(Mandatory = $true)]
    [string] $JavaHome
)

$ErrorActionPreference = 'Stop'
$repository = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$java = Join-Path $JavaHome 'bin\java.exe'
$jar = Join-Path $repository 'target\quarkus-app\quarkus-run.jar'
$password = 'synthetic-startup-password'
$salt = [byte[]]::new(16)
[Security.Cryptography.RandomNumberGenerator]::Fill($salt)
$derived = [Security.Cryptography.Rfc2898DeriveBytes]::Pbkdf2(
    [Text.Encoding]::UTF8.GetBytes($password), $salt, 600000,
    [Security.Cryptography.HashAlgorithmName]::SHA256, 32)
$hash = 'pbkdf2-sha256$600000$' + [Convert]::ToBase64String($salt) +
    '$' + [Convert]::ToBase64String($derived)
$key = [byte[]]::new(32)
[Security.Cryptography.RandomNumberGenerator]::Fill($key)
$encodedKey = [Convert]::ToBase64String($key)
$results = [Collections.Generic.List[string]]::new()

function Start-Probe($Case) {
    $database = Join-Path $repository ('target\startup-' + [Guid]::NewGuid().ToString('N') + '.db')
    $configuration = @{
        'auth.db.url' = 'jdbc:sqlite:' + $database
        'auth.db.migrate-plaintext' = 'false'
        'auth.password.iterations' = '600000'
        'auth.token.secret' = $encodedKey
        'auth.cors.origins' = 'https://allowed.example'
        'auth.seed.student-hash' = $hash
        'auth.seed.trainer-hash' = $hash
        'quarkus.http.host' = '127.0.0.1'
        'quarkus.http.port' = '0'
    }
    if ($Case.Omit) { $configuration.Remove($Case.Omit) }
    if ($Case.Secret) { $configuration['auth.token.secret'] = $Case.Secret }
    if ($Case.Origins) { $configuration['auth.cors.origins'] = $Case.Origins }
    $info = [Diagnostics.ProcessStartInfo]::new($java)
    $info.WorkingDirectory = $repository
    $info.UseShellExecute = $false
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    foreach ($name in @('AUTH_DB_URL', 'AUTH_TOKEN_SECRET', 'AUTH_CORS_ORIGINS',
            'AUTH_STUDENT_PASSWORD_HASH', 'AUTH_TRAINER_PASSWORD_HASH',
            'AUTH_DB_MIGRATE_PLAINTEXT', 'JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS')) {
        $null = $info.Environment.Remove($name)
    }
    foreach ($item in $configuration.GetEnumerator()) {
        $info.ArgumentList.Add('-D' + $item.Key + '=' + $item.Value)
    }
    $info.ArgumentList.Add('-jar')
    $info.ArgumentList.Add($jar)
    return @{ Process = [Diagnostics.Process]::Start($info); Database = $database }
}

function Stop-Probe($Probe) {
    if (-not $Probe.Process.HasExited) {
        Stop-Process -Id $Probe.Process.Id
        $Probe.Process.WaitForExit()
    }
    $Probe.Process.Dispose()
    foreach ($path in @($Probe.Database, ($Probe.Database + '-wal'),
            ($Probe.Database + '-shm'), ($Probe.Database + '-journal'))) {
        if (Test-Path -LiteralPath $path) { Remove-Item -LiteralPath $path }
    }
}

$cases = @(
    @{ Name = 'missing-secret'; Omit = 'auth.token.secret'; Expected = 'AUTH_TOKEN_SECRET|auth.token.secret' },
    @{ Name = 'invalid-base64-secret'; Secret = '?'; Expected = 'Base64 encoded' },
    @{ Name = 'short-secret'; Secret = 'dGVzdA=='; Expected = 'at least 256 bits' },
    @{ Name = 'missing-cors'; Omit = 'auth.cors.origins'; Expected = 'AUTH_CORS_ORIGINS|auth.cors.origins' },
    @{ Name = 'wildcard-cors'; Origins = '*'; Expected = 'explicit HTTP' },
    @{ Name = 'missing-seed'; Omit = 'auth.seed.student-hash'; Expected = 'AUTH_STUDENT_PASSWORD_HASH|auth.seed.student-hash' }
)
foreach ($case in $cases) {
    $probe = Start-Probe $case
    try {
        $stdout = $probe.Process.StandardOutput.ReadToEndAsync()
        $stderr = $probe.Process.StandardError.ReadToEndAsync()
        if (-not $probe.Process.WaitForExit(30000)) {
            throw "Startup case timed out: $($case.Name)"
        }
        $output = $stdout.Result + $stderr.Result
        if ($probe.Process.ExitCode -eq 0 -or $output -notmatch $case.Expected) {
            throw "Expected configuration failure not observed: $($case.Name)"
        }
        $results.Add("$($case.Name): PASS (explicit failure, exit=$($probe.Process.ExitCode))")
    } finally {
        Stop-Probe $probe
    }
}

$probe = Start-Probe @{}
try {
    $stderr = $probe.Process.StandardError.ReadToEndAsync()
    $lineTask = $probe.Process.StandardOutput.ReadLineAsync()
    $timer = [Diagnostics.Stopwatch]::StartNew()
    $port = $null
    while ($timer.Elapsed.TotalSeconds -lt 30 -and -not $probe.Process.HasExited) {
        if (-not $lineTask.Wait(100)) { continue }
        $line = $lineTask.Result
        if ($line -match 'Listening on: http://127\.0\.0\.1:(\d+)') {
            $port = [int] $Matches[1]
            break
        }
        $lineTask = $probe.Process.StandardOutput.ReadLineAsync()
    }
    if (-not $port) { throw 'Configured app did not become ready on loopback' }
    $baseUrl = "http://127.0.0.1:$port"
    $health = Invoke-RestMethod "$baseUrl/q/health" -TimeoutSec 10
    if ($health.status -ne 'UP') { throw 'Health probe failed' }
    $body = @{ username = 'student'; password = $password } | ConvertTo-Json
    $login = Invoke-RestMethod "$baseUrl/auth/login" -Method Post `
        -ContentType 'application/json' -Body $body -TimeoutSec 10
    if ($login.expiresIn -ne 3600 -or $login.user.username -ne 'student' `
            -or $login.token.Split('.').Length -ne 3 `
            -or $login.debug -ne 'authenticated-with-legacy-flow') {
        throw 'Packaged login response did not preserve the contract'
    }
    $results.Add('configured-startup: PASS (loopback readiness, health UP, login 200 and signed token)')
    $invalidBody = @{ username = 'synthetic-rejected-user'; password = 'synthetic-wrong-password' } |
        ConvertTo-Json
    $rejected = Invoke-WebRequest "$baseUrl/auth/login" -Method Post `
        -ContentType 'application/json' -Body $invalidBody -TimeoutSec 10 -SkipHttpErrorCheck
    $errorBody = $rejected.Content | ConvertFrom-Json
    if ($rejected.StatusCode -ne 401 -or $errorBody.message -ne 'Invalid credentials' `
            -or @($errorBody.PSObject.Properties).Count -ne 1) {
        throw 'Packaged login did not return the approved generic 401'
    }
    $results.Add('configured-invalid-login: PASS (401 with generic message only)')
} finally {
    Stop-Probe $probe
}
$results | Tee-Object -FilePath (Join-Path $PSScriptRoot 'startup-results.txt')
