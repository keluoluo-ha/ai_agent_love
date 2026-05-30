<script setup lang="ts">
import ChatPanel from '../components/ChatPanel.vue'
import { streamLoveReply, type StreamConnection } from '../api/chat'
import { createChatId } from '../utils/chatId'

const chatId = createChatId('love')

function sendLoveMessage(
  message: string,
  onChunk: (chunk: string) => void,
  onDone: () => void,
): StreamConnection {
  return streamLoveReply(message, chatId, onChunk, onDone)
}
</script>

<template>
  <ChatPanel
    title="AI 恋爱大师"
    placeholder="输入你的恋爱问题..."
    :chat-id="chatId"
    :send-message="sendLoveMessage"
  />
</template>
