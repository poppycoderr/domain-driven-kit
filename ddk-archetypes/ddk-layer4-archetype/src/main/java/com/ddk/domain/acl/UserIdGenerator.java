package com.ddk.domain.acl;

import com.ddk.domain.model.entity.UserId;

/**
 * 用户标识生成器。
 * <p>
 * 这是领域层定义的第二个端口（第一个是 {@link UserRepository}）。
 * 领域说「我需要能造出一个新的 UserId」，但不关心背后是雪花、UUID 还是号段——
 * 那是基础设施的选择。
 *
 * <h2>为什么要预生成标识，而不是等数据库自增</h2>
 * 聚合从诞生起就有身份，于是：
 * <ul>
 *     <li>工厂方法里就能登记带标识的领域事件，不需要「写入后回填」这一步</li>
 *     <li>不存在「传入仓储的实例」和「仓储返回的实例」不是同一个的困扰</li>
 *     <li>跨聚合引用可以在落库之前就建立</li>
 * </ul>
 * 代价是主键不再连续，对 InnoDB 的聚簇索引不算友好——雪花 ID 大体有序，
 * 这个代价可以接受。
 *
 * @author Elijah Du
 */
public interface UserIdGenerator {

    UserId nextId();
}
