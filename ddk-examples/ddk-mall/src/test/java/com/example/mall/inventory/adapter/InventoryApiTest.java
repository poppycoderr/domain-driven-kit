package com.example.mall.inventory.adapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 默认 profile：内存 H2，库存锁是进程内的实现。
 */
@SpringBootTest
@AutoConfigureMockMvc
class InventoryApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void resetStock() {
        jdbc.update("DELETE FROM t_stock_reservation");
        jdbc.update("UPDATE t_stock SET on_hand = 10, reserved = 0 WHERE sku_id = 'SKU-KEYBOARD'");
        jdbc.update("UPDATE t_stock SET on_hand = 50, reserved = 0 WHERE sku_id = 'SKU-MOUSE'");
        jdbc.update("UPDATE t_stock SET on_hand = 3, reserved = 0 WHERE sku_id = 'SKU-MONITOR'");
        // 上面绕过应用直接改了表，缓存里的库存要一并清掉
        cacheManager.getCache("stock").clear();
    }

    @Test
    void reserveThenConfirmShipsTheGoods() throws Exception {
        reserve(1001, "{\"skuId\":\"SKU-KEYBOARD\",\"quantity\":2},{\"skuId\":\"SKU-MOUSE\",\"quantity\":5}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].status").value("RESERVED"));
        stock("SKU-KEYBOARD").andExpect(jsonPath("$.data.onHand").value(10)).andExpect(jsonPath("$.data.available").value(8));

        mockMvc.perform(post("/inventory/reservations/1001/confirm")).andExpect(jsonPath("$.data[0].status").value("CONFIRMED"));
        mockMvc.perform(post("/inventory/reservations/1001/confirm")).andExpect(status().isOk());

        stock("SKU-KEYBOARD").andExpect(jsonPath("$.data.onHand").value(8)).andExpect(jsonPath("$.data.reserved").value(0));
        stock("SKU-MOUSE").andExpect(jsonPath("$.data.onHand").value(45));
        mockMvc.perform(post("/inventory/reservations/1001/release"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESERVATION_ALREADY_CONFIRMED"));
    }

    @Test
    void reserveThenReleaseMakesTheGoodsAvailableAgain() throws Exception {
        reserve(1002, "{\"skuId\":\"SKU-MONITOR\",\"quantity\":3}").andExpect(status().isOk());
        stock("SKU-MONITOR").andExpect(jsonPath("$.data.available").value(0));

        mockMvc.perform(post("/inventory/reservations/1002/release")).andExpect(jsonPath("$.data[0].status").value("RELEASED"));
        mockMvc.perform(post("/inventory/reservations/1002/release")).andExpect(status().isOk());

        stock("SKU-MONITOR").andExpect(jsonPath("$.data.available").value(3)).andExpect(jsonPath("$.data.onHand").value(3));
        mockMvc.perform(post("/inventory/reservations/1002/confirm")).andExpect(jsonPath("$.code").value("RESERVATION_ALREADY_RELEASED"));
    }

    @Test
    void reservingIsAllOrNothingAndRepeatable() throws Exception {
        reserve(1003, "{\"skuId\":\"SKU-KEYBOARD\",\"quantity\":2},{\"skuId\":\"SKU-MONITOR\",\"quantity\":4}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
        stock("SKU-KEYBOARD").andExpect(jsonPath("$.data.reserved").value(0));
        mockMvc.perform(get("/inventory/reservations/1003")).andExpect(jsonPath("$.data.length()").value(0));

        reserve(1004, "{\"skuId\":\"SKU-KEYBOARD\",\"quantity\":2}").andExpect(status().isOk());
        reserve(1004, "{\"skuId\":\"SKU-KEYBOARD\",\"quantity\":2}").andExpect(status().isOk());
        stock("SKU-KEYBOARD").andExpect(jsonPath("$.data.reserved").value(2));
    }

    @Test
    void unknownSkusAndOrdersAreReported() throws Exception {
        stock("SKU-GONE").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("STOCK_NOT_FOUND"));
        reserve(1005, "{\"skuId\":\"SKU-GONE\",\"quantity\":1}").andExpect(jsonPath("$.code").value("STOCK_NOT_FOUND"));
        mockMvc.perform(post("/inventory/reservations/9999/confirm")).andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
        mockMvc.perform(post("/inventory/reservations/9999/release")).andExpect(status().isOk());

        mockMvc.perform(post("/inventory/SKU-MONITOR/restock").contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":7}"))
                .andExpect(jsonPath("$.data.onHand").value(10));
    }

    private ResultActions reserve(long orderId, String lines) throws Exception {
        return mockMvc.perform(post("/inventory/reservations").contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":" + orderId + ",\"lines\":[" + lines + "]}"));
    }

    private ResultActions stock(String skuId) throws Exception {
        return mockMvc.perform(get("/inventory/{skuId}", skuId));
    }
}
