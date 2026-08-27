# ADR-004: 用户来源——管理员建用户 + 开放注册开关，删除 /setup 向导

日期：2026-08-27
状态：已接受

## 背景

多组织时期用户来源链路：`/setup` bootstrap-token 初始化平台管理员 → 邮箱验证码注册 → 创建/加入组织审批。链路长且为多租户特化。

## 决定

- **删除** `/setup` 平台初始化向导全链路（后端 controller/service、前端页面、bootstrap-token 配置、VerificationPurpose.PLATFORM_SETUP）
- **保留** 管理员建用户（`/users` CRUD，ADMIN 专属）与邮箱验证码开放注册两条通道
- 注册开关从数据库表（platform_registration_settings）改为配置项 `auth.registration-enabled`（环境变量 `AUTH_REGISTRATION_ENABLED`，默认 true）
- 开发环境由 V9 seed 提供初始用户（admin/operator/viewer，密码 Admin123）
- 告警邮件通道与注册邮件通道共用 spring.mail 基础设施，前者完整保留

## 理由

自部署实例的首次用户由 seed 或管理员创建即可；`/setup` 向导服务的是"公有云首次开通"场景，收束后不存在该场景。
