-- 保留已删除请求的 ID，避免超时重试把删除过的动态重新发布。
ALTER TABLE posts ADD COLUMN deleted boolean NOT NULL DEFAULT false;
