package com.example.user.infrastructure.converter;

import com.ddk.core.mapper.EnhancedMapper;
import com.ddk.core.mapper.ObjectMapper;
import com.example.user.domain.model.entity.User;
import com.example.user.domain.model.entity.UserId;
import com.example.user.domain.model.enums.Gender;
import com.example.user.domain.model.valueobject.Email;
import com.example.user.domain.model.valueobject.PhoneNumber;
import com.example.user.infrastructure.orm.po.UserPO;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 持久化对象转领域实体，经由 {@link User#restore} 重建，不产生领域事件。
 */
@Component
@EnhancedMapper(source = UserPO.class, target = User.class, description = "UserPO -> User")
public class UserEntityConverter implements ObjectMapper<UserPO, User> {

    @Override
    public User map(UserPO source) {
        return User.restore(UserId.of(source.getId()), source.getUsername(), source.getPassword(), Gender.of(source.getGender()),
                new PhoneNumber(source.getPhoneNumber()), Email.ofNullable(source.getEmail()), Boolean.TRUE.equals(source.getStatus()),
                source.getVersion());
    }

    @Override
    public List<User> map(List<UserPO> sources) {
        return sources.stream().map(this::map).toList();
    }
}
