package com.example.platform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;

/*
 * 类功能:Redis序列化与RedisTemplate配置，定义Key/Value的序列化策略
 *
 * 【为什么自定义序列化？】
 * Spring Boot默认的RedisTemplate使用JDK序列化：
 *   - Key变成 "\xac\xed\x00\x05t\x00\x0bmyKey" 这种乱码，redis-cli无法阅读
 *   - Value也是二进制，跨语言无法解析，类重构后反序列化直接崩溃
 *
 * 本配置的方案：
 *   - Key：StringRedisSerializer -> 可读字符串，如 "device:type:1"
 *   - Value：GenericJacksonJsonRedisSerializer (Jackson 3) -> JSON格式，redis-cli直接可读
 *
 * 【Spring Data Redis 4.x 推荐做法】
 * - Jackson2JsonRedisSerializer / GenericJackson2JsonRedisSerializer 自 4.0 起已弃用
 * - 官方推荐迁移至 JacksonJsonRedisSerializer / GenericJacksonJsonRedisSerializer
 * - Jackson 3 包名从 com.fasterxml.jackson 迁移至 tools.jackson
 * - 使用 PolymorphicTypeValidator 替代旧的 activateDefaultTyping 无校验版本
 *
 * 【@class的隐患】
 *   - 类重构（改包名/类名）后老数据反序列化失败
 *   - 每条数据多存一个全限定类名，体积膨胀
 *   - 跨语言（Go/Python）不认识@class字段
 * 规避方案：后续如需跨语言，可关闭default typing，改用手动JSON序列化
 * 当前作为基础设施层先用default typing快速落地
 * */
@Configuration
public class RedisConfig {

    /*
     * 方法功能:自定义RedisTemplate，覆盖Spring Boot默认的JDK序列化配置
     * 参数:connectionFactory-Spring Boot自动配置的Lettuce连接工厂
     * 返回数据:RedisTemplate<String, Object>-自定义序列化策略的RedisTemplate
     * */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Spring Data Redis 4.x 推荐：使用 GenericJacksonJsonRedisSerializer (Jackson 3)
        // 仅允许本项目 com.example.platform 包及其子包，以及 JDK 集合类型参与多态反序列化
        PolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType("com.example.platform")
                .allowIfBaseType("java.util")
                .allowIfBaseType("java.lang")
                .build();
        GenericJacksonJsonRedisSerializer jsonSerializer = GenericJacksonJsonRedisSerializer
                .builder()
                .enableDefaultTyping(typeValidator)
                .build();

        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }
}
