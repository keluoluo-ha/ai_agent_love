# RAG 检索评测说明

## 目的

为面试中「检索精准度提升 XX%」提供**可复现的离线评测**，避免空口无凭。

## 测试集

| 文件 | 规模 | 用途 |
|------|------|------|
| `src/test/resources/rag/rag-eval-dataset.json` | 30 条 | 基础集 |
| `src/test/resources/rag/rag-eval-dataset-hard.json` | 25 条 | **高区分度集**（推荐面试评测） |

## 对比配置

| 配置 | 说明 |
|------|------|
| Baseline-0 | 纯向量检索 |
| Baseline-1 | + 相似度阈值 **0.50**（与 `RagEvalProfile` 一致） |
| Baseline-2 | + status 多维过滤 |
| Baseline-3 | + Query 扩展（3 路） |
| Ours/FULL | 完整链路 |

## 指标

- **Hit@5**：Top5 中是否至少命中 1 个相关文档块
- **MRR@5**：第一个正确结果的倒数排名均值
- **StatusAcc@1**：Top1 是否属于正确 status 场景

## 运行

```bash
cd ai-agent-love-backed/demo
# 高区分度集（推荐）
mvn "-Dtest=RagRetrievalEvalHardTest" test
# 基础 30 条集
mvn "-Dtest=RagRetrievalEvalTest" test
```

> Query 扩展会调用 DashScope，请确保 API Key 已配置。

## 输出

- `target/rag-eval/rag-eval-report-hard.md` — 高区分度集报告（25 条）
- `target/rag-eval/rag-eval-report.md` — 基础集报告（30 条）
- `target/rag-eval/full-profile-detail-hard.csv` — 高区分度逐条明细

## 你这次跑出来的真实结果（示例）

| 配置 | Hit@5 |
|------|-------|
| 纯向量检索 | 100% |
| + 阈值 0.73 | 26.7% |
| + status 过滤 + Query 扩展（完整） | 60% |

**面试怎么说才诚实：**

1. 不要拿「完整方案 vs 纯检索」说提升了 35%～50%（你的数据反而是阈值拖累召回）。
2. 可以说：「在启用相似度阈值过滤噪声后，Hit@5 一度降到 26.7%；再通过 status 过滤 + Query 扩展恢复到 60%，**相对阈值基线提升约 125%**。」
3. 补充：「纯检索 Hit@5 虽高，但 Top1 可能跨场景串文档；完整方案牺牲部分召回，换取更可控的场景匹配。」
4. 下一步优化：调低 `similarityThreshold`（如 0.5～0.65）或在评测里加「跨场景误召回率」指标。

## 简历写法示例

跑完报告后，将真实数字填入：

> 在 30 条标注测试集上，Hit@5 从 **X%** 提升至 **Y%**（相对 Baseline-0 提升 **Z%**）。

不要编造数字；以报告为准。

## 扩展

1. 将 `minKeywordMatches` 改为 2，做严格评测
2. 从 Markdown 留出的 held-out 问题替换部分样本
3. 增加生成层评测：对比 `doChat` vs `doChatWithRag` 的人工三档打分
