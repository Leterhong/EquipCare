<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { http } from '../api/http';
import { useAuthStore } from '../stores/auth';
import { useRealtime } from '../composables/useRealtime';

/**
 * 工单详情（Vue 第七批，核心操作页）：
 * 全量展示（概况/故障现场/派单轨迹/维修记录/验收/备件/评价/巡检来源）
 * + allowedActions 驱动的基础动作（派单/接单/拒接/转派/撤回/取消）。
 * 复杂动作（维修过程/验收/评价）跳旧版前端完成。
 */
interface ImageRef { id: number; fileName?: string }
interface DispatchRecord {
  dispatchType: string;
  toUserName?: string;
  dispatcherName?: string;
  dispatchTime?: string;
  remark?: string;
  rejectFlag?: number;
  rejectReason?: string;
}
interface RepairRecord {
  recordType: string;
  content?: string;
  operatorName?: string;
  createTime?: string;
  costMinutes?: number;
  beforeImages?: ImageRef[];
  afterImages?: ImageRef[];
}
interface VerifyRecord {
  verifyResult?: string;
  verifyUserName?: string;
  verifyTime?: string;
  qualityScore?: number;
  remark?: string;
}
interface PartRow { partName: string; quantity?: number; unit?: string; amount?: number; issueStatus?: string; issueStatusLabel?: string; applyUserName?: string }
interface Evaluation { attitudeScore?: number; qualityScore?: number; speedScore?: number; averageScore?: number; evaluatorName?: string; createTime?: string; content?: string }
interface Detail {
  id: number;
  orderNo: string;
  title: string;
  status: string;
  level?: string;
  priority?: string;
  source?: string;
  equipmentName?: string;
  assetNo?: string;
  reporterName?: string;
  assigneeId?: number;
  dispatcherName?: string;
  assigneeName?: string;
  reportTime?: string;
  dispatchTime?: string;
  acceptTime?: string;
  startRepairTime?: string;
  submitVerifyTime?: string;
  actualFinishTime?: string;
  closeTime?: string;
  verifyResult?: string;
  laborHours?: number;
  spareCost?: number;
  rejectCount?: number;
  rejectDispatchCount?: number;
  faultDesc?: string;
  faultImages?: ImageRef[];
  allowedActions?: string[];
  repairStarted?: boolean;
  evaluated?: boolean;
  dispatchRecords?: DispatchRecord[];
  repairRecords?: RepairRecord[];
  verifyRecords?: VerifyRecord[];
  parts?: PartRow[];
  evaluation?: Evaluation;
  sourceInspection?: { anomalyNo?: string; itemName?: string; taskCode?: string; anomalyDescription?: string };
}

const STATUS: Record<string, { label: string; tag: 'warning' | 'primary' | 'success' | 'danger' | 'info' }> = {
  PENDING_ASSIGN: { label: '待派单', tag: 'warning' },
  PENDING_ACCEPT: { label: '待接单', tag: 'warning' },
  IN_PROGRESS: { label: '维修中', tag: 'primary' },
  PENDING_REVIEW: { label: '待验收', tag: 'primary' },
  COMPLETED: { label: '已完成', tag: 'success' },
  REJECTED: { label: '已驳回', tag: 'danger' },
  CANCELLED: { label: '已取消', tag: 'info' },
};
const LEVEL: Record<string, string> = { P1: 'P1 紧急', P2: 'P2 高', P3: 'P3 中', P4: 'P4 低' };
const DISPATCH_TYPE: Record<string, string> = {
  ASSIGN: '首次派单', REDISPATCH: '重新派单', TRANSFER: '转派', REASSIGN: '强制转派', REVOKE: '撤回',
};
const RECORD_TYPE: Record<string, string> = {
  DIAGNOSE: '诊断', PROCESS: '过程', START: '开始维修', FINISH: '完工', OTHER: '其他',
};

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const { on: onSse } = useRealtime();

const detail = ref<Detail | null>(null);
const loading = ref(true);
const imageUrlMap = reactive<Record<number, string>>({});

const id = computed(() => Number(route.params.id));
const dt = (v?: string) => (v || '').replace('T', ' ').slice(0, 16) || '—';
const money = (v?: number) => (v == null ? '—' : '¥' + Number(v).toFixed(2));

