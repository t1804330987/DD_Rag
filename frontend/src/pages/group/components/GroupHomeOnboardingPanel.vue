<script setup lang="ts">
import { RouterLink } from 'vue-router'
import type { JoinRequestItem } from '../../../api/group'
import type { WorkspaceNodeType } from '../groupWorkspaceView'

defineProps<{
  currentUserLabel: string
  hasAnyWorkspaceItem: boolean
  hasSelection: boolean
  ownedCount: number
  joinedCount: number
  invitationCount: number
  myJoinRequestCount: number
  joinGroupCode: string
  isSubmittingJoinRequest: boolean
  myJoinRequests: JoinRequestItem[]
  isMyRequestsLoading: boolean
}>()

const emit = defineEmits<{
  openCreate: []
  openSecurity: []
  submitJoinRequest: []
  focus: [section: WorkspaceNodeType]
  'update:joinGroupCode': [value: string]
}>()

function handleJoinGroupCodeInput(event: Event) {
  emit('update:joinGroupCode', (event.target as HTMLInputElement).value)
}
</script>

<template>
  <section class="group-home-onboarding">
    <header class="group-home-onboarding__hero">
      <div class="group-home-onboarding__copy">
        <p class="group-home-onboarding__eyebrow">开始使用</p>
        <h2>
          {{ hasAnyWorkspaceItem ? (hasSelection ? '工作台已就绪' : '从左侧选择一个组开始') : '创建或加入第一个组' }}
        </h2>
        <p>
          你好，{{ currentUserLabel }}。先在这里确定协作组，再去文档中心上传资料，或在问答/助手中基于证据提问。
        </p>
      </div>

      <div class="group-home-onboarding__stats">
        <article>
          <span>我拥有的组</span>
          <strong>{{ ownedCount }}</strong>
        </article>
        <article>
          <span>我加入的组</span>
          <strong>{{ joinedCount }}</strong>
        </article>
        <article>
          <span>待处理邀请</span>
          <strong>{{ invitationCount }}</strong>
        </article>
      </div>
    </header>

    <section class="group-home-onboarding__steps">
      <article class="group-home-onboarding__step">
        <span class="group-home-onboarding__step-index">01</span>
        <h3>确认当前组</h3>
        <p v-if="hasAnyWorkspaceItem">从左侧选择组或待处理邀请，中间会进入对应详情。</p>
        <p v-else>还没有组时，先创建自己的知识库，或申请加入已有组。</p>
        <div class="group-home-onboarding__actions">
          <button
            v-if="ownedCount > 0"
            class="primary-button"
            type="button"
            @click="emit('focus', 'ownedGroup')"
          >
            查看我拥有的组
          </button>
          <button
            v-else-if="joinedCount > 0"
            class="primary-button"
            type="button"
            @click="emit('focus', 'joinedGroup')"
          >
            查看我加入的组
          </button>
          <button v-else class="primary-button" type="button" @click="emit('openCreate')">创建第一个组</button>
          <button
            v-if="invitationCount > 0"
            class="ghost-button"
            type="button"
            @click="emit('focus', 'invitation')"
          >
            先处理邀请
          </button>
        </div>
      </article>

      <article class="group-home-onboarding__step">
        <span class="group-home-onboarding__step-index">02</span>
        <h3>准备文档</h3>
        <p>选中组后，到文档中心上传并确认索引状态。</p>
        <div class="group-home-onboarding__actions">
          <RouterLink class="ghost-button group-home-onboarding__link" to="/documents">前往文档中心</RouterLink>
        </div>
      </article>

      <article class="group-home-onboarding__step">
        <span class="group-home-onboarding__step-index">03</span>
        <h3>开始提问</h3>
        <p>确认当前组后，再到知识问答或个人助手里提问，避免选错知识库。</p>
        <div class="group-home-onboarding__actions">
          <RouterLink class="ghost-button group-home-onboarding__link" to="/qa">知识问答</RouterLink>
          <button class="ghost-button" type="button" @click="emit('openSecurity')">账号安全</button>
        </div>
      </article>
    </section>

    <section class="group-home-onboarding__foot">
      <article class="group-home-onboarding__card">
        <div class="group-home-onboarding__card-header">
          <div>
            <p class="panel__eyebrow">待办</p>
            <h3>接下来做什么</h3>
          </div>
          <span class="panel__pill panel__pill--soft">{{ invitationCount + myJoinRequestCount }}</span>
        </div>
        <ul class="group-home-onboarding__todo">
          <li v-if="invitationCount > 0">有 {{ invitationCount }} 条邀请待处理。</li>
          <li v-if="myJoinRequestCount > 0">有 {{ myJoinRequestCount }} 条加入申请在跟进。</li>
          <li v-if="!hasAnyWorkspaceItem">还没有组：先创建，或输入组织 ID 申请加入。</li>
          <li v-if="hasAnyWorkspaceItem && !hasSelection">已有组，但尚未选中具体工作区。</li>
          <li>敏感操作前可先检查账号安全状态。</li>
        </ul>
      </article>

      <article class="group-home-onboarding__card">
        <div class="group-home-onboarding__card-header">
          <div>
            <p class="panel__eyebrow">加入</p>
            <h3>用组织 ID 申请</h3>
          </div>
        </div>

        <div class="groups-inline-form">
          <input
            :value="joinGroupCode"
            type="text"
            maxlength="80"
            placeholder="例如：engineering-team"
            @input="handleJoinGroupCodeInput"
          />
          <button class="primary-button" :disabled="isSubmittingJoinRequest" type="button" @click="emit('submitJoinRequest')">
            {{ isSubmittingJoinRequest ? '提交中…' : '提交申请' }}
          </button>
        </div>

        <p class="group-home-onboarding__hint">填写页面上的组织 ID（groupCode），不是数字主键。</p>

        <p v-if="isMyRequestsLoading" class="placeholder-text">正在加载我的申请…</p>
        <ul v-else-if="myJoinRequests.length > 0" class="join-request-list">
          <li
            v-for="request in myJoinRequests.slice(0, 3)"
            :key="`onboarding-request-${request.requestId}`"
            class="join-request-list__item"
          >
            <div>
              <strong>{{ request.groupName }}</strong>
              <span>{{ request.groupCode }} · {{ request.status }}</span>
            </div>
            <span>{{ new Date(request.createdAt).toLocaleString() }}</span>
          </li>
        </ul>
        <p v-else class="placeholder-text">还没有提交过加入申请。</p>
      </article>
    </section>
  </section>
