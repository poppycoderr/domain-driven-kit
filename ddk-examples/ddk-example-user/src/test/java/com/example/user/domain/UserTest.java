package com.example.user.domain;

import com.ddk.test.domain.DdkAssertions;
import com.example.user.domain.error.UserError;
import com.example.user.domain.event.UserDisabledEvent;
import com.example.user.domain.event.UserRegisteredEvent;
import com.example.user.domain.model.entity.User;
import com.example.user.domain.model.entity.UserId;
import com.example.user.domain.model.enums.Gender;
import com.example.user.domain.model.valueobject.Email;
import com.example.user.domain.model.valueobject.PhoneNumber;
import org.junit.jupiter.api.Test;

import static com.ddk.test.domain.DdkAssertions.assertThatRejected;
import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    private static User newUser() {
        return User.register(UserId.of(1L), "alice", "hash", Gender.FEMALE, new PhoneNumber("13800138000"), null);
    }

    @Test
    void registerRecordsRegisteredEvent() {
        User user = newUser();

        assertThat(user.enabled()).isTrue();
        DdkAssertions.assertThat(user)
                .hasRaisedExactly(UserRegisteredEvent.class)
                .hasRaised(UserRegisteredEvent.class, event -> assertThat(event.userId()).isEqualTo(UserId.of(1L)));
    }

    @Test
    void restoreRecordsNoEvents() {
        User user = User.restore(UserId.of(1L), "alice", "hash", Gender.FEMALE, new PhoneNumber("13800138000"), null, true, 3L);

        DdkAssertions.assertThat(user).hasRaisedNoEvents();
        assertThat(user.version()).isEqualTo(3L);
    }

    @Test
    void disableIsIdempotent() {
        User user = newUser();
        user.drainDomainEvents();

        user.disable("spam");
        user.disable("spam again");

        assertThat(user.enabled()).isFalse();
        DdkAssertions.assertThat(user).hasRaisedExactly(UserDisabledEvent.class);
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
        assertThatRejected(() -> newUser().changeProfile("abc", null)).withCode(UserError.INVALID_USERNAME);
        assertThatRejected(() -> new PhoneNumber("12345")).withCode(UserError.INVALID_PHONE_NUMBER);
        assertThatRejected(() -> Gender.of(9)).withCode(UserError.INVALID_GENDER);
    }

    @Test
    void phoneNumberIsMasked() {
        assertThat(new PhoneNumber("13800138000").masked()).isEqualTo("138****8000");
    }
}
