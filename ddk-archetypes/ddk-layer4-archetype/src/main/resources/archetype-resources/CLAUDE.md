@AGENTS.md

Claude Code 额外说明：新增聚合、用例或领域事件时，优先调用 `.claude/skills/` 下对应的 Skill；完成后运行 `mvn verify`，确认 `ArchitectureTest` 通过再汇报。
