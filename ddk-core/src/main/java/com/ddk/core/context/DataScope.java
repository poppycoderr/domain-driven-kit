package com.ddk.core.context;

import java.util.Collection;
import java.util.Set;

/**
 * 一个操作者能看到、能改动哪些数据。
 * <p>
 * 用两个维度描述，覆盖最常见的行级数据权限：数据属于哪个组（部门、门店、项目组，由业务自己定义），以及数据是谁创建的。
 * 两个维度是「或」的关系：属于可见的组，或者是自己创建的，满足一个即可见。两个都不满足的操作者什么都看不到。
 *
 * @param unrestricted 不受限制，能看到全部数据；为 true 时另外两项没有意义
 * @param groups       可见的组的标识
 * @param own          自己创建的数据是否可见
 */
public record DataScope(
        boolean unrestricted,

        Set<String> groups,

        boolean own
) {

    public DataScope {
        groups = Set.copyOf(groups);
    }

    /**
     * 全部数据，例如管理员、系统任务。
     */
    public static DataScope all() {
        return new DataScope(true, Set.of(), false);
    }

    /**
     * 只有这些组的数据。
     */
    public static DataScope ofGroups(Collection<String> groups) {
        return new DataScope(false, Set.copyOf(groups), false);
    }

    /**
     * 只有自己创建的数据。
     */
    public static DataScope ownOnly() {
        return new DataScope(false, Set.of(), true);
    }

    /**
     * 什么都看不到。
     */
    public static DataScope none() {
        return new DataScope(false, Set.of(), false);
    }

    /**
     * 在当前范围之外，再加上自己创建的数据。
     */
    public DataScope andOwn() {
        return new DataScope(unrestricted, groups, true);
    }
}
