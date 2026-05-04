# config — 框架配置包

> **版本**: v1.0 | **更新日期**: 2026-05-04

---

## 一、功能用途

`config` 包集中管理 Spring Boot 框架级别的配置类，通过 Java Config 方式（`@Configuration`）替代传统的 XML 配置，实现对框架组件行为的定制化控制。

### 核心作用

- **组件注册**：将第三方框架的核心组件注册为 Spring Bean
- **行为定制**：通过配置类调整框架的默认行为
- **统一管理**：所有框架级配置集中存放，便于维护

### 与其他模块的关系

```
config/MybatisPlusConfig → 为 mapper 层提供分页等增强功能
application.yaml        → 与 config 包互补，管理外部化配置
```

---

## 二、接口说明

### 2.1 MybatisPlusConfig — MyBatis-Plus 配置类

**完整类名**: `com.example.platform.config.MybatisPlusConfig`

> **当前状态**：该配置类已被注释，暂未启用。分页功能需要此配置才能正常使用。

#### 配置内容

| Bean 名称 | 类型 | 说明 |
|-----------|------|------|
| `mybatisPlusInterceptor` | `MybatisPlusInterceptor` | MyBatis-Plus 拦截器，注册分页插件 |

#### 分页插件参数

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `DbType.MYSQL` | - | 数据库类型，影响分页语句生成 |

#### 启用方法

1. 取消 `MybatisPlusConfig.java` 中所有代码的注释
2. 确保项目依赖中包含 `mybatis-plus-extension`
3. 重启项目即可使用分页功能

---

## 三、使用示例

### 示例 1：启用分页配置后进行分页查询

```java
// 启用 MybatisPlusConfig 后，可在 Service 层直接使用分页
Page<Device> page = new Page<>(1, 10); // 第1页，每页10条
LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();
wrapper.eq(Device::getStatus, 1);

Page<Device> result = deviceService.page(page, wrapper);
System.out.println("总记录数：" + result.getTotal());
System.out.println("当前页数据：" + result.getRecords());
```

### 示例 2：在 Controller 中使用分页

```java
@GetMapping("/page")
public Result<Page<Device>> listByPage(
        @RequestParam(defaultValue = "1") Integer current,
        @RequestParam(defaultValue = "10") Integer size) {
    Page<Device> page = new Page<>(current, size);
    Page<Device> result = deviceService.page(page);
    return Result.success(result);
}
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
| 分页查询报错 | 未启用 `MybatisPlusConfig`，分页插件未注册 | 取消 `MybatisPlusConfig.java` 中的注释并重启 |
| 配置类不生效 | 缺少 `@Configuration` 注解 | 确保类上有 `@Configuration` 注解 |
| Bean 冲突 | 多个配置类注册了相同类型的 Bean | 使用 `@Primary` 或 `@Qualifier` 指定优先级 |
| 配置优先级 | `application.yaml` 与 Java Config 可能存在重叠 | Java Config 优先级通常更高，注意避免重复配置 |

---

*本文档基于项目 v0.0.1-SNAPSHOT 版本编写，最后更新于 2026-05-04。*

> ---
> 
> **© 2026 IoT-Platform Project.**
> 
> 本文档采用 [MIT License](../../../../../../../LICENSE) 授权。文档内容仅供参考，项目维护者不对因使用本文档而产生的任何直接或间接损失承担责任。
