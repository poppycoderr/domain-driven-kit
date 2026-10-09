package com.ddk.mybatis.starter.datascope;

import com.ddk.core.context.DataScope;
import com.ddk.core.context.Operator;

/**
 * 回答「这个操作者的数据范围是什么」。由应用实现并声明成 Bean：范围通常来自角色与组织关系，DDK 不知道它们怎么存。
 * <p>
 * 每条涉及受控表的 SQL 都会调用一次，实现里应当缓存，不要每次查库。查库时要注意：查询本身如果又落在受控的表上，会再次进入这里。
 */
@FunctionalInterface
public interface DataScopeResolver {

    DataScope resolve(Operator operator);
}
