<script setup lang="ts">
import ChatPanel from '../components/ChatPanel.vue'
import { streamManusReply, type StreamConnection } from '../api/chat'

function sendManusMessage(
  message: string,
  onChunk: (chunk: string) => void,
  onDone: () => void,
): StreamConnection {
  return streamManusReply(message, onChunk, onDone)
}
</script>

<template>
  <ChatPanel
    title="AI 超级智能体"
    placeholder="输入你希望智能体完成的任务..."
    :send-message="sendManusMessage"
  />
</template>
