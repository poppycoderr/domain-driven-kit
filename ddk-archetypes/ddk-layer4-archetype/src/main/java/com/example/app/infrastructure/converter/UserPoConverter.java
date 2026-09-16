package com.example.app.infrastructure.converter;

import com.ddk.core.mapper.EnhancedMapper;
import com.ddk.core.mapper.ObjectMapper;
import com.example.app.domain.model.entity.User;
import com.example.app.infrastructure.orm.po.UserPO;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 领域实体 -> 持久化对象。
 * <p>
 * <b>为什么是手写而不是 MapStruct 自动生成：</b>聚合根没有无参公开构造器和 setter，
 * 值对象是 record，标识是 {@code UserId} 而不是 {@code Long}——
 * 这些结构差异自动映射猜不对，也不该让它猜。
 * 手写转换器的代码量就是这几十行，换来的是映射规则完全可见、可单测。
 * <p>
 * 注意映射是<b>有方向</b>的：这里只负责 {@code User -> UserPO}，
 * 反向由 {@link UserEntityConverter} 负责。
 *
 * @author Elijah Du
 */
@Component
@EnhancedMapper(source = User.class, target = UserPO.class, description = "领域实体转持久化对象")
public class UserPoConverter implements ObjectMapper<User, UserPO> {

    @Override
    public UserPO map(User source) {
        if (source == null) {
            return null;
        }
        UserPO po = new UserPO();
        po.setId(source.id().value());
        po.setUsername(source.username());
        po.setPassword(source.encryptedPassword());
        po.setGender(source.gender() == null ? null : source.gender().getGender());
        po.setPhoneNumber(source.phoneNumber() == null ? null : source.phoneNumber().value());
        po.setEmail(source.email() == null ? null : source.email().value());
        po.setStatus(source.enabled());
        po.setVersion(source.version());
        return po;
    }

    @Override
    public List<UserPO> map(List<User> sources) {
        return sources == null ? List.of() : sources.stream().map(this::map).toList();
    }
}
