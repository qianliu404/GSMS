<template>
  <div class="page" v-loading="loading">
    <div class="mb-12">
      <el-button size="small" @click="goBack">‹ {{ backText }}</el-button>
    </div>

    <!-- 头部：案号 / 类型 / 状态 / 当前节点 / 操作 -->
    <div class="card detail-head">
      <div class="dh-top">
        <span class="no">{{ detail.caseNo }}</span>
        <span class="tag" :class="detail.caseType === 'INJURY' ? 'injury' : 'arb'">
          {{ detail.caseTypeName }}
        </span>
        <span v-if="detail.archiveStatus === 1" class="tag ok">已归档</span>
        <span v-else-if="detail.overdue" class="tag danger"><i class="dot"></i>已超期 {{ detail.overdueDays }} 天</span>
        <span v-else-if="detail.status === 'CLOSED'" class="tag ok">已结案</span>
        <span v-else class="tag neutral"><i class="dot" style="color: #9aa7ba"></i>正常办理</span>
        <span class="pill">当前节点 {{ currentNodeIndex + 1 }}/{{ flowNodes.length || '-' }} · {{ detail.statusName }}</span>

        <div class="dh-actions">
          <el-button
            class="act-back"
            size="small"
            :disabled="!canRollback"
            @click="openRollback"
          >↩ 退回节点</el-button>
          <el-button class="act-doc" size="small" :disabled="detail.archiveStatus === 1" @click="generateDoc">生成文书</el-button>
          <el-button
            v-if="detail.archiveStatus !== 1"
            :class="['act-go', { 'act-archive': !!closeAction }]"
            size="small"
            type="primary"
            :disabled="primaryDisabled"
            @click="onPrimaryAction"
          >{{ primaryActionText }}</el-button>
          <el-button v-else class="act-go" size="small" disabled>已归档</el-button>
          <el-button v-if="hasPerm('case:delete')" class="act-del" size="small" type="danger" plain @click="removeCase">删除</el-button>
        </div>
      </div>

      <div class="dh-sub">
        <span><i>当事人</i><b>{{ detail.employeeName }}</b></span>
        <span><i>厂区 / 班组</i><b>{{ detail.plantArea || detail.departmentName || '-' }} · {{ detail.workshopTeam || '-' }}</b></span>
        <span><i>发生日期</i><b>{{ detail.occurDate }}</b></span>
        <span><i>承办人</i><b>{{ detail.handlerName || detail.handlerId }}</b></span>
        <span><i>已办</i><b>{{ elapsedDays }} 天</b></span>
      </div>

      <div class="hr"></div>

      <div ref="stepsRef" class="steps">
        <div
          v-for="(node, i) in flowNodes"
          :key="node.statusCode"
          class="sx"
          :class="{ done: isDone(node, i), skip: isSkipped(node, i), cur: i === currentNodeIndex }"
        >
          <div class="d">{{ isDone(node, i) ? '✓' : (isSkipped(node, i) ? '–' : i + 1) }}</div>
          <div class="nm">{{ node.nodeName }}</div>
          <div class="tm" :class="nodeDueClass(node, i)">{{ nodeTime(node, i) }}</div>
        </div>
      </div>
    </div>

    <div class="cols">
      <!-- 左主区 -->
      <div>
        <!-- 当前节点办理 -->
        <div class="card">
          <div class="card-head">
            <h2>当前节点办理 · {{ detail.statusName }}</h2>
            <span
              v-if="currentDue"
              class="pill due"
              :class="currentDue.started ? (currentDue.state === 'OVERDUE' ? 'danger' : currentDue.state === 'DUE_SOON' ? 'warn' : '') : 'idle'"
            >
              <template v-if="!currentDue.started">未开启 · 截止 {{ shortDate(currentDue.deadlineDate) }}</template>
              <template v-else>截止 {{ shortDate(currentDue.deadlineDate) }} · {{ dueText(currentDue) }}</template>
            </span>
            <span v-else class="pill">时限 {{ currentNode?.timeLimit || '—' }}</span>
          </div>
          <div class="card-body">
            <el-form label-position="top">
              <!-- 本节点结论：办理后自动回填档案，不用再点「编辑档案」二次录入 -->
              <el-form-item
                v-for="field in nodeResultFields"
                :key="field.key"
                :label="field.label"
              >
                <div class="node-result">
                  <el-select
                    v-if="field.type === 'select'"
                    v-model="nodeResultForm[field.key]"
                    :disabled="detail.archiveStatus === 1"
                    style="width: 240px"
                  >
                    <el-option
                      v-for="o in field.options"
                      :key="o.value"
                      :label="o.label"
                      :value="o.value"
                    />
                  </el-select>
                  <el-date-picker
                    v-else-if="field.type === 'date'"
                    v-model="nodeResultForm[field.key]"
                    type="date"
                    value-format="YYYY-MM-DD"
                    :disabled="detail.archiveStatus === 1"
                    style="width: 240px"
                  />
                  <el-input-number
                    v-else-if="field.type === 'number'"
                    v-model="nodeResultForm[field.key]"
                    :min="0"
                    :precision="field.precision || 0"
                    :step="field.step || 1"
                    :controls="false"
                    :disabled="detail.archiveStatus === 1"
                    :placeholder="field.placeholder"
                    style="width: 240px"
                  />
                  <el-input
                    v-else
                    v-model="nodeResultForm[field.key]"
                    :disabled="detail.archiveStatus === 1"
                    :placeholder="field.placeholder"
                    style="width: 240px"
                  />
                </div>
              </el-form-item>
              <!-- 办理日期：默认当天，可修改；截止日期按该日期 + 节点时限（工作日，含节假日）推算 -->
              <el-form-item label="办理日期">
                <div class="node-result">
                  <el-date-picker
                    v-model="handleDateInput"
                    type="date"
                    value-format="YYYY-MM-DD"
                    :clearable="false"
                    :disabled="detail.archiveStatus === 1"
                    :loading="deadlineSaving"
                    style="width: 200px"
                    @change="saveHandleDate"
                  />
                  <span class="hint">截止日期按办理日期加节点时限计算，法定节假日不计入工作日</span>
                </div>
              </el-form-item>
              <!-- 截止日期：按办理日期 + 节点时限自动推算，允许人工调整（如与当事人约定） -->
              <el-form-item label="截止日期">
                <div class="node-result">
                  <el-date-picker
                    v-model="deadlineInput"
                    type="date"
                    value-format="YYYY-MM-DD"
                    :clearable="false"
                    :disabled="detail.archiveStatus === 1"
                    :loading="deadlineSaving"
                    style="width: 200px"
                    @change="saveDeadline"
                  />
                  <span v-if="deadlineHint" class="hint" :class="deadlineHintClass">{{ deadlineHint }}</span>
                  <el-button
                    v-if="autoDeadline && deadlineInput !== autoDeadline"
                    link
                    type="primary"
                    class="deadline-reset"
                    @click="resetDeadline"
                  >按时限重算</el-button>
                </div>
              </el-form-item>
              <el-form-item label="办理意见">
                <el-input
                  v-model="opinionText"
                  type="textarea"
                  :rows="3"
                  :disabled="detail.archiveStatus === 1"
                  placeholder="填写办理意见"
                />
              </el-form-item>
              <el-form-item label="上传材料">
                <el-upload
                  :show-file-list="false"
                  :http-request="uploadDoc"
                  :disabled="detail.archiveStatus === 1"
                  drag
                  class="upload-full"
                >
                  <div class="upload-tip">拖拽到此处，或 <b>点击上传</b></div>
                </el-upload>
              </el-form-item>
            </el-form>

            <div class="node-actions">
              <el-button
                class="act-back"
                :disabled="!canRollback"
                plain
                type="danger"
                @click="openRollback"
              >↩ 退回节点</el-button>
              <div class="right">
                <el-button class="act-save" :disabled="detail.archiveStatus === 1" @click="ElMessage.info('流程状态已实时保存，无需手动保存草稿')">
                  保存草稿
                </el-button>
                <!-- 提交：开启当前节点计时，开启后才按截止日期判断超期；流转仍走右侧按钮 -->
                <el-button
                  class="act-submit"
                  :disabled="quickSubmitDisabled"
                  :loading="submitting"
                  :title="submitTitle"
                  @click="quickSubmit"
                >{{ nodeOpened ? '已开启' : '提交' }}</el-button>
                <el-button
                  v-if="submitAction"
                  class="act-go"
                  type="primary"
                  :loading="submitting"
                  @click="runAction(submitAction)"
                >提交立案</el-button>
                <el-button
                  v-else-if="closeAction"
                  class="act-go"
                  type="primary"
                  :loading="submitting"
                  @click="runAction(closeAction)"
                >{{ closeAction.code === 'CLOSE' ? '结案归档 →' : '归档 →' }}</el-button>
                <el-button
                  v-else-if="forwardTargets.length"
                  class="act-go"
                  type="primary"
                  :loading="submitting"
                  @click="openTransition"
                >推进至下一节点 →</el-button>
                <el-button
                  v-else-if="detail.archiveStatus !== 1"
                  class="act-go"
                  type="primary"
                  disabled
                >当前无可流转动作</el-button>
              </div>
            </div>
          </div>
        </div>

        <!-- 档案信息 -->
        <div class="card">
          <div class="card-head">
            <h2>{{ detail.caseType === 'INJURY' ? '工伤档案信息' : '争议基本信息' }}</h2>
            <el-button
              size="small"
              style="margin-left: auto"
              :disabled="!canEditArchive"
              @click="editDialog = true"
            v-if="hasPerm('case:edit')"
          >编辑档案</el-button>
          </div>
          <div class="card-body">
            <div class="arc">
              <div v-for="cell in archiveCells" :key="cell.k" :class="{ span: cell.span }">
                <div class="k">{{ cell.k }}</div>
                <div class="v" v-html="cell.v"></div>
              </div>
            </div>
          </div>
        </div>

        <!-- 办理记录 -->
        <div class="card">
          <div class="card-head">
            <h2>办理记录</h2>
            <span class="pill">
              共 {{ timeline.length }} 条
            </span>
          </div>
          <div class="card-body">
            <div class="tl-scroll">
              <div class="tl">
                <div v-for="log in timeline" :key="log.operateTime + log.toStatus" class="tli">
                  <div class="tt">
                    {{ log.isReturn ? `退回至「${log.toStatusName}」` : log.toStatusName }}
                    <span class="tag" :class="log.tagCls">{{ log.tagText }}</span>
                  </div>
                  <div class="mm">
                    <span>{{ log.operatorName || log.operatorId }}</span>
                    <span>{{ log.operateTime }}</span>
                    <span v-if="log.opinion">{{ log.opinion }}</span>
                  </div>
                </div>
                <div v-if="nextNode" class="tli pending">
                  <div class="tt" style="color: var(--gsms-text-3)">{{ nextNode.nodeName }}</div>
                  <div class="mm"><span>待启动</span></div>
                </div>
                <div v-if="!timeline.length" class="empty">暂无办理记录</div>
              </div>
            </div>
          </div>
        </div>

        <!-- 一次性待遇 -->
        <div class="card" v-if="detail.caseType === 'INJURY'">
          <div class="card-head">
            <h2>一次性待遇申领</h2>
            <span class="pill">合计 {{ totalSubsidy }}</span>
          </div>
          <div class="card-body">
            <div class="sub-row" v-for="item in SUBSIDY_ITEMS" :key="item.key">
              <div class="ic2">{{ item.icon }}</div>
              <div>
                <div class="t2">{{ item.label }}</div>
              </div>
              <div class="money">
                <div>{{ subsidyAmount(item) }}</div>
                <span
                  class="tag stat-chip"
                  :class="subsidyClass(detail.injury?.[item.statusField])"
                  @click="openSubsidy(item)"
                >{{ subsidyName(detail.injury?.[item.statusField]) }} ↻</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 协同与文档（保留原有全部能力） -->
        <div class="card">
          <el-tabs v-model="activeTab" class="detail-tabs" @tab-change="onTabChange">
            <el-tab-pane :label="`成员 (${detail.members?.length || 0})`" name="members">
              <div class="pane">
                <el-table :data="detail.members" border>
                  <el-table-column prop="userName" label="姓名" width="140" />
                  <el-table-column prop="userId" label="账号" width="140" />
                  <el-table-column prop="memberRoleName" label="案件角色" width="120">
                    <template #default="{ row }">
                      <span class="tag neutral">{{ row.memberRoleName }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column prop="joinTime" label="加入时间" />
                </el-table>
              </div>
            </el-tab-pane>

            <el-tab-pane :label="`文档 (${documents.length})`" name="documents">
              <div class="pane">
                <div class="toolbar-inline">
                  <el-upload
                    :show-file-list="false"
                    :http-request="uploadDoc"
                    :disabled="detail.archiveStatus === 1"
                  >
                    <el-button v-if="hasPerm('doc:create')" type="primary" :icon="Upload" :disabled="detail.archiveStatus === 1">上传文档</el-button>
                  </el-upload>
                  <span class="text-sub" style="font-size: 12px">
                    支持 pdf/doc/xlsx/png 等，单文件 ≤100MB
                  </span>
                </div>
                <el-table :data="documents" border>
                  <el-table-column prop="docName" label="文档名称" min-width="240" show-overflow-tooltip />
                  <el-table-column prop="docTypeName" label="类型" width="110" />
                  <el-table-column label="版本" width="80">
                    <template #default="{ row }">v{{ row.version }}</template>
                  </el-table-column>
                  <el-table-column label="大小" width="100">
                    <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
                  </el-table-column>
                  <el-table-column prop="uploadByName" label="上传人" width="100" />
                  <el-table-column prop="uploadTime" label="上传时间" width="160" />
                  <el-table-column label="敏感" width="80">
                    <template #default="{ row }">
                      <span v-if="row.sensitiveFlag === 1" class="tag danger">敏感</span>
                      <span v-else class="text-sub">-</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="操作" width="200" fixed="right">
                    <template #default="{ row }">
                      <el-button link type="primary" size="small" @click="previewDoc(row)">预览</el-button>
                      <el-button link type="primary" size="small" :disabled="!row.canDownload" @click="downloadDoc(row)">
                        下载
                      </el-button>
                      <el-button link type="primary" size="small" @click="showVersions(row)">版本</el-button>
                      <el-button v-if="hasPerm('doc:delete')" link type="danger" size="small" @click="removeDoc(row)">删除</el-button>
                    </template>
                  </el-table-column>
                </el-table>
              </div>
            </el-tab-pane>

            <el-tab-pane :label="`任务 (${tasks.length})`" name="tasks">
              <div class="pane">
                <div class="toolbar-inline">
                  <el-button type="primary" :icon="Plus" @click="taskDialog = true">分派任务</el-button>
                </div>
                <el-table :data="tasks" border>
                  <el-table-column prop="taskType" label="任务" min-width="180" />
                  <el-table-column prop="assignerName" label="分派人" width="100" />
                  <el-table-column prop="assigneeName" label="责任人" width="100" />
                  <el-table-column prop="deadline" label="截止时间" width="160" />
                  <el-table-column label="优先级" width="90">
                    <template #default="{ row }">
                      <span class="tag" :class="priorityClass(row.priority)">{{ priorityText(row.priority) }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="状态" width="100">
                    <template #default="{ row }">
                      <span class="tag" :class="row.status === 'DONE' ? 'ok' : (row.overdue ? 'danger' : 'warn')">
                        {{ row.status === 'DONE' ? '已完成' : row.overdue ? '已超期' : '待处理' }}
                      </span>
                    </template>
                  </el-table-column>
                  <el-table-column prop="result" label="完成结果" min-width="160" show-overflow-tooltip />
                  <el-table-column label="操作" width="100" fixed="right">
                    <template #default="{ row }">
                      <el-button v-if="row.status !== 'DONE'" link type="primary" size="small" @click="finishTask(row)">
                        完成
                      </el-button>
                    </template>
                  </el-table-column>
                </el-table>
              </div>
            </el-tab-pane>

            <el-tab-pane :label="`会签 (${countersigns.length})`" name="countersign">
              <div class="pane">
                <div class="toolbar-inline">
                  <el-button :icon="Promotion" @click="initiateCountersign">发起会签</el-button>
                  <el-button type="primary" :icon="EditPen" @click="countersignDialog = true">提交会签意见</el-button>
                </div>
                <el-table :data="countersigns" border>
                  <el-table-column prop="nodeCode" label="会签节点" width="150" />
                  <el-table-column prop="countersignerName" label="会签人" width="120" />
                  <el-table-column prop="opinionTypeName" label="意见类型" width="120" />
                  <el-table-column prop="opinion" label="会签意见" min-width="220" show-overflow-tooltip />
                  <el-table-column label="结果" width="100">
                    <template #default="{ row }">
                      <span class="tag" :class="row.result === 'PASS' ? 'ok' : (row.result === 'REJECT' ? 'danger' : 'neutral')">
                        {{ row.result === 'PASS' ? '通过' : row.result === 'REJECT' ? '驳回' : '待会签' }}
                      </span>
                    </template>
                  </el-table-column>
                  <el-table-column prop="countersignTime" label="会签时间" width="160" />
                </el-table>
              </div>
            </el-tab-pane>

            <el-tab-pane label="期限" name="deadlines">
              <div class="pane">
                <el-table :data="caseDeadlines" border>
                  <el-table-column prop="nodeName" label="节点名称" min-width="160" />
                  <el-table-column label="类型" width="100">
                    <template #default="{ row }">
                      <span class="tag" :class="row.deadlineType === 'LEGAL' ? 'danger' : 'neutral'">
                        {{ row.deadlineTypeName }}
                      </span>
                    </template>
                  </el-table-column>
                  <el-table-column prop="deadlineDate" label="截止日期" width="120" />
                  <el-table-column label="剩余" width="110">
                    <template #default="{ row }">
                      <span v-if="row.status === 'DONE'" class="text-sub">已完成</span>
                      <span v-else-if="row.remainDays < 0" class="text-danger">超期 {{ -row.remainDays }} 天</span>
                      <span v-else>{{ row.remainDays }} 天</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="状态" width="100">
                    <template #default="{ row }">
                      <span class="tag" :class="deadlineClass(row.status)">{{ row.statusName }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column prop="handlerName" label="责任人" width="100" />
                  <el-table-column prop="overdueReason" label="超期原因" min-width="140" show-overflow-tooltip />
                </el-table>
              </div>
            </el-tab-pane>

            <el-tab-pane label="流转日志" name="logs">
              <div class="pane">
                <el-timeline style="padding-left: 6px">
                  <el-timeline-item
                    v-for="log in detail.statusLogs"
                    :key="log.operateTime + log.toStatus"
                    :timestamp="log.operateTime"
                    placement="top"
                    :type="log.toStatus === 'CLOSED' ? 'success' : 'primary'"
                  >
                    <div class="log-title">
                      {{ log.fromStatusName || '建档' }}
                      <el-icon style="vertical-align: middle; margin: 0 4px"><Right /></el-icon>
                      <strong>{{ log.toStatusName }}</strong>
                      <span class="text-sub" style="margin-left: 8px">{{ log.operatorName }}</span>
                    </div>
                    <div v-if="log.opinion" class="log-opinion">意见：{{ log.opinion }}</div>
                  </el-timeline-item>
                </el-timeline>
              </div>
            </el-tab-pane>
          </el-tabs>
        </div>
      </div>

      <!-- 右栏 -->
      <div>
        <div class="card">
          <div class="card-body" style="padding: 14px">
            <div class="timer">
              <div class="lb">节点剩余时限</div>
              <div class="big" :style="{ color: timerColor }">{{ timerText }}</div>
              <div class="lb">{{ detail.statusName }} · {{ currentNode?.timeLimit || '未配置时限' }}</div>
            </div>
            <div style="display: flex; gap: 8px">
              <el-button v-if="hasPerm('deadline:edit')" size="small" style="flex: 1" @click="supervise">督办</el-button>
              <el-button v-if="hasPerm('deadline:edit')" size="small" style="flex: 1" @click="registerReason">登记超期原因</el-button>
            </div>
          </div>
        </div>

        <div class="card">
          <div class="card-head">
            <h2>材料清单</h2>
            <span class="pill">{{ documents.length }} 个文件</span>
          </div>
          <div class="card-body" style="padding-top: 6px">
            <div v-if="!documents.length" class="empty" style="padding: 20px 0">暂无材料</div>
            <div class="doc" v-for="d in documents.slice(0, 6)" :key="d.id" @click="previewDoc(d)">
              <div class="fi">{{ fileExt(d.docName) }}</div>
              <div class="nm">{{ d.docName }}</div>
              <div class="sz">{{ formatSize(d.fileSize) }}</div>
            </div>
          </div>
        </div>

        <div class="card">
          <div class="card-head">
            <h2>本节点应产出</h2>
          </div>
          <div class="card-body" style="padding-top: 6px">
            <div class="doc" v-for="(m, i) in outputMaterials" :key="i">
              <div class="fi">文</div>
              <div class="nm">{{ m }}</div>
              <div class="sz">待生成</div>
            </div>
            <div v-if="!outputMaterials.length" class="empty" style="padding: 20px 0">该节点未配置产出文书</div>
          </div>
        </div>

        <div class="card">
          <div class="card-head"><h2>责任人</h2></div>
          <div class="card-body" style="padding-top: 8px">
            <div class="doc">
              <div class="fi">承</div>
              <div class="nm">{{ detail.handlerName || detail.handlerId }} <span class="mini">· 承办人</span></div>
            </div>
            <div class="doc">
              <div class="fi">负</div>
              <div class="nm">{{ detail.ownerName || detail.ownerId }} <span class="mini">· 负责人</span></div>
            </div>
            <div class="doc" v-for="m in archiveManagers" :key="m.userId">
              <div class="fi">协</div>
              <div class="nm">{{ m.userName }} <span class="mini">· {{ m.memberRoleName }}</span></div>
            </div>
          </div>
        </div>

        <!-- 争议关联案件：另一案的类型、当事人、当前阶段与关联时间，直接可跳转/解除 -->
        <div class="card" style="margin-bottom: 0">
          <div class="card-head">
            <h2>争议关联案件</h2>
            <span class="pill" style="margin-left: auto">{{ detail.relations?.length || 0 }}</span>
          </div>
          <div class="card-body rel-list">
            <div v-if="!detail.relations?.length" class="empty" style="padding: 14px 0">暂无关联案件</div>
            <div v-for="rel in detail.relations" :key="rel.id" class="rel-item">
              <div class="rel-hd">
                <span class="tag" :class="rel.relatedCaseType === 'INJURY' ? 'injury' : 'arb'">
                  {{ rel.relatedCaseType === 'INJURY' ? '工伤' : '仲裁' }}
                </span>
                <el-link type="primary" class="rel-no" @click="openCaseDetail(rel.relatedCaseId)">
                  {{ rel.relatedCaseNo }}
                </el-link>
                <el-button v-if="hasPerm('relation:delete')" link type="danger" size="small" class="rel-del" @click="removeRelation(rel)">解除</el-button>
              </div>
              <div class="rel-name">{{ rel.relatedCaseName }}</div>
              <div class="rel-who">{{ relatedWho(rel) }}</div>
              <div v-if="rel.relationDesc" class="rel-desc">{{ rel.relationDesc }}</div>
              <div class="rel-meta">
                <span>当前阶段 {{ rel.relatedStatusName || rel.relatedStatus || '—' }}</span>
                <span v-if="rel.createTime">关联于 {{ String(rel.createTime).slice(0, 10) }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 状态流转意见 -->
    <el-dialog v-model="opinionDialog" :title="opinionTitle" width="520px">
      <el-form label-position="top">
        <el-form-item :label="pendingKind === 'ROLLBACK' ? '退回至' : '推进至'">
          <el-select v-model="pendingTarget" style="width: 100%">
            <el-option
              v-for="t in pendingKind === 'ROLLBACK' ? rollbackTargets : forwardTargets"
              :key="t.code"
              :label="t.name"
              :value="t.code"
            />
          </el-select>
          <div v-if="skippedNodeNames.length" class="skip-tip">
            将跳过：{{ skippedNodeNames.join('、') }}
          </div>
        </el-form-item>
        <el-form-item :label="pendingKind === 'ROLLBACK' ? '退回原因' : '办理意见'">
          <el-input
            v-model="opinionText"
            type="textarea"
            :rows="3"
            :placeholder="pendingKind === 'ROLLBACK' ? '请说明退回原因' : '请输入处理意见'"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="opinionDialog = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="confirmOpinion">确定</el-button>
      </template>
    </el-dialog>

    <!-- 结案 -->
    <el-dialog v-model="closeDialog" title="结案" width="520px">
      <el-form label-position="top">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="结案方式">
              <el-select v-model="closeForm.closeType" style="width: 100%">
                <el-option v-for="c in CLOSE_TYPES" :key="c.value" :label="c.label" :value="c.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="卷宗页数">
              <el-input-number v-model="closeForm.archivePages" :min="0" :step="10" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="保管期限（年）">
          <el-input-number v-model="closeForm.archiveYears" :min="0" :step="5" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结案意见">
          <el-input v-model="closeForm.opinion" type="textarea" :rows="3" placeholder="结案审批意见" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="closeDialog = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="confirmClose">确认结案</el-button>
      </template>
    </el-dialog>

    <!-- 待遇申领 -->
    <el-dialog v-model="subsidyDialog" title="更新一次性待遇申领" width="420px">
      <el-form label-position="top">
        <el-form-item label="申领状态">
          <el-radio-group v-model="subsidyForm.status">
            <el-radio-button v-for="s in SUBSIDY_STATUS" :key="s.value" :label="s.value">{{ s.label }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="金额（元）">
          <el-input-number v-model="subsidyForm.amount" :min="0" :step="1000" :precision="2" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="subsidyDialog = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="saveSubsidy">保存</el-button>
      </template>
    </el-dialog>

    <!-- 编辑档案 -->
    <el-dialog v-model="editDialog" title="编辑案件档案" width="560px">
      <el-form :model="editForm" label-position="top" class="arc-form">
        <div class="arc-grid">
          <el-form-item label="姓名">
            <el-input v-model="editForm.employeeName" />
          </el-form-item>
          <el-form-item label="承办人">
            <el-select v-model="editForm.handlerId" filterable placeholder="选择承办人" style="width: 100%">
              <el-option v-for="u in users" :key="u.userId" :label="u.userName" :value="u.userId" />
            </el-select>
          </el-form-item>
          <el-form-item label="厂区">
            <el-select v-model="editForm.plantArea" filterable allow-create default-first-option clearable placeholder="选择或直接输入" style="width: 100%">
              <el-option v-for="a in areas" :key="a.id" :label="a.dictName" :value="a.dictName" />
            </el-select>
          </el-form-item>
          <el-form-item label="车间班组">
            <el-input v-model="editForm.workshopTeam" placeholder="如：生产一部 · 二车间甲班" />
          </el-form-item>
          <el-form-item v-if="isInjury" label="受伤原因">
            <el-input v-model="editForm.injuryReason" placeholder="如：设备故障导致右手挤压伤" />
          </el-form-item>
          <el-form-item v-if="isInjury" label="休假天数">
            <el-input-number v-model="editForm.restDays" :min="0" :precision="1" :step="0.5" :controls="false"
                             placeholder="按鉴定结论填写，可填小数" style="width: 100%" />
          </el-form-item>
          <el-form-item v-if="!isInjury" label="履行款（元）">
            <el-input-number v-model="editForm.performAmount" :min="0" :precision="2" :controls="false"
                             placeholder="按实际履行金额填写" style="width: 100%" />
          </el-form-item>
          <el-form-item v-if="!isInjury" label="仲裁案号">
            <el-input v-model="editForm.arbitrationNo" placeholder="选填" />
          </el-form-item>
          <el-form-item label="发生日期">
            <el-date-picker v-model="editForm.occurDate" type="date" value-format="YYYY-MM-DD"
                            placeholder="选择日期" style="width: 100%" />
          </el-form-item>
          <el-form-item v-if="isInjury" label="伤害日期">
            <el-date-picker v-model="editForm.injuryDate" type="date" value-format="YYYY-MM-DD"
                            :clearable="false" placeholder="选择日期" style="width: 100%" />
          </el-form-item>
          <el-form-item label="小班长 / 直属主管">
            <el-input v-model="editForm.leaderL1" />
          </el-form-item>
          <el-form-item label="大班长 / 上级主管">
            <el-input v-model="editForm.leaderL2" />
          </el-form-item>
          <el-form-item v-if="isInjury" label="工伤认定情况">
            <el-select v-model="editForm.recognizeResult" clearable placeholder="选择认定情况" style="width: 100%">
              <el-option v-for="(name, code) in RECOGNIZE_NAME" :key="code" :label="name" :value="code" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="isInjury" label="劳动能力鉴定结论">
            <el-select v-model="editForm.appraisalLevel" filterable allow-create clearable
                       placeholder="选择或直接输入" style="width: 100%">
              <el-option v-for="lv in APPRAISAL_LEVELS" :key="lv" :label="lv" :value="lv" />
            </el-select>
          </el-form-item>
          <el-form-item label="案件描述" class="span-2">
            <el-input v-model="editForm.description" type="textarea" :rows="3" maxlength="5000" show-word-limit
                      placeholder="简述事故经过或争议情况，可在档案中直接查看" />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="editDialog = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 分派任务 -->
    <el-dialog v-model="taskDialog" title="分派任务" width="520px">
      <el-form :model="taskForm" label-width="90px">
        <el-form-item label="任务内容">
          <el-input v-model="taskForm.taskType" placeholder="如：补充收集医疗票据" />
        </el-form-item>
        <el-form-item label="责任人">
          <el-select v-model="taskForm.assigneeId" filterable style="width: 100%">
            <el-option
              v-for="u in users"
              :key="u.userId"
              :label="`${u.userName}（${u.deptName || '-'}）`"
              :value="u.userId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="截止时间">
          <el-date-picker
            v-model="taskForm.deadline"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="优先级">
          <el-radio-group v-model="taskForm.priority">
            <el-radio-button label="HIGH">高</el-radio-button>
            <el-radio-button label="MEDIUM">中</el-radio-button>
            <el-radio-button label="LOW">低</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="taskDialog = false">取消</el-button>
        <el-button type="primary" @click="submitTask">分派</el-button>
      </template>
    </el-dialog>

    <!-- 会签意见 -->
    <el-dialog v-model="countersignDialog" title="提交会签意见" width="480px">
      <el-form :model="countersignForm" label-width="90px">
        <el-form-item label="意见类型">
          <el-radio-group v-model="countersignForm.opinionType">
            <el-radio label="AGREE">同意</el-radio>
            <el-radio label="CONDITIONAL">有条件同意</el-radio>
            <el-radio label="DISAGREE">不同意</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="会签意见">
          <el-input v-model="countersignForm.opinion" type="textarea" :rows="4" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="countersignDialog = false">取消</el-button>
        <el-button type="primary" @click="submitCountersign">提交</el-button>
      </template>
    </el-dialog>

    <!-- 版本 -->
    <el-dialog v-model="versionDialog" title="文档版本" width="640px">
      <el-table :data="versionList" border>
        <el-table-column label="版本" width="80">
          <template #default="{ row }">v{{ row.version }}</template>
        </el-table-column>
        <el-table-column prop="uploadBy" label="上传人" width="110" />
        <el-table-column prop="uploadTime" label="上传时间" width="170" />
        <el-table-column label="大小" width="100">
          <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="versionDialog = false">关闭</el-button>
      </template>
    </el-dialog>

  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Upload, Plus, Promotion, EditPen, Right } from '@element-plus/icons-vue'
import { caseApi, collabApi, docApi, deadlineApi, ledgerApi, systemApi, workflowApi } from '../api/modules'
import { useUserStore } from '../store/user'
import { useNarrow } from '../composables/useNarrow'
import { useCaseNav } from '../composables/useCaseNav'

/* 厂区选项来自字典，可在新建案件页自定义维护 */
const hasPerm = (code) => useUserStore().hasPerm(code)
const areas = ref([])

const CLOSE_TYPES = [
  { value: 'TREATMENT_SETTLED', label: '待遇结清' },
  { value: 'MEDIATION', label: '调解结案' },
  { value: 'AWARD', label: '裁决结案' },
  { value: 'WITHDRAW', label: '撤诉' },
  { value: 'OTHER', label: '其他' }
]

const SUBSIDY_STATUS = [
  { value: 'NOT_APPLIED', label: '未申领' },
  { value: 'APPLYING', label: '申领中' },
  { value: 'RECEIVED', label: '已到账' },
  { value: 'INELIGIBLE', label: '不符合条件' }
]

const SUBSIDY_ITEMS = [
  {
    key: 'DISABILITY',
    icon: '伤',
    label: '一次性伤残补助金',
    desc: '按伤残等级，由工伤保险基金支付',
    statusField: 'subsidyDisabilityStatus',
    amountField: 'subsidyDisabilityAmount'
  },
  {
    key: 'MEDICAL',
    icon: '医',
    label: '一次性工伤医疗补助金',
    desc: '解除或终止劳动关系时支付',
    statusField: 'subsidyMedicalStatus',
    amountField: 'subsidyMedicalAmount'
  },
  {
    key: 'EMPLOYMENT',
    icon: '就',
    label: '一次性伤残就业补助金',
    desc: '由用人单位支付',
    statusField: 'subsidyEmploymentStatus',
    amountField: 'subsidyEmploymentAmount'
  }
]

const RECOGNIZE_NAME = {
  RECOGNIZED: '已认定（工伤）',
  NOT_RECOGNIZED: '不予认定',
  DECLARING: '申报中',
  NOT_DECLARED: '未申报'
}

const route = useRoute()
const router = useRouter()
const caseId = route.params.id
/** 关联案件跳转：沿用当前详情地址作为来源，返回时逐级回退 */
const { openCaseDetail } = useCaseNav()

/** 来源地址：从列表页进来时写进 query.from（工伤 / 仲裁 / 台账 / 待办 / 关联…） */
const fromQuery = computed(() => (typeof route.query.from === 'string' ? route.query.from : ''))

/** 从归档库进入时为只读卷宗视图，返回归档库而不是案件列表 */
const fromArchive = computed(() => route.path.startsWith('/archive/'))

/** 返回文案按来源模块命名，避免从台账、待办进来也写「返回列表」 */
const backText = computed(() => {
  if (fromArchive.value) return '返回归档库'
  const path = fromQuery.value.split('?')[0]
  if (path.startsWith('/cases/') && path !== '/cases/create') return '返回上一案件'
  const named = {
    '/ledger': '返回台账',
    '/relations': '返回关联案件',
    '/todos': '返回待办',
    '/deadlines': '返回期限',
    '/dashboard': '返回工作台'
  }
  return named[path] || '返回列表'
})

function goBack() {
  const from = fromQuery.value
  // 回到点进来时的那个列表（含类型、筛选、分页），不是统一的案件列表
  if (from && from.startsWith('/')) {
    router.push(from)
    return
  }
  if (fromArchive.value && window.history.state && window.history.state.back) {
    router.back()
    return
  }
  router.push(fromArchive.value ? '/archive' : '/cases')
}

const loading = ref(false)
const submitting = ref(false)

/**
 * 窄屏：步骤条只能横向滚动，进入页面时把当前节点滚到可视区中间，
 * 否则 10 个节点里当前节点常常落在屏幕外，需要手动滑。
 */
const isNarrow = useNarrow()
const stepsRef = ref(null)

function centerCurrentStep() {
  const box = stepsRef.value
  if (!box) return
  const cur = box.querySelector('.sx.cur')
  if (!cur) return
  box.scrollLeft = cur.offsetLeft - (box.clientWidth - cur.offsetWidth) / 2
}

const activeTab = ref('members')
const detail = ref({})
const documents = ref([])
const tasks = ref([])
const countersigns = ref([])
const caseDeadlines = ref([])
const users = ref([])
const nodes = ref([])
const versionList = ref([])

const opinionDialog = ref(false)
const opinionTitle = ref('')
const opinionText = ref('')
/** 弹窗类型：TRANSIT 推进 / ROLLBACK 退回 */
const pendingKind = ref('TRANSIT')
/** 弹窗当前选中的目标节点 */
const pendingTarget = ref('')

const closeDialog = ref(false)
const closeForm = reactive({ closeType: 'TREATMENT_SETTLED', archivePages: 0, archiveYears: 30, opinion: '' })

/** 本节点结论：办理提交时随流转一起写回档案 */
const nodeResultForm = reactive({
  recognizeResult: '',
  appraisalLevel: '',
  restDays: null,
  acceptDate: '',
  resultType: '',
  performAmount: null
})

const NODE_RESULT_FIELDS = {
  INJURY: {
    RECOGNITION: {
      fields: [
        {
          key: 'recognizeResult',
          label: '工伤认定结论',
          type: 'select',
          options: [
            { value: 'RECOGNIZED', label: '已认定（工伤）' },
            { value: 'NOT_RECOGNIZED', label: '不予认定' }
          ]
        }
      ]
    },
    APPRAISAL: {
      fields: [
        {
          key: 'appraisalLevel',
          label: '劳动能力鉴定结论',
          type: 'input',
          placeholder: '如：九级'
        },
        {
          key: 'restDays',
          label: '休假天数',
          type: 'number',
          precision: 1,
          step: 0.5,
          placeholder: '如：9.5'
        }
      ]
    }
  },
  ARBITRATION: {
    FILED: {
      fields: [
        { key: 'acceptDate', label: '受理日期', type: 'date' }
      ]
    },
    AWARD: {
      fields: [
        {
          key: 'resultType',
          label: '裁决 / 调解结果',
          type: 'select',
          options: [
            { value: 'AWARD', label: '裁决结案' },
            { value: 'MEDIATION', label: '调解结案' },
            { value: 'WITHDRAW', label: '撤诉' }
          ]
        }
      ]
    },
    PERFORM: {
      fields: [
        {
          key: 'performAmount',
          label: '履行款',
          type: 'number',
          precision: 2,
          placeholder: '如：128000.00'
        }
      ]
    }
  }
}

/** 当前节点需要填写的结论字段；不需要时为空数组 */
const nodeResultFields = computed(
  () => NODE_RESULT_FIELDS[detail.value.caseType]?.[detail.value.status]?.fields || []
)

/** 用档案已有值预填，避免用户重复录入 */
function syncNodeResultForm() {
  const d = detail.value
  nodeResultForm.recognizeResult = d.injury?.recognizeResult || ''
  nodeResultForm.appraisalLevel =
    d.injury?.appraisalLevel && d.injury.appraisalLevel !== '—' ? d.injury.appraisalLevel : ''
  nodeResultForm.restDays = d.injury?.restDays ?? null
  nodeResultForm.acceptDate = d.arbitration?.acceptDate ? String(d.arbitration.acceptDate).slice(0, 10) : ''
  nodeResultForm.resultType = d.arbitration?.resultType || ''
  nodeResultForm.performAmount = d.arbitration?.performAmount ?? null
}


const subsidyDialog = ref(false)
const subsidyForm = reactive({ key: '', status: 'NOT_APPLIED', amount: 0 })

const editDialog = ref(false)
const editForm = reactive({
  employeeName: '',
  handlerId: '',
  plantArea: '',
  workshopTeam: '',
  /** 案件描述（case_main.description） */
  description: '',
  /** 受伤原因（工伤扩展表，独立于案件描述） */
  injuryReason: '',
  /** 鉴定休假天数：档案里可直接改写，不再只由鉴定节点回填 */
  restDays: null,
  /** 履行款：档案里可直接改写 */
  performAmount: null,
  /** 伤害日期（工伤扩展表），与案件发生日期分开，可单独修正 */
  injuryDate: '',
  occurDate: '',
  leaderL1: '',
  leaderL2: '',
  recognizeResult: '',
  appraisalLevel: '',
  arbitrationNo: ''
})

/** 编辑表单里工伤专有字段（认定情况、鉴定结论）只在工伤案件出现 */
const isInjury = computed(() => detail.value.caseType === 'INJURY')

/** 与后端 update 的校验保持一致：已结案/已归档、无敏感字段权限时不可编辑档案 */
const canEditArchive = computed(() =>
  detail.value.archiveStatus !== 1
  && detail.value.status !== 'CLOSED'
  && detail.value.status !== 'ARCHIVED'
  && !detail.value.sensitiveMasked
)

/** 劳动能力鉴定等级：常见十级，允许直接输入其他结论（如「未达级」） */
const APPRAISAL_LEVELS = ['未鉴定', '一级', '二级', '三级', '四级', '五级',
  '六级', '七级', '八级', '九级', '十级']

const taskDialog = ref(false)
const taskForm = reactive({ taskType: '', assigneeId: '', deadline: '', priority: 'MEDIUM' })

const countersignDialog = ref(false)
const countersignForm = reactive({ nodeCode: 'COUNTERSIGN', opinionType: 'AGREE', opinion: '' })

const versionDialog = ref(false)

/* ------------------ 流程条与节点 ------------------ */
const flowNodes = computed(() => nodes.value.filter((n) => n.flowKey === detail.value.caseType))

/** 调解结案与「裁决/调解」共用同一个展示节点 */
function aliasCode(code) {
  return code === 'MEDIATION' ? 'AWARD' : code
}

/**
 * 当前路径上真正办结的节点。
 *
 * 不能按「序号小于当前节点」判断：跳级推进（如裁决直接到履行）时被跳过的节点
 * 没有办理过，不能显示为已完成；退回某节点后，它及其之后的节点也要撤销完成态。
 * 因此按时间正序重放流转记录：非退回动作把 fromStatus 记为办结，退回则清掉退回到的节点及其后续。
 */
const nodeProgress = computed(() => {
  const order = new Map(flowNodes.value.map((n, i) => [n.statusCode, i]))
  const logs = [...(detail.value.statusLogs || [])].sort((a, b) =>
    String(a.operateTime || '').localeCompare(String(b.operateTime || ''))
  )
  const done = new Set()
  const doneAt = {}
  for (const log of logs) {
    const isReturn = log.action === 'ROLLBACK' || log.action === 'RETURN'
    if (isReturn) {
      const idx = order.get(aliasCode(log.toStatus))
      if (idx === undefined) continue
      for (const code of [...done]) {
        const ci = order.get(code)
        if (ci !== undefined && ci >= idx) {
          done.delete(code)
          // 完成时间一并撤销，否则会残留上一轮的日期，出现「已跳过 + 有日期」的矛盾
          delete doneAt[code]
        }
      }
      continue
    }
    if (!log.fromStatus || log.fromStatus === 'NONE') continue
    const code = aliasCode(log.fromStatus)
    done.add(code)
    doneAt[code] = log.operateTime
  }
  return { done, doneAt }
})

/** 节点是否已办：在当前节点之前，且本次路径上确实办理过 */
function isDone(node, index) {
  if (index >= currentNodeIndex.value) return false
  return nodeProgress.value.done.has(node.statusCode)
}

/** 跳级推进被绕过的节点：显示为「已跳过」而不是已完成 */
function isSkipped(node, index) {
  return index < currentNodeIndex.value && !isDone(node, index)
}

const currentNodeIndex = computed(() => {
  const idx = flowNodes.value.findIndex((n) => n.statusCode === detail.value.status)
  if (idx >= 0) return idx
  // 调解结案并入「裁决/调解」节点展示
  if (detail.value.status === 'MEDIATION') {
    return flowNodes.value.findIndex((n) => n.statusCode === 'AWARD')
  }
  return 0
})

const currentNode = computed(() => flowNodes.value[currentNodeIndex.value] || null)

/** 当前节点变化或切到窄屏时，把步骤条滚到当前节点居中 */
watch(
  () => [detail.value.status, flowNodes.value.length, isNarrow.value],
  () => {
    if (!isNarrow.value) return
    nextTick(centerCurrentStep)
  }
)

const nextNode = computed(() => flowNodes.value[currentNodeIndex.value + 1] || null)

/** 办理记录：按时间正序，退回记录单独标注，被退回的节点不再显示为已完成 */
const timeline = computed(() => {
  const logs = [...(detail.value.statusLogs || [])].reverse()
  const isReturn = (l) => l.action === 'ROLLBACK' || l.action === 'RETURN'
  const reverted = new Set(logs.filter(isReturn).map((l) => l.fromStatus))
  return logs.map((l) => {
    let tagText = '已完成'
    let tagCls = 'ok'
    if (isReturn(l)) {
      tagText = '已退回'
      tagCls = 'danger'
    } else if (reverted.has(l.toStatus)) {
      tagText = '已退回'
      tagCls = 'neutral'
    } else if (l.toStatus === detail.value.status) {
      tagText = '进行中'
      tagCls = 'warn'
    }
    return { ...l, tagText, tagCls, isReturn: isReturn(l) }
  })
})

/** 节点截止日期：后端按「进入日 + 时限工作日」算好下发，前端只做展示 */
const nodeDeadlineMap = computed(() => {
  const map = {}
  for (const item of detail.value.nodeDeadlines || []) map[item.statusCode] = item
  return map
})

function nodeDeadline(node) {
  return nodeDeadlineMap.value[node.statusCode] || null
}

/** 当前节点的截止信息，用于办理区提示 */
const currentDue = computed(() => {
  const dl = nodeDeadlineMap.value[detail.value.status]
  if (!dl || dl.state === 'DONE') return null
  return dl
})

/** 当前节点是否已「提交」开启计时：开启后才开始算超期 */
const nodeOpened = computed(() => !!nodeDeadlineMap.value[detail.value.status]?.started)

const submitTitle = computed(() => {
  if (nodeOpened.value) return '当前节点已开启计时'
  if (!currentDue.value) return '该节点未配置时限，无需开启计时'
  return '提交即开启本节点计时，之后按截止日期判断是否超期'
})

function shortDate(date) {
  return date ? String(date).slice(5) : '—'
}

function dueText(due) {
  if (!due) return ''
  if (due.state === 'OVERDUE') return `已超期 ${Math.abs(due.remainDays || 0)} 天`
  const days = due.remainDays ?? 0
  if (due.state === 'DUE_SOON') return `剩余 ${days} 天 · 临近超期`
  return `剩余 ${days} 天`
}

function nodeDueClass(node, index) {
  const dl = nodeDeadline(node)
  if (!dl || index !== currentNodeIndex.value) return ''
  if (!dl.started) return { idle: true }
  return { 'due-soon': dl.state === 'DUE_SOON', overdue: dl.state === 'OVERDUE' }
}

/* ------------------ 办理日期与截止日期 ------------------ */
/** 办理区里可编辑的办理日期，默认当天；修改后由后端重算截止日期 */
const handleDateInput = ref('')

function todayStr() {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

/** 办理区里可编辑的截止日期，默认取后端按时限算出的值 */
const deadlineInput = ref('')
const deadlineSaving = ref(false)

/** 后端按时限推算的「应到」截止日期，用于提示人工调整的偏差 */
const autoDeadline = computed(() => nodeDeadlineMap.value[detail.value.status]?.autoDate || '')

const deadlineHint = computed(() => {
  if (!deadlineInput.value) return '该节点未配置时限'
  if (!nodeOpened.value) return '未开启计时'
  const days = diffDays(deadlineInput.value)
  const base = days >= 0 ? `剩余 ${days} 天` : `已超期 ${-days} 天`
  if (autoDeadline.value && deadlineInput.value !== autoDeadline.value) {
    return `已手动调整 · 按时限应为 ${autoDeadline.value} · ${base}`
  }
  return base
})

const deadlineHintClass = computed(() => {
  if (!deadlineInput.value) return ''
  const days = diffDays(deadlineInput.value)
  if (days < 0) return 'danger'
  if (days < 7) return 'warn'
  return ''
})

function diffDays(date) {
  if (!date) return 0
  const target = new Date(`${date}T00:00:00`)
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return Math.round((target - today) / 86400000)
}

/** 案件详情刷新或节点切换后，把服务端的截止日期同步进输入框 */
function syncDeadlineInput() {
  const dl = nodeDeadlineMap.value[detail.value.status]
  const next = dl?.deadlineDate || ''
  if (next !== deadlineInput.value) deadlineInput.value = next
}

/** 详情刷新或节点切换后同步办理日期；历史节点没有办理日期时默认显示当天 */
function syncHandleDateInput() {
  const dl = nodeDeadlineMap.value[detail.value.status]
  const next = dl?.handleDate || todayStr()
  if (next !== handleDateInput.value) handleDateInput.value = next
}

watch([() => detail.value.status, () => detail.value.nodeDeadlines], () => {
  syncDeadlineInput()
  syncHandleDateInput()
}, { immediate: true, deep: true })

/** 保存办理日期：后端按「办理日期 + 节点时限（工作日，含节假日）」重算截止日期 */
async function saveHandleDate(value) {
  if (!value) return
  deadlineSaving.value = true
  try {
    const res = await caseApi.adjustDeadline(caseId, {
      nodeCode: detail.value.status,
      handleDate: value
    })
    patchLocalDeadline(res)
    ElMessage.success(`办理日期已更新为 ${value}，截止日期已重算`)
  } catch (e) {
    handleDateInput.value = nodeDeadlineMap.value[detail.value.status]?.handleDate || todayStr()
    throw e
  } finally {
    deadlineSaving.value = false
  }
}

/** 保存人工调整的截止日期：只改当前节点的期限，不影响其他节点 */
async function saveDeadline(value) {
  if (!value) return
  const before = nodeDeadlineMap.value[detail.value.status]?.deadlineDate || ''
  if (value === before) return
  deadlineSaving.value = true
  try {
    const res = await caseApi.adjustDeadline(caseId, { nodeCode: detail.value.status, deadlineDate: value })
    patchLocalDeadline(res)
    ElMessage.success(`截止日期已更新为 ${value}`)
  } catch (e) {
    deadlineInput.value = before
    throw e
  } finally {
    deadlineSaving.value = false
  }
}

/** 恢复为按节点时限推算的日期 */
async function resetDeadline() {
  if (autoDeadline.value) await saveDeadline(autoDeadline.value)
}

/** 接口已落库，本地同步一份，避免整页重载把正在填写的办理意见清空 */
function patchLocalDeadline(dl) {
  if (!dl) return
  const list = detail.value.nodeDeadlines || []
  const idx = list.findIndex((d) => d.statusCode === dl.statusCode)
  if (idx >= 0) list.splice(idx, 1, { ...list[idx], ...dl })
  else list.push(dl)
  detail.value.nodeDeadlines = [...list]
  deadlineInput.value = dl.deadlineDate || deadlineInput.value
}

function nodeTime(node, index) {
  const due = nodeDeadline(node)
  if (index < currentNodeIndex.value) {
    const at = nodeProgress.value.doneAt[node.statusCode]
    // 只有真正办理过的节点才显示办结日期，被跳过的显示「已跳过」
    return at ? String(at).slice(5, 10) : '已跳过'
  }
  if (index === currentNodeIndex.value) {
    return due ? `截止 ${shortDate(due.deadlineDate)}` : '办理中'
  }
  return node.timeLimit || '—'
}

/* ------------------ 档案与金额 ------------------ */

/**
 * 档案卡片用 v-html 渲染以保留 <small> 小字，所以任何来自后端的文本都必须先转义。
 * 受伤原因、争议事由等字段是用户自由输入，直接拼接等同于开放存储型 XSS。
 */
function esc(value) {
  return String(value == null ? '' : value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

const archiveCells = computed(() => {
  const d = detail.value
  const injury = d.injury || {}
  const arb = d.arbitration || {}
  if (d.caseType === 'INJURY') {
    return [
      { k: '厂区', v: esc(d.plantArea || '—') },
      { k: '车间班组', v: esc(d.workshopTeam || '—') },
      { k: '姓名', v: esc(d.employeeName || '—') },
      { k: '伤害日期', v: esc(injury.injuryDate || d.occurDate || '—') },
      { k: '发生日期', v: esc(d.occurDate || '—') },
      { k: '受伤原因', v: esc(injury.injuryReason || d.description || '—') },
      { k: '小班长', v: esc(d.leaderL1 || '—') },
      { k: '大班长', v: esc(d.leaderL2 || '—') },
      { k: '工伤认定情况', v: recTag(injury.recognizeResult) },
      {
        k: '劳动能力鉴定结论',
        v: injury.appraisalLevel && injury.appraisalLevel !== '—'
          ? `${esc(injury.appraisalLevel)} 伤残 · 已出具结论`
          : '<span style="color:var(--gsms-text-3)">待鉴定</span>'
      },
      {
        k: '休假天数',
        v: injury.restDays !== null && injury.restDays !== undefined && injury.restDays !== ''
          ? `${esc(String(injury.restDays))} 天`
          : '<span style="color:var(--gsms-text-3)">待登记</span>'
      },
      { k: '案件描述', v: esc(d.description || '—'), span: true }
    ]
  }
  return [
    { k: '厂区', v: esc(d.plantArea || '—') },
    { k: '部门班组', v: esc(d.workshopTeam || d.departmentName || '—') },
    { k: '当事人', v: esc(d.employeeName || '—') },
    { k: '发生日期', v: esc(d.occurDate || '—') },
    { k: '直属主管', v: esc(d.leaderL1 || '—') },
    { k: '仲裁案号', v: esc(arb.arbitrationNo || '—') },
    { k: '受理情况', v: acceptTag(arb.acceptDate) },
    { k: '裁决 / 调解结果', v: arb.resultType ? esc(resultName(arb.resultType)) : '待开庭' },
    { k: '履行款', v: performMoney(arb.performAmount) },
    { k: '案件描述', v: esc(d.description || '—'), span: true }
  ]
})

function recTag(code) {
  if (!code) return '<span style="color:var(--gsms-text-3)">未申报</span>'
  const name = RECOGNIZE_NAME[code] || code
  const cls = code === 'RECOGNIZED' ? 'ok' : code === 'NOT_RECOGNIZED' ? 'danger' : 'warn'
  return `<span class="tag ${cls}">${esc(name)}</span>`
}

function performMoney(value) {
  if (detail.value.sensitiveMasked) return '<span style="color:var(--gsms-text-3)">无权限查看</span>'
  if (value === null || value === undefined || value === '') return '<span style="color:var(--gsms-text-3)">待登记</span>'
  return `￥${Number(value).toLocaleString()}`
}

/** 仲裁受理情况：与工伤认定是两回事，不能复用认定结论文案 */
function acceptTag(date) {
  if (!date) return '<span style="color:var(--gsms-text-3)">待受理</span>'
  return `<span class="tag ok">已受理</span><small>${esc(String(date).slice(0, 10))}</small>`
}

function resultName(code) {
  if (code === 'AWARD') return '裁决结案'
  if (code === 'MEDIATION') return '调解结案'
  if (code === 'WITHDRAW') return '撤诉'
  return code
}

const totalSubsidy = computed(() => {
  const i = detail.value.injury || {}
  const sum = Number(i.subsidyDisabilityAmount || 0) + Number(i.subsidyMedicalAmount || 0) + Number(i.subsidyEmploymentAmount || 0)
  if (detail.value.sensitiveMasked) return '无权限查看'
  return sum ? `￥${sum.toLocaleString()}` : '￥0'
})

function subsidyAmount(item) {
  if (detail.value.sensitiveMasked) return '无权限查看'
  const v = Number(detail.value.injury?.[item.amountField] || 0)
  return v ? `￥${v.toLocaleString()}` : '—'
}

function subsidyName(code) {
  return SUBSIDY_STATUS.find((s) => s.value === code)?.label || '未申领'
}

function subsidyClass(code) {
  if (code === 'RECEIVED') return 'ok'
  if (code === 'APPLYING') return 'warn'
  if (code === 'INELIGIBLE') return 'danger'
  return 'neutral'
}

/* ------------------ 时限 ------------------ */
const elapsedDays = computed(() => {
  if (!detail.value.occurDate) return 0
  const start = new Date(detail.value.occurDate)
  return Math.max(1, Math.round((Date.now() - start.getTime()) / 86400000))
})

const currentDeadline = computed(() => {
  const list = (caseDeadlines.value || []).filter(
    (d) => d.status !== 'DONE' && d.nodeName === detail.value.statusName
  )
  return list[0] || (caseDeadlines.value || []).find((d) => d.status !== 'DONE') || null
})

const timerText = computed(() => {
  // 优先用流程节点口径：未开启的节点不显示剩余天数，避免误导为已开始计时
  const due = currentDue.value
  if (due) {
    if (!due.started) return '未开启'
    if (due.remainDays < 0) return `超期 ${-due.remainDays} 天`
    return `${due.remainDays} 天`
  }
  const d = currentDeadline.value
  if (!d) return '未设置'
  if (d.remainDays == null) return '未开启'
  if (d.remainDays < 0) return `超期 ${-d.remainDays} 天`
  return `${d.remainDays} 天`
})

const timerColor = computed(() => {
  const due = currentDue.value
  if (due) {
    if (!due.started) return 'var(--gsms-text-3)'
    if (due.remainDays < 0) return 'var(--gsms-danger)'
    return due.remainDays <= 3 ? 'var(--gsms-warning)' : 'var(--gsms-text)'
  }
  const d = currentDeadline.value
  if (!d) return 'var(--gsms-text-3)'
  if (d.remainDays == null) return 'var(--gsms-text-3)'
  if (d.remainDays < 0) return 'var(--gsms-danger)'
  if (d.remainDays <= 3) return 'var(--gsms-warning)'
  return 'var(--gsms-text)'
})

/* ------------------ 产出文书与责任人 ------------------ */
const outputMaterials = computed(() => {
  const material = currentNode.value?.keyMaterial
  if (!material) return []
  return material.split(/[、,，]/).map((s) => s.trim()).filter(Boolean)
})

const archiveManagers = computed(() =>
  (detail.value.members || []).filter((m) => m.memberRole === 'CO_HANDLER' || m.memberRole === 'COUNTERSIGN').slice(0, 2)
)

/* ------------------ 动作 ------------------ */
const actions = computed(() => detail.value.actions || [])

/* 归档必须走 archive 接口（要写 archive_status），不能当成一次普通流转 */
const submitAction = computed(() => actions.value.find((a) => a.code === 'SUBMIT') || null)

const closeAction = computed(
  () => actions.value.find((a) => a.code === 'CLOSE') || actions.value.find((a) => a.code === 'ARCHIVE') || null
)

/**
 * 可选推进目标：支持跳过中间节点（如立案直接到认定），由后端按流程拓扑下发。
 * 兜底：后端未返回时退回按直接后继动作组装，避免旧接口下按钮消失。
 */
const forwardTargets = computed(() => {
  if ((detail.value.forwardTargets || []).length) return detail.value.forwardTargets
  return actions.value
    .filter((a) => a.code.startsWith('TRANSIT:') && a.code !== 'TRANSIT:ARCHIVED')
    .map((a) => ({ code: a.code.split(':')[1], name: a.name.replace(/^推进至/, '') }))
})

/** 可选退回目标：可回到更早节点（如争议处理退回立案） */
const rollbackTargets = computed(() => detail.value.rollbackTargets || [])

const primaryActionText = computed(() => {
  if (submitAction.value) return '提交立案'
  const code = closeAction.value?.code || ''
  if (code === 'CLOSE') return '结案归档 →'
  if (code === 'ARCHIVE') return '归档 →'
  return '推进至下一节点 →'
})

const primaryDisabled = computed(
  () => !submitAction.value && !closeAction.value && !forwardTargets.value.length
)

/** 「提交」入口：已归档、已开启或本节点未配置时限时不可用 */
const quickSubmitDisabled = computed(
  () => detail.value.archiveStatus === 1 || nodeOpened.value || !currentDue.value
)

/** 弹窗中跳过的中间节点名，用于提示跳级推进的影响 */
const skippedNodeNames = computed(() => {
  if (pendingKind.value !== 'TRANSIT' || !pendingTarget.value) return []
  const from = currentNodeIndex.value
  const to = flowNodes.value.findIndex((n) => n.statusCode === pendingTarget.value)
  if (to <= from + 1) return []
  return flowNodes.value.slice(from + 1, to).map((n) => n.nodeName)
})

const canRollback = computed(() => rollbackTargets.value.length > 0)

/* ------------------ 交互 ------------------ */
function priorityClass(p) {
  return p === 'HIGH' ? 'danger' : p === 'LOW' ? 'neutral' : 'warn'
}

function priorityText(p) {
  return p === 'HIGH' ? '高' : p === 'LOW' ? '低' : '中'
}

function deadlineClass(status) {
  if (status === 'OVERDUE') return 'danger'
  if (status === 'DONE') return 'ok'
  if (status === 'REMINDED') return 'warn'
  return 'neutral'
}

function formatSize(size) {
  if (!size) return '-'
  if (size < 1024) return size + ' B'
  if (size < 1024 * 1024) return (size / 1024).toFixed(1) + ' KB'
  return (size / 1024 / 1024).toFixed(2) + ' MB'
}

function fileExt(name) {
  const ext = (name || '').split('.').pop()
  return ext ? ext.slice(0, 4).toUpperCase() : 'FILE'
}

/** 台账/详情用「—」占位空值，编辑时还原成空串，避免把占位符写回库里 */
function blankDash(value) {
  return value && value !== '—' ? value : ''
}

async function loadDetail() {
  loading.value = true
  try {
    detail.value = await caseApi.detail(caseId)
    Object.assign(editForm, {
      employeeName: detail.value.employeeName || '',
      handlerId: detail.value.handlerId || '',
      plantArea: blankDash(detail.value.plantArea),
      workshopTeam: blankDash(detail.value.workshopTeam),
      description: detail.value.description || '',
      injuryReason: detail.value.injury?.injuryReason || '',
      restDays: detail.value.injury?.restDays ?? null,
      performAmount: detail.value.arbitration?.performAmount ?? null,
      injuryDate: detail.value.injury?.injuryDate
        ? String(detail.value.injury.injuryDate).slice(0, 10)
        : detail.value.occurDate || '',
      occurDate: detail.value.occurDate || '',
      leaderL1: blankDash(detail.value.leaderL1),
      leaderL2: blankDash(detail.value.leaderL2),
      recognizeResult: detail.value.injury?.recognizeResult || '',
      appraisalLevel: blankDash(detail.value.injury?.appraisalLevel),
      arbitrationNo: detail.value.arbitration?.arbitrationNo || ''
    })
    syncNodeResultForm()
  } finally {
    loading.value = false
  }
}

async function loadDocs() {
  documents.value = (await docApi.list(caseId)) || []
}

async function loadTasks() {
  tasks.value = (await collabApi.tasks(caseId)) || []
}

async function loadCountersigns() {
  countersigns.value = (await collabApi.countersigns(caseId)) || []
}

async function loadDeadlines() {
  const data = await deadlineApi.page({ caseId, pageNum: 1, pageSize: 50 })
  caseDeadlines.value = data.list || []
}

function onTabChange(name) {
  if (name === 'documents') loadDocs()
  if (name === 'tasks') loadTasks()
  if (name === 'countersign') loadCountersigns()
  if (name === 'deadlines') loadDeadlines()
}

function runAction(action) {
  if (!action) return
  if (action.code === 'ARCHIVE') {
    ElMessageBox.confirm('归档后案件将变为只读，确认归档？', '归档确认', { type: 'warning' })
      .then(async () => {
        await caseApi.archive(caseId)
        ElMessage.success('已归档')
        loadDetail()
      })
      .catch(() => {})
    return
  }
  if (action.code === 'CLOSE') {
    closeForm.opinion = ''
    closeDialog.value = true
    return
  }
  if (action.code === 'SUBMIT') {
    caseApi.submit(caseId, opinionText.value || '提交立案').then(() => {
      ElMessage.success('已提交立案')
      opinionText.value = ''
      loadDetail()
      loadDeadlines()
    })
    return
  }
  // 流转/退回都支持自选目标节点，统一走弹窗
  if (action.code === 'ROLLBACK') {
    openRollback()
    return
  }
  if (action.code.startsWith('TRANSIT:')) {
    pendingKind.value = 'TRANSIT'
    pendingTarget.value = action.code.split(':')[1]
    opinionTitle.value = '推进节点'
    opinionDialog.value = true
  }
}

/** 打开「推进」弹窗：默认停在最近的下一节点，可下拉改选更靠后的节点实现跳级 */
function openTransition() {
  if (!forwardTargets.value.length) return
  pendingKind.value = 'TRANSIT'
  pendingTarget.value = forwardTargets.value[0].code
  opinionTitle.value = '推进节点'
  opinionDialog.value = true
}

/** 打开「退回」弹窗：默认上一节点，可改选更早节点 */
function openRollback() {
  if (!rollbackTargets.value.length) return
  pendingKind.value = 'ROLLBACK'
  pendingTarget.value = rollbackTargets.value[0].code
  opinionTitle.value = '退回节点'
  opinionDialog.value = true
}

/** 顶部主按钮：提交/结案/归档走原动作，其余打开推进弹窗 */
function onPrimaryAction() {
  if (submitAction.value) {
    runAction(submitAction.value)
    return
  }
  if (closeAction.value) {
    runAction(closeAction.value)
    return
  }
  openTransition()
}

/** 流转载荷：本节点结论随流转提交，后端据此回填档案 */
function buildTransitionPayload(toStatus) {
  const payload = { toStatus, opinion: opinionText.value }
  for (const field of nodeResultFields.value) {
    const value = nodeResultForm[field.key]
    if (value === '' || value === null || value === undefined) continue
    payload[field.key] = value
  }
  return payload
}

/**
 * 「提交」：开启当前节点计时，不流转到下一节点。
 * 开启后本节点才按截止日期判断是否超期；流转仍用右侧「推进至下一节点」。
 */
async function quickSubmit() {
  if (quickSubmitDisabled.value) return
  if (nodeOpened.value) {
    ElMessage.info('当前节点已开启计时')
    return
  }
  submitting.value = true
  try {
    const res = await caseApi.openNode(caseId, detail.value.status)
    patchLocalDeadline(res)
    opinionText.value = ''
    ElMessage.success(`已开启「${detail.value.statusName}」节点计时，仍可继续推进至下一节点`)
  } finally {
    submitting.value = false
  }
}

async function confirmOpinion() {
  submitting.value = true
  try {
    if (pendingKind.value === 'ROLLBACK') {
      await caseApi.rollback(caseId, opinionText.value || '退回节点', pendingTarget.value)
    } else {
      await caseApi.transition(caseId, buildTransitionPayload(pendingTarget.value))
    }
    ElMessage.success('操作成功')
    opinionDialog.value = false
    opinionText.value = ''
    await loadDetail()
    await loadDeadlines()
  } finally {
    submitting.value = false
  }
}

async function confirmClose() {
  submitting.value = true
  try {
    // 履行节点结案时，办理区填写的履行款随结案提交
    const payload = { ...closeForm }
    if (detail.value.status === 'PERFORM' && nodeResultForm.performAmount !== null) {
      payload.performAmount = nodeResultForm.performAmount
    }
    await caseApi.close(caseId, payload)
    ElMessage.success('案件已结案')
    closeDialog.value = false
    await loadDetail()
    await loadDeadlines()
  } finally {
    submitting.value = false
  }
}

/** 删除案件：软删除进入回收站，可在回收站恢复 */
async function removeCase() {
  await ElMessageBox.confirm(
    `确认删除案件「${detail.value.caseNo}」？删除后进入回收站，可在回收站恢复。`,
    '删除案件',
    { type: 'warning', confirmButtonText: '确认删除' }
  )
  await caseApi.remove(caseId)
  ElMessage.success('案件已移入回收站')
  // 删除后也回来源列表，保持用户所在的模块不变
  router.replace(fromQuery.value || (fromArchive.value ? '/archive' : '/cases'))
}

function generateDoc() {
  const material = currentNode.value?.keyMaterial
  ElMessage.info(material ? `本节点应产出：${material}（文书模板生成待接入）` : '当前节点未配置产出文书')
}

async function supervise() {
  const d = currentDeadline.value
  if (!d) {
    ElMessage.warning('当前节点未设置期限记录')
    return
  }
  const { value } = await ElMessageBox.prompt('请输入督办意见', '督办', { inputType: 'textarea' })
  await deadlineApi.supervise(d.id, value)
  ElMessage.success('督办已发送')
  loadDeadlines()
}

async function registerReason() {
  const d = currentDeadline.value
  if (!d) {
    ElMessage.warning('当前节点未设置期限记录')
    return
  }
  const { value } = await ElMessageBox.prompt('请填写超期或延期原因', '登记原因', { inputType: 'textarea' })
  await deadlineApi.registerReason(d.id, value)
  ElMessage.success('已登记')
  loadDeadlines()
}

function openSubsidy(item) {
  subsidyForm.key = item.key
  subsidyForm.status = detail.value.injury?.[item.statusField] || 'NOT_APPLIED'
  subsidyForm.amount = Number(detail.value.injury?.[item.amountField] || 0)
  subsidyDialog.value = true
}

async function saveSubsidy() {
  submitting.value = true
  try {
    await ledgerApi.updateSubsidy(caseId, { ...subsidyForm })
    ElMessage.success('申领进度已更新')
    subsidyDialog.value = false
    await loadDetail()
  } finally {
    submitting.value = false
  }
}

function needFullPayload() {
  // 更新接口会重建扩展表，injury / arbitration 必须是完整对象，否则未回传的字段会被清零
  const d = detail.value
  const handlerChanged = editForm.handlerId && editForm.handlerId !== d.handlerId

  let arbitration = d.arbitration
  if (!isInjury.value) {
    const performAmount = editForm.performAmount === '' ? null : editForm.performAmount
    if (arbitration) {
      arbitration = Object.assign({}, arbitration, {
        arbitrationNo: editForm.arbitrationNo || null,
        performAmount
      })
    } else if (editForm.arbitrationNo || performAmount !== null) {
      // 案件原本没有仲裁扩展记录：填了案号或履行款才创建，避免凭空生成一条空记录
      arbitration = { arbitrationNo: editForm.arbitrationNo || null, performAmount }
    }
  }

  return {
    caseType: d.caseType,
    caseName: d.caseName,
    employeeId: d.employeeId,
    employeeName: editForm.employeeName,
    departmentId: d.departmentId,
    departmentName: d.departmentName,
    plantArea: editForm.plantArea,
    workshopTeam: editForm.workshopTeam,
    leaderL1: editForm.leaderL1,
    leaderL2: editForm.leaderL2,
    occurDate: editForm.occurDate || d.occurDate,
    description: editForm.description,
    // 改承办人时负责人跟随，避免出现「负责人仍是旧承办人」的错位
    ownerId: handlerChanged ? editForm.handlerId : d.ownerId,
    handlerId: editForm.handlerId || d.handlerId,
    injury: isInjury.value
      ? Object.assign({}, d.injury || {}, {
          injuryDate: editForm.injuryDate || d.injury?.injuryDate || d.occurDate,
          injuryReason: editForm.injuryReason || d.injury?.injuryReason || null,
          restDays: editForm.restDays === '' ? null : editForm.restDays,
          recognizeResult: editForm.recognizeResult || null,
          appraisalLevel: editForm.appraisalLevel || null
        })
      : d.injury,
    arbitration
  }
}

async function saveEdit() {
  // 更新接口会重建扩展表，金额字段必须是完整值；无权限账号不开放编辑以免清零
  if (detail.value.sensitiveMasked) {
    ElMessage.warning('当前账号无敏感字段权限，不能编辑档案')
    return
  }
  submitting.value = true
  try {
    await caseApi.update(caseId, needFullPayload())
    ElMessage.success('档案已保存')
    editDialog.value = false
    await loadDetail()
  } finally {
    submitting.value = false
  }
}

async function uploadDoc(options) {
  const formData = new FormData()
  formData.append('file', options.file)
  formData.append('docType', 'EVIDENCE')
  formData.append('sensitiveFlag', '0')
  try {
    await docApi.upload(caseId, formData)
    ElMessage.success('上传成功')
    loadDocs()
  } catch (e) {
    /* 拦截器已提示 */
  }
}

async function previewDoc(row) {
  try {
    const response = await docApi.preview(row.id)
    const type = response.headers['content-type'] || 'application/octet-stream'
    if (type.includes('pdf') || type.includes('image')) {
      const blob = new Blob([response.data], { type })
      const url = window.URL.createObjectURL(blob)
      window.open(url, '_blank')
      setTimeout(() => window.URL.revokeObjectURL(url), 60000)
    } else {
      ElMessage.info('该格式不支持在线预览，请下载查看')
    }
  } catch (e) {
    /* ignore */
  }
}

async function downloadDoc(row) {
  try {
    const response = await docApi.download(row.id)
    const blob = new Blob([response.data])
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = row.docName
    link.click()
    window.URL.revokeObjectURL(url)
    ElMessage.success('已下载')
  } catch (e) {
    /* ignore */
  }
}

async function showVersions(row) {
  versionList.value = (await docApi.versions(row.id)) || []
  versionDialog.value = true
}

async function removeDoc(row) {
  await ElMessageBox.confirm(`确认删除文档「${row.docName}」？`, '删除确认', { type: 'warning' })
  await docApi.remove(row.id)
  ElMessage.success('已删除')
  loadDocs()
}

async function submitTask() {
  if (!taskForm.taskType || !taskForm.assigneeId || !taskForm.deadline) {
    ElMessage.warning('请填写任务内容、责任人与截止时间')
    return
  }
  await collabApi.createTask(caseId, { ...taskForm })
  ElMessage.success('任务已分派')
  taskDialog.value = false
  taskForm.taskType = ''
  loadTasks()
}

async function finishTask(row) {
  const { value } = await ElMessageBox.prompt('请输入完成结果', '完成任务', {
    inputType: 'textarea',
    inputPlaceholder: '简述完成情况'
  })
  await collabApi.finishTask(row.id, value)
  ElMessage.success('任务已完成')
  loadTasks()
}

async function initiateCountersign() {
  const count = await collabApi.initiateCountersign(caseId, 'COUNTERSIGN')
  ElMessage.success(`已发起会签，新增 ${count} 条待会签`)
  loadCountersigns()
}

async function submitCountersign() {
  if (!countersignForm.opinion) {
    ElMessage.warning('请填写会签意见')
    return
  }
  await collabApi.submitCountersign(caseId, { ...countersignForm })
  ElMessage.success('会签意见已提交')
  countersignDialog.value = false
  loadCountersigns()
}

/** 关联案件的人员/组织信息，缺项自动跳过 */
function relatedWho(rel) {
  return [rel.relatedEmployeeName, rel.relatedPlantArea, rel.relatedWorkshopTeam || rel.relatedDepartmentName]
    .filter((v) => v && v !== '—')
    .join(' · ') || '—'
}

async function removeRelation(row) {
  await ElMessageBox.confirm('确认解除该关联关系？', '解除确认', { type: 'warning' })
  await caseApi.removeRelation(caseId, row.id)
  ElMessage.success('已解除')
  loadDetail()
}

onMounted(async () => {
  systemApi
    .dictByType('plant_area')
    .then((list) => {
      areas.value = list || []
    })
    .catch(() => {
      areas.value = []
    })
  await loadDetail()
  nextTick(centerCurrentStep)
  loadDocs()
  loadDeadlines()
  try {
    const [userList, nodeList] = await Promise.all([systemApi.userOptions(), workflowApi.nodes()])
    users.value = userList || []
    nodes.value = nodeList || []
  } catch (e) {
    /* ignore */
  }
})
</script>

<style scoped>
.detail-head {
  padding: 16px 18px;
}

.dh-top {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.dh-top .no {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 0.3px;
}

.dh-actions {
  margin-left: auto;
  display: flex;
  gap: 8px;
}

.dh-sub {
  font-size: 12.5px;
  color: var(--gsms-text-3);
  margin-top: 6px;
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
}

.dh-sub b {
  color: var(--gsms-text-sub);
  font-weight: 600;
}

/* 窄屏把标签改成上置，这里保持标签正常字形与间距 */
.dh-sub i {
  font-style: normal;
  margin-right: 4px;
}

.hr {
  height: 1px;
  background: var(--gsms-line-2);
  margin: 14px 0;
}

.node-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 14px;
}

.node-actions .right {
  margin-left: auto;
  display: flex;
  gap: 8px;
}

/* 提交：主线操作的轻量入口，视觉上弱于右侧主按钮 */
.node-actions .act-submit {
  border-color: var(--gsms-primary);
  color: var(--gsms-primary);
  background: #fff;
}

.node-actions .act-submit:not(:disabled):hover {
  background: var(--gsms-primary-soft, rgba(37, 99, 235, 0.08));
}

/* 截止日期提示：临近超期/已超期着色，与期限中心口径一致 */
.node-result .hint.warn {
  color: var(--gsms-warning);
}

.node-result .hint.danger {
  color: var(--gsms-danger);
}

.deadline-reset {
  font-size: 12px;
  padding: 0;
  height: auto;
}

.upload-full {
  width: 100%;
}

/* 跳级推进时提示被跳过的中间节点 */
.skip-tip {
  margin-top: 4px;
  font-size: 12px;
  color: var(--gsms-warning);
}

.upload-full :deep(.el-upload) {
  width: 100%;
}

.upload-full :deep(.el-upload-dragger) {
  padding: 14px;
  border: 1px dashed #ccd7e8;
  border-radius: 9px;
  background: #fbfcff;
}

.upload-tip {
  font-size: 12.2px;
  color: var(--gsms-text-3);
}

.upload-tip b {
  color: var(--gsms-primary);
  font-weight: 600;
}

/* 本节点结论行：控件 + 回填说明 */
.node-result {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.node-result .hint {
  font-size: 11.8px;
  color: var(--gsms-text-3);
}

/* 编辑档案：两列表单，标签在上、控件等宽，与档案卡片的阅读顺序一致 */
.arc-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  column-gap: 14px;
}

.arc-grid .span-2 {
  grid-column: 1 / -1;
}

.arc-form :deep(.el-form-item) {
  margin-bottom: 16px;
}

.arc-form :deep(.el-form-item__label) {
  font-size: 12.5px;
  font-weight: 400;
  color: var(--gsms-text-sub);
  padding: 0 0 6px;
  line-height: 1.5;
}

.arc-form :deep(.el-input__wrapper),
.arc-form :deep(.el-select__wrapper) {
  min-height: 36px;
}

/* ---------------- 节点截止日期与提交 ---------------- */
.sx .tm.due-soon {
  color: var(--gsms-warning);
  font-weight: 600;
}

.sx .tm.overdue {
  color: var(--gsms-danger);
  font-weight: 600;
}

/* 未开启计时的节点：只显示截止日期，不着色告警 */
.sx .tm.idle {
  color: var(--gsms-text-3);
}

.pill.due.idle {
  color: var(--gsms-text-3);
  background: #f4f6fa;
}

.pill.due.warn {
  color: var(--gsms-warning);
  background: var(--gsms-warn-soft);
}

.pill.due.danger {
  color: var(--gsms-danger);
  background: var(--gsms-danger-soft);
}

.arc-note {
  font-size: 11.6px;
  color: var(--gsms-text-3);
  margin-top: 10px;
  line-height: 1.6;
}

/* ------- 争议关联案件卡片 ------- */
.rel-list {
  padding-top: 6px;
}

.rel-item {
  padding: 8px 0;
  border-bottom: 1px dashed var(--gsms-line-2);
}

.rel-item:first-child {
  padding-top: 2px;
}

.rel-item:last-child {
  border-bottom: 0;
  padding-bottom: 2px;
}

.rel-hd {
  display: flex;
  align-items: center;
  gap: 6px;
}

.rel-no {
  font-size: 12.4px;
  font-weight: 600;
}

.rel-del {
  margin-left: auto;
}

.rel-name {
  font-size: 12.4px;
  line-height: 1.5;
  margin-top: 4px;
}

.rel-who {
  font-size: 11.6px;
  color: var(--gsms-text-3);
  margin-top: 2px;
}

.rel-desc {
  font-size: 11.8px;
  color: var(--gsms-text-sub);
  line-height: 1.5;
  margin-top: 4px;
}

.rel-meta {
  display: flex;
  justify-content: space-between;
  gap: 6px;
  flex-wrap: wrap;
  font-size: 11.2px;
  color: var(--gsms-text-3);
  margin-top: 4px;
}

.mini {
  font-size: 11.5px;
  color: var(--gsms-text-3);
}

.detail-tabs {
  padding: 0 18px 14px;
}

.detail-tabs :deep(.el-tabs__header) {
  margin-bottom: 4px;
}

.detail-tabs :deep(.el-tabs__item) {
  font-size: 13px;
}

.pane {
  padding: 12px 2px 4px;
}

.toolbar-inline {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 12px;
}

.log-title {
  font-size: 13.5px;
}

.log-opinion {
  margin-top: 5px;
  font-size: 12.5px;
  color: var(--gsms-text-sub);
  background: #f7f9fd;
  padding: 6px 10px;
  border-radius: 6px;
  display: inline-block;
}

/* ---------------- 移动端：详情页重排 ---------------- */
@media (max-width: 768px) {
  .detail-head {
    padding: 12px 12px 10px;
  }

  /* 案号独占一行，类型/状态标签紧随其后排在同一行内 */
  .dh-top {
    gap: 6px;
  }

  .dh-top .no {
    width: 100%;
    font-size: 14px;
  }

  /* 主操作整行置顶，次要操作三等分一行，按钮不再大小不一 */
  .dh-actions {
    margin-left: 0;
    width: 100%;
    margin-top: 10px;
    gap: 8px;
    flex-wrap: wrap;
  }

  .dh-actions .el-button {
    flex: 1 1 0;
    min-width: 0;
    margin-left: 0;
    padding-left: 4px;
    padding-right: 4px;
  }

  .dh-actions .act-go {
    order: -1;
    flex: 1 1 100%;
  }

  /*
   * 窄屏隐藏头部的「归档 →」：底部「当前节点办理」中已有同名按钮，
   * 顶部再占一整行只会挤压案号与信息区，反而更难找入口。
   */
  .dh-actions .act-archive {
    display: none;
  }

  /* 信息区两列，标签在上、值在下，避免长值被挤成竖排 */
  .dh-sub {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 8px 10px;
    margin-top: 10px;
    font-size: 12px;
  }

  .dh-sub span {
    display: flex;
    flex-direction: column;
    gap: 1px;
    min-width: 0;
  }

  .dh-sub i {
    font-style: normal;
    font-size: 11px;
    color: var(--gsms-text-3);
  }

  /* 厂区·班组这类长值最多两行，避免单行省略号把关键信息截掉 */
  .dh-sub b {
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
    font-size: 13px;
    line-height: 1.35;
  }

  .hr {
    margin: 10px 0;
  }

  /* 步骤条紧凑化并隐藏滚动条，进入页面自动滚到当前节点 */
  .steps {
    padding: 2px 0;
    scroll-snap-type: x proximity;
    scrollbar-width: none;
  }

  .steps::-webkit-scrollbar {
    display: none;
  }

  .steps .sx {
    flex: 0 0 66px;
    min-width: 66px;
    padding-top: 24px;
    scroll-snap-align: center;
  }

  .steps .sx::before {
    top: 8px;
  }

  .steps .sx .d {
    width: 18px;
    height: 18px;
    font-size: 9.5px;
  }

  .steps .sx .nm {
    font-size: 11px;
  }

  .steps .sx .tm {
    font-size: 9.5px;
  }

  /* 节点办理：结论控件占满整行 */
  .node-result {
    flex-wrap: wrap;
    gap: 6px;
  }

  .node-result > * {
    max-width: 100%;
  }

  /* 退回与保存草稿各占一半，主操作独占一行 */
  .node-actions {
    gap: 8px;
    flex-wrap: wrap;
  }

  .node-actions .right {
    display: contents;
  }

  .node-actions .el-button {
    flex: 1 1 calc(50% - 4px);
    min-width: 0;
    margin-left: 0;
  }

  .node-actions .act-go {
    flex: 1 1 100%;
  }

  .upload-full :deep(.el-upload-dragger) {
    padding: 12px 8px;
  }

  .upload-tip {
    font-size: 11.5px;
    line-height: 1.5;
  }

  /* 档案信息：单列行式（标签左、值右），12 项不再占满两屏 */
  .arc {
    grid-template-columns: minmax(0, 1fr) !important;
  }

  .arc > div {
    display: flex;
    align-items: baseline;
    gap: 12px;
    padding: 8px 12px;
    border-right: 0;
  }

  .arc .k {
    flex: 0 0 100px;
    margin-bottom: 0;
    font-size: 11.5px;
    line-height: 1.35;
  }

  .arc .v {
    flex: 1;
    min-width: 0;
    text-align: right;
    font-size: 12.5px;
  }

  /* 一次性待遇：金额块换行到下一行，避免挤压标题 */
  .sub-row {
    flex-wrap: wrap;
  }

  /* Tab 与内嵌表格 */
  .detail-tabs {
    padding: 0 12px 12px;
  }

  .detail-tabs :deep(.el-tabs__item) {
    padding: 0 10px;
    font-size: 12.5px;
  }

  /* Tab 内表格列多，缩小字号与内边距，减少横向滑动距离 */
  .pane :deep(.el-table) {
    font-size: 11.5px;
  }

  .pane :deep(.el-table .cell) {
    padding-left: 6px;
    padding-right: 6px;
  }

  .toolbar-inline {
    flex-wrap: wrap;
    gap: 8px;
  }

  .arc-note {
    line-height: 1.6;
  }

  /* 编辑档案弹窗里的两列表单改单列，中文控件在窄屏会被挤爆 */
  .arc-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .arc-grid .span-2 {
    grid-column: auto;
  }
}

/* 办理记录：固定显示 5 行（每行 54px），超出部分在卡片内滚动 */
.tl-scroll {
  --tl-row-h: 54px;
  max-height: calc(var(--tl-row-h) * 5);
  overflow-y: auto;
  overflow-x: hidden;
  /* 给滚动条留出位置，避免它压住右侧文字 */
  padding-right: 4px;
}

.tl-scroll::-webkit-scrollbar {
  width: 6px;
}

.tl-scroll::-webkit-scrollbar-thumb {
  background: #d7deea;
  border-radius: 3px;
}

.tl-scroll::-webkit-scrollbar-thumb:hover {
  background: #c2cbdb;
}
</style>
