package io.github.ddk.core.page;

import com.baomidou.mybatisplus.core.metadata.IPage;
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
public class PageResponse<E> {

    // 当前页数据列表
    private List<E> records;

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
    public <D> PageResponse<D> of(Function<List<E>, List<D>> mapper) {
        return PageResponse.<D>builder()
                .records(mapper.apply(records))
                .total(total)
                .pageNum(pageNum)
                .pageSize(pageSize)
                .pages(pages)
                .hasPrevious(hasPrevious)
                .hasNext(hasNext)
                .build();
    }

    /**
     * IPage 转换为 PageResponse
     *
     * @param page   IPage 对象
     * @param mapper 转换函数
     * @param <P>    IPage 对象中的记录类型
     * @param <E>    PageResponse 中的记录类型
     * @return PageResponse
     */
    public static <P, E> PageResponse<E> of(IPage<P> page, Function<List<P>, List<E>> mapper) {
        return PageResponse.<E>builder()
                .records(mapper.apply(page.getRecords()))
                .total(page.getTotal())
                .pageNum(page.getCurrent())
                .pageSize(page.getSize())
                .pages(page.getPages())
                .hasPrevious(page.getCurrent() > 1)
                .hasNext(page.getCurrent() < page.getPages())
                .build();
    }
}
