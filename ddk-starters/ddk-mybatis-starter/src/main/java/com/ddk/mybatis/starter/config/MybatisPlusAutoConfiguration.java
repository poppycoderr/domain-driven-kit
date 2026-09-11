package com.ddk.mybatis.starter.config;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.ddk.core.mapper.MapperProvider;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 自动配置
 * <p>
 * 提供分页插件、雪花 ID 生成器、自动填充处理器的默认实现，
 * 并注册通用仓储所依赖的 {@link MapperProvider}。
 * 用户可以通过自定义 Bean 覆盖这些默认配置。
 *
 * @author Elijah Du
 */
@AutoConfiguration
@ConditionalOnClass(MybatisPlusInterceptor.class)
public class MybatisPlusAutoConfiguration {

    /**
     * 注册映射器注册表。
     * <p>
     * 它必须由自动配置提供，而不是靠 {@code @Component} 加使用方的 component scan：
     * 业务应用的启动类通常在自己的包下，扫不到 {@code com.ddk}，
     * 那样 {@code GenericRepositoryImpl} 里的注入会直接失败。
     */
    @Bean
    @ConditionalOnMissingBean
    public MapperProvider mapperProvider(ApplicationContext context) {
        return new MapperProvider(context);
    }

    @Bean
    @ConditionalOnMissingBean
    public IdentifierGenerator idGenerator() {
        return param -> IdUtil.getSnowflakeNextId();
    }

    @Bean
    @ConditionalOnMissingBean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(500L);
        pagination.setOverflow(false);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }

    @Bean
    @ConditionalOnMissingBean
    public MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
                this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
            }
        };
    }
}
