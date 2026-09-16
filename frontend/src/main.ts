import { createApp } from 'vue';
import { createPinia } from 'pinia';
import PrimeVue from 'primevue/config';
import Aura from '@primeuix/themes/aura';
import 'primeicons/primeicons.css';
import '@xterm/xterm/css/xterm.css';
import App from './App.vue';
import router from './router';
import { i18n } from './i18n';
import './styles/base.css';
import './styles/form-controls.css';
import './styles/components/cluster-detail.css';
import './styles/main.css';
import './styles/components/analysis.css';
import './styles/components/analysis-operations.css';
import './styles/components/trust-center.css';
import './styles/components/commercial-readiness.css';
import './styles/components/operator-workspace.css';
import './styles/components/application-delivery.css';
import './styles/components/ai-providers.css';
import './styles/components/access-control.css';
import './styles/kubernetes-console.css';
import './styles/product-shell.css';

createApp(App)
  .use(createPinia())
  .use(i18n)
  .use(router)
  .use(PrimeVue, {
    theme: {
      preset: Aura
    }
  })
  .mount('#app');
