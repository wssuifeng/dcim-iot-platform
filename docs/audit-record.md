# 文档审查问题记录表

> **审查项目**: IoT-Platform 文档体系 | **审查基准版本**: v0.0.1-SNAPSHOT | **文档版本**: v2.0

---

## 审查流程说明

| 轮次 | 审查范围 | 审查人员模式 | 审查日期 | 发现问题数 | 解决数 | 状态 |
|------|----------|-------------|----------|-----------|--------|------|
| R1 | 全部 9 个文档 | 深度审查 | 2026-05-04 | 8 | 8 | ✅ 已完成 |
| R2 | 全部 9 个文档 | 交叉验证 | 2026-05-04 | 1 | 1 | ✅ 已完成 |
| R3 | 全部 9 个文档 + LICENSE | 终审确认 | 2026-05-04 | 0 | — | ✅ 已完成（无新缺陷） |
| R4 | Controller/Config/Common/Task 层代码 | 代码审查 | 2026-05-17 | 12 | 12 | ✅ 已完成 |

---

## 问题记录

### 第 1~3 轮：文档审查

| 问题ID | 类型 | 位置 | 严重度 | 问题描述 | 修改建议 | 状态 |
|--------|------|------|--------|----------|----------|------|
| R1-001 | ACC | 项目README → 九、文档维护规范 | 高 | 第九章"九"下子标题编号为"8.1/8.2/8.3/8.4"，与章号不一致 | 将子标题修改为"9.1~9.4" | ✅ 已解决 |
| R1-002 | CNS | entity/README.md | 中 | 章节结构为6章，与其他模块标准4章结构不一致 | 将"三、实体间关系"和"四、常用注解说明"合并入"二、接口说明"作为子章节(2.5和2.6) | ✅ 已解决 |
| R1-003 | CNS | docs/images/README.md | 低 | 缺少版本信息和更新日期头部 | 添加版本信息头部 | ✅ 已解决 |
| R1-004 | RED | docs/images/README.md → 使用说明 | 低 | 相对路径示例 `../docs/images/` 因引用位置不同而易造成初学者困惑 | 新增"路径引用方式"章节，用表格列出不同引用来源的正确路径 | ✅ 已解决 |
| R1-005 | CNS | common/README.md → 2.1 标题 | 低 | `Result\<T\>` 反斜杠转义无必要，渲染时会显示字面量反斜杠 | 改为 `Result<T>` | ✅ 已解决 |
| R1-006 | PRO | 全部 9 个文档 | 中 | 所有文档均缺少版权声明、许可证信息和责任声明 | 在每个文档尾部添加统一版权声明块 | ✅ 已解决 |
| R1-007 | CMP | 项目README → 九、文档维护规范 | 中 | 缺少版本变更历史记录表 | 在维护规范中新增"9.4 版本历史"子章节 | ✅ 已解决 |
| R1-008 | ACC | 项目README → 4.4 安装依赖 | 中 | `./mvnw clean install` 会执行集成测试，未配置数据库时失败 | 命令改为 `./mvnw clean compile -DskipTests`，并添加说明块 | ✅ 已解决 |
| R2-001 | ACC | 全部 9 个文档版权块 | 中 | 所有文档版权块中 `[MIT License](LICENSE)` 链接指向不存在的文件 | 创建 `LICENSE` 文件于项目根目录，包含完整 MIT License 文本 | ✅ 已解决 |

### 第 4 轮：代码审查（2026-05-17）

| 问题ID | 类型 | 位置 | 严重度 | 问题描述 | 修改建议 | 状态 |
|--------|------|------|--------|----------|----------|------|
| R4-001 | ACC | DictMetricController | 高 | 未使用 Result 统一响应，直接返回原始对象 | 统一使用 `Result<T>` 包装返回值 | ✅ 已修复 |
| R4-002 | ACC | DictMetricController / DeviceTypeController | 高 | URL 前缀 `/api` 重复，如 `/api/api/...` | 去除 Controller 层多余的 `/api` 前缀 | ✅ 已修复 |
| R4-003 | ACC | DeviceDataController | 高 | `@Valid` 不支持分组校验，无法区分 Create/Update 场景 | 改为 `@Validated(CreateGroup.class)` 支持分组校验 | ✅ 已修复 |
| R4-004 | CMP | GlobalExceptionHandler | 高 | 缺少 `ConstraintViolationException` 处理器，校验异常无法统一捕获 | 新增 `ConstraintViolationException` 异常处理方法 | ✅ 已修复 |
| R4-005 | CMP | MybatisPlusConfig | 高 | 缺少乐观锁插件，`@Version` 注解不生效 | 添加 `OptimisticLockerInnerInterceptor` 插件 | ✅ 已修复 |
| R4-006 | SEC | CorsConfig | 中 | 允许源硬编码为 `*`，存在安全风险 | 改为从配置文件动态读取 `cors.allowed-origins` | ✅ 已修复 |
| R4-007 | ACC | MyMetaObjectHandler | 中 | `createTime` 使用 `fillStrategy` 强制覆盖，更新时可能误写 | 改为 `strictInsertFill`，仅在插入时填充 | ✅ 已修复 |
| R4-008 | ACC | DeviceTypeNotFound | 中 | 异常返回 HTTP 400，语义应为 404 Not Found | 修改异常处理器返回 `HttpStatus.NOT_FOUND` (404) | ✅ 已修复 |
| R4-009 | CNS | Controller 层 | 中 | Controller 层包含业务逻辑，违反分层原则 | 将业务逻辑下沉到 Service 层 | ✅ 已修复 |
| R4-010 | LOG | AlertRuleEngine | 高 | 告警升级方向反转，`<` 应为 `>` | 将比较运算符 `<` 修正为 `>` | ✅ 已修复 |
| R4-011 | ROB | AlertRuleEngine | 中 | `getEffectiveRuleConfig` 存在 NPE 风险，未做空值防御 | 添加空值检查与默认值处理 | ✅ 已修复 |
| R4-012 | TXN | DeviceServiceImpl | 中 | `save` 方法缺少 `@Transactional` 注解，数据一致性无保障 | 添加 `@Transactional` 注解 | ✅ 已修复 |

---

## 问题类型图例

| 代码 | 含义 |
|------|------|
| ACC | 准确性 (Accuracy) |
| CMP | 完整性 (Completeness) |
| CNS | 一致性 (Consistency) |
| RED | 可读性 (Readability) |
| PRO | 专业性 (Professionalism) |
| SEC | 安全性 (Security) |
| LOG | 逻辑正确性 (Logic) |
| ROB | 健壮性 (Robustness) |
| TXN | 事务一致性 (Transaction) |

---

## 审查结论

经 4 轮系统性审查，共发现并解决 21 个问题。第 3 轮终审确认文档无新缺陷；第 4 轮代码审查发现 12 个问题，全部已修复。

| 指标 | 数值 |
|------|------|
| 审查轮次 | 4 |
| 发现问题总数 | 21 |
| 已解决问题 | 21 |
| 未解决问题 | 0 |
| 涉及文档数 | 10（含 LICENSE） |
| 涉及代码文件数 | 9 |

### 各轮次统计

| 轮次 | 高 | 中 | 低 | 合计 | 已修复 |
|------|----|----|-----|------|--------|
| R1 | 1 | 4 | 3 | 8 | 8 |
| R2 | 0 | 1 | 0 | 1 | 1 |
| R3 | 0 | 0 | 0 | 0 | — |
| R4 | 5 | 7 | 0 | 12 | 12 |
| **合计** | **6** | **12** | **3** | **21** | **21** |

---
