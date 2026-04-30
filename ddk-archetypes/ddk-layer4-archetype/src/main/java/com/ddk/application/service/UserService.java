package com.ddk.application.service;

import com.ddk.application.command.UserCreateCommand;
import com.ddk.application.command.UserUpdateCommand;
import com.ddk.application.query.UserPageQuery;
import com.ddk.application.response.UserDTO;
import com.ddk.core.mapper.MapperProvider;
import com.ddk.core.page.PageResponse;
import com.ddk.domain.acl.UserRepository;
import com.ddk.domain.model.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户应用服务
 *
 * @author Elijah Du
 * @date 2025/2/19
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final MapperProvider mapperProvider;

    public void create(List<UserCreateCommand> commands) {
        List<User> users = mapperProvider.lookup(UserCreateCommand.class, User.class).map(commands);
        userRepository.create(users);
    }

    public UserDTO getById(Long id) {
        User user = userRepository.find(id);
        return mapperProvider.lookup(User.class, UserDTO.class).map(user);
    }

    public PageResponse<UserDTO> getByPage(@Valid UserPageQuery query) {
        PageResponse<User> page = userRepository.page(query);
        return page.map(mapperProvider.lookup(User.class, UserDTO.class)::map);
    }

    public void update(@Valid UserUpdateCommand command) {
        User user = mapperProvider.lookup(UserUpdateCommand.class, User.class).map(command);
        userRepository.update(user);
    }

    public void deleteById(Long id) {
        userRepository.remove(id);
    }
}
