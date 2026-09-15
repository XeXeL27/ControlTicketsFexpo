/// <reference types="vite/client" />

// Le dice a TypeScript como tratar los imports de componentes .vue.
declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}
