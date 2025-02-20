package io.github.ddk.application.response;

import lombok.Data;

/**
 * @author Elijah Du
 * @date 2025/2/19
 */
@Data
public class UserDTO {

    private Long id;

    private String username;

    private String email;

    private String phoneNumber;

    private Boolean status;
}
