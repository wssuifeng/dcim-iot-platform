# config — 框架配置包

> **版本**: v1.1 | **更新日期**: 2026-05-08

---

## 一、功能用途

`config` 包集中管理 Spring Boot 框架级别的配置类，通过 Java Config 方式（`@Configuration`）替代传统的 XML 配置，实现对框架组件行为的定制化控制。

### 核心作用

- **组件注册**：将第三方框架的核心组件注册为 Spring Bean
- **行为定制**：通过配置类调整框架的默认行为
- **统一管理**：所有框架级配置集中存放，便于维护

### 与其他模块的关系

```
config/MybatisPlusConfig    → 为 mapper 层提供分页等增强功能
config/MyMetaObjectHandler  → 为 entity 层提供 createTime/updateTime 自动填充
application.yaml            → 与 config 包互补，管理外部化配置
```

---

## 二、接口说明

### 2.1 MybatisPlusConfig — MyBatis-Plus 配置类

**完整类名**: `com.example.platform.config.MybatisPlusConfig`

**当前状态**：✅ 已启用

#### 配置内容

| Bean 名称 | 类型 | 说明 |
|-----------|------|------|
| `mybatisPlusInterceptor` | `MybatisPlusInterceptor` | MyBatis-Plus 拦截器，注册分页插件 |

#### 分页插件参数

| 参数 | 值 | 说明 |
|------|-----|------|
| `DbType.MYSQL` | - | 数据库类型，影响分页语句生成 |

### 2.2 MyMetaObjectHandler — 自动填充处理器

**完整类名**: `com.example.platform.config.MyMetaObjectHandler`

实现 `MetaObjectHandler` 接口，配合 Entity 中 `@TableField(fill = FieldFill.INSERT)` 等注解，自动填充 `createTime` 和 `updateTime` 字段。

#### 填充策略

| 字段 | 填充时机 | 填充值 |
|------|----------|--------|
| `createTime` | INSERT（插入时） | `LocalDateTime.now()` |
| `updateTime` | INSERT + UPDATE（插入和更新时） | `LocalDateTime.now()` |

#### 工作原理

```
执行 save() → MyBatis-Plus 检测到 fill = FieldFill.INSERT
    → 调用 MyMetaObjectHandler.insertFill()
    → 自动设置 createTime = 当前时间, updateTime = 当前时间

执行 updateById() → MyBatis-Plus 检测到 fill = FieldFill.INSERT_UPDATE
    → 调用 MyMetaObjectHandler.updateFill()
    → 自动设置 updateTime = 当前时间
```

---

## 三、使用示例

### 示例 1：分页查询

```java
Page<Device> page = new Page<>(1, 10);
LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();
wrapper.eq(Device::getStatus, 1);

Page<Device> result = deviceService.page(page, wrapper);
System.out.println("总记录数：" + result.getTotal());
System.out.println("当前页数据：" + result.getRecords());
```

### 示例 2：自动填充效果验证

```java
// 插入时：createTime 和 updateTime 自动填充
Device device = new Device();
device.setDeviceName("新设备");
device.setDeviceCode("SN-002");
device.setTypeId(1);
device.setStatus(0);
device.setLocation("测试位置");
// 无需手动设置 createTime / updateTime
deviceService.save(device);
// device.getCreateTime() → 2026-05-08 12:00:00
// device.getUpdateTime() → 2026-05-08 12:00:00

// 更新时：updateTime 自动更新
device.setLocation("新位置");
deviceService.updateById(device);
// device.getUpdateTime() → 2026-05-08 12:05:00（自动更新）
```

### 示例 3：自定义新的配置类

```java
@Configuration
public class CorsConfig {
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.addAllowedOriginPattern("*");
        config.addAllowedMethod("*");
        config.addAllowedHeader("*");
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
```

---

## 四、注意事项

| 问题 | 说明 | 解决方法 |
|------|------|----------|
| 自动填充不生效 | Entity 字段缺少 `fill` 属性 | 确保字段注解为 `@TableField(value = "create_time", fill = FieldFill.INSERT)` |
| 自动填充值为 null | `MyMetaObjectHandler` 未注册为 Spring Bean | 确保类有 `@Component` 注解 |
| 分页查询报错 | 分页插件未注册 | `MybatisPlusConfig` 已启用，确保类有 `@Configuration` 和 `@Bean` 注解 |
| 配置类不生效 | 缺少 `@Configuration` 注解 | 确保类上有 `@Configuration` 注解 |
| Bean 冲突 | 多个配置类注册了相同类型的 Bean | 使用 `@Primary` 或 `@Qualifier` 指定优先级 |

---

## 五、变更记录

| 版本 | 日期 | Commit | 变更说明 |
|------|------|--------|----------|
| v1.0 | 2026-05-04 | — | 初始版本，MybatisPlusConfig 为注释状态 |
| v1.1 | 2026-05-08 | `[f88934db]` | MybatisPlusConfig 已正式启用；新增 MyMetaObjectHandler 自动填充处理器文档 |

---

*本文档基于项目 v0.0.1-SNAPSHOT 版本编写，最后更新于 2026-05-08。*

> ---
>
> **© 2026 IoT-Platform Project.**
>
> 本文档采用 [MIT License](../../../../../../../LICENSE) 授权。文档内容仅供参考，项目维护者不对因使用本文档而产生的任何直接或间接损失承担责任。
