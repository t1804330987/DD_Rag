<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { renderMarkdown } from '../utils/markdown'

const props = withDefaults(
  defineProps<{
    content: string
    /** plain：用户消息等纯文本；markdown：模型回复 */
    mode?: 'markdown' | 'plain'
    /** 是否显示复制全文 */
    showCopy?: boolean
    /** 是否为流式输出（流式时不挂代码块按钮，避免频繁重绑） */
    streaming?: boolean
  }>(),
  {
    mode: 'markdown',
    showCopy: false,
    streaming: false,
  },
)

const rootRef = ref<HTMLElement | null>(null)
const copyState = ref<'idle' | 'copied' | 'failed'>('idle')
let copyResetTimer: number | null = null

const html = computed(() => {
  if (props.mode === 'plain') {
    return ''
  }
  return renderMarkdown(props.content)
})

const copyLabel = computed(() => {
  if (copyState.value === 'copied') return '已复制'
  if (copyState.value === 'failed') return '复制失败'
  return '复制'
})

async function copyText(text: string): Promise<boolean> {
  const value = text.trim()
  if (value.length === 0) {
    return false
  }
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(value)
      return true
    }
  } catch {
    // fallback below
  }
  try {
    const area = document.createElement('textarea')
    area.value = value
    area.setAttribute('readonly', 'true')
    area.style.position = 'fixed'
    area.style.left = '-9999px'
    document.body.appendChild(area)
    area.select()
    const ok = document.execCommand('copy')
    document.body.removeChild(area)
    return ok
  } catch {
    return false
  }
}

async function copyAll() {
  const ok = await copyText(props.content)
  copyState.value = ok ? 'copied' : 'failed'
  if (copyResetTimer !== null) {
    window.clearTimeout(copyResetTimer)
  }
  copyResetTimer = window.setTimeout(() => {
    copyState.value = 'idle'
    copyResetTimer = null
  }, 1600)
}

function clearCodeCopyButtons() {
  const root = rootRef.value
  if (root == null) {
    return
  }
  root.querySelectorAll('.md-code-block').forEach((wrap) => {
    const pre = wrap.querySelector('pre')
    if (pre != null && wrap.parentElement != null) {
      wrap.parentElement.insertBefore(pre, wrap)
    }
    wrap.remove()
  })
}

async function enhanceCodeBlocks() {
  await nextTick()
  const root = rootRef.value
  if (root == null || props.mode !== 'markdown' || props.streaming) {
    return
  }

  clearCodeCopyButtons()

  root.querySelectorAll('pre').forEach((pre) => {
    if (pre.parentElement?.classList.contains('md-code-block')) {
      return
    }
    const wrap = document.createElement('div')
    wrap.className = 'md-code-block'
    pre.parentElement?.insertBefore(wrap, pre)
    wrap.appendChild(pre)

    const button = document.createElement('button')
    button.type = 'button'
    button.className = 'md-code-block__copy'
    button.textContent = '复制代码'
    button.addEventListener('click', async () => {
      const code = pre.textContent ?? ''
      const ok = await copyText(code)
      button.textContent = ok ? '已复制' : '失败'
      window.setTimeout(() => {
        button.textContent = '复制代码'
      }, 1400)
    })
    wrap.appendChild(button)
  })
}

watch(
  () => [props.content, props.mode, props.streaming] as const,
  () => {
    void enhanceCodeBlocks()
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  if (copyResetTimer !== null) {
    window.clearTimeout(copyResetTimer)
  }
  clearCodeCopyButtons()
})
</script>

<template>
  <div class="md-content-shell" :class="{ 'md-content-shell--with-toolbar': showCopy }">
    <div v-if="showCopy" class="md-content-toolbar">
      <button
        class="md-content-toolbar__btn"
        type="button"
        :disabled="!content.trim()"
        @click="copyAll"
      >
        {{ copyLabel }}
      </button>
    </div>
    <div
      v-if="mode === 'markdown'"
      ref="rootRef"
      class="md-content"
      v-html="html"
    />
    <div
      v-else
      ref="rootRef"
      class="md-content md-content--plain"
    >{{ content }}</div>
  </div>
</template>
