<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref } from 'vue'
import type { StreamConnection } from '../api/chat'

interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  text: string
}

const props = defineProps<{
  title: string
  placeholder: string
  chatId?: string
  sendMessage: (
    message: string,
    onChunk: (chunk: string) => void,
    onDone: () => void,
  ) => StreamConnection
}>()

const inputText = ref('')
const isSending = ref(false)
const errorText = ref('')
const messages = ref<ChatMessage[]>([])
const chatListRef = ref<HTMLElement | null>(null)
let activeSource: StreamConnection | null = null

function scrollToBottom() {
  nextTick(() => {
    if (chatListRef.value) {
      chatListRef.value.scrollTop = chatListRef.value.scrollHeight
    }
  })
}

function addMessage(role: 'user' | 'assistant', text: string) {
  messages.value.push({
    id: `${role}-${Date.now()}-${Math.random().toString(36).slice(2)}`,
    role,
    text,
  })
  scrollToBottom()
}

function stopCurrentStream() {
  if (activeSource) {
    activeSource.close()
    activeSource = null
  }
}

function submitMessage() {
  const message = inputText.value.trim()
  if (!message || isSending.value) {
    return
  }

  stopCurrentStream()
  errorText.value = ''
  addMessage('user', message)
  inputText.value = ''
  isSending.value = true

  const assistantMessage: ChatMessage = {
    id: `assistant-${Date.now()}`,
    role: 'assistant',
    text: '',
  }
  messages.value.push(assistantMessage)
  const assistantMessageIndex = messages.value.length - 1
  scrollToBottom()

  activeSource = props.sendMessage(
    message,
    (chunk) => {
      const targetMessage = messages.value[assistantMessageIndex]
      if (targetMessage) {
        targetMessage.text += chunk
      }
      scrollToBottom()
    },
    () => {
      isSending.value = false
      activeSource = null
      const targetMessage = messages.value[assistantMessageIndex]
      if (!targetMessage || !targetMessage.text.trim()) {
        errorText.value = '暂未收到有效回复，请稍后重试。'
      }
    },
  )
}

function handleInputEnter(event: KeyboardEvent) {
  if (!event.shiftKey) {
    event.preventDefault()
    submitMessage()
  }
}

onBeforeUnmount(() => {
  stopCurrentStream()
})
</script>

<template>
  <a-layout class="chat-page">
    <a-layout-header class="chat-header">
      <div class="chat-header-left">
        <a-typography-title :heading="4" class="chat-title">{{ title }}</a-typography-title>
        <a-typography-text v-if="chatId" type="secondary" class="chat-id">
          聊天室 ID：{{ chatId }}
        </a-typography-text>
      </div>
      <router-link to="/" class="back-link">返回主页</router-link>
    </a-layout-header>

    <a-layout-content class="chat-content">
      <a-card class="chat-list-card" :body-style="{ padding: '16px' }">
        <section ref="chatListRef" class="chat-list">
          <a-empty v-if="messages.length === 0" class="chat-empty">
            <template #description>
              <a-space direction="vertical" :size="6" fill>
                <a-typography-text class="empty-title">开启一段新对话</a-typography-text>
                <a-typography-text type="secondary">
                  输入问题后按 Enter 发送，Shift + Enter 可换行
                </a-typography-text>
              </a-space>
            </template>
          </a-empty>
          <div
            v-for="message in messages"
            :key="message.id"
            class="chat-item"
            :class="`chat-item-${message.role}`"
          >
            <a-avatar
              class="chat-avatar"
              :style="
                message.role === 'assistant'
                  ? { backgroundColor: 'rgb(var(--arcoblue-6))' }
                  : { backgroundColor: 'rgb(var(--green-6))' }
              "
            >
              {{ message.role === 'assistant' ? 'AI' : '我' }}
            </a-avatar>
            <div class="bubble">
              <template v-if="message.text">
                {{ message.text }}
              </template>
              <div v-else-if="message.role === 'assistant'" class="typing-indicator" aria-label="AI 正在输入">
                <span class="typing-dot" />
                <span class="typing-dot" />
                <span class="typing-dot" />
              </div>
            </div>
          </div>
        </section>
      </a-card>

      <a-alert v-if="errorText" type="error" :content="errorText" />
    </a-layout-content>

    <a-layout-footer class="chat-input-wrap">
      <a-textarea
        v-model="inputText"
        class="chat-input"
        :placeholder="placeholder"
        :disabled="isSending"
        :auto-size="{ minRows: 1, maxRows: 4 }"
        allow-clear
        @keydown.enter="handleInputEnter"
      />
      <a-button
        type="primary"
        class="send-btn"
        :loading="isSending"
        :disabled="!inputText.trim()"
        @click="submitMessage"
      >
        发送
      </a-button>
    </a-layout-footer>
    <a-typography-text type="secondary" class="input-tip">
      Enter 发送，Shift + Enter 换行
    </a-typography-text>
  </a-layout>
</template>
