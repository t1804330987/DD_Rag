<script setup lang="ts">
import type { WorkspaceNodeType } from '../groupWorkspaceView'

defineProps<{
  ownedCount: number
  joinedCount: number
  invitationCount: number
  activeSection: WorkspaceNodeType | ''
}>()

const emit = defineEmits<{
  focus: [section: WorkspaceNodeType]
  create: []
}>()

function handleFocus(section: WorkspaceNodeType) {
  emit('focus', section)
}
</script>

<template>
  <section class="groups-summary">
    <div class="groups-summary__heading">
      <div>
        <p class="groups-summary__eyebrow">组工作台</p>
        <h2>我的组</h2>
        <p class="groups-summary__intro">
          先选择一个对象，再在右侧完成邀请审批、成员管理或退出操作。
        </p>
      </div>
      <button class="primary-button groups-summary__create" type="button" @click="emit('create')">
        创建组
      </button>
    </div>

    <div class="groups-summary__grid">
      <button
        class="groups-summary__card"
        :class="{ 'is-active': activeSection === 'ownedGroup' }"
        type="button"
        @click="handleFocus('ownedGroup')"
      >
        <span class="groups-summary__label">我拥有的组</span>
        <strong>{{ ownedCount }}</strong>
        <small>点击查看你拥有的知识库空间</small>
      </button>

      <button
        class="groups-summary__card"
        :class="{ 'is-active': activeSection === 'joinedGroup' }"
        type="button"
        @click="handleFocus('joinedGroup')"
      >
        <span class="groups-summary__label">我加入的组</span>
        <strong>{{ joinedCount }}</strong>
        <small>点击查看你加入的知识库空间</small>
      </button>

      <button
        class="groups-summary__card groups-summary__card--pending"
        :class="{ 'is-active': activeSection === 'invitation' }"
        type="button"
        @click="handleFocus('invitation')"
      >
        <span class="groups-summary__label">
          待处理邀请
          <span v-if="invitationCount > 0" class="groups-summary__badge">待处理</span>
        </span>
        <strong>{{ invitationCount }}</strong>
        <small>点击优先处理邀请事项</small>
      </button>
    </div>
  </section>
</template>

<style scoped>
.groups-summary {
  display: grid;
  gap: 1rem;
}

.groups-summary__heading {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  align-items: start;
}

.groups-summary__eyebrow {
  margin: 0 0 0.35rem;
  font-size: 0.74rem;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: #195a76;
}

.groups-summary__heading h2 {
  margin: 0;
  color: #102a3b;
}

.groups-summary__intro {
  margin: 0.55rem 0 0;
  color: #4f6472;
  line-height: 1.65;
  max-width: 42rem;
}

.groups-summary__create {
  flex-shrink: 0;
}

.groups-summary__grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0.9rem;
}

.groups-summary__card {
  display: grid;
  gap: 0.35rem;
  padding: 1rem 1.05rem;
  border-radius: 24px;
  border: 1px solid rgba(16, 42, 59, 0.08);
  background: rgba(255, 255, 255, 0.86);
  color: #102a3b;
  text-align: left;
  transition:
    transform 0.22s ease,
    border-color 0.22s ease,
    box-shadow 0.22s ease;
}

.groups-summary__card:hover {
  transform: translateY(-1px);
  border-color: rgba(25, 90, 118, 0.24);
  box-shadow: 0 16px 30px rgba(16, 42, 59, 0.08);
}

.groups-summary__card strong {
  font-size: clamp(1.9rem, 3vw, 2.6rem);
  line-height: 1;
}

.groups-summary__label {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  font-size: 0.84rem;
  font-weight: 600;
  color: #335567;
}

.groups-summary__badge {
  padding: 0.16rem 0.48rem;
  border-radius: 999px;
  background: rgba(226, 111, 60, 0.14);
  color: #9b3f1b;
  font-size: 0.72rem;
}

.groups-summary__card small {
  color: #607684;
  line-height: 1.5;
}

.groups-summary__card.is-active {
  border-color: rgba(25, 90, 118, 0.3);
  background: linear-gradient(150deg, rgba(244, 251, 255, 0.98), rgba(226, 240, 247, 0.92));
  box-shadow: 0 20px 44px rgba(16, 42, 59, 0.12);
}

.groups-summary__card--pending {
  background: linear-gradient(150deg, rgba(255, 251, 246, 0.96), rgba(255, 242, 233, 0.9));
}

@media (max-width: 900px) {
  .groups-summary__heading,
  .groups-summary__grid {
    grid-template-columns: 1fr;
  }

  .groups-summary__heading {
    display: grid;
  }
}
</style>
