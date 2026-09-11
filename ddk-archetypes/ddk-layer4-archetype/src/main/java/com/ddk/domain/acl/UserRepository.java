package com.ddk.domain.acl;

import com.ddk.core.repository.GenericRepository;
import com.ddk.domain.model.entity.User;

/**
 * 用户仓储契约。
 * <p>
 * 放在<b>领域层</b>而实现放在基础设施层——这就是依赖倒置：
 * 领域层说「我需要能存取用户」，但不关心是 MySQL 还是别的什么。
 * 包名叫 {@code acl}（Anti-Corruption Layer，防腐层），
 * 表达的是「外部世界不许把它的形状渗进领域模型」。
 *
 * @author Elijah Du
 * @date 2025/2/19
 */
public interface UserRepository extends GenericRepository<User, Long> {

    /**
     * 保存新用户，回填数据库生成的标识，并发布聚合上累积的领域事件。
     * <p>
     * 为什么不直接用继承来的 {@code create(User)}：它返回 {@code boolean}，
     * 拿不回自增主键，聚合也就无法完成 {@code onPersisted} 这一步。
     *
     * @return 已带上标识的同一个聚合实例
     */
    User save(User user);

    /**
     * 更新已有用户，并发布聚合上累积的领域事件。
     */
    User saveChanges(User user);
}
