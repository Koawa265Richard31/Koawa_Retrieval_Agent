param(
  [string]$RemoteHost = "jd-ecs",
  [string]$BackupRoot = "/opt/ragent-backup",
  [string]$LocalRoot  = "D:\ragent-backups",
  [string]$MirrorDir  = ""    # 可选：拉取后同步到的第二设备目录（移动硬盘/NAS/网盘同步夹）
)

# 拉取服务器最新一份知识库备份到本机，可选同步到第二设备
$ErrorActionPreference = "Stop"

# 找到服务器上最新的备份目录名（按名称排序取最后一个）
$latest = (ssh $RemoteHost "ls -1 $BackupRoot | sort | tail -1").Trim()
if (-not $latest) {
  Write-Host "服务器 $BackupRoot 下没有备份" -ForegroundColor Red
  exit 1
}

$dest = Join-Path $LocalRoot $latest
New-Item -ItemType Directory -Force -Path (Split-Path $dest) | Out-Null

Write-Host "拉取 $RemoteHost`:$BackupRoot/$latest -> $dest"
# 用 tar over ssh 流式拉取（避免逐文件 scp 慢），本地解包
ssh $RemoteHost "tar -C $BackupRoot -cf - $latest" | tar -xf - -C (Split-Path $dest)

# 校验：backup.txt 存在且非空
$manifest = Join-Path $dest "backup.txt"
if (-not (Test-Path $manifest)) {
  Write-Host "拉取结果缺少 backup.txt，可能不完整" -ForegroundColor Red
  exit 1
}
Get-Content $manifest

if ($MirrorDir) {
  New-Item -ItemType Directory -Force -Path (Split-Path $MirrorDir) | Out-Null
  Write-Host "同步到第二设备目录 $MirrorDir"
  robocopy $dest (Join-Path $MirrorDir $latest) /MIR /NFL /NDL /NJH | Out-Null
  Write-Host "同步完成: $($latest)"
}

Write-Host "备份就绪: $dest" -ForegroundColor Green
