<script setup lang="ts">
// Dialogo de confirmacion global. Se monta UNA sola vez en App.vue y responde a
// useConfirmacion().confirmar(...). Reemplaza al confirm() del navegador.
import ModalBase from '@/components/ModalBase.vue'
import { useConfirmacion } from '@/composables/useConfirmacion'

const { estado, responder } = useConfirmacion()
</script>

<template>
  <ModalBase
    v-if="estado.visible"
    style="z-index: 60"
    :titulo="estado.titulo"
    ancho="400px"
    @cerrar="responder(false)"
  >
    <p style="margin:4px 0 0;color:var(--texto)">{{ estado.mensaje }}</p>
    <template #pie>
      <button class="secundario" @click="responder(false)">{{ estado.textoCancelar }}</button>
      <button :class="{ peligro: estado.peligro }" @click="responder(true)">
        {{ estado.textoConfirmar }}
      </button>
    </template>
  </ModalBase>
</template>
