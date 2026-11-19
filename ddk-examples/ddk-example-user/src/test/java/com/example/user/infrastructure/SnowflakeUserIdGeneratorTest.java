package com.example.user.infrastructure;

import com.example.user.domain.acl.UserIdGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 用户标识和 {@code ASSIGN_ID} 主键走同一个发号器，{@code ddk.mybatis.worker-id} 和 {@code datacenter-id} 对它们一样生效。
 * 雪花 ID 从低位起是 12 位序号、5 位机器号、5 位数据中心号。
 */
@SpringBootTest(properties = {"ddk.mybatis.worker-id=5", "ddk.mybatis.datacenter-id=3"})
class SnowflakeUserIdGeneratorTest {

    @Autowired
    private UserIdGenerator ids;

    @Test
    void userIdsCarryTheConfiguredWorkerAndDatacenter() {
        long id = ids.nextId().value();

        assertThat((id >> 12) & 31).isEqualTo(5);
        assertThat((id >> 17) & 31).isEqualTo(3);
    }
}
