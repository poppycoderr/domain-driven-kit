package com.example.user.infrastructure.converter;

import com.ddk.core.mapper.EnhancedMapper;
import com.ddk.core.mapper.ObjectMapper;
import com.example.user.domain.model.entity.User;
import com.example.user.infrastructure.orm.po.UserPO;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 领域实体转持久化对象。聚合根没有 setter、值对象是 record，结构差异由手写转换显式表达。
 */
@Component
@EnhancedMapper(source = User.class, target = UserPO.class, description = "User -> UserPO")
public class UserPoConverter implements ObjectMapper<User, UserPO> {

    @Override
    public UserPO map(User source) {
        UserPO po = new UserPO();
        po.setId(source.id().value());
        po.setUsername(source.username());
        po.setPassword(source.encryptedPassword());
        po.setGender(source.gender().getCode());
        po.setPhoneNumber(source.phoneNumber().value());
        po.setEmail(source.email() == null ? null : source.email().value());
        po.setStatus(source.enabled());
        po.setVersion(source.version());
        return po;
    }

    @Override
    public List<UserPO> map(List<User> sources) {
        return sources.stream().map(this::map).toList();
    }
}
