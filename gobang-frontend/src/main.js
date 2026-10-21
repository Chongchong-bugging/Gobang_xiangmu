import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import { userApi } from './api'

const app = createApp(App)
app.use(router)

async function bootstrap() {
  // Cookie-based sessions require a CSRF token before the first mutation.
  await userApi.getCsrf()

  // Reconcile the client hint with the authoritative server session.
  if (sessionStorage.getItem('user')) {
    const currentUser = await userApi.getInfo()
    if (currentUser.code === 200) {
      sessionStorage.setItem('user', JSON.stringify(currentUser.data))
    }
  }

  app.mount('#app')
}

bootstrap()
