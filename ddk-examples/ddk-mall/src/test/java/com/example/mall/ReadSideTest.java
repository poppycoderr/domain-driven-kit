package com.example.mall;

import com.example.mall.order.domain.acl.ProductCatalog;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 读侧：缓存与接口文档。默认 profile 没有 Redis，缓存只有进程内的一级。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReadSideTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private ProductCatalog productCatalog;

    @Test
    void productsAreCachedIncludingTheOnesThatDoNotExist() {
        cacheManager.getCache("product").clear();
        ProductCatalog.Product mouse = productCatalog.find("SKU-MOUSE").orElseThrow();
        assertThat(productCatalog.find("SKU-GONE")).isEmpty();

        jdbc.update("UPDATE t_product SET name = '改名了' WHERE sku_id = 'SKU-MOUSE'");
        jdbc.update("INSERT INTO t_product (sku_id, name, unit_price) VALUES ('SKU-GONE', '刚上架', 1.00)");
        try {
            assertThat(productCatalog.find("SKU-MOUSE")).contains(mouse);
            assertThat(productCatalog.find("SKU-GONE")).as("the miss is cached too").isEmpty();

            cacheManager.getCache("product").clear();
            assertThat(productCatalog.find("SKU-MOUSE").orElseThrow().name()).isEqualTo("改名了");
            assertThat(productCatalog.find("SKU-GONE")).isPresent();
        } finally {
            jdbc.update("UPDATE t_product SET name = ? WHERE sku_id = 'SKU-MOUSE'", mouse.name());
            jdbc.update("DELETE FROM t_product WHERE sku_id = 'SKU-GONE'");
            cacheManager.getCache("product").clear();
        }
    }

    @Test
    void stockIsCachedUntilAWriteThroughTheApplicationCommits() throws Exception {
        cacheManager.getCache("stock").clear();
        int onHand = jdbc.queryForObject("SELECT on_hand FROM t_stock WHERE sku_id = 'SKU-MOUSE'", Integer.class);
        mockMvc.perform(get("/inventory/SKU-MOUSE")).andExpect(jsonPath("$.data.onHand").value(onHand));

        jdbc.update("UPDATE t_stock SET on_hand = on_hand + 7, version = version + 1 WHERE sku_id = 'SKU-MOUSE'");
        mockMvc.perform(get("/inventory/SKU-MOUSE")).andExpect(jsonPath("$.data.onHand").value(onHand));

        mockMvc.perform(post("/inventory/SKU-MOUSE/restock").contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":3}"))
                .andExpect(jsonPath("$.data.onHand").value(onHand + 10));
        mockMvc.perform(get("/inventory/SKU-MOUSE")).andExpect(jsonPath("$.data.onHand").value(onHand + 10));

        jdbc.update("UPDATE t_stock SET on_hand = ?, version = version + 1 WHERE sku_id = 'SKU-MOUSE'", onHand);
        cacheManager.getCache("stock").clear();
    }

    @Test
    void apiDocsAreGroupedByContextAndCarryTheErrorContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs/order"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("ddk-mall"))
                .andExpect(jsonPath("$.paths['/orders/{id}'].get.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/orders/{id}'].get.responses['400'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/ApiErrorResponse"))
                .andExpect(jsonPath("$.paths['/payments/orders/{orderId}']").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.ErrorCode.enum", hasItems("ORDER_NOT_FOUND", "INSUFFICIENT_STOCK", "PAYMENT_DECLINED")))
                .andExpect(jsonPath("$.components.schemas.ErrorCode.description", containsString("| `ORDER_NOT_CANCELLABLE` |")));
        mockMvc.perform(get("/v3/api-docs/payment"))
                .andExpect(jsonPath("$.paths['/payments/orders/{orderId}/pay'].post.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/orders/{id}']").doesNotExist());
        mockMvc.perform(get("/v3/api-docs/inventory")).andExpect(jsonPath("$.paths['/inventory/{skuId}'].get").exists());
    }
}
