package com.ddk.mybatis.page;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ddk.core.page.PageQuery;
import com.ddk.core.page.PageResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MybatisPlusPageAdapter：分页对象双向转换")
class MybatisPlusPageAdapterTest {

    @Test
    @DisplayName("PageQuery -> Page 保留页码与页大小")
    void toPageKeepsPagination() {
        PageQuery query = new PageQuery();
        query.setPageNum(3L);
        query.setPageSize(20L);

        Page<String> page = MybatisPlusPageAdapter.toPage(query);

        assertEquals(3L, page.getCurrent());
        assertEquals(20L, page.getSize());
    }

    @Test
    @DisplayName("Page -> PageResponse 携带正确的翻页标志")
    void toPageResponseComputesFlags() {
        Page<String> page = new Page<>(2L, 10L);
        page.setTotal(25L);
        page.setRecords(List.of("a", "b"));

        PageResponse<String> response = MybatisPlusPageAdapter.toPageResponse(page);

        assertEquals(25L, response.getTotal());
        assertEquals(2L, response.getPageNum());
        assertEquals(3L, response.getPages());
        assertTrue(response.getHasPrevious());
        assertTrue(response.getHasNext());
        assertEquals(List.of("a", "b"), response.getRecords());
    }

    @Test
    @DisplayName("末页 hasNext 为 false")
    void lastPageHasNoNext() {
        Page<String> page = new Page<>(3L, 10L);
        page.setTotal(25L);
        page.setRecords(List.of("a"));

        assertFalse(MybatisPlusPageAdapter.toPageResponse(page).getHasNext());
    }

    @Test
    @DisplayName("带映射函数时只替换记录，元信息不变")
    void toPageResponseWithMapper() {
        Page<String> page = new Page<>(1L, 10L);
        page.setTotal(2L);
        page.setRecords(List.of("1", "2"));

        PageResponse<Integer> response = MybatisPlusPageAdapter.toPageResponse(
                page, records -> records.stream().map(Integer::parseInt).toList());

        assertEquals(List.of(1, 2), response.getRecords());
        assertEquals(2L, response.getTotal());
        assertEquals(1L, response.getPages());
        assertFalse(response.getHasPrevious());
        assertFalse(response.getHasNext());
    }

    @Test
    @DisplayName("空结果集不抛异常")
    void emptyPage() {
        Page<String> page = new Page<>(1L, 10L);
        page.setTotal(0L);
        page.setRecords(List.of());

        PageResponse<String> response = MybatisPlusPageAdapter.toPageResponse(page);

        assertEquals(0L, response.getTotal());
        assertTrue(response.getRecords().isEmpty());
        assertFalse(response.getHasNext());
    }
}
