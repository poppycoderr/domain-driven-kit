package com.ddk.infrastructure.orm.config;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * @author Elijah Du
 * @date 2025/2/20
 */
@Configuration
@RequiredArgsConstructor
@EnableTransactionManagement
public class MybatisPlusConfig {

    @Bean
    public IdentifierGenerator idGenerator() {
        // TODO：core -> 自定义ID生成策略
        return param -> IdUtil.getSnowflakeNextId();
    }

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(500L);
        pagination.setOverflow(false);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }

    @Bean
    public MetaObjectHandler metaObjectHandler() {
        // TODO：core -> 自定义填充策略
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
            }

            @Override
            public void updateFill(MetaObject metaObject) {
            }
        };
    }
}
