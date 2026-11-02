#requires -Version 7.0
param(
    [string]$BaseUrl = 'http://localhost:8080',
    [ValidateSet('NONE','MATERIAL_CHECK','MOCK_GENERATION','RESULT_ASSEMBLY')]
    [string]$FailAtStage = 'NONE',
    [ValidateRange(0,30000)][int]$DelayMs = 1500,
    [ValidateSet('NONE','MATERIAL_CHECK','MOCK_GENERATION','RESULT_ASSEMBLY')][string]$TransientFailAtStage = 'NONE',
    [ValidateRange(0,5)][int]$TransientFailureCount = 0,
    [switch]$ReplayAcceptance
)
$ErrorActionPreference = 'Stop'
if ($FailAtStage -ne 'NONE' -and $TransientFailureCount -gt 0) { throw 'Choose permanent failure or transient failure for this demo.' }
if ($TransientFailureCount -gt 0 -and $TransientFailAtStage -eq 'NONE') { throw 'Specify TransientFailAtStage.' }
$intentHeaders = @{ 'Idempotency-Key' = [Guid]::NewGuid().ToString('N') }
$sampleFile = [IO.Path]::GetTempFileName()
try {
    Invoke-RestMethod "$BaseUrl/api/session/demo" -Method Post -ContentType 'application/json' -Body '{"demoKey":"demo-a"}' -SessionVariable demoSession | Out-Null
    Invoke-WebRequest "$BaseUrl/files/samples/pet" -OutFile $sampleFile
    $asset = Invoke-RestMethod "$BaseUrl/api/assets" -Method Post -WebSession $demoSession -Form @{ templateId='demo-pet'; file=Get-Item -LiteralPath $sampleFile }
    $payload = @{
        templateId='demo-pet'; assetIds=@($asset.assetId)
        simulation=@{ failAtStage=$FailAtStage; materialCheckDelayMs=$DelayMs; mockGenerationDelayMs=$DelayMs; resultAssemblyDelayMs=$DelayMs; transientFailAtStage=$TransientFailAtStage; transientFailureCount=$TransientFailureCount }
    } | ConvertTo-Json -Depth 4
    $accepted = Invoke-RestMethod "$BaseUrl/api/tasks" -Method Post -ContentType 'application/json' -Body $payload -Headers $intentHeaders -WebSession $demoSession
    if ($ReplayAcceptance) {
        $replayed = Invoke-RestMethod "$BaseUrl/api/tasks" -Method Post -ContentType 'application/json' -Body $payload -Headers $intentHeaders -WebSession $demoSession
        if ($replayed.taskId -ne $accepted.taskId) { throw 'Idempotent replay returned another task.' }
        Write-Host "Idempotent replay verified: $($replayed.taskId)"
    }
    Write-Host "Accepted: $($accepted.taskId). The Java worker continues independently."
    $deadline = [DateTime]::UtcNow.AddMinutes(5)
    do {
        $task = Invoke-RestMethod "$BaseUrl/api/tasks/$($accepted.taskId)" -WebSession $demoSession
        Write-Host "$($task.status) / $($task.currentStage) / attempt $($task.attemptCount) of $($task.maxAttempts)"
        if ($task.status -in @('SUCCEEDED','FAILED')) { break }
        if ([DateTime]::UtcNow -ge $deadline) { throw 'Polling timed out; the server task may still be running.' }
        Start-Sleep -Milliseconds 500
    } while ($true)
    $task | ConvertTo-Json -Depth 8
    $expectedFailure = $FailAtStage
    if ($TransientFailAtStage -ne 'NONE' -and $TransientFailureCount -ge $task.maxAttempts) { $expectedFailure = $TransientFailAtStage }
    if ($expectedFailure -eq 'NONE' -and $task.status -ne 'SUCCEEDED') { throw 'Expected a successful task.' }
    if ($expectedFailure -ne 'NONE' -and ($task.status -ne 'FAILED' -or $task.failureStage -ne $expectedFailure)) { throw 'Expected failure at the configured stage.' }
    if ($task.status -eq 'SUCCEEDED') {
        $preview = Invoke-WebRequest "$BaseUrl$($task.result.url)" -WebSession $demoSession
        if ($preview.StatusCode -ne 200 -or $preview.Headers.'Content-Type' -notmatch 'image/png') { throw 'Result preview unavailable.' }
        Write-Host 'Result preview verified.'
    }
} finally {
    Remove-Item -LiteralPath $sampleFile -ErrorAction SilentlyContinue
}
