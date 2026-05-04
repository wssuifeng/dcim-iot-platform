# IoT-Platform 文档目录索引

> **版本**: v1.0 | **更新日期**: 2026-05-04

---

## 文档总览

本文档索引覆盖 IoT-Platform 物联网设备管理平台的所有文档，帮助你快速定位所需内容。

---

## 一、项目级文档

| 文档名称 | 路径 | 内容概要 |
|----------|------|----------|
| 项目总览 README | [README.md](../README.md) | 项目概述、核心功能、目录结构、环境配置、快速启动指南、技术栈、已知问题、维护规范 |

---

## 二、模块级文档

### 2.1 通用工具包（common）

| 文档名称 | 路径 | 内容概要 |
|----------|------|----------|
| common 模块文档 | [common/README.md](../src/main/java/com/example/platform/common/README.md) | 统一响应结果 `Result<T>` 的接口说明、使用示例、注意事项 |

### 2.2 框架配置包（config）

| 文档名称 | 路径 | 内容概要 |
|----------|------|----------|
| config 模块文档 | [config/README.md](../src/main/java/com/example/platform/config/README.md) | MyBatis-Plus 分页配置、启用方法、自定义配置示例 |

### 2.3 控制器包（controller）

| 文档名称 | 路径 | 内容概要 |
|----------|------|----------|
| controller 模块文档 | [controller/README.md](../src/main/java/com/example/platform/controller/README.md) | REST 接口设计、常用注解、CRUD 控制器实现示例 |

### 2.4 实体类包（entity）

| 文档名称 | 路径 | 内容概要 |
|----------|------|----------|
| entity 模块文档 | [entity/README.md](../src/main/java/com/example/platform/entity/README.md) | 四个实体类的字段说明、实体间关系、ORM 注解说明、使用示例 |

### 2.5 数据访问层包（mapper）

| 文档名称 | 路径 | 内容概要 |
|----------|------|----------|
| mapper 模块文档 | [mapper/README.md](../src/main/java/com/example/platform/mapper/README.md) | BaseMapper 通用方法、自定义 SQL、条件构造器查询示例 |

### 2.6 业务逻辑层包（service）

| 文档名称 | 路径 | 内容概要 |
|----------|------|----------|
| service 模块文档 | [service/README.md](../src/main/java/com/example/platform/service/README.md) | IService 通用方法、DeviceService 自定义方法、事务控制示例 |

---

## 三、文档资源

| 资源类型 | 路径 | 说明 |
|----------|------|------|
| 图片资源目录 | [docs/images/](images/) | 架构图、流程图、界面截图等视觉辅助资源 |

---

## 四、阅读建议

### 初学者推荐阅读顺序

1. **项目总览** — 了解项目整体架构和目标
2. **entity 模块** — 理解数据模型，这是所有业务的基础
3. **mapper 模块** — 学习数据库交互方式
4. **service 模块** — 掌握业务逻辑的组织方式
5. **controller 模块** — 了解如何对外暴露接口
6. **common 模块** — 学习统一响应封装
7. **config 模块** — 了解框架配置管理

### 按场景查阅

| 场景 | 推荐文档 |
|------|----------|
| 搭建开发环境 | 项目总览 → 环境配置指南 |
| 理解数据模型 | entity 模块文档 |
| 编写新接口 | controller + service + mapper 文档 |
| 添加新实体 | entity + mapper + service 文档 |
| 统一响应格式 | common 模块文档 |
| 启用分页功能 | config 模块文档 |
| 排查启动问题 | 项目总览 → 已知问题 |

---

## 五、文档维护记录

| 版本 | 日期 | 变更内容 |
|------|------|----------|
| v1.0 | 2026-05-04 | 初始版本，创建全部文档 |

---

*如发现文档问题或有改进建议，请通过 GitHub Issues 提交反馈。*

> ---
> 
> **© 2026 IoT-Platform Project.**
> 
> 本文档采用 [MIT License](../LICENSE) 授权。文档内容仅供参考，项目维护者不对因使用本文档而产生的任何直接或间接损失承担责任。
