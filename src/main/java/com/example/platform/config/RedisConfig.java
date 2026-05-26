package com.example.platform.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/*
 * 类功能:Redis序列化与RedisTemplate配置，定义Key/Value的序列化策略
 *
 * 【为什么自定义序列化？】
 * Spring Boot默认的RedisTemplate使用JDK序列化：
 *   - Key变成 "\xac\xed\x00\x05t\x00\x0bmyKey" 这种乱码，redis-cli无法阅读
 *   - Value也是二进制，跨语言无法解析，类重构后反序列化直接崩溃
 *
 * 本配置的方案：
 *   - Key：StringRedisSerializer → 可读字符串，如 "device:type:1"
 *   - Value：Jackson2JsonRedisSerializer + activateDefaultTyping → JSON格式，redis-cli直接可读
 *
 * 【为何用Jackson2JsonRedisSerializer替代GenericJackson2JsonRedisSerializer？】
 * GenericJackson2JsonRedisSerializer在Spring Data Redis 4.0.x中已标记@Deprecated：
 *   - 内部类型处理逻辑硬编码，传入自定义ObjectMapper可能破坏行为
 *   - 官方推荐Jackson2JsonRedisSerializer + 手动配置ObjectMapper
 *
 * 两者效果一致：都会在JSON中写入 "@class":"com.example.platform.entity.Device"
 * 【@class的隐患】
 *   - 类重构（改包名/类名）后老数据反序列化失败
 *   - 每条数据多存一个全限定类名，体积膨胀
 *   - 跨语言（Go/Python）不认识@class字段
 * 规避方案：后续如需跨语言，可关闭activateDefaultTyping，改用手动JSON序列化
 * 当前作为基础设施层先用activateDefaultTyping快速落地
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

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        objectMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );

        Jackson2JsonRedisSerializer<Object> jsonSerializer =
                new Jackson2JsonRedisSerializer<>(objectMapper, Object.class);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }
}
