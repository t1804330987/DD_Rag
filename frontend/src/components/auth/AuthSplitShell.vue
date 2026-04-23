<script setup lang="ts">
defineProps<{
  eyebrow?: string
  title: string
  description: string
}>()
</script>

<template>
  <main class="auth-split-shell">
    <section class="auth-split-shell__brand">
      <div class="auth-split-shell__brand-surface">
        <p v-if="eyebrow" class="auth-split-shell__eyebrow">{{ eyebrow }}</p>
        <h1>{{ title }}</h1>
        <p class="auth-split-shell__description">{{ description }}</p>

        <div v-if="$slots.brand" class="auth-split-shell__brand-extra">
          <slot name="brand" />
        </div>

        <div v-if="$slots.actions" class="auth-split-shell__actions">
          <slot name="actions" />
        </div>
      </div>
    </section>

    <section class="auth-split-shell__panel">
      <slot />
    </section>
  </main>
</template>

<style scoped>
.auth-split-shell {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(0, 1.08fr) minmax(20rem, 28rem);
  gap: clamp(1.5rem, 4vw, 3.5rem);
  align-items: center;
  padding: clamp(1.25rem, 3vw, 2.25rem);
  position: relative;
  isolation: isolate;
}

.auth-split-shell::before,
.auth-split-shell::after {
  content: '';
  position: absolute;
  inset: 0;
  z-index: -1;
  pointer-events: none;
}

.auth-split-shell::before {
  background:
    radial-gradient(circle at 14% 18%, rgba(47, 107, 149, 0.18), transparent 24%),
    radial-gradient(circle at 82% 12%, rgba(182, 125, 80, 0.22), transparent 20%),
    linear-gradient(140deg, rgba(255, 255, 255, 0.72), rgba(232, 241, 247, 0.9));
}

.auth-split-shell::after {
  inset: 1rem;
  border: 1px solid rgba(96, 117, 140, 0.08);
  border-radius: 2rem;
  background:
    linear-gradient(120deg, rgba(255, 255, 255, 0.18), transparent 42%),
    linear-gradient(300deg, rgba(47, 107, 149, 0.08), transparent 30%);
}

.auth-split-shell__brand,
.auth-split-shell__panel {
  min-width: 0;
}

.auth-split-shell__brand-surface {
  position: relative;
  display: grid;
  gap: 1.5rem;
  padding: clamp(1.75rem, 5vw, 4rem);
  border: 1px solid rgba(96, 117, 140, 0.12);
  border-radius: 2rem;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.72), rgba(240, 246, 250, 0.84)),
    rgba(255, 255, 255, 0.72);
  box-shadow: 0 30px 72px rgba(17, 37, 58, 0.16);
  backdrop-filter: blur(18px);
}

.auth-split-shell__brand-surface::before {
  content: '';
  position: absolute;
  top: -2.5rem;
  right: -1.5rem;
  width: 12rem;
  height: 12rem;
  border-radius: 2.5rem;
  border: 1px solid rgba(96, 117, 140, 0.12);
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.28), rgba(47, 107, 149, 0.08)),
    rgba(255, 255, 255, 0.18);
  transform: rotate(16deg);
}

.auth-split-shell__eyebrow {
  margin: 0;
  color: #b67d50;
  font-size: 0.78rem;
  font-weight: 800;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.auth-split-shell h1 {
  margin: 0;
  max-width: 12ch;
  font-family: var(--wb-font-display);
  color: #0f2b46;
  font-size: clamp(3.4rem, 6vw, 6.4rem);
  line-height: 0.88;
  letter-spacing: -0.08em;
}

.auth-split-shell__description {
  margin: 0;
  max-width: 40rem;
  color: var(--wb-color-text-muted);
  font-size: 1rem;
  line-height: 1.8;
}

.auth-split-shell__brand-extra {
  display: grid;
  gap: 1rem;
}

.auth-split-shell__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.85rem;
  align-items: center;
}

.auth-split-shell__panel {
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 960px) {
  .auth-split-shell {
    grid-template-columns: minmax(0, 1fr);
    gap: 1.25rem;
    padding: 1rem;
  }

  .auth-split-shell::after {
    inset: 0.65rem;
    border-radius: 1.5rem;
  }

  .auth-split-shell__brand-surface {
    padding: 1.5rem;
    border-radius: 1.5rem;
  }

  .auth-split-shell__brand-surface::before {
    width: 8rem;
    height: 8rem;
    top: -1rem;
    right: -1rem;
  }

  .auth-split-shell__panel {
    justify-content: stretch;
  }
}
</style>
