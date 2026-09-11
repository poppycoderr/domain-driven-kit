package com.ddk.mybatis.repository;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ddk.core.domain.AbstractDomainEvent;
import com.ddk.core.domain.AggregateRoot;
import com.ddk.core.domain.Identifier;
import com.ddk.core.mapper.EnhancedMapper;
import com.ddk.core.mapper.ObjectMapper;
import com.ddk.core.page.PageQuery;
import com.ddk.mybatis.query.Operator;
import com.ddk.mybatis.query.Query;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 集成测试用的最小领域：一个用户聚合根、它的 PO、两个方向的转换器。
 * <p>
 * 刻意做成「聚合根 + 类型化标识 + 领域事件」的完整形态，
 * 因为通用仓储要验证的正是这套东西能不能真的落库再读回来。
 */
final class IntegrationFixtures {

    private IntegrationFixtures() {
    }

    static final class UserId extends Identifier<Long> {
        private UserId(Long value) {
            super(value);
        }

        static UserId of(Long value) {
            return new UserId(value);
        }
    }

    static final class UserDisabledEvent extends AbstractDomainEvent {
        private final UserId userId;

        UserDisabledEvent(UserId userId) {
            this.userId = userId;
        }

        UserId userId() {
            return userId;
        }
    }

    static final class User extends AggregateRoot<UserId> {
        private String username;
        private int gender;
        private String email;
        private boolean enabled;

        private User() {
        }

        static User register(String username, int gender, String email) {
            User user = new User();
            user.username = username;
            user.gender = gender;
            user.email = email;
            user.enabled = true;
            return user;
        }

        static User restore(UserId id, String username, int gender, String email,
                            boolean enabled, Long version) {
            User user = new User();
            user.id = id;
            user.username = username;
            user.gender = gender;
            user.email = email;
            user.enabled = enabled;
            user.assignVersion(version);
            return user;
        }

        void rename(String newName) {
            this.username = newName;
        }

        void disable() {
            if (!enabled) {
                return;
            }
            this.enabled = false;
            registerEvent(new UserDisabledEvent(id()));
        }

        String username() {
            return username;
        }

        int gender() {
            return gender;
        }

        String email() {
            return email;
        }

        boolean enabled() {
            return enabled;
        }
    }

    @Data
    @TableName("ddk_user")
    public static class UserPO {

        @TableId(type = IdType.AUTO)
        private Long id;

        private String username;

        private Integer gender;

        private String email;

        private Boolean status;

        @Version
        private Long version;
    }

    @Mapper
    public interface UserMapper extends BaseMapper<UserPO> {
    }

    @EnhancedMapper(source = User.class, target = UserPO.class)
    public static class UserPoConverter implements ObjectMapper<User, UserPO> {
        @Override
        public UserPO map(User source) {
            if (source == null) {
                return null;
            }
            UserPO po = new UserPO();
            po.setId(source.isNew() ? null : source.id().value());
            po.setUsername(source.username());
            po.setGender(source.gender());
            po.setEmail(source.email());
            po.setStatus(source.enabled());
            po.setVersion(source.version());
            return po;
        }

        @Override
        public List<UserPO> map(List<User> sources) {
            return sources == null ? List.of() : sources.stream().map(this::map).toList();
        }
    }

    @EnhancedMapper(source = UserPO.class, target = User.class)
    public static class UserEntityConverter implements ObjectMapper<UserPO, User> {
        @Override
        public User map(UserPO source) {
            if (source == null) {
                return null;
            }
            return User.restore(
                    UserId.of(source.getId()),
                    source.getUsername(),
                    source.getGender(),
                    source.getEmail(),
                    Boolean.TRUE.equals(source.getStatus()),
                    source.getVersion());
        }

        @Override
        public List<User> map(List<UserPO> sources) {
            return sources == null ? List.of() : sources.stream().map(this::map).toList();
        }
    }

    public static class UserRepository extends GenericRepositoryImpl<User, Long, UserPO, UserMapper> {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class UserPageQuery extends PageQuery {

        @Query(value = "username", operator = Operator.LIKE)
        private String username;

        @Query(value = "gender", operator = Operator.IN)
        private List<Integer> genders;

        @Query(value = "status")
        private Boolean status;
    }
}
