# 🌹 AI Agent Love

> 一个基于 **Spring AI + Vue3** 构建的 AI 恋爱建议 Agent，集成 RAG 知识库、多工具调用、AskHuman 交互机制，参考 OpenManus 架构设计。

---

## ✨ 功能特性

### 🤖 Agent 能力

| 功能 | 说明 |
|------|------|
| **YuManus Agent** | 全能 AI 助手，基于 ReAct 框架，支持多步骤工具链推理 |
| **AskHuman 工具** | AI 自主判断何时需要人类介入，在信息缺失/操作不可逆时主动提问 |
| **Web 搜索** | 调用 SearchAPI 实时检索互联网内容 |
| **PDF 生成** | 自动汇总内容并生成 PDF 报告，返回磁盘绝对路径 |
| **文件操作** | 读写本地文件 |
| **网页抓取** | 抓取指定 URL 页面内容 |
| **资源下载** | 下载网络资源到本地 |
| **终端操作** | 执行系统命令 |
| **时间工具** | 获取当前日期时间 |

### 🧠 RAG 知识库

- 基于 **PgVector** 向量数据库，存储恋爱建议领域知识
- 支持 Query Rewriter（查询重写）、关键词增强、文档分块加载
- RAG 效果评估框架（`RagRetrievalEvaluator`）

### 💬 对话记忆

- **双模式存储**：文件（`FileBasedChatMemory`）或 MySQL（`MySqlChatmemory`）
- 基于 `conversationId` 保持多轮会话上下文

### 🎨 前端界面

- **LoveChat**：恋爱建议对话页，接入 RAG 知识库
- **ManusChat**：Agent 对话页，支持 AskHuman 提问卡片展示与回传

---

## 🏗️ 技术架构

```
ai-agent-love
├── ai-agent-love-backed/          # Spring Boot 后端
│   └── src/main/java/.../
│       ├── agent/                 # Agent 核心
│       │   ├── BaseAgent          # 基础 Agent（状态机）
│       │   ├── ReActAgent         # ReAct 推理框架
│       │   ├── ToolCallAgent      # 工具调用 Agent（含 AskHuman 拦截）
│       │   ├── YuManus            # 主 Agent 实例
│       │   └── InteractiveAgentRunner  # 交互式运行器（暂停/恢复）
│       ├── tools/                 # 工具集
│       │   ├── AskHumanTool       # 人类介入工具 ✨
│       │   ├── WebSearchTool      # Web 搜索
│       │   ├── PDFGenerationTool  # PDF 生成
│       │   ├── FileOperationTool  # 文件操作
│       │   └── ...
│       ├── rag/                   # RAG 模块
│       └── chatmemory/            # 对话记忆
└── ai-agent-love-fronted/         # Vue3 前端
    └── src/
        ├── views/
        │   ├── LoveChatView.vue   # 恋爱建议页
        │   └── ManusChatView.vue  # Agent 对话页
        └── components/
            └── ChatPanel.vue      # 聊天面板（含 AskHuman 卡片）
```

### 技术栈

**后端**
- Java 17 + Spring Boot 3
- Spring AI Alibaba（通义千问 `qwen-plus`）
- PgVector 向量数据库
- MySQL + MyBatis-Plus（对话记忆）
- Knife4j（API 文档）

**前端**
- Vue3 + TypeScript + Vite
- Axios（HTTP 请求）

---

## 🚀 快速启动

### 前置依赖

- JDK 17+
- Node.js 18+
- MySQL 8.0+
- PostgreSQL（PgVector 扩展）
- 通义千问 API Key（[申请地址](https://dashscope.aliyuncs.com/)）

### 后端

```bash
# 1. 创建数据库
mysql -u root -p -e "CREATE DATABASE ai_agent_love;"

# 2. 修改配置
vim ai-agent-love-backed/demo/src/main/resources/application.yml
# 填写：spring.datasource.password、spring.ai.dashscope.api-key、search-api.api-key

# 3. 启动
cd ai-agent-love-backed/demo
./mvnw spring-boot:run
# 服务地址: http://localhost:8123/api
# API 文档: http://localhost:8123/api/swagger-ui.html
```

### 前端

```bash
cd ai-agent-love-fronted
npm install
npm run dev
# 访问: http://localhost:5173
```

---

## 🔑 AskHuman 机制

参考 [OpenManus](https://github.com/manusai/openmanus) 设计，实现 AI 自主决策的人机协作流程：

```
用户发送任务
     ↓
YuManus 推理 → 发现信息缺失/操作危险
     ↓
调用 askHuman 工具
     ↓
ToolCallAgent 拦截 → 设置 AgentState = WAITING_FOR_HUMAN
     ↓
前端展示提问卡片 ← SSE 推送 __ASK_HUMAN__ 标记
     ↓
用户输入答案 → POST /api/ai/agent/reply
     ↓
Agent 恢复执行，携带用户答案继续推理
```

---

## 📡 主要接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/ai/love_app/chat` | GET (SSE) | 恋爱建议对话（RAG） |
| `/api/ai/agent/chat` | GET (SSE) | YuManus Agent 对话 |
| `/api/ai/agent/reply` | POST | 回答 AskHuman 提问 |
| `/api/files/pdf/{filename}` | GET | 下载生成的 PDF |

---

## 📄 License

MIT
