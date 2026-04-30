package com.ddk.core.page;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.function.Function;

/**
 * 通用分页响应对象（纯 POJO，不依赖任何持久层框架）
 *
 * @author Elijah Du
 * @date 2025/2/8
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<E> {

    /** 当前页数据列表 */
    private List<E> records;

    /** 总记录数 */
    private Long total;

    /** 当前页码 */
    private Long pageNum;

    /** 每页数量 */
    private Long pageSize;

    /** 总页数 */
    private Long pages;

    /** 是否有上一页 */
    private Boolean hasPrevious;

    /** 是否有下一页 */
    private Boolean hasNext;

    /**
     * 将当前分页结果转换为另一种类型
     *
     * @param mapper 转换函数
     * @param <D>    目标类型
     * @return 转换后的分页结果
     */
    public <D> PageResponse<D> map(Function<List<E>, List<D>> mapper) {
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
     * 手动构建分页响应
     *
     * @param records  数据列表
     * @param total    总记录数
     * @param pageNum  当前页码
     * @param pageSize 每页数量
     * @param <E>      数据类型
     * @return 分页响应
     */
    public static <E> PageResponse<E> of(List<E> records, long total, long pageNum, long pageSize) {
        long pages = (total + pageSize - 1) / pageSize;
        return PageResponse.<E>builder()
                .records(records)
                .total(total)
                .pageNum(pageNum)
                .pageSize(pageSize)
                .pages(pages)
                .hasPrevious(pageNum > 1)
                .hasNext(pageNum < pages)
                .build();
    }
}
