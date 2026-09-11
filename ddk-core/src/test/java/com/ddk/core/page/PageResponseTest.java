package com.ddk.core.page;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PageResponse：分页结果")
class PageResponseTest {

    @Test
    @DisplayName("总页数向上取整，首页没有上一页")
    void computesPagesAndFlags() {
        PageResponse<String> page = PageResponse.of(List.of("a", "b"), 25L, 1L, 10L);

        assertEquals(3L, page.getPages(), "25 条按每页 10 条应为 3 页");
        assertFalse(page.getHasPrevious());
        assertTrue(page.getHasNext());
    }

    @Test
    @DisplayName("末页没有下一页")
    void lastPageHasNoNext() {
        PageResponse<String> page = PageResponse.of(List.of("a"), 25L, 3L, 10L);

        assertTrue(page.getHasPrevious());
        assertFalse(page.getHasNext());
    }

    @Test
    @DisplayName("整除时不会多出一页")
    void exactDivisionDoesNotAddPage() {
        assertEquals(2L, PageResponse.of(List.of(), 20L, 1L, 10L).getPages());
    }

    @Test
    @DisplayName("空结果集：0 页、前后都没有")
    void emptyResult() {
        PageResponse<String> page = PageResponse.of(List.of(), 0L, 1L, 10L);

        assertEquals(0L, page.getPages());
        assertFalse(page.getHasPrevious());
        assertFalse(page.getHasNext());
        assertTrue(page.getRecords().isEmpty());
    }

    @Test
    @DisplayName("map 只换记录类型，分页元信息原样保留")
    void mapKeepsPaginationMetadata() {
        PageResponse<String> source = PageResponse.of(List.of("1", "2"), 25L, 2L, 10L);

        PageResponse<Integer> mapped = source.map(records -> records.stream().map(Integer::parseInt).toList());

        assertEquals(List.of(1, 2), mapped.getRecords());
        assertEquals(source.getTotal(), mapped.getTotal());
        assertEquals(source.getPageNum(), mapped.getPageNum());
        assertEquals(source.getPageSize(), mapped.getPageSize());
        assertEquals(source.getPages(), mapped.getPages());
        assertEquals(source.getHasPrevious(), mapped.getHasPrevious());
        assertEquals(source.getHasNext(), mapped.getHasNext());
    }
}
