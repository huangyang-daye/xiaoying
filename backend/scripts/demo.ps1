#requires -Version 7.0
param(
    [string]$BaseUrl = 'http://localhost:8080',
    [ValidateSet('NONE','MATERIAL_CHECK','MOCK_GENERATION','RESULT_ASSEMBLY')]
    [string]$FailAtStage = 'NONE',
    [ValidateRange(0,30000)][int]$DelayMs = 1500
)
$ErrorActionPreference = 'Stop'
$sampleFile = [IO.Path]::GetTempFileName()
try {
    Invoke-RestMethod "$BaseUrl/api/session/demo" -Method Post -ContentType 'application/json' -Body '{"demoKey":"demo-a"}' -SessionVariable demoSession | Out-Null
    Invoke-WebRequest "$BaseUrl/files/samples/pet" -OutFile $sampleFile
    $asset = Invoke-RestMethod "$BaseUrl/api/assets" -Method Post -WebSession $demoSession -Form @{ templateId='demo-pet'; file=Get-Item -LiteralPath $sampleFile }
    $payload = @{
        templateId='demo-pet'; assetIds=@($asset.assetId)
        simulation=@{ failAtStage=$FailAtStage; materialCheckDelayMs=$DelayMs; mockGenerationDelayMs=$DelayMs; resultAssemblyDelayMs=$DelayMs }
    } | ConvertTo-Json -Depth 4
    $accepted = Invoke-RestMethod "$BaseUrl/api/tasks" -Method Post -ContentType 'application/json' -Body $payload -WebSession $demoSession
    Write-Host "Accepted: $($accepted.taskId). The Java worker continues independently."
    $deadline = [DateTime]::UtcNow.AddMinutes(5)
    do {
        $task = Invoke-RestMethod "$BaseUrl/api/tasks/$($accepted.taskId)" -WebSession $demoSession
        Write-Host "$($task.status) / $($task.currentStage)"
        if ($task.status -in @('SUCCEEDED','FAILED')) { break }
        if ([DateTime]::UtcNow -ge $deadline) { throw 'Polling timed out; the server task may still be running.' }
        Start-Sleep -Milliseconds 500
    } while ($true)
    $task | ConvertTo-Json -Depth 8
    if ($FailAtStage -eq 'NONE' -and $task.status -ne 'SUCCEEDED') { throw 'Expected a successful task.' }
    if ($FailAtStage -ne 'NONE' -and ($task.status -ne 'FAILED' -or $task.failureStage -ne $FailAtStage)) { throw 'Expected failure at the configured stage.' }
    if ($task.status -eq 'SUCCEEDED') {
        $preview = Invoke-WebRequest "$BaseUrl$($task.result.url)" -WebSession $demoSession
        if ($preview.StatusCode -ne 200 -or $preview.Headers.'Content-Type' -notmatch 'image/png') { throw 'Result preview unavailable.' }
        Write-Host 'Result preview verified.'
    }
} finally {
    Remove-Item -LiteralPath $sampleFile -ErrorAction SilentlyContinue
}
