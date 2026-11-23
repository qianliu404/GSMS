<template>
  <div class="login-wrap">
    <div class="left">
      <div class="brand">
        <div class="brand-copy">
          <div class="brand-name">案件管理中心</div>
          <div class="brand-en">LEGAL CASE SYSTEM</div>
        </div>
      </div>
      <div class="hero">
        <div class="hero-line"></div>
        <h1>工伤与劳动仲裁<br />案件管理系统</h1>
        <p class="hero-desc">
          覆盖立案、调查取证、认定鉴定、待遇处理、争议关联到归档结案的全流程管理，让每一起案件的节点、时限与责任清晰可追溯。
        </p>
      </div>
      <div class="rings"></div>
    </div>

    <div class="right">
      <div class="panel">
        <h2>欢迎登录</h2>
        <p class="sub">请输入您的账号信息以继续访问</p>

        <div class="tabs">
          <span class="on">账号密码登录</span>
        </div>

        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" :hide-required-asterisk="true" @submit.prevent>
          <el-form-item label="工号 / 用户名" prop="userId">
            <el-input v-model="form.userId" placeholder="请输入工号或用户名" clearable autocomplete="off" :prefix-icon="User" />
          </el-form-item>
          <el-form-item label="登录密码" prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="请输入登录密码"
              show-password
              autocomplete="new-password"
              :prefix-icon="Lock"
              @keyup.enter="onSubmit"
            />
          </el-form-item>
        </el-form>

        <div class="opts">
          <el-checkbox v-model="remember">记住登录状态</el-checkbox>
        </div>

        <el-button type="primary" class="submit" :loading="loading" @click="onSubmit">登 录</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useUserStore } from '../store/user'

const router = useRouter()
const route = useRoute()
const store = useUserStore()

const formRef = ref()
const loading = ref(false)
const remember = ref(localStorage.getItem('gsms-login-remember') !== '0')
const form = reactive({ userId: '', password: '' })

const rules = {
  userId: [{ required: true, message: '请输入工号或用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入登录密码', trigger: 'blur' }]
}

onMounted(() => {
  // 历史版本会记住账号，清除后不再自动填充；密码从未写入本地存储
  localStorage.removeItem('gsms-login-user')
})

function saveRemember() {
  localStorage.setItem('gsms-login-remember', remember.value ? '1' : '0')
}

async function onSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await store.login({ userId: form.userId, password: form.password })
    saveRemember()
    ElMessage.success('登录成功')
    const redirect = route.query.redirect || '/dashboard'
    router.replace(redirect)
  } catch (e) {
    /* 错误提示已由拦截器统一处理 */
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-wrap {
  background: #fff;
}

.left {
  position: relative;
  width: 52%;
  min-height: 100vh;
  padding: 40px 64px;
  color: #fff;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: linear-gradient(150deg, #345ce0 0%, #2749c9 45%, #1d379c 100%);
}

.brand {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 12px;
}

.brand-name {
  font-size: 16px;
  font-weight: 700;
  line-height: 1.3;
}

.brand-en {
  font-size: 10px;
  letter-spacing: 1.4px;
  color: #b9c8f5;
  margin-top: 3px;
}

/* 顶部柔和光晕，避免纯色块单调 */
.left::before {
  content: '';
  position: absolute;
  left: -160px;
  top: -190px;
  width: 620px;
  height: 620px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.10) 0%, rgba(255, 255, 255, 0) 70%);
  pointer-events: none;
}

.hero {
  position: relative;
  z-index: 1;
  margin: auto 0;
}

.hero-line {
  width: 46px;
  height: 4px;
  border-radius: 4px;
  background: linear-gradient(90deg, #fff 0%, rgba(255, 255, 255, 0.3) 100%);
  margin-bottom: 26px;
}

h1 {
  font-size: clamp(34px, 3.2vw, 46px);
  line-height: 1.34;
  font-weight: 700;
  letter-spacing: 1px;
  margin: 0 0 22px;
  text-shadow: 0 2px 18px rgba(9, 24, 66, 0.12);
}

.hero-desc {
  max-width: 490px;
  color: rgba(255, 255, 255, 0.8);
  font-size: 13.5px;
  line-height: 2;
  margin: 0;
  letter-spacing: 0.3px;
}

.rings {
  position: absolute;
  right: -200px;
  bottom: -300px;
  width: 680px;
  height: 680px;
  border: 1px solid rgba(255, 255, 255, 0.10);
  border-radius: 50%;
  box-shadow:
    0 0 0 100px rgba(255, 255, 255, 0.03),
    0 0 0 200px rgba(255, 255, 255, 0.02),
    0 0 0 300px rgba(255, 255, 255, 0.012);
  pointer-events: none;
}

.right {
  flex: 1;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 32px;
}

.panel {
  width: 100%;
  max-width: 400px;
}

h2 {
  font-size: 27px;
  font-weight: 700;
  color: #16202f;
  margin: 0;
  letter-spacing: 0.5px;
}

.sub {
  font-size: 13px;
  color: #8b96a7;
  margin: 8px 0 26px;
}

.tabs {
  display: flex;
  border-bottom: 1px solid #e8ecf3;
  margin-bottom: 26px;
}

.tabs span {
  font-size: 14px;
  color: var(--gsms-primary);
  font-weight: 600;
  padding-bottom: 12px;
  position: relative;
}

.tabs span::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: -1px;
  height: 2px;
  border-radius: 2px;
  background: var(--gsms-primary);
}

.panel :deep(.el-form-item) {
  margin-bottom: 20px;
}

.panel :deep(.el-form-item__label) {
  font-size: 13px;
  font-weight: 600;
  color: #3a4557;
  line-height: 1.5;
  padding: 0 0 8px;
}

.panel :deep(.el-input__wrapper) {
  height: 46px;
  border-radius: 9px;
  box-shadow: 0 0 0 1px #e2e8f1 inset;
  background: #fff;
  transition: box-shadow 0.2s;
}

.panel :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px #b9c9f5 inset;
}

.panel :deep(.el-input__inner) {
  font-size: 13.5px;
}

.opts {
  margin: 4px 0 24px;
  font-size: 12.5px;
}

.submit {
  width: 100%;
  height: 46px;
  font-size: 15px;
  letter-spacing: 4px;
  border-radius: 9px;
  border: none;
  background: linear-gradient(135deg, #2a56dd 0%, #1f4fd8 100%);
  box-shadow: 0 8px 20px rgba(31, 79, 216, 0.24);
  transition: transform 0.15s, box-shadow 0.15s;
}

.submit:hover {
  transform: translateY(-1px);
  box-shadow: 0 12px 26px rgba(31, 79, 216, 0.32);
}

@media (max-width: 1100px) {
  .left {
    width: 46%;
    padding: 40px 36px;
  }

  .hero-desc {
    font-size: 12.5px;
  }
}

@media (max-width: 860px) {
  .left {
    display: none;
  }

  .right {
    padding: 32px 20px;
  }

  .panel {
    max-width: 400px;
  }
}
</style>
