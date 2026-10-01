<#
.SYNOPSIS
  Descobre a versao estavel mais recente do Minecraft e as versoes compativeis (Fabric, NeoForge, Litematica).
  Sem dependencias: so PowerShell 5.1+ e internet. Nao altera nenhum arquivo.
.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts/check-versions.ps1
  powershell -ExecutionPolicy Bypass -File scripts/check-versions.ps1 -Minecraft 26.3
#>
param([string]$Minecraft = "")

$ErrorActionPreference = "Stop"
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$headers = @{ "User-Agent" = "stashlink-check-versions (github.com/Leoascenci0/stashlink)" }
function Get-Json($url) { Invoke-RestMethod -Uri $url -Headers $headers -UseBasicParsing }

# 1. Versao do Minecraft (ultima estavel segundo o Fabric meta, ou a pedida)
$games = @(Get-Json "https://meta.fabricmc.net/v2/versions/game" | ForEach-Object { $_ })
if ($Minecraft -eq "") { $Minecraft = @($games | Where-Object { $_.stable })[0].version }
Write-Host "Minecraft: $Minecraft" -ForegroundColor Cyan

# 2. Fabric: loader estavel mais novo e Fabric API para esta versao
$loaders = @(Get-Json "https://meta.fabricmc.net/v2/versions/loader" | ForEach-Object { $_ } | Where-Object { $_.stable })
$loader = $loaders[0].version
$fapi = Get-Json ('https://api.modrinth.com/v2/project/fabric-api/version?game_versions=["' + $Minecraft + '"]&loaders=["fabric"]')
$fabricApi = if ($fapi) { @($fapi)[0].version_number } else { $null }

# 3. NeoForge: o maven lista todas as versoes; a linha 26.3 => "26.3.x.y" (sufixo -beta = nao estavel)
$xml = [xml](Invoke-WebRequest -Uri "https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml" -Headers $headers -UseBasicParsing).Content
$all = @($xml.metadata.versioning.versions.version)
$mcShort = $Minecraft -replace '^1\.', ''          # 1.21.1 -> 21.1 ; 26.3 -> 26.3
$neo = @($all | Where-Object { $_ -like "$mcShort.*" -and $_ -notlike "*-alpha*" })
$neoStable = $neo | Where-Object { $_ -notlike "*-beta*" } | Select-Object -Last 1
$neoLatest = $neo | Select-Object -Last 1
$neoPick = if ($neoStable) { $neoStable } else { $neoLatest }

# 4. NeoForm: so existe uma versao por Minecraft ("26.3-1"); o NeoForge DEVE usar a mesma
$form = Get-Json "https://maven.neoforged.net/api/maven/versions/releases/net/neoforged/neoform"
$formVers = @($form.versions | Where-Object { $_ -like "$Minecraft-*" })
$formPick = $formVers | Select-Object -Last 1

# 5. Litematica / Forgematica no Modrinth
function Get-Mod($slug, $loaderName) {
    $r = Get-Json ('https://api.modrinth.com/v2/project/' + $slug + '/version?game_versions=["' + $Minecraft + '"]&loaders=["' + $loaderName + '"]')
    if ($r) { "$(@($r)[0].version_number) [$(@($r)[0].version_type)]" } else { $null }
}
$litFabric = Get-Mod "litematica" "fabric"
$litNeo = Get-Mod "litematica" "neoforge"
$forgematica = Get-Mod "forgematica" "neoforge"

Write-Host ""
Write-Host "Fabric loader estavel : $loader"
Write-Host ("Fabric API            : " + $(if ($fabricApi) { $fabricApi } else { "NAO ENCONTRADA para $Minecraft" }))
Write-Host ("NeoForge              : " + $(if ($neoPick) { $neoPick } else { "NAO ENCONTRADO para $Minecraft" }) + $(if ($neoPick -and -not $neoStable) { "  (so beta disponivel)" }))
Write-Host ("NeoForm               : " + $(if ($formPick) { $formPick } else { "NAO ENCONTRADO para $Minecraft" }))
Write-Host ""
Write-Host "Litematica (Fabric)   : $(if ($litFabric) { $litFabric } else { 'sem build para esta versao' })"
Write-Host "Litematica (NeoForge) : $(if ($litNeo) { $litNeo } else { 'sem build' })"
Write-Host "Forgematica (NeoForge): $(if ($forgematica) { $forgematica } else { 'sem build' })"
Write-Host ""
Write-Host "Cole em gradle.properties (confira cada linha antes):" -ForegroundColor Yellow
Write-Host "minecraft_version=$Minecraft"
$parts = $Minecraft.Split(".")
$next = $parts[0] + "." + ([int]$parts[1] + 1)
Write-Host "minecraft_version_range=[$Minecraft, $next)"
Write-Host "neo_form_version=$formPick"
Write-Host "fabric_version=$fabricApi"
Write-Host "fabric_loader_version=$loader"
Write-Host "neoforge_version=$neoPick"
Write-Host ""
Write-Host "Lembretes: NeoForge e NeoForm precisam ser da MESMA versao do Minecraft; veja docs/UPDATING.md." -ForegroundColor DarkGray
