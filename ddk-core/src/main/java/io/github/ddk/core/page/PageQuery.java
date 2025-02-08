package io.github.ddk.core.page;

import lombok.Data;

import javax.validation.constraints.Min;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Elijah Du
 * @date 2025/2/8
 */
@Data
public class PageQuery {

    @Min(value = 1, message = "页码不能小于1")
    private Long pageNum = 1L;

    @Min(value = 1, message = "每页数量不能小于1")
    private Long pageSize = 10L;

    // 多重排序
    private List<Sort> sorts;

    // 添加排序条件的便捷方法
    public PageQuery addSort(String field, String order) {
        if (sorts == null) {
            sorts = new ArrayList<>();
        }
        sorts.add(Sort.of(field, order));
        return this;
    }
}