async function loadImageUrls(images?: ImageRef[]) {
  for (const img of images || []) {
    if (img.id && !imageUrlMap[img.id]) {
      try {
        const blob = await http.get<never, Blob>(`/api/files/${img.id}`, { responseType: 'blob' } as never);
        imageUrlMap[img.id] = URL.createObjectURL(blob);
      } catch {
        /* 单图失败静默 */
      }
    }
  }
}

async function load() {
  loading.value = true;
  try {
    detail.value = await http.get<never, Detail>(`/api/workorder/${id.value}`);
    await loadImageUrls(detail.value.faultImages);
    for (const r of detail.value.repairRecords || []) {
      await loadImageUrls(r.beforeImages);
      await loadImageUrls(r.afterImages);
    }
  } catch (e) {
    ElMessage.error((e as Error).message || '加载失败');
  } finally {
    loading.value = false;
  }
}

async function run(promise: Promise<unknown>, message: string) {
  try {
    await promise;
    ElMessage.success(message);
    await load();
  } catch (e) {
    ElMessage.error((e as Error).message || '操作失败');
  }
}

/** 简单原因弹窗（拒接/撤回/取消共用） */
async function reasonAction(title: string, label: string, action: (reason: string) => Promise<unknown>, successMessage: string) {
  try {
    const { value } = await ElMessageBox.prompt(`请输入${label}（至少 5 字）`, title, {
      inputPattern: /^.{5,}$/,
      inputErrorMessage: '至少 5 个字符',
      type: 'warning',
    });
    await run(action(value), successMessage);
  } catch {
    /* 取消输入 */
  }
}

interface Assignee { id: number; realName?: string; username?: string; pendingCount?: number }
const dispatchDialog = reactive({
  visible: false,
  title: '',
  path: '',
  list: [] as Assignee[],
  picked: null as number | null,
  note: '',
});

async function dispatchAction(title: string, path: string) {
  try {
    dispatchDialog.list = await http.get<never, Assignee[]>('/api/workorder/assignees');
  } catch (e) {
    ElMessage.error((e as Error).message || '维修人加载失败');
    return;
  }
  dispatchDialog.title = title;
  dispatchDialog.path = path;
  dispatchDialog.picked = null;
  dispatchDialog.note = '';
  dispatchDialog.visible = true;
}

async function confirmDispatch() {
  if (!dispatchDialog.picked) {
    ElMessage.warning('请先选择维修人');
    return;
  }
  await run(
    http.post(`/api/workorder/${id.value}/${dispatchDialog.path}`, {
      assigneeId: dispatchDialog.picked,
      reason: dispatchDialog.note.trim() || null,
    }),
    '已派单',
  );
  dispatchDialog.visible = false;
}

const canRepair = computed(() => auth.can('wo:workorder:repair'));
const canVerify = computed(() => auth.can('wo:workorder:verify'));
const canEvaluate = computed(() => auth.can('wo:workorder:evaluate'));
const legacyRepairUrl = computed(() =>
  `${location.protocol}//${location.hostname}:8080/app.html#/workorder/${id.value}/repair`);

/** 跳旧版前端完成复杂操作（维修过程/验收/评价） */
function goLegacyPath(path: string) {
  window.open(`${location.protocol}//${location.hostname}:8080/app.html#${path}`, '_blank');
}

let offSse: (() => void) | null = null;
onMounted(() => {
  load();
  offSse = onSse('workorder:changed', (data) => {
    if (Number(data.workOrderId) === id.value) {
      ElMessage.info('该工单有状态更新，已自动刷新');
      load();
    }
  });
});
onUnmounted(() => offSse?.());
</script>

