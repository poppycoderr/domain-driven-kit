package io.github.ddk.core.page;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;

import javax.validation.constraints.Min;
import java.util.ArrayList;
import java.util.List;

/**
 * 分页查询对象
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

    public <P> Page<P> page() {
        return new Page<>(pageNum, pageSize);
    }

    public PageQuery addSort(String field, String order) {
        if (sorts == null) {
            sorts = new ArrayList<>();
        }
        sorts.add(Sort.of(field, order));
        return this;
    }
}
