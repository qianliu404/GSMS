import { defineStore } from 'pinia'
import { authApi } from '../api/modules'
import { TOKEN_KEY } from '../api'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    user: null
  }),
  getters: {
    isLogin: (state) => !!state.token,
    roles: (state) => (state.user && state.user.roles) || [],
    perms: (state) => (state.user && state.user.permissions) || [],
    menus: (state) => (state.user && state.user.menus) || [],
    canViewSensitive: (state) => !!(state.user && state.user.canViewSensitive)
  },
  actions: {
    async login(form) {
      const data = await authApi.login(form)
      this.token = data.token
      this.user = data.user
      localStorage.setItem(TOKEN_KEY, data.token)
      return data
    },
    async fetchMe() {
      const data = await authApi.me()
      this.user = data
      return data
    },
    hasRole(code) {
      return this.roles.includes(code)
    },
    /** 细粒度权限码：系统管理员始终放行，避免配置失误把管理员锁死 */
    hasPerm(code) {
      return this.hasRole('ROLE_SYS_ADMIN') || this.perms.includes(code)
    },
    logout() {
      this.token = ''
      this.user = null
      localStorage.removeItem(TOKEN_KEY)
    }
  }
})
