package com.example.user.application.service;

import com.ddk.core.exception.BusinessException;
import com.ddk.core.page.PageResponse;
import com.example.user.application.command.RegisterUserCommand;
import com.example.user.application.command.UpdateUserCommand;
import com.example.user.application.query.UserPageQuery;
import com.example.user.application.response.UserResponse;
import com.example.user.domain.acl.UserIdGenerator;
import com.example.user.domain.acl.UserRepository;
import com.example.user.domain.error.UserError;
import com.example.user.domain.model.entity.User;
import com.example.user.domain.model.enums.Gender;
import com.example.user.domain.model.valueobject.Email;
import com.example.user.domain.model.valueobject.PhoneNumber;
import com.example.user.domain.service.PasswordEncryptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户应用服务：取出聚合、调用领域方法、保存、转换响应。业务规则在 {@link User} 里，这里只做编排与事务。
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserIdGenerator userIdGenerator;
    private final PasswordEncryptionService passwordEncryptionService;

    @Transactional
    public UserResponse register(RegisterUserCommand command) {
        requireUsernameAvailable(command.username());
        User user = User.register(userIdGenerator.nextId(), command.username(), passwordEncryptionService.encrypt(command.password()),
                Gender.of(command.gender()), new PhoneNumber(command.phoneNumber()), Email.ofNullable(command.email()));
        return UserResponse.from(userRepository.create(user));
    }

    public UserResponse get(Long id) {
        return UserResponse.from(requireUser(id));
    }

    public PageResponse<UserResponse> page(UserPageQuery query) {
        return userRepository.page(query).map(users -> users.stream().map(UserResponse::from).toList());
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserCommand command) {
        User user = requireUser(id);
        if (user.isRenamingTo(command.username())) {
            requireUsernameAvailable(command.username());
        }
        user.changeProfile(command.username(), Email.ofNullable(command.email()));
        return UserResponse.from(userRepository.update(user));
    }

    @Transactional
    public void disable(Long id, String reason) {
        User user = requireUser(id);
        user.disable(reason);
        userRepository.update(user);
    }

    @Transactional
    public void enable(Long id) {
        User user = requireUser(id);
        user.enable();
        userRepository.update(user);
    }

    @Transactional
    public void delete(Long id) {
        if (!userRepository.remove(id)) {
            throw new BusinessException(UserError.USER_NOT_FOUND, id);
        }
    }

    private User requireUser(Long id) {
        return userRepository.find(id).orElseThrow(() -> new BusinessException(UserError.USER_NOT_FOUND, id));
    }

    private void requireUsernameAvailable(String username) {
        if (userRepository.findByUsername(username).isPresent()) {
            throw new BusinessException(UserError.USERNAME_TAKEN, username);
        }
    }
}
