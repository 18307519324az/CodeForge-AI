function ConvertTo-Utf8JsonBytes {
    param(
        [Parameter(Mandatory = $true)]
        [object]$Value
    )

    $json = $Value | ConvertTo-Json -Depth 10
    return [System.Text.UTF8Encoding]::new($false).GetBytes($json)
}

function Invoke-Utf8JsonRequest {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Method,
        [Parameter(Mandatory = $true)]
        [string]$Uri,
        [Parameter(Mandatory = $true)]
        [hashtable]$BodyObject,
        [hashtable]$Headers = @{}
    )

    $json = $BodyObject | ConvertTo-Json -Depth 10
    $utf8 = [System.Text.UTF8Encoding]::new($false)
    $bytes = $utf8.GetBytes($json)

    $requestHeaders = @{
        'Content-Type' = 'application/json; charset=utf-8'
        'Accept' = 'application/json'
    }
    foreach ($key in $Headers.Keys) {
        $requestHeaders[$key] = $Headers[$key]
    }

    $response = Invoke-WebRequest -Uri $Uri -Method $Method -Headers $requestHeaders -Body $bytes
    $responseText = [System.Text.Encoding]::UTF8.GetString($response.RawContentStream.ToArray())
    return $responseText | ConvertFrom-Json
}

function Invoke-Utf8JsonRestMethod {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Uri,
        [Parameter(Mandatory = $true)]
        [ValidateSet('Get', 'Post', 'Put', 'Patch', 'Delete')]
        [string]$Method,
        [hashtable]$Body,
        [hashtable]$Headers = @{}
    )

    if ($null -ne $Body) {
        return Invoke-Utf8JsonRequest -Method $Method -Uri $Uri -BodyObject $Body -Headers $Headers
    }

    $requestHeaders = @{
        'Accept' = 'application/json'
    }
    foreach ($key in $Headers.Keys) {
        $requestHeaders[$key] = $Headers[$key]
    }

    $response = Invoke-WebRequest -Uri $Uri -Method $Method -Headers $requestHeaders
    $responseText = [System.Text.Encoding]::UTF8.GetString($response.RawContentStream.ToArray())
    return $responseText | ConvertFrom-Json
}
