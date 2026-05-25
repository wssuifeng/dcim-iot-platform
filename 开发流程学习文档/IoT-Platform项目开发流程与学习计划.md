# IoT-Platform 项目开发流程与学习计划

> 版本：v2.0 | 更新日期：2026-05-18
>
> 文档定位：将学习内容与项目实践紧密结合，形成完整的学习-实践-评估闭环体系

---

## 文档修订记录

| 版本 | 日期 | 修订内容 | 修订人 |
|------|------|----------|--------|
| v1.0 | 2026-05-18 | 初始版本创建 | — |
| v2.0 | 2026-05-18 | 全面审查完善：补充目录导航、术语表、部署指南、API清单；修正技术术语；优化文档结构与格式规范 | — |

---

## 目录导航

- [第一部分：项目概述](#第一部分项目概述)
  - [1.1 项目基本信息](#11-项目基本信息)
  - [1.2 核心功能模块](#12-核心功能模块)
  - [1.3 项目包结构](#13-项目包结构)
  - [1.4 数据库ER关系图](#14-数据库er关系图)
- [第二部分：七周开发流程全景图](#第二部分七周开发流程全景图)
- [第三部分：学习-实践-评估闭环体系](#第三部分学习-实践-评估闭环体系)
- [第四部分：核心知识点与求职关联](#第四部分核心知识点与求职关联)
- [第五部分：项目亮点与求职话术](#第五部分项目亮点与求职话术)
- [第六部分：API接口完整清单](#第六部分api接口完整清单)
- [第七部分：部署指南](#第七部分部署指南)
- [第八部分：后续学习路线](#第八部分后续学习路线)
- [附录A：术语表与缩略语](#附录a术语表与缩略语)
- [附录B：常见问题排查指南](#附录b常见问题排查指南)

---

## 第一部分：项目概述

### 1.1 项目基本信息

| 项目 | 说明 |
|------|------|
| 项目名称 | IoT-Platform 物联网监控平台 |
| 技术栈 | Spring Boot 4.0.6 + MyBatis-Plus 3.5.16 + MySQL 8.0 + JDK 17 |
| 连接池 | Druid 1.2.28（Spring Boot 4 专用 Starter） |
| MySQL驱动 | mysql-connector-j 9.6.0 |
| 项目架构 | Controller → Service → Mapper → Entity 四层架构 |
| 构建工具 | Maven 3.8+ |
| 版本控制 | Git 2.30+（Feature Branch 工作流） |

### 1.2 核心功能模块

| 模块 | 说明 | 核心实体 | 对应周次 |
|------|------|----------|----------|
| 设备管理 | 设备CRUD、状态管理、编码唯一性校验 | Device | Week 5 |
| 设备类型 | 类型管理、类型级告警规则配置 | DeviceType | Week 5 |
| 数据采集 | 设备数据上报、批量上报、统计查询 | DeviceData | Week 6 |
| 告警引擎 | 配置化规则评估、告警生成与升级 | Alert | Week 6 |
| 指标字典 | 指标元数据管理、ConcurrentHashMap内存缓存 | DictMetric | Week 6 |
| 定时监控 | 设备离线检测、自动生成离线告警 | DeviceMonitorTask | Week 6 |

### 1.3 项目包结构

```
com.example.platform
├── common/                     # 通用组件
│   ├── Result.java            # 统一响应包装器 Result<T>
│   ├── ResultCode.java        # 错误码常量类
│   ├── exception/             # 异常层次体系
│   │   ├── BusinessException           # 业务异常基类（code + message）
│   │   ├── DeviceNotFoundException     # 设备未找到（4003）
│   │   ├── DeviceCodeAlreadyExistsException  # 编码重复（4002）
│   │   ├── DeviceTypeNotFound          # 类型未找到（4001）
│   │   └── GlobalExceptionHandler      # 全局异常处理器
│   └── group/                 # 校验分组
│       ├── CreateGroup.java
│       └── UpdateGroup.java
├── config/                     # 配置类
│   ├── MybatisPlusConfig.java  # 分页+乐观锁插件
│   ├── MyMetaObjectHandler.java # 自动填充处理器
│   └── CorsConfig.java        # CORS跨域配置
├── controller/                 # 控制层
│   ├── DeviceController.java
│   ├── DeviceTypeController.java
│   ├── DeviceDataController.java
│   └── DictMetricController.java
├── dto/                        # 数据传输对象（Data Transfer Object）
│   ├── AlertRuleConfig.java    # 告警规则配置（规则列表容器）
│   ├── AlertRuleDefinition.java # 单条告警规则定义
│   ├── DeviceDataBatchDTO.java
│   ├── HandleAlertDTO.java
│   └── BatchHandleAlertDTO.java
├── entity/                     # 实体层
│   ├── Device.java
│   ├── DeviceType.java
│   ├── DeviceData.java
│   ├── Alert.java
│   └── DictMetric.java
├── mapper/                     # 数据访问层
│   ├── DeviceMapper.java
│   ├── DeviceTypeMapper.java
│   ├── DeviceDataMapper.java
│   ├── AlertMapper.java
│   └── DictMetricMapper.java
├── service/                    # 服务层
│   ├── DeviceService.java
│   ├── DeviceTypeService.java
│   ├── DeviceDataService.java
│   ├── AlertService.java
│   ├── DictMetricService.java
│   └── impl/                  # 服务实现
│       ├── DeviceServiceImpl.java
│       ├── DeviceTypeServiceImpl.java
│       ├── DeviceDataServiceImpl.java
│       ├── AlertServiceImpl.java
│       ├── DictMetricServiceImpl.java
│       └── AlertRuleEngine.java  # 告警规则引擎
└── task/                       # 定时任务
    └── DeviceMonitorTask.java
```

### 1.4 数据库ER关系图

IoT-Platform 数据库 **iot_device_db** 包含 5 张核心表，关系如下：

```
┌──────────────┐       ┌──────────────┐       ┌──────────────┐
│ device_types │ 1───N │   devices    │ 1───N │ device_data  │
│              │       │              │       │              │
│ id (PK)      │       │ id (PK)      │       │ id (PK)      │
│ name         │       │ name         │       │ device_id(FK)│
│ code (UNI)   │       │ device_code  │       │ metric_key   │
│ description  │       │ type_id (FK) │       │ metric_value │
│ alert_rule_  │       │ status       │       │ collect_time │
│  config(JSON)│       │ alert_rule_  │       └──────────────┘
└──────────────┘       │  config(JSON)│
                       └──────┬───────┘       ┌──────────────┐
                              │               │ dict_metric  │
                              │ 1───N         │              │
                       ┌──────┴───────┐       │ id (PK)      │
                       │    alerts    │       │ metric_key   │
                       │              │       │ metric_name  │
                       │ id (PK)      │       │ unit         │
                       │ device_id(FK)│       └──────────────┘
                       │ alert_level  │        (独立字典表)
                       │ metric_key   │
                       │ message      │
                       │ is_handled   │
                       └──────────────┘
```

**表关系说明**：

| 关系 | 类型 | 说明 |
|------|------|------|
| device_types → devices | 1:N | 一个设备类型下有多台设备（type_id 外键） |
| devices → device_data | 1:N | 一台设备有多条采集数据（device_id 外键） |
| devices → alerts | 1:N | 一台设备有多条告警记录（device_id 外键） |
| dict_metric | 独立 | 指标字典表，无外键关联，通过 metric_key 逻辑关联 |

---

## 第二部分：七周开发流程全景图

### Week 1：Git版本控制

| 维度 | 内容 |
|------|------|
| **周次主题** | Git版本控制——从零掌握团队协作基础 |
| **核心技术点** | Git安装配置、仓库管理（init/clone）、分支策略（main/feature）、远程协作（push/pull/PR） |
| **产出物清单** | 本地Git仓库、首次commit、远程仓库关联、分支操作记录 |
| **与前周依赖** | 无（项目起点） |
| **学习-实践闭环** | 理论：Git工作流文档 → 实践：完成仓库初始化与首次提交 → 验证：git log查看提交历史 → 文档：记录操作步骤 |
| **详细学习文档** | Week_1(开发环境与版本控制)/ |

### Week 2：MySQL数据库

| 维度 | 内容 |
|------|------|
| **周次主题** | MySQL数据库——IoT平台数据建模与查询 |
| **核心技术点** | 数据库设计（ER图→表结构）、DDL建表（主键/外键/索引/约束）、DML操作（INSERT/UPDATE/DELETE）、聚合查询（GROUP BY/HAVING/子查询） |
| **产出物清单** | IoT平台数据库ER图、5张核心表DDL（devices/device_types/device_data/alerts/dict_metric）、测试数据INSERT脚本 |
| **与前周依赖** | 依赖Week 1的Git仓库管理（SQL脚本需纳入版本控制） |
| **学习-实践闭环** | 理论：数据库范式与IoT数据模型 → 实践：编写DDL与测试数据 → 验证：MySQL Workbench执行验证 → 文档：ER图与表结构说明 |
| **详细学习文档** | Week_2(数据库设计与建模)/ |

### Week 3：MyBatis-Plus持久层

| 维度 | 内容 |
|------|------|
| **周次主题** | MyBatis-Plus持久层——掌握ORM映射与条件构造 |
| **核心技术点** | MyBatis-Plus集成（mybatis-plus-spring-boot4-starter 3.5.16）、BaseMapper继承（CRUD方法自动生成）、LambdaQueryWrapper/LambdaUpdateWrapper（类型安全条件构造）、@Select注解自定义SQL |
| **产出物清单** | 5个Mapper接口（继承BaseMapper）、LambdaQueryWrapper条件构造示例、@Select自定义统计查询 |
| **与前周依赖** | 依赖Week 2的数据库表结构（Mapper需对应已有表） |
| **学习-实践闭环** | 理论：MyBatis-Plus核心概念与BaseMapper API → 实践：编写Mapper接口与条件构造器 → 验证：单元测试验证CRUD → 文档：Mapper接口清单 |
| **详细学习文档** | Week_3(持久层框架集成)/ |

> **说明**：本项目使用 MyBatis-Plus（MP）而非原生 MyBatis。MyBatis-Plus 是 MyBatis 的增强工具，在 MyBatis 基础上只做增强不做改变，提供 BaseMapper 通用 CRUD、LambdaWrapper 条件构造器等特性，简化了 80% 以上的重复代码。项目不使用 XML 映射文件，全部使用注解方式。

### Week 4：项目初始化

| 维度 | 内容 |
|------|------|
| **周次主题** | 项目初始化——Spring Boot工程搭建与MyBatis-Plus集成 |
| **核心技术点** | Spring Boot项目创建（Spring Initializr）、MyBatis-Plus集成（mybatis-plus-spring-boot4-starter 3.5.16）、包结构设计（四层架构）、application.yml配置管理、Git远程仓库推送 |
| **产出物清单** | 可运行的Spring Boot项目骨架、application.yml配置、Entity实体类、Mapper接口（继承BaseMapper）、Service接口（继承IService）、ServiceImpl（继承ServiceImpl<M,T>）、Git远程仓库 |
| **与前周依赖** | 依赖Week 3的Mapper设计（正式项目中的Mapper继承BaseMapper替代原生MyBatis）+ Week 1的Git远程协作 |
| **学习-实践闭环** | 理论：Spring Boot自动配置与MyBatis-Plus特性 → 实践：搭建项目骨架并验证启动 → 验证：Actuator健康检查 + 简单CRUD测试 → 文档：项目结构说明 |
| **详细学习文档** | Week_4(项目初始化与分层架构)/ |

### Week 5：设备管理模块

| 维度 | 内容 |
|------|------|
| **周次主题** | 设备管理模块——Controller开发与请求处理 |
| **核心技术点** | Controller实现（@RestController/@RequestMapping）、请求校验（@Valid/@Validated + 分组校验）、异常处理（BusinessException层次 + GlobalExceptionHandler）、分页查询（MybatisPlusInterceptor + PaginationInnerInterceptor）、CORS配置（CorsFilter + 配置文件外部化） |
| **产出物清单** | DeviceController、DeviceTypeController、Result\<T\>统一响应、BusinessException异常体系、GlobalExceptionHandler、MybatisPlusConfig（分页+乐观锁）、CorsConfig、MyMetaObjectHandler（自动填充） |
| **与前周依赖** | 依赖Week 4的项目骨架（Service/Mapper/Entity已就位） |
| **学习-实践闭环** | 理论：RESTful API设计原则 → 实践：实现CRUD接口与异常处理 → 验证：Postman接口测试全通过 → 文档：API接口文档 |
| **详细学习文档** | Week_5(业务接口开发)/ |

### Week 6：数据采集与告警

| 维度 | 内容 |
|------|------|
| **周次主题** | 数据采集与告警——配置化规则引擎与定时监控 |
| **核心技术点** | 数据上报接口（单条/批量）、配置化规则引擎（AlertRuleConfig + AlertRuleDefinition）、告警处理（生成/升级/批量处理）、统计查询（MIN/MAX/AVG/COUNT）、定时任务（@Scheduled + 外部化cron）、ConcurrentHashMap内存缓存 |
| **产出物清单** | DeviceDataController、AlertRuleEngine、AlertServiceImpl、DictMetricServiceImpl（含缓存）、DeviceMonitorTask、AlertRuleConfig/AlertRuleDefinition DTO |
| **与前周依赖** | 依赖Week 5的Device模块（数据采集需校验设备存在性、告警需关联设备） |
| **学习-实践闭环** | 理论：规则引擎设计模式与告警体系 → 实践：实现规则引擎与定时任务 → 验证：模拟数据触发告警测试 → 文档：告警规则配置说明 |
| **详细学习文档** | Week_6(高级功能实现)/ |

### Week 7：项目整合与工程化

| 维度 | 内容 |
|------|------|
| **周次主题** | 项目整合与工程化——质量保障与交付准备 |
| **核心技术点** | 架构整合（模块间依赖梳理）、代码质量（统一注释规范、命名规范）、单元测试（JUnit5 + Mockito）、文档规范（API文档、架构文档）、部署准备（jar打包、配置外部化） |
| **产出物清单** | 完整可运行的IoT-Platform项目、单元测试覆盖核心Service、项目架构文档、部署配置 |
| **与前周依赖** | 依赖Week 1-6全部产出物（整合阶段） |
| **学习-实践闭环** | 理论：软件工程化最佳实践 → 实践：代码审查与测试补充 → 验证：全量接口回归测试 → 文档：项目交付文档 |
| **详细学习文档** | Week_7(项目整合与工程化实践)/ |

### 七周依赖关系图

```
Week 1 (Git) ─────────────────────────────────────────→ Week 7 (整合)
     │                                                      ↑
     ↓                                                      │
Week 4 (项目初始化) ──→ Week 5 (设备管理) ──→ Week 6 (告警) ──┘
     ↑                    ↑
     │                    │
Week 3 (MyBatis-Plus) ───┘
     ↑
     │
Week 2 (MySQL)
```

---

## 第三部分：学习-实践-评估闭环体系

### 3.1 学习阶段

| 步骤 | 具体行动 | 检查点 |
|------|----------|--------|
| 文档阅读 | 阅读每周学习手册，标记核心技术概念 | 完成手册中所有章节的阅读 |
| 概念理解 | 用自己的语言复述关键技术点 | 能脱稿描述核心概念（如：什么是ServiceImpl继承链） |
| 关键技术点标记 | 识别面试高频考点，建立知识索引 | 每个技术点关联到项目中的具体代码位置 |

### 3.2 实践阶段

| 步骤 | 具体行动 | 检查点 |
|------|----------|--------|
| 代码实现 | 按照手册要求完成每周编码任务 | 代码编译通过，无语法错误 |
| 单元测试 | 为核心Service编写JUnit5测试用例 | 测试覆盖核心业务逻辑（正常路径 + 异常路径） |
| Postman接口测试 | 对所有Controller接口进行功能测试 | 所有接口返回符合Result\<T\>格式，状态码正确 |

### 3.3 评估阶段

| 步骤 | 具体行动 | 验收标准 |
|------|----------|----------|
| 代码审查 | 检查命名规范、注释规范、异常处理完整性 | 无硬编码魔法数字、异常处理覆盖所有BusinessException子类 |
| 文档校准 | 确保文档与代码实现一致 | API文档与Controller实际行为完全匹配 |
| 质量检查报告 | 汇总本周学习成果与待改进项 | 产出每周学习总结，明确下周改进方向 |

### 3.4 闭环流程图

```
┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│   学习阶段    │ ──→ │   实践阶段    │ ──→ │   评估阶段    │
│              │     │              │     │              │
│ 1.文档阅读   │     │ 1.代码实现    │     │ 1.代码审查    │
│ 2.概念理解   │     │ 2.单元测试    │     │ 2.文档校准    │
│ 3.技术标记   │     │ 3.接口测试    │     │ 3.质量报告    │
└──────────────┘     └──────────────┘     └──────────────┘
       ↑                                        │
       │           ┌──────────────┐             │
       └────────── │  反馈改进     │ ←───────────┘
                   │              │
                   │ 1.问题记录   │
                   │ 2.优化方案   │
                   │ 3.下周计划   │
                   └──────────────┘
```

### 3.5 每周学习时间分配建议

| 阶段 | 时间占比 | 建议时长 | 关键活动 |
|------|----------|----------|----------|
| 学习阶段 | 30% | 约 4-5 小时 | 阅读文档、理解概念、标记考点 |
| 实践阶段 | 50% | 约 7-8 小时 | 编码实现、编写测试、接口调试 |
| 评估阶段 | 20% | 约 3 小时 | 代码审查、文档校准、质量报告 |

---

## 第四部分：核心知识点与求职关联

### 4.1 Spring Boot

| 知识点 | 面试频次 | 掌握要求 | 项目应用位置 |
|--------|----------|----------|-------------|
| 自动配置原理 | 🔥🔥🔥🔥🔥 | 理解@EnableAutoConfiguration机制，能说出starter如何生效 | pom.xml中spring-boot-starter-parent 4.0.6、mybatis-plus-spring-boot4-starter |
| 起步依赖 | 🔥🔥🔥🔥 | 理解starter的依赖传递与版本管理 | spring-boot-starter-webmvc、spring-boot-starter-validation、druid-spring-boot-4-starter |
| 配置管理 | 🔥🔥🔥🔥 | 掌握application.yml多环境配置与@Value注入 | CorsConfig中@Value("${cors.allowed-origins}")、DeviceMonitorTask中@Value("${task.offline-threshold-seconds:300}") |
| @Scheduled定时任务 | 🔥🔥🔥 | 掌握cron表达式与配置外部化 | DeviceMonitorTask中@Scheduled(cron = "${task.check-device-cron}") |
| 配置外部化 | 🔥🔥🔥🔥 | 理解配置优先级与外部化部署 | application.yml中的数据库连接、cron表达式、CORS源等均支持外部覆盖 |

### 4.2 MyBatis-Plus

| 知识点 | 面试频次 | 掌握要求 | 项目应用位置 |
|--------|----------|----------|-------------|
| ServiceImpl继承链 | 🔥🔥🔥🔥🔥 | 理解IService\<T\>(接口) → ServiceImpl\<M,T\>(实现) → 业务ServiceImpl 的继承体系，baseMapper字段在祖父类AbstractRepository中通过Spring注入 | 所有ServiceImpl：DeviceServiceImpl extends ServiceImpl\<DeviceMapper, Device\> |
| LambdaWrapper | 🔥🔥🔥🔥 | 掌握LambdaQueryWrapper/LambdaUpdateWrapper的类型安全查询 | DeviceServiceImpl.findOnlineDevices()、pageByCondition()、deviceCodeExists() |
| 分页插件 | 🔥🔥🔥🔥 | 理解MybatisPlusInterceptor + PaginationInnerInterceptor配置 | MybatisPlusConfig注册PaginationInnerInterceptor(DbType.MYSQL) |
| 自动填充 | 🔥🔥🔥 | 掌握MetaObjectHandler + @TableField(fill)机制 | MyMetaObjectHandler：insertFill(collectTime/createTime/updateTime)、updateFill(updateTime) |
| 乐观锁插件 | 🔥🔥🔥 | 理解OptimisticLockerInnerInterceptor + @Version | MybatisPlusConfig注册OptimisticLockerInnerInterceptor |
| JacksonTypeHandler | 🔥🔥 | 掌握JSON字段与Java对象的自动转换，需配合@TableName(autoResultMap=true) | Device/DeviceType中alertRuleConfig字段存为JSON |

> **ServiceImpl 继承链详解**：
> ```
> IService<T> (接口，定义业务方法)
>     └── ServiceImpl<M extends BaseMapper<T>, T> (实现类，提供CRUD模板)
>             └── AbstractRepository (祖父类，定义baseMapper字段)
>                     └── CrudRepository (父类)
>                             └── 业务ServiceImpl (如 DeviceServiceImpl)
> ```
> **关键点**：baseMapper 定义在 AbstractRepository 中，@InjectMocks 无法注入祖父类字段，单元测试中需通过反射手动注入。

### 4.3 RESTful API设计

| 知识点 | 面试频次 | 掌握要求 | 项目应用位置 |
|--------|----------|----------|-------------|
| HTTP方法语义 | 🔥🔥🔥🔥 | GET查询/POST新增/PUT修改/DELETE删除 | 所有Controller遵循RESTful语义 |
| 状态码规范 | 🔥🔥🔥🔥 | 200成功/400参数错误/404资源未找到/500服务器异常 | Result\<T\>中code字段 + GlobalExceptionHandler |
| 统一响应封装 | 🔥🔥🔥🔥🔥 | 掌握Result\<T\>泛型封装模式 | Result\<T\>：success()/success(data)/error(message)/error(code,message) |
| 分组校验 | 🔥🔥🔥 | 理解@Validated + Groups实现不同场景校验 | CreateGroup/UpdateGroup分组、@Valid触发级联校验 |

### 4.4 事务管理

| 知识点 | 面试频次 | 掌握要求 | 项目应用位置 |
|--------|----------|----------|-------------|
| @Transactional | 🔥🔥🔥🔥🔥 | 掌握rollbackFor、propagation、isolation参数 | DeviceDataServiceImpl.saveWithAlertCheck()、AlertServiceImpl.batchHandle() |
| 传播行为 | 🔥🔥🔥🔥 | 理解REQUIRED/REQUIRES_NEW/NESTED区别 | 默认REQUIRED：saveWithAlertCheck中保存数据+触发告警在同一事务 |
| 失效场景 | 🔥🔥🔥🔥🔥 | 能说出4种事务失效原因及解决方案 | 1.同类方法调用（未走代理）2.异常被catch吞掉 3.非public方法 4.rollbackFor未指定 |

**事务失效4种场景详解**：

| 序号 | 失效场景 | 原因 | 解决方案 |
|------|----------|------|----------|
| 1 | 同类方法调用 | 内部调用未走AOP代理 | 注入自身（@Autowired自身）或使用AopContext.currentProxy() |
| 2 | 异常被catch吞掉 | 代理无法感知异常，事务不会回滚 | catch后重新throw，或手动设置TransactionAspectSupport |
| 3 | 非public方法 | Spring AOP基于动态代理，只拦截public方法 | 确保事务方法为public |
| 4 | rollbackFor未指定 | 默认只回滚RuntimeException，checked异常不回滚 | 指定rollbackFor = Exception.class |

### 4.5 设计模式

| 知识点 | 面试频次 | 掌握要求 | 项目应用位置 |
|--------|----------|----------|-------------|
| 配置化规则引擎 | 🔥🔥🔥🔥 | 理解数据驱动替代硬编码if-else | AlertRuleEngine：AlertRuleConfig + AlertRuleDefinition替代硬编码告警判断 |
| 查询或抛异常模式 | 🔥🔥🔥 | 掌握getXxxOrThrow()统一存在性校验 | DeviceServiceImpl.getDeviceOrThrow()、DeviceTypeServiceImpl.getTypeOrThrow() |
| 模板方法 | 🔥🔥🔥 | 理解父类定义流程、子类实现细节 | ServiceImpl提供save/update/remove模板，子类重写save()添加校验逻辑 |
| 策略模式 | 🔥🔥🔥 | 理解运算符策略化 | AlertRuleEngine.isThresholdExceeded()：switch(GT/LT/GTE/LTE/EQ)实现策略分发 |

### 4.6 异常处理

| 知识点 | 面试频次 | 掌握要求 | 项目应用位置 |
|--------|----------|----------|-------------|
| BusinessException层次 | 🔥🔥🔥🔥 | 掌握异常继承体系与错误码设计 | BusinessException(code,message) → DeviceNotFoundException(4003)、DeviceCodeAlreadyExistsException(4002)、DeviceTypeNotFound(4001) |
| 全局异常处理器 | 🔥🔥🔥🔥🔥 | 掌握@RestControllerAdvice + @ExceptionHandler | GlobalExceptionHandler：处理BusinessException、MethodArgumentNotValidException、Exception三类异常 |
| 两层校验 | 🔥🔥🔥🔥 | 理解参数校验（@Valid）与业务校验（Service层）的职责划分 | 第一层：Controller @Valid校验格式；第二层：Service业务规则校验（编码唯一性、类型存在性） |

---

## 第五部分：项目亮点与求职话术

### 5.1 核心求职话术

**话术1：配置化规则引擎**

> 场景：面试官问"项目中有没有用到设计模式？"
> 话术："我设计了一个配置化的告警规则引擎。传统做法是在代码里写if-else判断温度超不超标，我改为用AlertRuleConfig + AlertRuleDefinition做数据驱动的规则配置，支持GT/LT/GTE/LTE/EQ五种运算符，告警阈值和级别全部可配置。同时实现了设备级优先、类型级降级的规则获取策略，新增告警规则只需要修改配置，不需要改代码。"
> 项目实证：AlertRuleEngine.java、AlertRuleConfig.java、AlertRuleDefinition.java

**话术2：5级告警体系与自动升级**

> 场景：面试官问"告警系统是怎么设计的？"
> 话术："我设计了5级告警体系——1紧急、2严重、3一般、4警告、5提示，级别数字越小越紧急。当同一设备同一指标已有未处理告警时，如果新触发的告警级别更高（数字更小），会自动升级已有告警的级别和消息，避免告警冗余。告警消息支持模板化，用String.format动态替换当前值和阈值。"
> 项目实证：AlertRuleEngine.evaluateAndCreateAlerts()、AlertRuleDefinition.messageTemplate

**话术3：统一异常处理与错误码体系**

> 场景：面试官问"项目中的异常处理是怎么做的？"
> 话术："我设计了两层校验+统一异常处理的方案。第一层是Controller的@Valid参数校验，处理格式问题；第二层是Service的业务校验，比如设备编码唯一性、设备类型存在性。业务异常统一继承BusinessException，携带错误码——4001类型不存在、4002编码重复、4003设备不存在。全局用@RestControllerAdvice拦截，保证所有接口返回统一的Result\<T\>格式。"
> 项目实证：BusinessException层次、GlobalExceptionHandler、ResultCode常量类

**话术4：ConcurrentHashMap内存缓存**

> 场景：面试官问"有没有做过性能优化？"
> 话术："指标字典是高频查询但低频变更的数据，每次查数据库太浪费。我用ConcurrentHashMap做了内存缓存，应用启动时@PostConstruct自动加载全量数据，查询时直接走内存不查数据库。缓存还提供了refreshCache()手动刷新接口，数据变更时可以即时更新。ConcurrentHashMap保证了线程安全，支持高并发读取。"
> 项目实证：DictMetricServiceImpl.metricCache、initCache()、refreshCache()

**话术5：定时任务配置外部化**

> 场景：面试官问"定时任务怎么做的？"
> 话术："我用@Scheduled实现了设备离线检测定时任务，但关键点是cron表达式和离线阈值都做了配置外部化——cron从application.yml的task.check-device-cron读取，离线阈值从task.offline-threshold-seconds读取并设了默认值300秒。这样不同环境可以用不同的检测频率和阈值，不需要改代码。检测到离线设备后，委托AlertService批量生成离线告警，含去重逻辑。"
> 项目实证：DeviceMonitorTask、@Value("${task.offline-threshold-seconds:300}")、@Scheduled(cron = "${task.check-device-cron}")

**话术6：MyBatis-Plus继承链与自动填充**

> 场景：面试官问"MyBatis-Plus在项目中怎么用的？"
> 话术："所有Service实现类都继承ServiceImpl\<Mapper, Entity\>，自动获得CRUD方法，我只需要写业务逻辑。比如DeviceServiceImpl重写save()方法添加编码唯一性和类型存在性校验。查询用LambdaQueryWrapper保证类型安全，分页用PaginationInnerInterceptor。时间字段用MetaObjectHandler自动填充——insertFill填充collectTime/createTime/updateTime，updateFill填充updateTime，用strictInsertFill尊重手动传入值，updateTime则强制覆盖。"
> 项目实证：DeviceServiceImpl.save()、MyMetaObjectHandler、MybatisPlusConfig

### 5.2 项目亮点总结

| 亮点 | 技术深度 | 价值 |
|------|----------|------|
| 配置化规则引擎 | 数据驱动替代硬编码if-else，支持5种运算符+模板消息 | 新增告警规则零代码改动 |
| 5级告警体系 | 1-紧急到5-提示，支持自动升级与去重 | 避免告警风暴，保证关键告警优先处理 |
| 设备级/类型级规则降级 | 设备配置优先，类型配置降级 | 灵活的告警策略管理 |
| ConcurrentHashMap内存缓存 | @PostConstruct启动加载 + 手动刷新 | 高频查询场景性能优化 |
| 定时任务配置外部化 | cron + 阈值均从配置文件读取 | 多环境灵活部署 |
| 统一异常处理体系 | BusinessException继承 + 错误码 + @RestControllerAdvice | 前端对接友好，问题定位快速 |

### 5.3 简历项目描述模板

```
IoT-Platform 物联网监控平台 | Spring Boot 4 + MyBatis-Plus + MySQL
- 设计并实现配置化告警规则引擎，基于AlertRuleConfig + AlertRuleDefinition数据驱动架构，
  支持GT/LT/GTE/LTE/EQ五种运算符，实现设备级优先/类型级降级的规则获取策略
- 构建5级告警体系（紧急/严重/一般/警告/提示），实现告警自动升级与去重机制，
  支持模板化消息（String.format动态替换当前值与阈值）
- 设计BusinessException异常继承体系（4001/4002/4003错误码），
  配合@RestControllerAdvice全局异常处理器，实现两层校验与统一Result<T>响应封装
- 基于ConcurrentHashMap实现指标字典内存缓存，@PostConstruct启动加载，
  提供手动刷新接口，避免高频查询数据库
- 实现设备离线检测定时任务，cron表达式与离线阈值均配置外部化，
  支持多环境灵活部署
```

---

## 第六部分：API接口完整清单

### 6.1 设备管理接口（DeviceController）

| 方法 | HTTP | 路径 | 功能 | 核心参数 |
|------|------|------|------|----------|
| list | GET | /devices | 设备列表（支持过滤） | typeId, status, keyword |
| getById | GET | /devices/{id} | 设备详情 | id (PathVariable) |
| create | POST | /devices | 新增设备 | @RequestBody Device |
| update | PUT | /devices/{id} | 更新设备 | id + @RequestBody Device |
| delete | DELETE | /devices/{id} | 删除设备 | id (PathVariable) |
| updateStatus | PUT | /devices/{id}/status | 更新设备状态 | id + status |

### 6.2 设备类型接口（DeviceTypeController）

| 方法 | HTTP | 路径 | 功能 | 核心参数 |
|------|------|------|------|----------|
| list | GET | /device-types | 类型列表 | — |
| getById | GET | /device-types/{id} | 类型详情 | id (PathVariable) |
| create | POST | /device-types | 新增类型 | @RequestBody DeviceType |
| update | PUT | /device-types/{id} | 更新类型 | id + @RequestBody DeviceType |
| delete | DELETE | /device-types/{id} | 删除类型 | id (PathVariable) |

### 6.3 数据采集接口（DeviceDataController）

| 方法 | HTTP | 路径 | 功能 | 核心参数 |
|------|------|------|------|----------|
| create | POST | /device-data | 单条数据上报 | @RequestBody DeviceData |
| batchCreate | POST | /device-data/batch | 批量数据上报 | @RequestBody List\<DeviceData\> |
| listByDevice | GET | /device-data/device/{deviceId} | 按设备查询 | deviceId |
| getLatest | GET | /device-data/device/{deviceId}/latest | 最新数据 | deviceId |
| listByTimeRange | GET | /device-data/time-range | 时间范围查询 | deviceId, startTime, endTime |
| getStatistics | GET | /device-data/stats | 统计查询 | deviceId, startTime, endTime |
| page | GET | /device-data/page | 分页查询 | pageNum, pageSize, deviceId |

### 6.4 指标字典接口（DictMetricController）

| 方法 | HTTP | 路径 | 功能 | 核心参数 |
|------|------|------|------|----------|
| list | GET | /dict-metrics | 指标列表 | — |
| getById | GET | /dict-metrics/{id} | 指标详情 | id (PathVariable) |
| create | POST | /dict-metrics | 新增指标 | @RequestBody DictMetric |
| update | PUT | /dict-metrics/{id} | 更新指标 | id + @RequestBody DictMetric |
| delete | DELETE | /dict-metrics/{id} | 删除指标 | id (PathVariable) |
| refreshCache | POST | /dict-metrics/cache/refresh | 刷新缓存 | — |

### 6.5 告警接口（AlertController）

| 方法 | HTTP | 路径 | 功能 | 核心参数 |
|------|------|------|------|----------|
| listByDevice | GET | /alerts/device/{deviceId} | 按设备查询告警 | deviceId |
| findUnhandled | GET | /alerts/unhandled | 查询未处理告警 | deviceId |
| handle | PUT | /alerts/{id}/handle | 处理单条告警 | id |
| batchHandle | PUT | /alerts/batch-handle | 批量处理告警 | @RequestBody BatchHandleAlertDTO |

---

## 第七部分：部署指南

### 7.1 环境要求

| 组件 | 最低版本 | 推荐版本 | 说明 |
|------|----------|----------|------|
| JDK | 17 | 17+ | Spring Boot 4.0.6 要求 |
| Maven | 3.8 | 3.9+ | 项目构建工具 |
| MySQL | 8.0 | 8.0+ | 数据库服务 |

### 7.2 本地开发部署

**步骤1：克隆项目**

```bash
git clone <repository-url>
cd iot-platform
```

**步骤2：初始化数据库**

```bash
mysql -u root -p < sql/iot_device_db.sql
mysql -u root -p < sql/test_data.sql
```

**步骤3：修改配置**

编辑 `src/main/resources/application.yml`，修改数据库连接信息：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/iot_device_db?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
    username: root
    password: your_password
```

**步骤4：编译运行**

```bash
mvn clean compile
mvn spring-boot:run
```

**步骤5：验证启动**

访问 `http://localhost:8080/api/devices` 确认服务正常。

### 7.3 生产环境部署

**打包**

```bash
mvn clean package -DskipTests
```

生成的可执行JAR位于 `target/iot-platform-1.0.0.jar`。

**运行**

```bash
java -jar iot-platform-1.0.0.jar \
  --spring.datasource.url=jdbc:mysql://prod-db:3306/iot_device_db \
  --spring.datasource.username=prod_user \
  --spring.datasource.password=prod_password \
  --task.check-device-cron="0 */5 * * * *" \
  --task.offline-threshold-seconds=600
```

> **关键点**：生产环境通过命令行参数覆盖 application.yml 中的默认配置，实现配置外部化。

### 7.4 配置外部化参数清单

| 配置项 | 默认值 | 说明 | 生产建议 |
|--------|--------|------|----------|
| server.port | 8080 | 服务端口 | 按实际部署调整 |
| spring.datasource.url | localhost:3306 | 数据库地址 | 使用生产数据库地址 |
| spring.datasource.username | root | 数据库用户 | 使用专用账号 |
| spring.datasource.password | — | 数据库密码 | 使用加密配置 |
| task.check-device-cron | — | 离线检测频率 | 建议 "0 */5 * * * *"（每5分钟） |
| task.offline-threshold-seconds | 300 | 离线判定阈值 | 建议 600（10分钟） |
| cors.allowed-origins | — | CORS允许源 | 仅开放前端域名 |

---

## 第八部分：后续学习路线

### 8.1 短期（1-2周）

| 方向 | 学习内容 | 与本项目的衔接 |
|------|----------|---------------|
| Spring Security | 认证授权、JWT Token、角色权限 | 为IoT平台添加用户登录与接口鉴权 |
| Redis缓存集成 | RedisTemplate、缓存穿透/击穿/雪崩 | 替换ConcurrentHashMap为Redis分布式缓存 |

### 8.2 中期（1-2月）

| 方向 | 学习内容 | 与本项目的衔接 |
|------|----------|---------------|
| 消息队列 | RabbitMQ/Kafka生产消费模型 | 设备数据上报改为异步消息驱动，解耦采集与告警 |
| 微服务架构 | Spring Cloud、服务注册发现、网关 | IoT平台拆分为设备服务、告警服务、数据服务 |

### 8.3 长期（3-6月）

| 方向 | 学习内容 | 与本项目的衔接 |
|------|----------|---------------|
| 容器化部署 | Docker镜像构建、Docker Compose编排 | IoT平台容器化部署，实现一键启动 |
| CI/CD流水线 | Jenkins/GitHub Actions、自动化测试 | 代码提交自动构建、测试、部署 |

---

## 附录A：术语表与缩略语

| 缩略语/术语 | 全称/解释 | 项目中的使用场景 |
|-------------|----------|-----------------|
| ORM | Object-Relational Mapping（对象关系映射） | MyBatis-Plus 实现 Java 对象与数据库表的映射 |
| CRUD | Create, Read, Update, Delete（增删改查） | BaseMapper 提供的通用数据操作方法 |
| DTO | Data Transfer Object（数据传输对象） | AlertRuleConfig、BatchHandleAlertDTO 等 |
| DDL | Data Definition Language（数据定义语言） | CREATE TABLE 等 SQL 语句 |
| DML | Data Manipulation Language（数据操作语言） | INSERT/UPDATE/DELETE 等 SQL 语句 |
| ER | Entity-Relationship（实体关系） | 数据库 ER 图描述表间关系 |
| API | Application Programming Interface（应用编程接口） | RESTful API 接口设计 |
| CORS | Cross-Origin Resource Sharing（跨域资源共享） | CorsConfig 配置前端跨域访问 |
| AOP | Aspect-Oriented Programming（面向切面编程） | @Transactional 基于 AOP 代理实现 |
| BOM | Byte Order Mark（字节顺序标记） | 文件编码格式，项目统一使用 UTF-8 无 BOM |
| CRLF | Carriage Return + Line Feed（回车换行） | Windows 换行符格式（\r\n） |
| LF | Line Feed（换行） | Linux/Unix 换行符格式（\n） |
| JAR | Java Archive（Java 归档文件） | 项目打包为可执行 JAR 部署 |
| JPA | Java Persistence API（Java 持久化 API） | MyBatis-Plus 不使用 JPA，使用自有的 Mapper 体系 |
| JDK | Java Development Kit（Java 开发工具包） | 项目要求 JDK 17+ |
| JSON | JavaScript Object Notation | alert_rule_config 字段使用 JSON 格式存储 |
| MP | MyBatis-Plus 的简称 | 项目持久层框架 |
| PK | Primary Key（主键） | 所有表的 id 字段 |
| FK | Foreign Key（外键） | devices.type_id、alerts.device_id 等 |
| UNI | Unique（唯一约束） | device_types.code、devices.device_code |

---

## 附录B：常见问题排查指南

| 问题现象 | 可能原因 | 排查步骤 | 解决方案 |
|----------|----------|----------|----------|
| 启动报 `Invalid bound statement` | Mapper XML 与接口不匹配 | 检查 namespace 和方法签名 | 确认 Mapper 接口正确继承 BaseMapper |
| 查询返回 JSON 字段为 null | 缺少 autoResultMap 配置 | 检查 Entity 类注解 | 添加 @TableName(autoResultMap = true) |
| baseMapper 为 null | ServiceImpl 未注入 Mapper | 检查 @Mapper 注解和扫描路径 | 确认 @MapperScan 配置正确 |
| @Transactional 不生效 | 同类方法调用未走代理 | 检查事务方法调用方式 | 注入自身或使用 AopContext |
| CORS 跨域被拦截 | 跨域配置未生效 | 检查 CorsConfig 和 allowed-origins | 确认配置值包含前端域名 |
| 定时任务未执行 | @EnableScheduling 未配置 | 检查启动类或配置类 | 添加 @EnableScheduling 注解 |
| 分页查询返回全部数据 | 分页插件未注册 | 检查 MybatisPlusConfig | 确认 PaginationInnerInterceptor 已注册 |
| 自动填充不生效 | MetaObjectHandler 未注册 | 检查 @Component 注解 | 确认 MyMetaObjectHandler 有 @Component |
| Lambda 缓存未初始化 | 单元测试中未初始化 TableInfo | 检查测试 setUp 方法 | 调用 TableInfoHelper.initTableInfo() |
| Druid 连接池启动失败 | starter 版本不匹配 | 检查 pom.xml 依赖 | 使用 druid-spring-boot-4-starter 1.2.28 |