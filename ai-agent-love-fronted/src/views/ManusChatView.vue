<script setup lang="ts">
import { ref } from 'vue'
import ChatPanel from '../components/ChatPanel.vue'
import { streamManusReply, type AskHumanPayload, type StreamConnection } from '../api/chat'
import { createChatId } from '../utils/chatId'

const chatId = createChatId('manus')
const pendingRunId = ref<string | null>(null)
const askHumanBanner = ref<AskHumanPayload | null>(null)
const awaitingHumanInput = ref(false)

function sendManusMessage(
  message: string,
  onChunk: (chunk: string) => void,
  onDone: () => void,
): StreamConnection {
  const isAgentReply = pendingRunId.value !== null

  return streamManusReply(message, onChunk, () => {
    if (!awaitingHumanInput.value) {
      pendingRunId.value = null
      askHumanBanner.value = null
    }
    onDone()
  }, {
    chatId,
    runId: pendingRunId.value ?? undefined,
    replyType: isAgentReply ? 'AGENT_REPLY' : 'NEW_TASK',
    onAskHuman: (payload) => {
      pendingRunId.value = payload.runId
      askHumanBanner.value = payload
      awaitingHumanInput.value = true
      onChunk(`\n\n🤔 ${payload.question}`)
    },
  })
}

function handleSendMessage(
  message: string,
  onChunk: (chunk: string) => void,
  onDone: () => void,
): StreamConnection {
  if (awaitingHumanInput.value) {
    awaitingHumanInput.value = false
    askHumanBanner.value = null
  }
  return sendManusMessage(message, onChunk, onDone)
}
</script>

<template>
  <ChatPanel
    title="AI 超级智能体"
    placeholder="输入你希望智能体完成的任务..."
    :chat-id="chatId"
    :ask-human-banner="askHumanBanner"
    :allow-empty-assistant="awaitingHumanInput"
    :send-message="handleSendMessage"
  />
</template>
