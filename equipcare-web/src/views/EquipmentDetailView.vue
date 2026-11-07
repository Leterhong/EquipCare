<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { http } from '../api/http';

/**
 * 设备详情（Vue 第五批迁移，最密集展示页）：
 * 概况 / 技术参数 / 附件 / 二维码 / 巡检历史 / 维修历史。
 * 状态变更与编辑操作仍在旧版前端（顶部入口跳转）。
 */
interface FileItem {
  fileId: number;
  fileName: string;
  fileSize?: number;
  fileUrl?: string;
}
interface InspectHistoryItem {
  id?: number;
  taskCode?: string;
  planName?: string;
  planDate?: string;
  result?: string;
  abnormalCount?: number;
  assigneeName?: string;
}
interface RepairHistoryItem {
  id?: number;
  orderNo?: string;
  title?: string;
  status?: string;
  reportTime?: string;
  assigneeName?: string;
}
interface EquipmentDetail {
  id: number;
  assetNo: string;
  equipmentName: string;
  categoryName?: string;
  locationPath?: string;
  deptName?: string;
  responsibleUserName?: string;
  brand?: string;
  model?: string;
  spec?: string;
  serialNo?: string;
  manufacturer?: string;
  supplier?: string;
  purchaseDate?: string;
  installDate?: string;
  warrantyEndDate?: string;
  originalValue?: number;
  status: string;
  healthScore?: number;
  lastInspectTime?: string;
  nextInspectTime?: string;
  remark?: string;
  createTime?: string;
  files: FileItem[];
  recentInspections: InspectHistoryItem[];
  repairHistory: RepairHistoryItem[];
  repairCount?: number;
}

const STATUS: Record<string, { label: string; tag: 'success' | 'warning' | 'danger' | 'info' }> = {
  IN_USE: { label: '在用', tag: 'success' },
  IDLE: { label: '停用/闲置', tag: 'info' },
  REPAIRING: { label: '维修中', tag: 'warning' },
  SCRAPPED: { label: '已报废', tag: 'danger' },
};
const RESULT: Record<string, string> = { NORMAL: '正常', ABNORMAL: '异常', PARTIAL: '部分异常' };

const route = useRoute();
const router = useRouter();
const detail = ref<EquipmentDetail | null>(null);
const qrcodeUrl = ref('');
const activeTab = ref('inspect');
const loading = ref(true);

const id = computed(() => Number(route.params.id));
const fileUrl = (f: FileItem) => f.fileUrl || `/api/files/${f.fileId}`;
const fileSizeText = (v?: number) =>
  v == null ? '—' : v > 1048576 ? (v / 1048576).toFixed(1) + ' MB' : Math.max(1, Math.round(v / 1024)) + ' KB';
const dt = (v?: string) => (v || '').replace('T', ' ').slice(0, 16) || '—';
const d = (v?: string) => (v || '').slice(0, 10) || '—';

onMounted(async () => {
  try {
    detail.value = await http.get<never, EquipmentDetail>(`/api/asset/equipment/${id.value}`);
    // 二维码以二进制拉取（带令牌），失败静默（不影响档案展示）
    try {
      const res = await http.get<never, Blob>(`/api/asset/equipment/${id.value}/qrcode`, {
        responseType: 'blob',
      } as never);
      qrcodeUrl.value = URL.createObjectURL(res);
    } catch {
      /* 二维码缺失不阻塞 */
    }
  } finally {
    loading.value = false;
  }
});

function goLegacy() {
  window.open(`${location.protocol}//${location.hostname}:8080/app.html#/equipment/${id.value}`, '_blank');
}
</script>

