import http from './index'

export const authApi = {
  login: (data) => http.post('/auth/login', data),
  me: () => http.get('/users/me')
}

export const caseApi = {
  page: (params) => http.get('/cases', { params }),
  detail: (id) => http.get(`/cases/${id}`),
  create: (data) => http.post('/cases', data),
  update: (id, data) => http.put(`/cases/${id}`, data),
  submit: (id, opinion) => http.post(`/cases/${id}/submit`, { opinion }),
  transition: (id, data) => http.post(`/cases/${id}/transition`, data),
  rollback: (id, opinion, toStatus) => http.post(`/cases/${id}/rollback`, { opinion, toStatus }),
  adjustDeadline: (id, data) => http.post(`/cases/${id}/node-deadline`, data),
  openNode: (id, nodeCode) => http.post(`/cases/${id}/node-open`, { nodeCode }),
  remove: (id) => http.delete(`/cases/${id}`),
  close: (id, data) => http.post(`/cases/${id}/close`, data),
  archive: (id) => http.post(`/cases/${id}/archive`),
  options: (params) => http.get('/cases/options', { params }),
  removeRelation: (id, relationId) => http.delete(`/cases/${id}/relations/${relationId}`)
}

/** 争议关联：关联关系集中管理 */
export const relationApi = {
  page: (params) => http.get('/relations', { params }),
  create: (data) => http.post('/relations', data),
  remove: (id) => http.delete(`/relations/${id}`),
  exportList: (params) => http.get('/relations/export', { params, responseType: 'blob' })
}

/** 历史案件批量导入 */
export const caseImportApi = {
  template: () => http.get('/case-import/template', { responseType: 'blob' }),
  preview: (file) => {
    const form = new FormData()
    form.append('file', file)
    return http.post('/case-import/preview', form, { headers: { 'Content-Type': 'multipart/form-data' } })
  },
  confirm: (file) => {
    const form = new FormData()
    form.append('file', file)
    return http.post('/case-import/confirm', form, { headers: { 'Content-Type': 'multipart/form-data' } })
  }
}

/** 流程总览 */
export const dashboardApi = {
  overview: () => http.get('/dashboard')
}

/** 台账（工伤 + 劳动仲裁） */
export const ledgerApi = {
  page: (params) => http.get('/ledger', { params }),
  summary: () => http.get('/ledger/summary'),
  columns: () => http.get('/ledger/columns'),
  updateSubsidy: (caseId, data) => http.put(`/ledger/cases/${caseId}/subsidy`, data),
  exportLedger: (params) => http.get('/ledger/export', { params, responseType: 'blob' })
}

/** 流程节点配置 */
export const workflowApi = {
  definitions: () => http.get('/workflow/definitions'),
  instances: (params) => http.get('/workflow/instances', { params }),
  nodes: (flowKey) => http.get('/workflow/nodes', { params: { flowKey } }),
  createNode: (data) => http.post('/workflow/nodes', data),
  updateNode: (id, data) => http.put(`/workflow/nodes/${id}`, data),
  moveNode: (id, direction) => http.post(`/workflow/nodes/${id}/move`, null, { params: { direction } }),
  deleteNode: (id) => http.delete(`/workflow/nodes/${id}`),
  setNodeEnabled: (id, enabled) => http.put(`/workflow/nodes/${id}/enabled`, null, { params: { enabled } })
}

export const collabApi = {
  tasks: (caseId) => http.get(`/cases/${caseId}/tasks`),
  createTask: (caseId, data) => http.post(`/cases/${caseId}/tasks`, data),
  finishTask: (taskId, result) => http.put(`/tasks/${taskId}/finish`, { result }),
  countersigns: (caseId) => http.get(`/cases/${caseId}/countersigns`),
  initiateCountersign: (caseId, nodeCode) =>
    http.post(`/cases/${caseId}/countersigns/initiate`, null, { params: { nodeCode } }),
  submitCountersign: (caseId, data) => http.post(`/cases/${caseId}/countersigns`, data),
  todos: () => http.get('/todos'),
  notifications: (limit = 20) => http.get('/notifications', { params: { limit } }),
  readNotification: (id) => http.put(`/notifications/${id}/read`),
  readAllNotifications: () => http.put('/notifications/read-all')
}

export const docApi = {
  list: (caseId) => http.get(`/cases/${caseId}/documents`),
  upload: (caseId, formData) =>
    http.post(`/cases/${caseId}/documents`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    }),
  newVersion: (docId, formData) =>
    http.post(`/documents/${docId}/versions`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    }),
  versions: (docId) => http.get(`/documents/${docId}/versions`),
  remove: (docId) => http.delete(`/documents/${docId}`),
  download: (docId) => http.get(`/documents/${docId}/download`, { responseType: 'blob' }),
  preview: (docId) => http.get(`/documents/${docId}/preview`, { responseType: 'blob' })
}

export const deadlineApi = {
  page: (params) => http.get('/deadlines', { params }),
  supervise: (id, opinion) => http.post(`/deadlines/${id}/supervise`, { opinion }),
  registerReason: (id, reason) => http.post(`/deadlines/${id}/reason`, { reason }),
  markDone: (id) => http.post(`/deadlines/${id}/done`),
  scan: () => http.post('/deadlines/scan')
}

export const reportApi = {
  caseSummary: () => http.get('/reports/case-summary'),
  efficiency: () => http.get('/reports/efficiency'),
  amount: () => http.get('/reports/amount'),
  overdue: () => http.get('/reports/overdue'),
  exportCases: (params) => http.get('/reports/cases/export', { params, responseType: 'blob' }),
  exportArchive: (params) => http.get('/reports/archive/export', { params, responseType: 'blob' })
}

export const systemApi = {
  users: (params) => http.get('/users', { params }),
  createUser: (data) => http.post('/users', data),
  updateUser: (id, data) => http.put(`/users/${id}`, data),
  changeStatus: (id, status) => http.put(`/users/${id}/status`, { status }),
  deleteUser: (id) => http.delete(`/users/${id}`),
  resetPassword: (id, password) => http.put(`/users/${id}/password`, { password }),
  roles: () => http.get('/roles'),
  createRole: (data) => http.post('/roles', data),
  deleteRole: (id) => http.delete(`/roles/${id}`),
  permissions: () => http.get('/permissions'),
  rolePermissions: (roleId) => http.get(`/roles/${roleId}/permissions`),
  saveRolePermissions: (roleId, data) => http.put(`/roles/${roleId}/permissions`, data),
  userOptions: () => http.get('/users/options'),
  depts: () => http.get('/depts'),
  createDept: (name) => http.post('/depts', { name }),
  removeDept: (id) => http.delete(`/depts/${id}`),
  dicts: () => http.get('/dicts'),
  dictByType: (type) => http.get(`/dicts/${type}`),
  addDictItem: (type, name) => http.post(`/dicts/${type}/items`, { name }),
  removeDictItem: (type, id) => http.delete(`/dicts/${type}/items/${id}`)
}

export const recycleApi = {
  page: (params) => http.get('/recycle-bin', { params }),
  restore: (target, ids) => http.post('/recycle-bin/restore', { target, ids }),
  purge: (target, ids) => http.delete('/recycle-bin', { data: { target, ids } })
}

export const auditApi = {
  page: (params) => http.get('/audit-logs', { params })
}
