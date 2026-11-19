# 编码代理评测集

[English](./README.md) | 简体中文

DDK 生成的项目既写给人看，也写给编码代理看：`AGENTS.md`、`.claude/skills/` 下的 skill，以及一个失败时会说明怎么改的 `ArchitectureTest`。这个评测集检验这些是否真的起作用：每个任务给代理一个生成的项目和一段需求，再用代理没见过的测试给结果打分。

它不改任何库代码，也不属于发布内容。CI 只检查评分测试本身是否可靠。运行代理要花钱，也需要代理的凭据，所以由维护者手动运行，例如在一个改动了 archetype 的版本发布之前。

## 任务

| 任务 | 骨架 | 检验什么 |
|---|---|---|
| `layer4-place-order` | 四层 | 从零新增一个聚合：下单、查询、取消，错误码与参数校验，以及带上新订单 ID 的领域事件 |
| `layer4-cancel-reason` | 四层，在订单之上 | 修改已有的聚合和表：可选的取消原因和长度规则。要新增迁移脚本，不能改已经执行过的那个 |
| `layer4-status-shortcut` | 四层，在订单之上 | 一个简单的只读接口，需求里建议在控制器里直接调用仓储。分层规则不允许，代理要拒绝这个建议 |
| `layer3-place-order` | 三层 | 在三层骨架上实现与 `layer4-place-order` 相同的契约 |

每个任务目录包含：

- `task.json`：使用的骨架、起点任务（`seed`），以及仍须通过其评分测试的其他任务（`also_grade`）；
- `prompt.md`：交给代理的需求，语言与生成项目一致；
- `grader/`：隐藏的评分测试。代理结束后才复制到 `src/test/java/com/acme/order/eval/`，测试需求里写明的 HTTP 契约和少数类型名；
- `reference/`：一份参考实现，覆盖到生成的项目上。CI 用它确认评分测试可以通过，以 `seed` 引用它的任务也从它开始。

## 评分过程

见英文版的伪代码。生成项目后，`ArchitectureTest.java` 和已有的迁移脚本被记为受保护文件，代理改动了其中任何一个就判为失败（阶段 `protected`）；之后复制评分测试并执行 `mvn verify`，失败时阶段为 `compile` 或 `verify`。结果把 surefire 报告分成架构测试、评分测试和项目自己的测试三组，从而看得出失败是破坏了分层规则，还是没有满足契约。

## 运行

先把当前检出的 DDK 安装到本地 Maven 仓库：

```bash
mvn -B -ntp install -DskipTests
```

像 CI 那样检查评分测试：每个任务在空项目上必须失败，套上参考实现后必须通过。

```bash
scripts/run-agent-eval.py --check
```

运行代理。命令从标准输入读取需求，工作目录是生成的项目：

```bash
scripts/run-agent-eval.py --agent 'claude -p --permission-mode acceptEdits'
```

```bash
scripts/run-agent-eval.py --agent 'codex exec -s workspace-write -' --task layer4-status-shortcut
```

项目生成在仓库之外的一个新临时目录里，这样代理读到的是生成项目的 `AGENTS.md`，而不是 DDK 自己的；`--work DIR` 可以指定别的位置。结果在 `target/agent-eval/<时间>/`：`results.md`、`results.json`，以及每个任务的代理输出、构建日志、代理所做的 diff 和 ArchGuard 报告。

代理的行为不是确定的。一次失败不足以下结论，每个任务应当多跑几次，并且要读 diff：一个把大量代码塞进控制器的通过，或者一个因为需求没写清楚名字而导致的失败，比通过的数量更能说明问题。

## 新增任务

按用户的口吻写需求，并写明评分测试编译时依赖的每一个名字。尽量通过 HTTP 契约评分，让代理自己决定内部设计。写好参考实现后运行 `scripts/run-agent-eval.py --check --task <id>`：空项目必须失败，参考实现必须通过。加入任务之前，还要确认评分测试能拒绝这个任务针对的那种错误，例如写一个小脚本作为 `--agent`：套上参考实现，再故意犯这个错误。
