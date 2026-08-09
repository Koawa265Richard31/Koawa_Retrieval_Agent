$ErrorActionPreference = 'Continue'
$origin = [Uri]'https://gakuen.idolmaster-official.jp/'
$root = Join-Path (Get-Location) 'assets\gakuen-official'
$targetPage = 'https://gakuen.idolmaster-official.jp/media/fankit/distribution/'
$headers = @{ 'User-Agent' = 'Mozilla/5.0 (compatible; personal-asset-index/1.0)' }
$extensions = '\.(png|jpe?g|gif|webp|avif|svg|ico|mp4|webm|mov|pdf|zip|woff2?|ttf|otf)(\?.*)?$'
$manifestPath = Join-Path $root 'manifest.json'
New-Item -ItemType Directory -Force -Path $root | Out-Null

function Get-Page([string]$url) {
  try { return (Invoke-WebRequest -Uri $url -Headers $headers -UseBasicParsing -TimeoutSec 30).Content }
  catch { Write-Output "page-skip $url"; return $null }
}

function Add-Asset([string]$pageUrl, [string]$rawUrl) {
  if ([string]::IsNullOrWhiteSpace($rawUrl) -or $rawUrl.StartsWith('data:') -or $rawUrl.StartsWith('#')) { return }
  try { $uri = [Uri]::new([Uri]$pageUrl, $rawUrl) } catch { return }
  if ($uri.Host -ne $origin.Host -or $uri.AbsoluteUri -notmatch $extensions) { return }
  [void]$assetUrls.Add($uri.AbsoluteUri)
}

$assetUrls = [System.Collections.Generic.HashSet[string]]::new()
$pages = [System.Collections.Generic.HashSet[string]]::new()
[void]$pages.Add($targetPage)
foreach ($page in @(
  'https://gakuen.idolmaster-official.jp/',
  'https://gakuen.idolmaster-official.jp/media/',
  'https://gakuen.idolmaster-official.jp/media/fankit/',
  'https://gakuen.idolmaster-official.jp/introduction/',
  'https://gakuen.idolmaster-official.jp/system/',
  'https://gakuen.idolmaster-official.jp/idol/',
  'https://gakuen.idolmaster-official.jp/road-to-a-plus/'
)) { [void]$pages.Add($page) }

try {
  $index = [xml](Invoke-WebRequest -Uri ($origin.AbsoluteUri + 'sitemap.xml') -Headers $headers -UseBasicParsing -TimeoutSec 30).Content
  foreach ($sitemap in $index.sitemapindex.sitemap.loc) {
    try {
      $map = [xml](Invoke-WebRequest -Uri ([string]$sitemap) -Headers $headers -UseBasicParsing -TimeoutSec 30).Content
      foreach ($loc in $map.urlset.url.loc) { [void]$pages.Add([string]$loc) }
    } catch { Write-Output "sitemap-skip $sitemap" }
  }
} catch { Write-Output 'sitemap-skip index' }

$pageCount = 0
foreach ($page in $pages) {
  $pageCount++
  $html = Get-Page $page
  if (-not $html) { continue }
  foreach ($match in [regex]::Matches($html, '(?:src|href|poster)=["'']([^"'']+)["'']')) { Add-Asset $page $match.Groups[1].Value }
  foreach ($match in [regex]::Matches($html, 'url\((?:["'']?)([^)"'']+)(?:["'']?)\)')) { Add-Asset $page $match.Groups[1].Value }
  if (($pageCount % 25) -eq 0) { Write-Output "pages $pageCount/$($pages.Count), assets discovered $($assetUrls.Count)" }
}

$records = @{}
if (Test-Path $manifestPath) {
  try { foreach ($item in @(Get-Content $manifestPath -Raw | ConvertFrom-Json)) { $records[$item.url] = $item } } catch {}
}
$assetCount = 0
foreach ($url in $assetUrls) {
  $assetCount++
  $uri = [Uri]$url
  $relative = $uri.AbsolutePath.TrimStart('/').Replace('/', '\\')
  $target = Join-Path $root $relative
  New-Item -ItemType Directory -Force -Path (Split-Path $target -Parent) | Out-Null
  if (-not (Test-Path -LiteralPath $target)) {
    try { Invoke-WebRequest -Uri $url -Headers $headers -UseBasicParsing -OutFile $target -TimeoutSec 30 }
    catch { Write-Output "asset-skip $url"; continue }
  }
  $records[$url] = [pscustomobject]@{ url = $url; path = $target.Substring($root.Length + 1); bytes = (Get-Item -LiteralPath $target).Length }
  if (($assetCount % 25) -eq 0) { Write-Output "assets $assetCount/$($assetUrls.Count)" }
}

$records.Values | Sort-Object url | ConvertTo-Json -Depth 3 | Set-Content -LiteralPath $manifestPath -Encoding UTF8
$files = @(Get-ChildItem $root -Recurse -File | Where-Object { $_.Name -ne 'manifest.json' })
Write-Output "completed pages=$($pages.Count) assets=$($files.Count) bytes=$((($files | Measure-Object Length -Sum).Sum))"
