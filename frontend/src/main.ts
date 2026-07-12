import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import { pinia } from './stores/pinia'
import { installHttpCryptoPolyfill } from './utils/secure-crypto'
import './style.css'

// Must run before any feature code: plain HTTP IP demos lack secure-context crypto APIs.
installHttpCryptoPolyfill()

const app = createApp(App)

app.use(pinia)
app.use(router)
app.mount('#app')
