package com.ddk.application.service;

import com.ddk.application.assembler.UserAssembler;
import com.ddk.application.command.UserCreateCommand;
import com.ddk.application.command.UserUpdateCommand;
import com.ddk.application.query.UserPageQuery;
import com.ddk.application.response.UserDTO;
import com.ddk.core.exception.BusinessException;
import com.ddk.core.page.PageResponse;
import com.ddk.domain.acl.UserIdGenerator;
import com.ddk.domain.acl.UserRepository;
import com.ddk.domain.model.entity.User;
import com.ddk.domain.model.enums.Gender;
import com.ddk.domain.model.valueobject.Email;
import com.ddk.domain.model.valueobject.PhoneNumber;
import com.ddk.domain.service.PasswordEncryptionService;
import com.ddk.domain.error.UserError;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;

/**
 * 用户应用服务。
 * <p>
 * 应用服务的职责只有四件：<b>取出聚合、调用领域方法、保存、装配返回</b>。
 * 注意这里<b>没有一行业务规则</b>——用户名长度、禁用的幂等性、状态机约束
 * 全在 {@link User} 里。一旦开始在这里写 if，领域模型就又退化成 DTO 了。
 *
 * @author Elijah Du
 * @date 2025/2/19
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserIdGenerator userIdGenerator;
    private final PasswordEncryptionService passwordEncryptionService;
    private final UserAssembler userAssembler;

    @Transactional
    public UserDTO register(@Valid UserCreateCommand command) {
        User user = User.register(
                userIdGenerator.nextId(),
                command.getUsername(),
                passwordEncryptionService.encrypt(command.getPassword()),
                toGender(command.getGender()),
                new PhoneNumber(command.getPhoneNumber()),
                Email.ofNullable(command.getEmail()));

        return userAssembler.toDTO(userRepository.create(user));
    }

    public UserDTO getById(Long id) {
        return userAssembler.toDTO(requireUser(id));
    }

    public PageResponse<UserDTO> getByPage(@Valid UserPageQuery query) {
        return userRepository.page(query).map(userAssembler::toDTO);
    }

    @Transactional
    public UserDTO update(Long id, @Valid UserUpdateCommand command) {
        User user = requireUser(id);
        if (command.getUsername() != null) {
            user.rename(command.getUsername());
        }
        if (command.getEmail() != null) {
            user.changeEmail(new Email(command.getEmail()));
        }
        return userAssembler.toDTO(userRepository.update(user));
    }

    @Transactional
    public void disable(Long id, String reason) {
        User user = requireUser(id);
        user.disable(reason);
        userRepository.update(user);
    }

    @Transactional
    public void deleteById(Long id) {
        userRepository.remove(id);
    }

    private User requireUser(Long id) {
        return userRepository.find(id)
                .orElseThrow(() -> new BusinessException(UserError.USER_NOT_FOUND, id));
    }

    private Gender toGender(Integer value) {
        return Arrays.stream(Gender.values())
                .filter(g -> g.getGender() == value)
                .findFirst()
                .orElseThrow(() -> new BusinessException(UserError.INVALID_GENDER, value));
    }
}
