package io.github.ddk.core.page;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;
import java.util.function.Function;

/**
 * 分页转换器
 *
 * @author Elijah Du
 * @date 2025/2/8
 */
public class PageConverter {

    /**
     * IPage 转换为 PageResponse
     *
     * @param page   IPage 对象
     * @param mapper 转换函数
     * @param <T>    IPage 对象中的记录类型
     * @param <R>    PageResponse 中的记录类型
     * @return PageResponse
     */
    public static <T, R> PageResponse<R> convert(IPage<T> page, Function<List<T>, List<R>> mapper) {
        return PageResponse.<R>builder()
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
