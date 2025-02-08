package io.github.ddk.core.page;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.function.Function;

/**
 * @author Elijah Du
 * @date 2025/2/8
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> {

    // 当前页数据列表
    private List<T> records;

    // 总记录数
    private Long total;

    // 当前页码
    private Long pageNum;

    // 每页数量
    private Long pageSize;

    // 总页数
    private Long pages;

    // 是否有上一页
    private Boolean hasPrevious;

    // 是否有下一页
    private Boolean hasNext;

    // DO 转换成 DTO
    public <R> PageResponse<R> convert(Function<List<T>, List<R>> mapper) {
        return PageResponse.<R>builder()
                .records(mapper.apply(records))
                .total(total)
                .pageNum(pageNum)
                .pageSize(pageSize)
                .pages(pages)
                .hasPrevious(hasPrevious)
                .hasNext(hasNext)
                .build();
    }
}
