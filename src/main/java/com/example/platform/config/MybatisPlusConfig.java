package com.example.platform.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/*
 * 类功能:MyBatis-Plus配置类，注册乐观锁和分页插件
 * */
@Configuration
public class MybatisPlusConfig {

    /*
     * 方法功能:创建MyBatis-Plus拦截器，依次注册乐观锁插件和分页插件
     * 参数:无
     * 返回数据:MybatisPlusInterceptor-拦截器实例
     * */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 乐观锁插件：防止并发更新时丢失更新，需配合@Version字段使用
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        // 分页插件：基于MySQL方言的分页支持
        PaginationInnerInterceptor paginationInterceptor = new PaginationInnerInterceptor(DbType.MYSQL);
        // 单页最大100条，防止恶意大页查询拖垮数据库
        paginationInterceptor.setMaxLimit(100L);
        // 页码超出总页数时不自动回到最后一页，直接按空结果返回
        paginationInterceptor.setOverflow(false);
        interceptor.addInnerInterceptor(paginationInterceptor);
        return interceptor;
    }
}
