package com.example.app.application.assembler;

import com.example.app.application.response.UserDTO;
import com.example.app.domain.model.entity.User;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 领域实体 -> 对外 DTO。
 * <p>
 * 放在应用层而不是基础设施层：DTO 的形状由对外契约决定，属于用例的一部分。
 * 也<b>没有</b>注册成 {@code @EnhancedMapper}——{@code MapperProvider} 服务的是
 * 仓储的 Entity↔PO 转换，出站装配走普通 Bean 更直白。
 * <p>
 * 注意手机号在这里做了脱敏，这正是「不要把领域对象直接扔给 Jackson」的理由。
 *
 * @author Elijah Du
 */
@Component
public class UserAssembler {

    public UserDTO toDTO(User user) {
        if (user == null) {
            return null;
        }
        UserDTO dto = new UserDTO();
        dto.setId(user.id() == null ? null : user.id().value());
        dto.setUsername(user.username());
        dto.setGender(user.gender() == null ? null : user.gender().getDescription());
        dto.setEmail(user.email() == null ? null : user.email().value());
        dto.setPhoneNumber(user.phoneNumber() == null ? null : user.phoneNumber().masked());
        dto.setStatus(user.enabled());
        return dto;
    }

    public List<UserDTO> toDTO(List<User> users) {
        return users == null ? List.of() : users.stream().map(this::toDTO).toList();
    }
}
