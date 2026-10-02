package com.ddk.web.context;

import com.ddk.core.context.Operator;
import com.ddk.core.context.OperatorContext;
import com.ddk.web.internal.OperatorContextFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

@DisplayName("请求期间的 OperatorContext")
@SpringBootTest(classes = OperatorContextFilterTest.TestApplication.class)
class OperatorContextFilterTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private OperatorContextFilter filter;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(filter).build();
    }

    @Test
    @DisplayName("请求处理期间能取到解析出的操作者，请求结束后线程上不留下")
    void operatorIsVisibleDuringTheRequestOnly() throws Exception {
        mvc.perform(get("/whoami").header("X-User", "alice").header("X-Tenant", "7")).andExpect(content().string("alice@7"));

        assertThat(OperatorContext.current()).isEmpty();
    }

    @Test
    @DisplayName("解析不出操作者的请求照常处理")
    void anonymousRequestsPassThrough() throws Exception {
        mvc.perform(get("/whoami")).andExpect(content().string("anonymous"));
    }

    @Test
    @DisplayName("没有声明 OperatorResolver 时不注册过滤器")
    void noFilterWithoutResolver() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(com.ddk.web.config.WebAutoConfiguration.class))
                .run(context -> assertThat(context).doesNotHaveBean(OperatorContextFilter.class));
    }

    @RestController
    static class WhoAmIController {

        @GetMapping("/whoami")
        String whoAmI() {
            return OperatorContext.current().map(operator -> operator.id() + "@" + operator.tenantId()).orElse("anonymous");
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {

        @Bean
        WhoAmIController whoAmIController() {
            return new WhoAmIController();
        }

        @Bean
        OperatorResolver operatorResolver() {
            return request -> {
                String user = request.getHeader("X-User");
                return user == null ? null : Operator.of(user, request.getHeader("X-Tenant"));
            };
        }
    }
}
