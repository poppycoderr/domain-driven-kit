package com.example.app.infrastructure.converter;

import com.ddk.core.mapper.EnhancedMapper;
import com.ddk.core.mapper.ObjectMapper;
import com.example.app.domain.model.entity.User;
import com.example.app.domain.model.entity.UserId;
import com.example.app.domain.model.enums.Gender;
import com.example.app.domain.model.valueobject.Email;
import com.example.app.domain.model.valueobject.PhoneNumber;
import com.example.app.infrastructure.orm.po.UserPO;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 持久化对象 -> 领域实体。
 * <p>
 * 走 {@code User.restore(...)} 而不是逐字段 set：重建聚合是一条受控路径，
 * 它明确表示「这是从存储恢复的既有事实」，因此<b>不产生领域事件</b>。
 *
 * @author Elijah Du
 */
@Component
@EnhancedMapper(source = UserPO.class, target = User.class, description = "持久化对象转领域实体")
public class UserEntityConverter implements ObjectMapper<UserPO, User> {

    @Override
    public User map(UserPO source) {
        if (source == null) {
            return null;
        }
        return User.restore(
                UserId.of(source.getId()),
                source.getUsername(),
                source.getPassword(),
                toGender(source.getGender()),
                new PhoneNumber(source.getPhoneNumber()),
                Email.ofNullable(source.getEmail()),
                Boolean.TRUE.equals(source.getStatus()),
                source.getVersion());
    }

    @Override
    public List<User> map(List<UserPO> sources) {
        return sources == null ? List.of() : sources.stream().map(this::map).toList();
    }

    private Gender toGender(Integer value) {
        if (value == null) {
            return null;
        }
        return Arrays.stream(Gender.values())
                .filter(g -> g.getGender() == value)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("数据库中存在非法的 gender 值：" + value));
    }
}
