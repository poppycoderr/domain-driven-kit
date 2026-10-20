package com.ddk.projection.starter;

/**
 * 读模型的入口：标记哪一条数据变了，以及重建整个读模型。
 * <p>
 * 标记写进一张待刷新表。在事务里调用时，标记和业务修改一起提交、一起回滚：数据改了而读模型没有跟上的情况不会因为进程崩溃而出现。
 * 提交之后立即刷新，失败的稍后重试，另有定时清扫处理遗留的标记。同一条数据在刷新之前被标记多次，只刷新一次。
 * <p>
 * 多租户应用里，标记记录调用时 {@code OperatorContext} 里的租户，后台以同一个租户刷新；租户内唯一的标识在不同租户下互不影响。
 * <p>
 * 实现由自动配置提供，应用注入使用即可；测试里可以直接替换成桩。
 */
public interface Projections {

    /**
     * 标记读模型里的这一条需要刷新。通常在领域事件的监听器里调用，事件里带着聚合的标识。
     *
     * @param id 聚合的标识；{@link com.ddk.core.domain.Identifier} 取它的原始值，其他类型取 {@code String.valueOf}
     * @throws IllegalArgumentException 没有这个名称的读模型
     */
    void markDirty(String projection, Object id);

    /**
     * 重建读模型：把写模型里的每一个标识都标记为需要刷新，随后由后台逐个刷新。方法返回时标记已经写完，刷新可能还在进行，
     * 用 {@link #pending} 查看剩余数量。重建期间进程重启不影响结果，没处理完的标记仍在表里。
     * <p>
     * 写模型的标识在调用方的上下文里枚举，所以多租户应用里重建的是调用方所在的租户；要重建全部租户，逐个租户用
     * {@code OperatorContext.runAs} 调用。
     * <p>
     * 重建不会删除读模型里多出来的数据（写模型里已经没有的那些）；需要清理时先清空读模型再重建。
     *
     * @return 标记的数量
     * @throws IllegalArgumentException      没有这个名称的读模型
     * @throws UnsupportedOperationException 这个读模型没有实现 {@link Projection#forEachId}
     */
    long rebuild(String projection);

    /**
     * 这个读模型还有多少条等待刷新，包括失败后等待重试的，所有租户合计。
     *
     * @throws IllegalArgumentException 没有这个名称的读模型
     */
    long pending(String projection);
}
