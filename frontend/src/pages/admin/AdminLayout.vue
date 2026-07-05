<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, RouterView, useRoute } from 'vue-router'
import PageHeaderHero from '../../components/layout/PageHeaderHero.vue'
import WorkbenchShell from '../../components/layout/WorkbenchShell.vue'
import WorkbenchSidebar from '../../components/layout/WorkbenchSidebar.vue'
import { useAuthStore } from '../../stores/auth'
import '../../assets/page-shell.css'
import '../../assets/admin-page.css'

const authStore = useAuthStore()
const route = useRoute()

const currentUserName = computed(() => authStore.currentUser?.displayName ?? '系统管理员')
const currentUserCode = computed(() => authStore.currentUser?.userCode ?? 'ADMIN')
const heroDescription = computed(() =>
  route.path === '/admin/overview'
    ? '后台首页先呈现系统账号的全局态势，再引导进入具体治理动作。'
    : route.path.startsWith('/admin/users/') && route.path !== '/admin/users'
      ? '单账号详情页集中展示身份与状态，减少列表页的视觉负担。'
      : '管理区只负责系统账号、角色与状态治理，不自动获得任何组内 OWNER/MEMBER 权限。',
)
const activeSectionLabel = computed(() =>
  route.path === '/admin/overview'
    ? '后台首页'
    : route.path.startsWith('/admin/users/') && route.path !== '/admin/users'
      ? '用户详情'
      : '用户管理',
)
</script>

<template>
  <WorkbenchShell class="admin-workbench">
    <template #sidebar>
      <WorkbenchSidebar />
    </template>

    <template #main>
      <main class="admin-layout">
        <PageHeaderHero eyebrow="后台管理" title="系统治理" :description="heroDescription">
          <template #actions>
            <div class="admin-hero-actions">
              <nav class="admin-local-nav" aria-label="管理员导航">
                <RouterLink to="/admin/overview" active-class="is-active">后台首页</RouterLink>
                <RouterLink to="/admin/users" active-class="is-active">用户管理</RouterLink>
                <RouterLink to="/account/security" active-class="is-active">账户安全</RouterLink>
              </nav>
            </div>
          </template>
        </PageHeaderHero>

        <section class="admin-identity-strip" aria-label="管理员上下文">
          <article class="admin-identity-card">
            <span>当前管理员</span>
            <strong>{{ currentUserName }}</strong>
            <small>{{ currentUserCode }}</small>
          </article>
          <article class="admin-identity-card">
            <span>当前区域</span>
            <strong>{{ activeSectionLabel }}</strong>
            <small>后台操作与业务工作台保持视觉同品牌，但权限边界隔离。</small>
          </article>
          <article class="admin-identity-card">
            <span>治理原则</span>
            <strong>Fail-fast</strong>
            <small>账号状态、改密要求与角色变更都在后端规则内收口。</small>
          </article>
        </section>

        <RouterView />
      </main>
    </template>
  </WorkbenchShell>
</template>
