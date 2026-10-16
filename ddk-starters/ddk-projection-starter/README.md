# DDK Projection Starter

Read models that follow the write model: a search index, a statistics table, a denormalized list view. A change is marked in the same transaction, the read model is refreshed after the commit, failures are retried, and the whole read model can be rebuilt.

```text
application service (one transaction)
  save the aggregate
  projections.markDirty("order-search", orderId)   -> a row in ddk_projection_pending
commit
  └─ background worker: OrderSearchProjection.refresh(orderId)
       load the order as it is now -> write it to the read model -> remove the row
```

## Refresh, not apply

A projection does not apply each event's change to the read model. It regenerates one entry from the current state of the write model:

```java
@Component
@RequiredArgsConstructor
public class OrderSearchProjection implements Projection {

    private final OrderRepository orders;
    private final OrderSearchIndex index;      // your own port to the read store
    private final JdbcTemplate jdbc;

    @Override
    public String name() {
        return "order-search";
    }

    @Override
    public void refresh(String id) {
        orders.find(OrderId.of(Long.valueOf(id)))
                .ifPresentOrElse(order -> index.save(OrderDocument.from(order)), () -> index.delete(id));
    }

    @Override
    public void forEachId(Consumer<String> ids) {          // only needed for rebuilds
        jdbc.query("SELECT id FROM t_order", rs -> {
            ids.accept(rs.getString(1));
        });
    }
}
```

This shape was chosen because DDK aggregates are stored as state, not as an event log:

- **Repeating it is harmless.** `refresh` overwrites, so a retry changes nothing. Two refreshes of one entry that overlap are a different matter, see [Several instances](#several-instances).
- **Order does not matter.** Two events for one aggregate can arrive in any order; both lead to "load the current state".
- **A rebuild runs the same code.** There is no event history to replay, and none is needed.

Events only say which entry changed:

```java
@EventListener
public void on(OrderPaidEvent event) {
    projections.markDirty("order-search", event.orderId());
}
```

Use a plain `@EventListener` so the mark joins the transaction that raised the event.

## What the starter guarantees

| Situation | Behaviour |
|---|---|
| The transaction commits | The mark commits with it; the refresh starts right after |
| The transaction rolls back | No mark, no refresh |
| The process dies after the commit | The mark is still in the table; the next sweep picks it up |
| One entry is marked several times before it is refreshed | One refresh |
| The entry changes again while it is being refreshed | The mark survives that refresh and the entry is refreshed once more |
| `refresh` throws | Retried with a doubling delay, up to `max-retry-delay`; the error is kept in `last_error` |
| Several instances | An entry is claimed by one worker at a time; see below |
| A worker pauses or dies mid-refresh | Its claim expires after `lease-time` and another worker takes over |

`markDirty` outside a transaction writes the mark at once and refreshes immediately.

## Several instances

All instances share the pending table. Each mark carries two identities:

| Column | Changes when | Answers |
|---|---|---|
| `generation` | the entry is marked again; a new random value, never reused | Has the entry changed since I started? |
| `claim_token`, `claimed_until` | a worker claims the mark, with a lease | Am I still the one handling it? |

```text
claim the mark (only if unclaimed, or the lease has expired)
refresh(id)                       read the source, write the read model
DELETE ... WHERE generation = the one I claimed AND claim_token = mine
  deleted      -> nothing changed since my claim; my write was the latest state
  not deleted  -> it changed, or my lease expired and someone took over:
                  my write may be stale, so mark the entry again
```

A worker that loses its lease during a long pause can still write an old snapshot when it wakes up. It cannot hide that: its acknowledgement misses and the entry is refreshed once more. **When no mark is left for an entry, the read model holds the state that was current at its last refresh.** The same holds for deletions: a stale write that brings a deleted entry back is followed by a refresh that removes it again.

The read model can show the stale snapshot for a moment, until that extra refresh runs. A read model that must never go back, even briefly, makes its writes conditional on the source version inside `refresh`: `WHERE source_version < ?` in SQL, `version_type=external_gte` in Elasticsearch. DDK aggregates carry that version (`AggregateRoot.version()`).

Choose `lease-time` longer than a normal refresh. Too short is not wrong, only wasteful: a slow refresh is taken over and the entry is refreshed twice.

## Rebuilding

```java
long marked = projections.rebuild("order-search");   // marks every id, returns when the marks are written
long left = projections.pending("order-search");     // what is still waiting
```

A rebuild only marks; the worker does the refreshing. It survives a restart, because unprocessed marks stay in the table. It does not remove entries the write model no longer has: empty the read model first when that matters.

## Configuration

| Property | Default | Description |
|---|---|---|
| `ddk.projection.enabled` | `true` | Register `Projections` and process pending refreshes |
| `ddk.projection.table` | `ddk_projection_pending` | Name of the pending table |
| `ddk.projection.initialize-schema` | `true` | Create the table at startup; turn off when a migration tool owns the schema |
| `ddk.projection.sweep-interval` | `30s` | How often leftover marks are picked up: other instances', unfinished ones from before a restart, retries that are due |
| `ddk.projection.batch-size` | `100` | Marks read from the table at a time |
| `ddk.projection.retry-delay` | `5s` | Wait before the first retry; doubles with every failure |
| `ddk.projection.max-retry-delay` | `5m` | Upper bound of the retry wait |
| `ddk.projection.lease-time` | `2m` | How long a claimed mark belongs to one worker before another may take over |

Invalid values fail the application at startup: a batch size below 1, a duration that is zero or negative, or a `retry-delay` above `max-retry-delay`.

The starter is active when the application has at least one `Projection` bean, a `JdbcTemplate` and a transaction manager.

```sql
CREATE TABLE ddk_projection_pending (
    projection    VARCHAR(100) NOT NULL,
    entity_id     VARCHAR(200) NOT NULL,
    version       BIGINT       NOT NULL,
    generation    VARCHAR(36),
    claim_token   VARCHAR(36),
    claimed_until TIMESTAMP,
    attempts      INT          NOT NULL,
    available_at  TIMESTAMP    NOT NULL,
    last_error    VARCHAR(500),
    PRIMARY KEY (projection, entity_id)
);
```

### Upgrading from 0.8

0.8 created the table without `generation`, `claim_token` and `claimed_until`. At startup the starter checks the columns:

- With `initialize-schema=true` (the default) it adds the missing columns and gives existing marks a generation.
- With `initialize-schema=false` it fails and prints the statements to put into your migration:

  ```sql
  ALTER TABLE ddk_projection_pending ADD COLUMN generation VARCHAR(36);
  ALTER TABLE ddk_projection_pending ADD COLUMN claim_token VARCHAR(36);
  ALTER TABLE ddk_projection_pending ADD COLUMN claimed_until TIMESTAMP;
  UPDATE ddk_projection_pending SET generation = CONCAT('upgraded-', version) WHERE generation IS NULL;
  ```

  It also fails when the table does not exist at all, instead of starting and failing on the first refresh.

During a rolling upgrade, 0.8 workers still run the old protocol and can still leave a stale entry behind. Stop the old instances first, or rebuild the projection once the upgrade is complete.

## Where it does not fit

- **Read models built from other services' events.** Those cannot be regenerated from a write model this application owns. Consume the events with `ddk-event-starter` and update the read model in the consumer.
- **High write rates.** One background thread per instance refreshes entries one at a time. There is no parallelism and no batching of writes to the read store.
- **The read store itself.** The starter does not talk to Elasticsearch or any other store; `refresh` does, with whatever client the application uses.
- **A mark that keeps failing is retried forever.** Watch `attempts` and `last_error` in the pending table; there is no dead-letter state.
