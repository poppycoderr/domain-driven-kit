package com.ddk.projection.starter;

import java.util.function.Consumer;

/**
 * 一个读模型：为查询准备的另一份数据，例如搜索索引、统计表、列表页用的宽表。由应用实现并声明成 Bean。
 * <p>
 * 读模型不靠「把每个事件的变化应用上去」来维护，而是在数据变了之后整条重新生成：{@link #refresh} 读取写模型里的当前状态，
 * 写进读模型。这样做有三个好处：重复执行没有副作用，不依赖事件到达的先后，重建时走的是同一段代码。
 * 事件只需要说明「哪一条变了」，见 {@link Projections#markDirty}。
 */
public interface Projection {

    /**
     * 读模型的名称，应用内唯一。
     */
    String name();

    /**
     * 用写模型里的当前状态更新读模型里的这一条；写模型里已经不存在时把它从读模型里删掉。
     * <p>
     * 同一个标识可能被调用多次，也可能在多个实例上同时调用，所以要写成覆盖式的（upsert / delete），不要写成累加式的。
     * 抛出异常表示这次刷新失败，稍后会重试。
     */
    void refresh(String id);

    /**
     * 列出写模型里的全部标识，用于重建。数据量大时应当分页读取，逐个交给 {@code ids}，不要一次全部读进内存。
     *
     * @throws UnsupportedOperationException 这个读模型不支持重建（默认）
     */
    default void forEachId(Consumer<String> ids) {
        throw new UnsupportedOperationException("Projection " + name() + " does not support rebuilds: implement forEachId");
    }
}
