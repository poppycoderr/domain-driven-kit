package com.ddk.seata.starter.config;

import org.apache.seata.core.context.RootContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.autoconfigure.web.client.RestTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 只覆盖 DDK 自己的装配。Seata 的 {@code GlobalTransactionScanner} 依赖 spring.factories 里的
 * {@code ApplicationContextInitializer}，{@code ApplicationContextRunner} 不执行它，所以这里不加载 Seata 自身的自动配置。
 */
class DdkSeataAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(HttpMessageConvertersAutoConfiguration.class,
                    RestClientAutoConfiguration.class, RestTemplateAutoConfiguration.class,
                    DdkSeataAutoConfiguration.class));

    @AfterEach
    void unbind() {
        RootContext.unbind();
    }

    @Test
    void containerProvidedRestClientBuilderPropagatesXid() {
        runner.run(context -> {
            RestClient.Builder builder = context.getBean(RestClient.Builder.class);
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
            server.expect(requestTo("http://order/pay")).andExpect(header(RootContext.KEY_XID, "xid-1"))
                    .andRespond(withSuccess());

            RootContext.bind("xid-1");
            builder.build().post().uri("http://order/pay").retrieve().toBodilessEntity();

            server.verify();
        });
    }

    @Test
    void containerProvidedRestTemplateBuilderPropagatesXid() {
        runner.run(context -> {
            RestTemplate restTemplate = context.getBean(RestTemplateBuilder.class).build();
            MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
            server.expect(requestTo("http://order/pay")).andExpect(header(RootContext.KEY_XID, "xid-2"))
                    .andRespond(withSuccess());

            RootContext.bind("xid-2");
            restTemplate.postForEntity("http://order/pay", null, Void.class);

            server.verify();
        });
    }

    @Test
    void backsOffWhenPropagationDisabled() {
        runner.withPropertyValues("ddk.seata.http-propagation.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean("ddkSeataRestClientCustomizer");
            assertThat(context).doesNotHaveBean("ddkSeataRestTemplateCustomizer");
        });
    }

    @Test
    void backsOffWhenSeataDisabled() {
        runner.withPropertyValues("seata.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(DdkSeataAutoConfiguration.class);
            assertThat(context).doesNotHaveBean("ddkSeataRestClientCustomizer");
        });
    }
}
