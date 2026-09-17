package com.ddk.seata.starter.http;

import org.apache.seata.core.context.RootContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class XidPropagationInterceptorTest {

    private final RestClient.Builder builder = RestClient.builder().requestInterceptor(new XidPropagationInterceptor());
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final RestClient client = builder.build();

    @AfterEach
    void unbind() {
        RootContext.unbind();
    }

    @Test
    void carriesXidInsideGlobalTransaction() {
        RootContext.bind("192.168.0.1:8091:123456");
        server.expect(requestTo("http://inventory/deduct")).andExpect(method(HttpMethod.POST))
                .andExpect(header(RootContext.KEY_XID, "192.168.0.1:8091:123456"))
                .andRespond(withSuccess());

        client.post().uri("http://inventory/deduct").retrieve().toBodilessEntity();

        server.verify();
    }

    @Test
    void addsNothingOutsideGlobalTransaction() {
        server.expect(requestTo("http://inventory/stock")).andExpect(headerDoesNotExist(RootContext.KEY_XID))
                .andRespond(withSuccess());

        client.get().uri("http://inventory/stock").retrieve().toBodilessEntity();

        server.verify();
    }

    @Test
    void keepsXidSetExplicitlyByCaller() {
        RootContext.bind("from-context");
        server.expect(requestTo("http://inventory/stock")).andExpect(header(RootContext.KEY_XID, "explicit"))
                .andRespond(withSuccess());

        client.get().uri("http://inventory/stock").header(RootContext.KEY_XID, "explicit").retrieve().toBodilessEntity();

        server.verify();
    }
}
