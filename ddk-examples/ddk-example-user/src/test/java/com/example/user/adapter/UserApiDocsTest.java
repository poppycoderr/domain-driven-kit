package com.example.user.adapter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 接口文档是给调用方的契约：成功时的数据结构、失败时的状态码和错误码都要在里面。
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserApiDocsTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void apiDocsDescribeTheErrorContract() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/users/{id}'].get.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/users/{id}'].get.responses['400'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/ApiErrorResponse"))
                .andExpect(jsonPath("$.paths['/users/{id}'].get.responses['409']").exists())
                .andExpect(jsonPath("$.components.schemas.ErrorCode.enum", hasItems("USER_NOT_FOUND", "USERNAME_TAKEN", "VALIDATION_ERROR")))
                .andExpect(jsonPath("$.components.schemas.ErrorCode.description", containsString("| `USER_NOT_FOUND` | 用户不存在：{0} |")));
    }
}
