<template>
  <transition name="undo-slide">
    <div
      v-if="visible"
      class="undo-bar"
      role="status"
      aria-live="polite"
    >
      <div class="undo-bar-inner">
        <span class="undo-bar-message">{{ message }}</span>
        <span class="undo-bar-countdown">({{ secondsLeft }}s)</span>
        <el-button
          type="primary"
          size="small"
          plain
          @click="$emit('undo')"
        >
          撤销
        </el-button>
        <el-button
          link
          size="small"
          class="undo-bar-close"
          @click="$emit('dismiss')"
        >
          关闭
        </el-button>
      </div>
    </div>
  </transition>
</template>

<script setup lang="ts">
/**
 * 通用底部撤销条（乐观操作 + 倒计时撤销窗口）。
 *
 * 由父组件拥有 visible / secondsLeft 状态，本组件只是渲染 + emit 撤销/关闭事件。
 * 例如：
 *   const visible = ref(false)
 *   const secondsLeft = ref(5)
 *   const message = ref('')
 *   let commitTimer: number | undefined
 *   function startUndoBar(msg: string, onCommit: () => void) {
 *     message.value = msg
 *     visible.value = true
 *     secondsLeft.value = 5
 *     clearTimeout(commitTimer)
 *     const start = Date.now()
 *     const interval = window.setInterval(() => {
 *       const left = 5 - Math.floor((Date.now() - start) / 1000)
 *       if (left <= 0) { window.clearInterval(interval); return }
 *       secondsLeft.value = left
 *     }, 250)
 *     commitTimer = window.setTimeout(() => {
 *       window.clearInterval(interval)
 *       visible.value = false
 *       onCommit()
 *     }, 5000)
 *   }
 *   function onUndo() { clearTimeout(commitTimer); visible.value = false }
 */

withDefaults(defineProps<{
  visible: boolean
  message: string
  secondsLeft: number
}>(), {
  visible: false,
  message: '',
  secondsLeft: 0
})

defineEmits<{
  undo: []
  dismiss: []
}>()
</script>

<style scoped>
.undo-bar {
  position: fixed;
  left: 50%;
  bottom: 24px;
  transform: translateX(-50%);
  z-index: 2000;
  pointer-events: none;
}

.undo-bar-inner {
  display: flex;
  align-items: center;
  gap: 10px;
  background: #1f2937;
  color: #f9fafb;
  padding: 10px 16px;
  border-radius: 999px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.18), 0 2px 8px rgba(0, 0, 0, 0.08);
  pointer-events: auto;
  font-size: 13px;
  line-height: 1;
}

.undo-bar-message {
  color: #f9fafb;
  font-weight: 500;
}

.undo-bar-countdown {
  color: #9ca3af;
  font-variant-numeric: tabular-nums;
  margin-right: 4px;
}

.undo-bar-close {
  color: #9ca3af !important;
  font-size: 12px !important;
}

/* Transition */
.undo-slide-enter-active, .undo-slide-leave-active {
  transition: transform 0.2s ease, opacity 0.2s ease;
}
.undo-slide-enter-from, .undo-slide-leave-to {
  transform: translate(-50%, 20px);
  opacity: 0;
}
</style>