<script setup lang="ts">
// Contenedor de los toasts (alertas flotantes). Se monta UNA sola vez en App.vue.
// Lee la lista reactiva de useAlertas() y las apila arriba a la derecha.
import { useAlertas } from '@/composables/useAlertas'

const { alertas, quitar } = useAlertas()

const icono = { exito: '✓', error: '⚠', info: 'ℹ' } as const
</script>

<template>
  <div class="toasts" aria-live="polite">
    <TransitionGroup name="toast">
      <div v-for="a in alertas" :key="a.id" class="toast" :class="`toast--${a.tipo}`">
        <span class="toast__icono">{{ icono[a.tipo] }}</span>
        <span class="toast__texto">{{ a.texto }}</span>
        <button class="toast__cerrar" aria-label="Cerrar" @click="quitar(a.id)">×</button>
      </div>
    </TransitionGroup>
  </div>
</template>

<style scoped>
.toasts {
  position: fixed;
  top: 16px;
  right: 16px;
  z-index: 100;
  display: flex;
  flex-direction: column;
  gap: 10px;
  max-width: min(360px, 92vw);
}
.toast {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px 14px;
  border-radius: 10px;
  background: #fff;
  border: 1px solid var(--borde);
  border-left: 4px solid;
  box-shadow: 0 10px 25px rgba(15, 23, 42, .15);
  font-size: 14px;
}
.toast__icono { font-weight: 700; }
.toast__texto { flex: 1; }
.toast__cerrar {
  background: none; color: var(--texto-suave); padding: 0 4px;
  font-size: 18px; line-height: 1;
}
.toast__cerrar:hover { background: none; color: var(--texto); }

.toast--exito { border-left-color: var(--verde); }
.toast--exito .toast__icono { color: var(--verde); }
.toast--error { border-left-color: var(--rojo); }
.toast--error .toast__icono { color: var(--rojo); }
.toast--info { border-left-color: var(--azul-claro); }
.toast--info .toast__icono { color: var(--azul-claro); }

/* Animacion de entrada/salida */
.toast-enter-active, .toast-leave-active { transition: all .25s ease; }
.toast-enter-from { opacity: 0; transform: translateX(20px); }
.toast-leave-to { opacity: 0; transform: translateX(20px); }
</style>
