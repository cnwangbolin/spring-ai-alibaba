# KLD 定制变更记录

> 本文档记录对 spring-ai-alibaba 上游项目的所有定制改动，版本基线 `2.0.0.0-RC1-kld-SNAPSHOT`（基于上游 `2.0.0.0-RC1`）。
> 每次变更必须在此登记，确保可追溯、可回滚、可向上游同步。

---

## 2.0.0.0-RC1-kld-SNAPSHOT (2026-07-08 — rebase 到上游 2.0.0.0-RC1)

### 背景

KLD fork 最初基于上游 `2.0.0-M1`（Spring AI `2.0.0-M6`）创建，做了 3 个 GA 适配补丁。
上游 `2.0.0.0-RC1` 已独立完成全部 GA 适配，且实现更优（新增 `AUTO_REGISTER=false`、`OptionsOverrideChatModel`、`ToolCallResponse.success()` 等）。
本次 rebase 到上游 `2.0.0.0-RC1`，丢弃 3 个已冗余的 GA 适配补丁，仅保留 KLD 独有功能。

### 保留的 KLD 定制

| # | 文件 | 变更 | 原因 |
|---|------|------|------|
| 1 | `SerializationHelper.java` | 新增 `serializeMediaList()` / `deserializeMediaList()` | 上游 media 序列化代码仍注释状态，cloneState 深拷贝时多模态数据丢失 |
| 2 | `UserMessageHandler.java` | 序列化/反序列化时处理 media 字段 | 同上 |
| 3 | `AssistantMessageHandler.java` | 序列化/反序列化时处理 media 字段 | 同上 |
| 4 | `SummarizationHook.java` | `findSafeCutoff()` 增强：消息数不足时也截断 + fallback 截断点 | tool-call 密集场景 token 超限但消息数未达阈值 |

### 已丢弃的补丁（上游已覆盖）

| # | 文件 | 原 KLD 变更 | 上游状态 |
|---|------|------------|----------|
| 1 | `DefaultBuilder.java` | NPE 修复 + `defaultOptions(Builder)` 适配 | 上游已修 + `OptionsOverrideChatModel` 演进 |
| 2 | `AgentLlmNode.java` | `call()`/`stream()` GA 适配 | 上游已修 + `AUTO_REGISTER=false` |
| 3 | `AgentToolNode.java` | `ToolCallback` 接口适配 | 上游已修 + `ToolCallResponse.success()` |

### 版本对齐

| 属性 | 值 | 说明 |
|------|-----|------|
| `revision` | `2.0.0.0-RC1-kld-SNAPSHOT` | 上游基线 + KLD 定制 + SNAPSHOT 可频繁改动 |
| `java.version` | `25` | 对齐 garnet + apex 全栈 JDK 25 |
| `spring-boot.version` | `4.1.0` | 对齐 garnet BOM 升级后的版本 |
| `spring-ai.version` | `2.0.0` | 不变 |
| `extensions-bom` | `2.0.0-M1.1`（临时） | 上游 `2.0.0.0-RC1` 未发布到 Maven Central/KLD Nexus，临时用 M1.1 编译 |

### 编译验证

```bash
mvn compile -pl spring-ai-alibaba-graph-core,spring-ai-alibaba-agent-framework -am -DskipTests
```

结果：**BUILD SUCCESS**（JDK 25 + Spring Boot 4.1.0 + Spring AI 2.0.0）
- 编译时间：30s
- 仅有 deprecated API warnings（`JsonParser`、`toolCallbacks`），不影响功能
| `spring-ai.version` | `2.0.0` | 不变 |

### 部署

- Nexus 仓库：`https://nexus.klxz.cnpc/repository/maven-snapshots/`
- 部署模块：`spring-ai-alibaba-graph-core`, `spring-ai-alibaba-agent-framework`

---

## 变更登记模板

```
## x.x.x-kld.N (YYYY-MM-DD)

### 简述
一句话描述本次变更目的。

### 变更明细
| # | 文件 | 变更 | 原因 |
|---|------|------|------|
| 1 | | | |

### 影响范围
- 影响的下游模块：
- 是否需要重新 deploy：

### 回滚方案
如何回退本次变更。
```