<template>
  <div v-loading="loading">
    <template v-if="detail">
      <!-- 头部 + 动作 -->
      <el-card shadow="never" class="blk">
        <div class="head">
          <div>
            <div class="title-row">
              <h2>{{ detail.title }}</h2>
              <el-tag :type="STATUS[detail.status]?.tag || 'info'">
                {{ STATUS[detail.status]?.label || detail.status }}
              </el-tag>
            </div>
            <div class="sub">
              <span class="mono">{{ detail.orderNo }}</span> · {{ LEVEL[detail.level || ''] || detail.level }}
              · 报修于 {{ dt(detail.reportTime) }}
            </div>
          </div>
          <div class="actions">
            <el-button v-if="(detail.allowedActions || []).includes('DISPATCH')" type="primary"
              @click="dispatchAction(detail.status === 'REJECTED' ? '重新派单' : '派单', 'dispatch')">
              {{ detail.status === 'REJECTED' ? '重新派单' : '派单' }}
            </el-button>
            <el-button v-if="(detail.allowedActions || []).includes('ACCEPT')" type="primary"
              @click="run(http.post(`/api/workorder/${id}/accept`, {}), '已接单，请到维修过程记录填写诊断')">
              接单
            </el-button>
            <el-button v-if="(detail.allowedActions || []).includes('REJECT_DISPATCH')"
              @click="reasonAction('拒接工单', '拒接原因', (r) => http.post(`/api/workorder/${id}/reject`, { reason: r }), '已拒接')">
              拒接
            </el-button>
            <el-button v-if="(detail.allowedActions || []).includes('TRANSFER')"
              @click="dispatchAction('转派', 'transfer')">转派</el-button>
            <el-button v-if="(detail.allowedActions || []).includes('FORCE_TRANSFER')"
              @click="dispatchAction('强制转派', 'force-transfer')">强制转派</el-button>
            <el-button v-if="(detail.allowedActions || []).includes('REVOKE')"
              @click="reasonAction('撤回派单', '撤回原因', (r) => http.post(`/api/workorder/${id}/revoke`, { reason: r }), '已撤回')">
              撤回派单
            </el-button>
            <el-button v-if="(detail.allowedActions || []).includes('CANCEL')" type="danger"
              @click="reasonAction('取消工单', '取消原因', (r) => http.post(`/api/workorder/${id}/cancel`, { reason: r }), '已取消')">
              取消工单
            </el-button>
            <el-button v-if="detail.status === 'IN_PROGRESS' && detail.assigneeId && canRepair"
              @click="goLegacyPath(`/workorder/${id}/repair`)">
              {{ detail.repairStarted ? '维修过程记录' : '开始维修' }}
            </el-button>
            <el-button v-if="detail.status === 'PENDING_REVIEW' && canVerify"
              @click="goLegacyPath(`/workorder/${id}/verify`)">
              去验收
            </el-button>
            <el-button v-if="detail.status === 'COMPLETED' && canEvaluate && !detail.evaluated"
              @click="goLegacyPath(`/workorder/${id}/evaluate`)">
              评价本次服务
            </el-button>
          </div>
        </div>
      </el-card>

      <!-- 概况 + 故障 -->
      <el-row :gutter="14">
        <el-col :span="14">
          <el-card shadow="never" class="blk">
            <template #header>工单概况</template>
            <el-descriptions :column="2" border size="small">
              <el-descriptions-item label="设备">{{ detail.equipmentName || '—' }}</el-descriptions-item>
              <el-descriptions-item label="资产编号">{{ detail.assetNo || '—' }}</el-descriptions-item>
              <el-descriptions-item label="故障等级">{{ LEVEL[detail.level || ''] || detail.level }}</el-descriptions-item>
              <el-descriptions-item label="报修人">{{ detail.reporterName || '—' }}</el-descriptions-item>
              <el-descriptions-item label="维修人">{{ detail.assigneeName || '未派单' }}</el-descriptions-item>
              <el-descriptions-item label="验收结果">
                {{ detail.verifyResult === 'PASS' ? '通过' : detail.verifyResult === 'REJECT' ? '不通过' : '—' }}
              </el-descriptions-item>
              <el-descriptions-item label="工时">{{ detail.laborHours != null ? detail.laborHours + ' 小时' : '—' }}</el-descriptions-item>
              <el-descriptions-item label="备件成本">{{ money(detail.spareCost) }}</el-descriptions-item>
              <el-descriptions-item label="完成时间">{{ dt(detail.actualFinishTime) }}</el-descriptions-item>
              <el-descriptions-item label="驳回/拒接次数">
                {{ detail.rejectCount || 0 }} / {{ detail.rejectDispatchCount || 0 }}
              </el-descriptions-item>
            </el-descriptions>
          </el-card>
        </el-col>
        <el-col :span="10">
          <el-card shadow="never" class="blk">
            <template #header>故障描述</template>
            <p style="font-size: 13.5px; white-space: pre-wrap">{{ detail.faultDesc || '—' }}</p>
            <div v-if="(detail.faultImages || []).length" class="thumbs">
              <img v-for="img in detail.faultImages" :key="img.id" :src="imageUrlMap[img.id]" class="thumb">
            </div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 派单轨迹 -->
      <el-card shadow="never" class="blk">
        <template #header>派单历史</template>
        <el-empty v-if="!(detail.dispatchRecords || []).length" description="尚未派单" :image-size="50" />
        <div v-for="(rec, i) in detail.dispatchRecords" :key="i" class="stack-row">
          <b>{{ DISPATCH_TYPE[rec.dispatchType] || rec.dispatchType }} · {{ rec.toUserName || '—' }}</b>
          <span class="sub">{{ dt(rec.dispatchTime) }} · 派单人 {{ rec.dispatcherName || '—' }}</span>
          <span v-if="rec.remark" class="sub">说明：{{ rec.remark }}</span>
          <span v-if="rec.rejectFlag === 1" class="sub danger">拒接原因：{{ rec.rejectReason || '—' }}</span>
        </div>
      </el-card>

      <!-- 维修记录 -->
      <el-card shadow="never" class="blk">
        <template #header>维修过程记录</template>
        <el-empty v-if="!(detail.repairRecords || []).length" description="还没有维修记录" :image-size="50" />
        <div v-for="(rec, i) in detail.repairRecords" :key="i" class="stack-row">
          <div>
            <el-tag size="small">{{ RECORD_TYPE[rec.recordType] || rec.recordType }}</el-tag>
            <span class="sub" style="margin-left: 6px">{{ dt(rec.createTime) }} · {{ rec.operatorName || '—' }}
              <template v-if="rec.costMinutes"> · 耗时 {{ rec.costMinutes }} 分钟</template>
            </span>
          </div>
          <p style="font-size: 13px; white-space: pre-wrap; margin: 4px 0">{{ rec.content }}</p>
          <div v-if="(rec.beforeImages || []).length" class="thumbs">
            <span class="sub">维修前：</span>
            <img v-for="img in rec.beforeImages" :key="img.id" :src="imageUrlMap[img.id]" class="thumb">
          </div>
          <div v-if="(rec.afterImages || []).length" class="thumbs">
            <span class="sub">维修后：</span>
            <img v-for="img in rec.afterImages" :key="img.id" :src="imageUrlMap[img.id]" class="thumb">
          </div>
        </div>
      </el-card>

      <!-- 验收 + 备件 -->
      <el-row :gutter="14">
        <el-col :span="12">
          <el-card shadow="never" class="blk">
            <template #header>验收记录</template>
            <el-empty v-if="!(detail.verifyRecords || []).length" description="尚未验收" :image-size="50" />
            <div v-for="(rec, i) in detail.verifyRecords" :key="i" class="stack-row">
              <el-tag :type="rec.verifyResult === 'PASS' ? 'success' : 'danger'" size="small">
                {{ rec.verifyResult === 'PASS' ? '验收通过' : '验收不通过' }}
              </el-tag>
              <span class="sub">{{ dt(rec.verifyTime) }} · {{ rec.verifyUserName || '—' }}
                <template v-if="rec.qualityScore"> · 质量评分 {{ rec.qualityScore }}</template>
              </span>
              <p style="font-size: 13px">{{ rec.remark || '—' }}</p>
            </div>
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card shadow="never" class="blk">
            <template #header>备件使用</template>
            <el-empty v-if="!(detail.parts || []).length" description="本单未领用备件" :image-size="50" />
            <div v-for="(p, i) in detail.parts" :key="i" class="stack-row">
              <b>{{ p.partName }} × {{ p.quantity }}{{ p.unit || '' }}</b>
              <span class="sub">{{ p.issueStatusLabel || p.issueStatus }} · 金额 {{ money(p.amount) }} · 申请人 {{ p.applyUserName || '—' }}</span>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 评价 + 巡检来源 -->
      <el-row :gutter="14" v-if="detail.evaluation || detail.sourceInspection">
        <el-col :span="12" v-if="detail.evaluation">
          <el-card shadow="never" class="blk">
            <template #header>服务评价（平均 {{ detail.evaluation.averageScore }} 分）</template>
            <el-descriptions :column="3" size="small">
              <el-descriptions-item label="态度">{{ detail.evaluation.attitudeScore }}</el-descriptions-item>
              <el-descriptions-item label="质量">{{ detail.evaluation.qualityScore }}</el-descriptions-item>
              <el-descriptions-item label="速度">{{ detail.evaluation.speedScore }}</el-descriptions-item>
            </el-descriptions>
            <p style="font-size: 13px">{{ detail.evaluation.content || '（未填写文字评价）' }}</p>
          </el-card>
        </el-col>
        <el-col :span="12" v-if="detail.sourceInspection">
          <el-card shadow="never" class="blk">
            <template #header>巡检来源</template>
            <el-descriptions :column="1" size="small">
              <el-descriptions-item label="异常单号">{{ detail.sourceInspection.anomalyNo || '—' }}</el-descriptions-item>
              <el-descriptions-item label="检查项">{{ detail.sourceInspection.itemName || '—' }}</el-descriptions-item>
              <el-descriptions-item label="描述">{{ detail.sourceInspection.anomalyDescription || '—' }}</el-descriptions-item>
            </el-descriptions>
          </el-card>
        </el-col>
      </el-row>
    </template>
  </div>

  <!-- 派单/转派弹窗（简化版：选人 + 说明；完整版见旧版工作台） -->
  <el-dialog v-model="dispatchDialog.visible" :title="`${dispatchDialog.title} · 选择维修人`" width="460px">
    <div class="assignee-list">
      <div
        v-for="a in dispatchDialog.list"
        :key="a.id"
        class="assignee-item"
        :class="{ picked: dispatchDialog.picked === a.id }"
        @click="dispatchDialog.picked = a.id"
      >
        <b>{{ a.realName || a.username }}</b>
        <span class="sub">在办 {{ a.pendingCount ?? 0 }} 单</span>
      </div>
      <el-empty v-if="!dispatchDialog.list.length" description="暂无可派维修人" :image-size="50" />
    </div>
    <el-input
      v-model="dispatchDialog.note"
      type="textarea"
      :rows="2"
      placeholder="派单说明（可选，会写进派单轨迹）"
      style="margin-top: 10px"
    />
    <template #footer>
      <el-button @click="dispatchDialog.visible = false">取消</el-button>
      <el-button type="primary" :disabled="!dispatchDialog.picked" @click="confirmDispatch">确认派单</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.blk { margin-bottom: 14px; }
