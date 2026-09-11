package com.ddk.core.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MapperProvider：注册、查找与失败语义")
class MapperProviderTest {

    // ---------- 测试用类型：两个包内同名类无法在一个文件里声明，用嵌套类模拟同 simpleName 的冲突 ----------

    record User(String name) {
    }

    record UserPO(String name) {
    }

    /** 另一个限界上下文里的 User，simpleName 与上面完全相同 */
    static class Billing {
        record User(String name) {
        }

        record UserPO(String name) {
        }
    }

    @EnhancedMapper(source = User.class, target = UserPO.class)
    static class UserToPoMapper implements ObjectMapper<User, UserPO> {
        @Override
        public UserPO map(User source) {
            return new UserPO(source.name());
        }

        @Override
        public List<UserPO> map(List<User> sources) {
            return sources.stream().map(this::map).toList();
        }
    }

    @EnhancedMapper(source = UserPO.class, target = User.class)
    static class PoToUserMapper implements ObjectMapper<UserPO, User> {
        @Override
        public User map(UserPO source) {
            return new User(source.name());
        }

        @Override
        public List<User> map(List<UserPO> sources) {
            return sources.stream().map(this::map).toList();
        }
    }

    @EnhancedMapper(source = Billing.User.class, target = Billing.UserPO.class)
    static class BillingUserToPoMapper implements ObjectMapper<Billing.User, Billing.UserPO> {
        @Override
        public Billing.UserPO map(Billing.User source) {
            return new Billing.UserPO("billing:" + source.name());
        }

        @Override
        public List<Billing.UserPO> map(List<Billing.User> sources) {
            return sources.stream().map(this::map).toList();
        }
    }

    /** 重复注册同一个映射方向 */
    @EnhancedMapper(source = User.class, target = UserPO.class)
    static class DuplicateUserToPoMapper implements ObjectMapper<User, UserPO> {
        @Override
        public UserPO map(User source) {
            return new UserPO("duplicate");
        }

        @Override
        public List<UserPO> map(List<User> sources) {
            return sources.stream().map(this::map).toList();
        }
    }

    /** 标了注解但没实现 ObjectMapper */
    @EnhancedMapper(source = User.class, target = UserPO.class)
    static class NotAMapper {
    }

    // ---------- 配置 ----------

    @Configuration
    static class BaseConfig {
        @Bean
        UserToPoMapper userToPoMapper() {
            return new UserToPoMapper();
        }

        @Bean
        PoToUserMapper poToUserMapper() {
            return new PoToUserMapper();
        }

        @Bean
        MapperProvider mapperProvider(org.springframework.context.ApplicationContext context) {
            return new MapperProvider(context);
        }
    }

    @Configuration
    static class SameSimpleNameConfig extends BaseConfig {
        @Bean
        BillingUserToPoMapper billingUserToPoMapper() {
            return new BillingUserToPoMapper();
        }
    }

    @Configuration
    static class DuplicateConfig extends BaseConfig {
        @Bean
        DuplicateUserToPoMapper duplicateUserToPoMapper() {
            return new DuplicateUserToPoMapper();
        }
    }

    @Configuration
    static class InvalidBeanConfig {
        @Bean
        NotAMapper notAMapper() {
            return new NotAMapper();
        }

        @Bean
        MapperProvider mapperProvider(org.springframework.context.ApplicationContext context) {
            return new MapperProvider(context);
        }
    }

    private static MapperProvider providerOf(Class<?> config) {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(config)) {
            return ctx.getBean(MapperProvider.class);
        }
    }

    // ---------- 用例 ----------

    @Test
    @DisplayName("注册后可按方向查到映射器")
    void lookupRegisteredMapper() {
        MapperProvider provider = providerOf(BaseConfig.class);

        assertEquals(new UserPO("alice"), provider.lookup(User.class, UserPO.class).map(new User("alice")));
        assertEquals(new User("alice"), provider.lookup(UserPO.class, User.class).map(new UserPO("alice")));
    }

    @Test
    @DisplayName("映射是有方向的，两个方向各算一次注册")
    void directionsAreIndependent() {
        MapperProvider provider = providerOf(BaseConfig.class);

        assertTrue(provider.contains(User.class, UserPO.class));
        assertTrue(provider.contains(UserPO.class, User.class));
        assertEquals(2, provider.size());
    }

    @Test
    @DisplayName("列表映射走同一个映射器")
    void mapsList() {
        MapperProvider provider = providerOf(BaseConfig.class);

        List<UserPO> pos = provider.lookup(User.class, UserPO.class)
                .map(List.of(new User("a"), new User("b")));

        assertEquals(List.of(new UserPO("a"), new UserPO("b")), pos);
    }

    @Test
    @DisplayName("找不到映射器时立刻失败，而不是返回会丢数据的兜底实现")
    void failsFastOnMissingMapper() {
        MapperProvider provider = providerOf(BaseConfig.class);

        MissingMapperException ex = assertThrows(MissingMapperException.class,
                () -> provider.lookup(User.class, String.class));

        assertEquals(User.class, ex.getSource());
        assertEquals(String.class, ex.getTarget());
        assertTrue(ex.getMessage().contains("@EnhancedMapper"), "异常消息应给出补救写法");
        assertFalse(provider.contains(User.class, String.class));
    }

    @Test
    @DisplayName("不同包的同名类不会互相覆盖——key 用的是全限定名")
    void sameSimpleNameDoesNotCollide() {
        MapperProvider provider = providerOf(SameSimpleNameConfig.class);

        assertEquals(3, provider.size());
        assertEquals(new UserPO("alice"),
                provider.lookup(User.class, UserPO.class).map(new User("alice")));
        assertEquals(new Billing.UserPO("billing:alice"),
                provider.lookup(Billing.User.class, Billing.UserPO.class).map(new Billing.User("alice")));
    }

    @Test
    @DisplayName("同一映射方向被重复注册时，启动期就失败")
    void failsOnDuplicateRegistration() {
        Exception ex = assertThrows(Exception.class, () -> providerOf(DuplicateConfig.class));
        assertTrue(rootCauseMessage(ex).contains("重复注册"), "实际消息：" + rootCauseMessage(ex));
    }

    @Test
    @DisplayName("标了 @EnhancedMapper 却没实现 ObjectMapper 时，启动期就失败")
    void failsOnNonMapperBean() {
        Exception ex = assertThrows(Exception.class, () -> providerOf(InvalidBeanConfig.class));
        assertTrue(rootCauseMessage(ex).contains("没有实现 ObjectMapper"), "实际消息：" + rootCauseMessage(ex));
    }

    private static String rootCauseMessage(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return String.valueOf(cause.getMessage());
    }
}
