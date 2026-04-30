package com.ddk.core.page;

import jakarta.validation.constraints.Min;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 通用分页查询对象（纯 POJO，不依赖任何持久层框架）
 *
 * @author Elijah Du
 * @date 2025/2/8
 */
@Data
public class PageQuery {

    @Min(value = 1, message = "页码不能小于1")
    private Long pageNum = 1L;

    @Min(value = 1, message = "每页数量不能小于1")
    private Long pageSize = 10L;

    private List<Sort> sorts;

    public PageQuery addSort(String field, String order) {
        if (sorts == null) {
            sorts = new ArrayList<>();
        }
        sorts.add(Sort.of(field, order));
        return this;
    }
}