<template>
  <div v-loading="loading">
    <template v-if="detail">
      <!-- 头部 -->
      <el-card shadow="never" class="head-card">
        <div class="head">
          <div>
            <div class="title-row">
              <h2>{{ detail.equipmentName }}</h2>
              <el-tag :type="STATUS[detail.status]?.tag || 'info'">
                {{ STATUS[detail.status]?.label || detail.status }}
              </el-tag>
            </div>
            <div class="sub">
              <span class="mono">{{ detail.assetNo }}</span> ·
              {{ detail.categoryName || '未分类' }} ·
              健康分 <b :style="{ color: (detail.healthScore ?? 100) < 60 ? '#dc2626' : (detail.healthScore ?? 100) < 75 ? '#d97706' : '#16845b' }">
                {{ detail.healthScore ?? '—' }}
              </b>
            </div>
          </div>
          <div class="actions">
            <el-button @click="router.push('/equipment')">← 返回列表</el-button>
            <el-button @click="goLegacy">完整操作（旧版）</el-button>
          </div>
        </div>
      </el-card>

      <el-row :gutter="14">
        <el-col :span="16">
          <!-- 概况 -->
          <el-card shadow="never" class="blk">
            <template #header>基本信息</template>
            <el-descriptions :column="2" border>
              <el-descriptions-item label="分类">{{ detail.categoryName || '—' }}</el-descriptions-item>
              <el-descriptions-item label="位置">{{ detail.locationPath || '—' }}</el-descriptions-item>
              <el-descriptions-item label="所属部门">{{ detail.deptName || '—' }}</el-descriptions-item>
              <el-descriptions-item label="责任人">{{ detail.responsibleUserName || '—' }}</el-descriptions-item>
              <el-descriptions-item label="最近巡检">{{ dt(detail.lastInspectTime) }}</el-descriptions-item>
              <el-descriptions-item label="下次巡检">{{ dt(detail.nextInspectTime) }}</el-descriptions-item>
            </el-descriptions>
          </el-card>

          <!-- 技术参数 -->
          <el-card shadow="never" class="blk">
            <template #header>技术参数</template>
            <el-descriptions :column="2" border>
              <el-descriptions-item label="品牌">{{ detail.brand || '—' }}</el-descriptions-item>
              <el-descriptions-item label="型号">{{ detail.model || '—' }}</el-descriptions-item>
              <el-descriptions-item label="规格">{{ detail.spec || '—' }}</el-descriptions-item>
              <el-descriptions-item label="序列号">{{ detail.serialNo || '—' }}</el-descriptions-item>
              <el-descriptions-item label="制造商">{{ detail.manufacturer || '—' }}</el-descriptions-item>
              <el-descriptions-item label="供应商">{{ detail.supplier || '—' }}</el-descriptions-item>
              <el-descriptions-item label="采购日期">{{ d(detail.purchaseDate) }}</el-descriptions-item>
              <el-descriptions-item label="安装日期">{{ d(detail.installDate) }}</el-descriptions-item>
              <el-descriptions-item label="保修截止">{{ d(detail.warrantyEndDate) }}</el-descriptions-item>
              <el-descriptions-item label="资产原值">
                {{ detail.originalValue != null ? '¥' + Number(detail.originalValue).toLocaleString() : '—' }}
              </el-descriptions-item>
            </el-descriptions>
            <p v-if="detail.remark" class="remark">{{ detail.remark }}</p>
          </el-card>

          <!-- 历史双 Tab -->
          <el-card shadow="never" class="blk">
            <el-tabs v-model="activeTab">
              <el-tab-pane label="巡检历史" name="inspect">
                <el-empty
                  v-if="!detail.recentInspections.length"
                  description="暂无巡检记录"
                  :image-size="60"
                />
                <el-table v-else :data="detail.recentInspections" size="small" stripe>
                  <el-table-column prop="planDate" label="计划日" width="100">
                    <template #default="{ row }">{{ d(row.planDate) }}</template>
                  </el-table-column>
                  <el-table-column prop="taskCode" label="任务号" width="140">
                    <template #default="{ row }"><span class="mono">{{ row.taskCode }}</span></template>
                  </el-table-column>
                  <el-table-column prop="planName" label="计划" min-width="140" show-overflow-tooltip />
                  <el-table-column label="结果" width="90">
                    <template #default="{ row }">
                      <el-tag
                        :type="row.result === 'ABNORMAL' ? 'danger' : 'success'"
                        size="small"
                      >
                        {{ RESULT[row.result || ''] || row.result || '—' }}
                        {{ row.abnormalCount ? `(${row.abnormalCount})` : '' }}
                      </el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column prop="assigneeName" label="执行人" width="95">
                    <template #default="{ row }">{{ row.assigneeName || '—' }}</template>
                  </el-table-column>
                </el-table>
              </el-tab-pane>
              <el-tab-pane :label="`维修历史（${detail.repairCount ?? detail.repairHistory.length}）`" name="repair">
                <el-empty
                  v-if="!detail.repairHistory.length"
                  description="暂无维修记录"
                  :image-size="60"
                />
                <el-table v-else :data="detail.repairHistory" size="small" stripe>
                  <el-table-column label="工单号" width="150">
                    <template #default="{ row }"><span class="mono">{{ row.orderNo }}</span></template>
                  </el-table-column>
                  <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip />
                  <el-table-column prop="status" label="状态" width="100" />
                  <el-table-column label="报修时间" width="150">
                    <template #default="{ row }">{{ dt(row.reportTime) }}</template>
                  </el-table-column>
                  <el-table-column prop="assigneeName" label="维修人" width="95">
                    <template #default="{ row }">{{ row.assigneeName || '—' }}</template>
                  </el-table-column>
                </el-table>
              </el-tab-pane>
            </el-tabs>
          </el-card>
        </el-col>

        <el-col :span="8">
          <!-- 二维码 -->
          <el-card shadow="never" class="blk">
            <template #header>设备二维码</template>
            <div class="qr-box">
              <img v-if="qrcodeUrl" :src="qrcodeUrl" alt="设备二维码" class="qr-img">
              <el-empty v-else description="二维码未生成" :image-size="50" />
              <p class="hint">手机扫码直达本机档案（旧版扫码页）</p>
            </div>
          </el-card>
          <!-- 附件 -->
          <el-card shadow="never" class="blk">
            <template #header>附件（{{ detail.files.length }}）</template>
            <template v-if="detail.files.length">
              <div v-for="f in detail.files" :key="f.fileId" class="file-row">
                <a :href="fileUrl(f)" target="_blank" rel="noopener">{{ f.fileName }}</a>
                <span class="hint">{{ fileSizeText(f.fileSize) }}</span>
              </div>
            </template>
            <el-empty v-else description="无附件" :image-size="50" />
          </el-card>
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<style scoped>
.head-card { margin-bottom: 14px; }
.head { display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 12px; }
.title-row { display: flex; align-items: center; gap: 10px; }
.title-row h2 { margin: 0; font-size: 20px; }
.sub { color: #64748b; font-size: 13.5px; margin-top: 6px; }
.actions { display: flex; gap: 8px; }
.blk { margin-bottom: 14px; }
.mono { font-family: ui-monospace, Menlo, Consolas, monospace; font-size: 12.5px; }
.remark { margin-top: 10px; color: #475569; font-size: 13px; background: #f8fafc; padding: 8px 12px; border-radius: 8px; }
.qr-box { text-align: center; }
.qr-img { width: 200px; height: 200px; object-fit: contain; }
.hint { color: #94a3b8; font-size: 12px; }
.file-row { display: flex; justify-content: space-between; padding: 6px 0; border-bottom: 1px dashed var(--el-border-color-lighter); font-size: 13px; }
</style>
