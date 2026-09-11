package com.ddk.core.domain;

import com.ddk.core.domain.TestFixtures.User;
import com.ddk.core.domain.TestFixtures.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Specification：可组合的业务规则")
class SpecificationTest {

    private static final Specification<User> ACTIVE = User::active;
    private static final Specification<User> NAMED_ALICE = u -> "alice".equals(u.name());

    private static User activeAlice() {
        return new User(UserId.of(1L), "alice");
    }

    private static User inactiveBob() {
        User user = new User(UserId.of(2L), "bob");
        user.deactivate();
        return user;
    }

    @Test
    @DisplayName("and：两个条件都满足才成立")
    void andRequiresBoth() {
        Specification<User> spec = ACTIVE.and(NAMED_ALICE);

        assertTrue(spec.isSatisfiedBy(activeAlice()));
        assertFalse(spec.isSatisfiedBy(inactiveBob()));
        assertFalse(spec.isSatisfiedBy(new User(UserId.of(3L), "carol")));
    }

    @Test
    @DisplayName("or：任一条件满足即成立")
    void orRequiresEither() {
        Specification<User> spec = NAMED_ALICE.or(ACTIVE);

        assertTrue(spec.isSatisfiedBy(activeAlice()));
        assertTrue(spec.isSatisfiedBy(new User(UserId.of(3L), "carol")));
        assertFalse(spec.isSatisfiedBy(inactiveBob()));
    }

    @Test
    @DisplayName("not：取反")
    void notInverts() {
        assertTrue(ACTIVE.not().isSatisfiedBy(inactiveBob()));
        assertFalse(ACTIVE.not().isSatisfiedBy(activeAlice()));
    }

    @Test
    @DisplayName("and 短路：左侧为假时不再判定右侧")
    void andShortCircuits() {
        boolean[] rightEvaluated = {false};
        Specification<User> right = u -> {
            rightEvaluated[0] = true;
            return true;
        };

        Specification<User> alwaysFalse = u -> false;
        assertFalse(alwaysFalse.and(right).isSatisfiedBy(activeAlice()));
        assertFalse(rightEvaluated[0]);
    }

    @Test
    @DisplayName("any / none 可以作为归约的初始值")
    void identitiesForReduction() {
        List<Specification<User>> specs = List.of(ACTIVE, NAMED_ALICE);

        Specification<User> all = specs.stream().reduce(Specification.any(), Specification::and);
        Specification<User> anyOf = specs.stream().reduce(Specification.none(), Specification::or);

        assertTrue(all.isSatisfiedBy(activeAlice()));
        assertFalse(all.isSatisfiedBy(inactiveBob()));
        assertTrue(anyOf.isSatisfiedBy(activeAlice()));
        assertTrue(Specification.<User>any().isSatisfiedBy(inactiveBob()));
        assertFalse(Specification.<User>none().isSatisfiedBy(activeAlice()));
    }

    @Test
    @DisplayName("组合 null 规格直接失败，而不是留到判定时才空指针")
    void rejectsNullComposition() {
        assertThrows(NullPointerException.class, () -> ACTIVE.and(null));
        assertThrows(NullPointerException.class, () -> ACTIVE.or(null));
    }
}
