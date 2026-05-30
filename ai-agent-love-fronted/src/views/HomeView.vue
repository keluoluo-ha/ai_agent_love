<script setup lang="ts">
const moodTags = ['✨ 高情商话术', '🔥 追爱行动力', '🌈 情绪价值', '🤖 智能执行']

const apps = [
  {
    to: '/love',
    title: 'AI 恋爱大师',
    emoji: '💘',
    desc: '聊天陪练 + 恋爱策略建议，帮你把想说的话说到点子上。',
    highlights: ['开场白润色', '暧昧升级建议', '聊天记录复盘'],
  },
  {
    to: '/manus',
    title: 'AI 超级智能体',
    emoji: '🧠',
    desc: '像搭档一样拆解目标，给出可执行步骤并持续推进。',
    highlights: ['任务拆解', '执行路径规划', '实时思路输出'],
  },
]

const inspiration = [
  '😄 帮我写一句不油腻的晚安消息',
  '🥳 设计一个第一次约会话题清单',
  '🚀 帮我制定 7 天行动计划',
]
</script>

<template>
  <main class="home-page">
    <div class="floating-emoji floating-emoji-left">😍</div>
    <div class="floating-emoji floating-emoji-right">🤩</div>
    <div class="floating-emoji floating-emoji-bottom">🥰</div>

    <a-space direction="vertical" fill :size="18">
      <a-card class="hero-card" :bordered="false">
        <a-space direction="vertical" fill :size="12">
          <a-typography-title :heading="2" class="home-title">
            AI 应用导航 <span class="title-emoji">🎉</span>
          </a-typography-title>
          <a-typography-paragraph class="home-desc">
            不只是“入口页”，这里是你的灵感补给站。选一个应用，马上开启高质量对话。
          </a-typography-paragraph>
          <a-space wrap>
            <a-tag v-for="item in moodTags" :key="item" color="arcoblue" bordered>
              {{ item }}
            </a-tag>
          </a-space>
        </a-space>
      </a-card>

      <a-grid :cols="{ xs: 1, lg: 2 }" :col-gap="16" :row-gap="16">
        <a-grid-item v-for="app in apps" :key="app.to">
          <router-link :to="app.to" class="app-card-link">
            <a-card hoverable class="app-card">
              <template #title>
                <span class="app-title">{{ app.title }}</span>
              </template>
              <a-space direction="vertical" fill :size="12">
                <div class="app-emoji">{{ app.emoji }}</div>
                <a-typography-text type="secondary">{{ app.desc }}</a-typography-text>
                <ul class="feature-list">
                  <li v-for="feature in app.highlights" :key="feature">✅ {{ feature }}</li>
                </ul>
              </a-space>
            </a-card>
          </router-link>
        </a-grid-item>
      </a-grid>

      <a-card class="inspiration-card">
        <template #title>今日灵感 ✨</template>
        <a-space direction="vertical" fill>
          <a-typography-text type="secondary">
            不知道聊什么时，直接复制一句试试：
          </a-typography-text>
          <a-space direction="vertical" fill :size="8">
            <a-tag v-for="text in inspiration" :key="text" size="large" class="prompt-tag">
              {{ text }}
            </a-tag>
          </a-space>
        </a-space>
      </a-card>
    </a-space>
  </main>
</template>
