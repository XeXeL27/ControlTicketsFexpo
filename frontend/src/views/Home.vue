<script setup lang="ts">
// Pantalla de inicio (dashboard simple). Muestra el usuario y sus roles.
// En la siguiente fase aqui iran las tarjetas de tickets y el monitoreo.
import { auth } from '@/store/auth'
</script>

<template>
  <div class="card">
    <h2>Bienvenido, {{ auth.usuario?.nombreCompleto }}</h2>
    <p>Usuario: <strong>{{ auth.usuario?.username }}</strong></p>
    <p>
      Roles:
      <span v-for="r in auth.usuario?.roles" :key="r" class="chip">{{ r }}</span>
    </p>
    <hr />
    <p style="color:var(--texto-suave)">
      Modulo actual: administracion de accesos (autenticacion, personas,
      usuarios y roles). La gestion de tickets, QR y monitoreo en tiempo real
      se agregara en la siguiente fase.
    </p>
  </div>
</template>
