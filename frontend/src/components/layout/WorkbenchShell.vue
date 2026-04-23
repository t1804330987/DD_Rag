<script setup lang="ts">
import { computed, useSlots } from 'vue'

interface Props {
  sidebarWidth?: string
  asideWidth?: string
}

const props = withDefaults(defineProps<Props>(), {
  sidebarWidth: '18.5rem',
  asideWidth: '20rem',
})

const slots = useSlots()
const shellClasses = computed(() => ({
  'workbench-shell--with-aside': Boolean(slots.aside),
}))
</script>

<template>
  <div
    class="workbench-shell"
    :class="shellClasses"
    :style="{
      '--workbench-sidebar-width': props.sidebarWidth,
      '--workbench-aside-width': props.asideWidth,
    }"
  >
    <aside v-if="$slots.sidebar" class="workbench-shell__sidebar">
      <slot name="sidebar" />
    </aside>

    <main class="workbench-shell__main">
      <slot>
        <slot name="main" />
      </slot>
    </main>

    <aside v-if="$slots.aside" class="workbench-shell__aside">
      <slot name="aside" />
    </aside>
  </div>
</template>