</template>

<style scoped>
.group-home-onboarding {
  display: grid;
  gap: 1.25rem;
}

.group-home-onboarding__hero {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(16rem, 0.8fr);
  gap: 1rem;
  padding: 1.25rem;
  border-radius: 0.8rem;
  background:
    radial-gradient(circle at top right, rgba(106, 167, 189, 0.22), transparent 28rem),
    linear-gradient(155deg, rgba(255, 255, 255, 0.98), rgba(233, 244, 248, 0.94));
  border: 1px solid rgba(16, 42, 59, 0.08);
  box-shadow: 0 18px 40px rgba(13, 40, 58, 0.07);
  overflow: visible;
}

.group-home-onboarding__eyebrow {
  margin: 0 0 0.4rem;
  font-size: 0.72rem;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: #195a76;
}

.group-home-onboarding__copy h2,
.group-home-onboarding__card-header h3 {
  margin: 0;
  color: #102a3b;
  line-height: 1.3;
  overflow-wrap: anywhere;
}

.group-home-onboarding__copy p {
  margin: 0.75rem 0 0;
  color: #4f6472;
  line-height: 1.7;
  max-width: 42rem;
}

.group-home-onboarding__stats {
  display: grid;
  gap: 0.8rem;
}

.group-home-onboarding__stats article,
.group-home-onboarding__card {
  display: grid;
  gap: 0.45rem;
  min-width: 0;
  padding: 0.95rem 1rem;
  border-radius: 0.7rem;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(16, 42, 59, 0.08);
}

.group-home-onboarding__stats span {
  font-size: 0.78rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #607684;
}

.group-home-onboarding__stats strong {
  font-size: 2rem;
  line-height: 1;
  color: #102a3b;
}

.group-home-onboarding__steps,
.group-home-onboarding__foot {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 1rem;
}

.group-home-onboarding__foot {
  grid-template-columns: minmax(0, 1.1fr) minmax(0, 1fr);
}

.group-home-onboarding__step {
  display: grid;
  gap: 0.7rem;
  min-width: 0;
  padding: 1rem;
  border-radius: 0.7rem;
  border: 1px solid rgba(16, 42, 59, 0.08);
  background: rgba(255, 255, 255, 0.9);
  overflow: visible;
  transition:
    transform 0.24s ease,
    box-shadow 0.24s ease;
}

.group-home-onboarding__step:hover {
  transform: translateY(-2px);
  box-shadow: 0 20px 42px rgba(13, 40, 58, 0.08);
}

.group-home-onboarding__step-index {
  font-size: 0.78rem;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: #7d99a8;
}

.group-home-onboarding__step h3 {
  margin: 0;
  color: #102a3b;
  line-height: 1.35;
  overflow-wrap: anywhere;
}

.group-home-onboarding__step p,
.group-home-onboarding__hint,
.group-home-onboarding__todo {
  margin: 0;
  color: #607684;
  line-height: 1.65;
}

.group-home-onboarding__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.65rem;
}

.group-home-onboarding__link {
  text-decoration: none;
}

.group-home-onboarding__card-header {
  display: flex;
  justify-content: space-between;
  gap: 0.8rem;
  align-items: start;
}

.group-home-onboarding__todo {
  display: grid;
  gap: 0.55rem;
  padding-left: 1rem;
}

@media (max-width: 1100px) {
  .group-home-onboarding__hero,
  .group-home-onboarding__steps,
  .group-home-onboarding__foot {
    grid-template-columns: 1fr;
  }
}
</style>
