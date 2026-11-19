package com.ddk.event.starter.inbox;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 已处理表的到期索引和分批清理，在 H2、PostgreSQL 和 MySQL 上各跑一遍：清理用的行值 {@code IN} 和建索引都与方言有关。
 */
final class InboxCleanupChecks {

    private InboxCleanupChecks() {
    }

    static void purgesInBatchesAndKeepsRecentRecords(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        IdempotentConsumer consumer = new IdempotentConsumer(jdbc, transactionManager, "batch_processed", Clock.systemUTC());
        consumer.prepare(true);
        jdbc.update("DELETE FROM batch_processed");
        Timestamp old = Timestamp.from(Instant.now().minus(Duration.ofDays(10)));
        for (int i = 1; i <= 5; i++) {
            jdbc.update("INSERT INTO batch_processed (consumer, message_id, processed_at) VALUES (?, ?, ?)", "mail", "evt-" + i, old);
        }
        jdbc.update("INSERT INTO batch_processed (consumer, message_id, processed_at) VALUES (?, ?, ?)", "points", "evt-1", old);
        consumer.handle("mail", "evt-recent", () -> { });

        assertThat(consumer.purgeOlderThan(Duration.ofDays(7), 2)).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM batch_processed", Integer.class)).isEqualTo(1);
        assertThat(consumer.isProcessed("mail", "evt-recent")).isTrue();
        assertThat(consumer.purgeOlderThan(Duration.ofDays(7), 2)).isZero();
        assertThatThrownBy(() -> consumer.purgeOlderThan(Duration.ofDays(7), 0)).isInstanceOf(IllegalArgumentException.class);
    }

    static void addsTheExpiryIndexOnlyWhenAllowed(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        IdempotentConsumer consumer = new IdempotentConsumer(jdbc, transactionManager, "indexed_processed", Clock.systemUTC());
        jdbc.execute(consumer.schema());
        assertThat(leadingIndexColumns(jdbc, "indexed_processed")).doesNotContain("processed_at");

        consumer.prepare(false);
        assertThat(leadingIndexColumns(jdbc, "indexed_processed")).as("left to the migration tool").doesNotContain("processed_at");

        consumer.prepare(true);
        assertThat(leadingIndexColumns(jdbc, "indexed_processed")).contains("processed_at");
        consumer.prepare(true);
    }

    private static Set<String> leadingIndexColumns(JdbcTemplate jdbc, String table) {
        return jdbc.execute((ConnectionCallback<Set<String>>) connection -> {
            DatabaseMetaData metaData = connection.getMetaData();
            Set<String> columns = new HashSet<>();
            for (String name : new String[] {table, table.toUpperCase(Locale.ROOT)}) {
                try (ResultSet rs = metaData.getIndexInfo(null, null, name, false, true)) {
                    while (rs.next()) {
                        String column = rs.getString("COLUMN_NAME");
                        if (column != null && rs.getShort("ORDINAL_POSITION") == 1) {
                            columns.add(column.toLowerCase(Locale.ROOT));
                        }
                    }
                }
            }
            return columns;
        });
    }
}
