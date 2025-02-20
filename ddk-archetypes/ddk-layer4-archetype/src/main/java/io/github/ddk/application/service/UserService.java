package io.github.ddk.application.service;

import io.github.ddk.application.command.UserCreateCommand;
import io.github.ddk.application.command.UserUpdateCommand;
import io.github.ddk.application.query.UserPageQuery;
import io.github.ddk.application.response.UserDTO;
import io.github.ddk.core.mapper.MapperProvider;
import io.github.ddk.core.page.PageResponse;
import io.github.ddk.domain.acl.UserRepository;
import io.github.ddk.domain.model.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.validation.Valid;
import java.util.List;

/**
 * @author Elijah Du
 * @date 2025/2/19
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final MapperProvider<User, UserCreateCommand> provider;

    @Deprecated(since = "2025-02-19", forRemoval = true)
    public void create(UserCreateCommand command) {
        userRepository.create(provider.mapToLeft(command));
    }

    public void create(List<UserCreateCommand> commands) {
        userRepository.create(provider.mapToLeft(commands));
    }

    public UserDTO getById(Long id) {
        return null;
    }

    public PageResponse<UserDTO> getByPage(@Valid UserPageQuery query) {
        return null;
    }

    public void update(@Valid UserUpdateCommand command) {
    }

    public void deleteById(Long id) {
    }
}