.head { display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 12px; }
.title-row { display: flex; align-items: center; gap: 10px; }
.title-row h2 { margin: 0; font-size: 19px; }
.sub { color: #64748b; font-size: 12.5px; display: block; }
.danger { color: #dc2626; }
.mono { font-family: ui-monospace, Menlo, Consolas, monospace; font-size: 12.5px; }
.actions { display: flex; flex-wrap: wrap; gap: 8px; }
.stack-row { padding: 8px 0; border-bottom: 1px dashed var(--el-border-color-lighter); font-size: 13px; }
.stack-row:last-child { border-bottom: none; }
.thumbs { display: flex; gap: 6px; flex-wrap: wrap; margin-top: 6px; align-items: center; }
.thumb { width: 72px; height: 54px; object-fit: cover; border-radius: 6px; border: 1px solid var(--el-border-color-lighter); }
.assignee-list { max-height: 300px; overflow-y: auto; display: flex; flex-direction: column; gap: 6px; }
.assignee-item { padding: 9px 12px; border: 1px solid var(--el-border-color); border-radius: 8px; cursor: pointer; }
.assignee-item:hover { border-color: var(--el-color-primary); }
.assignee-item.picked { border-color: var(--el-color-primary); background: var(--el-color-primary-light-9); }
</style>
