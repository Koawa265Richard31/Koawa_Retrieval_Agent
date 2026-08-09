#!/usr/bin/env bash
# 服务器侧知识库备份：pg_dump 全库 + RustFS 数据目录
# 用法：可放入 crontab（见 docs/knowledge-base-backup-plan.md）
set -euo pipefail

RAGENT_DATA_ROOT="${RAGENT_DATA_ROOT:-/opt/ragent/data}"
BACKUP_ROOT="${KB_BACKUP_ROOT:-/opt/ragent-backup}"
KEEP_DAYS="${KB_BACKUP_KEEP_DAYS:-14}"
STAMP="$(date +%Y%m%d-%H%M%S)"
DEST="$BACKUP_ROOT/$STAMP"

mkdir -p "$DEST"

echo "[$(date -Is)] backup start -> $DEST"

# 1) PostgreSQL 全库逻辑备份（custom format，含 pgvector 向量）
if docker exec ragent-postgres-1 pg_dump -U ragent -d ragent -Fc -f /tmp/ragent-kb.dump; then
  docker cp ragent-postgres-1:/tmp/ragent-kb.dump "$DEST/kb-postgres.dump"
  docker exec ragent-postgres-1 rm -f /tmp/ragent-kb.dump
  echo "[$(date -Is)] pg_dump ok"
else
  echo "[$(date -Is)] pg_dump FAILED" >&2
  exit 1
fi

# 2) RustFS 数据目录（源文件 + 图片缓存）
if tar -C "$RAGENT_DATA_ROOT" -czf "$DEST/rustfs.tar.gz" rustfs; then
  echo "[$(date -Is)] rustfs tar ok"
else
  echo "[$(date -Is)] rustfs tar FAILED" >&2
  exit 1
fi

# 3) 清单
cat > "$DEST/backup.txt" <<EOF
time=$(date -Is)
kb_id=2084920454895685632
collection=gakumas-gamekee-pilot-v3
pg_dump_bytes=$(stat -c%s "$DEST/kb-postgres.dump" 2>/dev/null || stat -f%z "$DEST/kb-postgres.dump")
rustfs_tar_bytes=$(stat -c%s "$DEST/rustfs.tar.gz" 2>/dev/null || stat -f%z "$DEST/rustfs.tar.gz")
EOF

# 4) 保留策略：清理 14 天前的备份目录（只清 BACKUP_ROOT 下的子目录，不动根目录）
find "$BACKUP_ROOT" -mindepth 1 -maxdepth 1 -type d -mtime +"$KEEP_DAYS" -exec rm -rf {} +

echo "[$(date -Is)] backup done -> $DEST"
