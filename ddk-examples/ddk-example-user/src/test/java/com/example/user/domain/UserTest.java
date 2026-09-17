package com.example.user.domain;

import com.ddk.core.exception.BusinessException;
import com.example.user.domain.error.UserError;
import com.example.user.domain.event.UserDisabledEvent;
import com.example.user.domain.event.UserRegisteredEvent;
import com.example.user.domain.model.entity.User;
import com.example.user.domain.model.entity.UserId;
import com.example.user.domain.model.enums.Gender;
import com.example.user.domain.model.valueobject.Email;
import com.example.user.domain.model.valueobject.PhoneNumber;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

    private static User newUser() {
        return User.register(UserId.of(1L), "alice", "hash", Gender.FEMALE, new PhoneNumber("13800138000"), null);
    }

    @Test
    void registerRecordsRegisteredEvent() {
        User user = newUser();

        assertThat(user.enabled()).isTrue();
        assertThat(user.drainDomainEvents()).singleElement().isInstanceOfSatisfying(UserRegisteredEvent.class,
                event -> assertThat(event.userId()).isEqualTo(UserId.of(1L)));
    }

    @Test
    void restoreRecordsNoEvents() {
        User user = User.restore(UserId.of(1L), "alice", "hash", Gender.FEMALE, new PhoneNumber("13800138000"), null, true, 3L);

        assertThat(user.hasDomainEvents()).isFalse();
        assertThat(user.version()).isEqualTo(3L);
    }

    @Test
    void disableIsIdempotent() {
        User user = newUser();
        user.drainDomainEvents();

        user.disable("spam");
        user.disable("spam again");

        assertThat(user.enabled()).isFalse();
        assertThat(user.drainDomainEvents()).singleElement().isInstanceOf(UserDisabledEvent.class);
    }

    @Test
    void changeProfileKeepsFieldsLeftNull() {
        User user = newUser();

        user.changeProfile(null, new Email("alice@example.com"));

        assertThat(user.username()).isEqualTo("alice");
        assertThat(user.email().value()).isEqualTo("alice@example.com");
    }

    @Test
    void invariantsAreBusinessErrors() {
        assertThatThrownBy(() -> newUser().changeProfile("abc", null)).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(UserError.INVALID_USERNAME));
        assertThatThrownBy(() -> new PhoneNumber("12345")).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(UserError.INVALID_PHONE_NUMBER));
        assertThatThrownBy(() -> Gender.of(9)).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(UserError.INVALID_GENDER));
    }

    @Test
    void phoneNumberIsMasked() {
        assertThat(new PhoneNumber("13800138000").masked()).isEqualTo("138****8000");
    }
}
