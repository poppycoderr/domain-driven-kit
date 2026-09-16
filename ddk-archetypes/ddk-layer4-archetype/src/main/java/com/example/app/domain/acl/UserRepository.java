package com.example.app.domain.acl;

import com.ddk.core.repository.GenericRepository;
import com.example.app.domain.model.entity.User;

import java.util.Optional;

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
     * 按用户名查找。
     * <p>
     * 通用仓储只提供按标识和分页查询；这种「领域里有名字的查询」应当显式声明在
     * 领域层的契约上，而不是让应用层自己拼条件。
     */
    Optional<User> findByUsername(String username);
}
