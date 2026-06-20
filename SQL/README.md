# SQL 目录说明

本目录仅保留**非 Flyway 管理**的 SQL 文件，用于本地开发、查询参考和初始数据注入。

## 文件清单

| 文件 | 用途 | 是否可重复执行 |
|------|------|---------------|
| `seed.sql` | 开发环境基础数据（指标字典、设备类型、设备、用户、示例数据/告警） | 否（首次初始化后建议清空表再执行） |
| `sample-queries.sql` | 常用业务统计查询示例 | 是 |

## 注意事项

1. **表结构由 Flyway 统一管理**：所有 DDL 脚本已迁移到 `src/main/resources/db/migration/`，请勿在本目录下维护建表语句。
2. **旧文件已清理**：`Iot_DLL.SQL`、`indexes.sql` 已删除；`query.sql` 已重命名为 `sample-queries.sql`。
3. **seed.sql 使用方式**：
   - 在 Flyway 完成表创建后执行；
   - 若表内已有数据，请先清空相关表或处理唯一键冲突；
   - 默认密码：admin / Operator@123 / Viewer@123。
