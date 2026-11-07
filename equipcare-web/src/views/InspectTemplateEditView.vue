<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { http } from '../api/http';

/**
 * 巡检模板编辑（Vue 迁移：编辑表单类第一个）。
 * 新建/编辑共用：基本信息 + 动态检查项行（类型驱动字段显隐）。
 * 接口：POST /api/inspection/templates | PUT /{id}（检查项整组替换）。
 * TemplateSaveDTO：{templateCode?, templateName, categoryId, description, items[]}
 */
interface ItemRow {
  itemName: string;
  itemType: string;
  unit: string;
  minValue: number | null;
  maxValue: number | null;
  normalValue: string;
  enumOptions: string;
  required: boolean;
  checkMethod: string;
  standardRef: string;
  abnormalRule: string;
  abnormalLevel: string;
  autoWorkOrder: boolean;
  photoRequired: boolean;
}

interface CategoryNode {
  id: number;
  categoryName: string;
  children?: CategoryNode[];
}

const ITEM_TYPES = [
  { value: 'NUMBER', label: '数值（min~max 自动判定）' },
  { value: 'BOOLEAN', label: '是否' },
  { value: 'TEXT', label: '文本' },
  { value: 'ENUM', label: '枚举' },
];
const ABNORMAL_LEVELS = [
  { value: 'HIGH', label: '高' },
  { value: 'MEDIUM', label: '中' },
  { value: 'LOW', label: '低' },
];

const route = useRoute();
const router = useRouter();
const editId = computed(() => (route.params.id ? Number(route.params.id) : null));

const categories = ref<CategoryNode[]>([]);
const loading = ref(true);
const saving = ref(false);
const base = reactive({
  templateCode: '',
  templateName: '',
  categoryId: null as number | null,
  description: '',
});
const items = ref<ItemRow[]>([]);

function blankItem(): ItemRow {
  return {
    itemName: '', itemType: 'TEXT', unit: '', minValue: null, maxValue: null,
    normalValue: '', enumOptions: '', required: true, checkMethod: '', standardRef: '',
    abnormalRule: '', abnormalLevel: 'MEDIUM', autoWorkOrder: false, photoRequired: false,
  };
}

function flatten(nodes: CategoryNode[], prefix: string): { id: number; label: string }[] {
  const out: { id: number; label: string }[] = [];
  for (const n of nodes) {
    const label = prefix ? `${prefix} / ${n.categoryName}` : n.categoryName;
    out.push({ id: n.id, label });
    if (n.children?.length) out.push(...flatten(n.children, label));
  }
  return out;
}
const categoryOptions = computed(() => flatten(categories.value, ''));

const isNumber = (row: ItemRow) => row.itemType === 'NUMBER';
const isEnum = (row: ItemRow) => row.itemType === 'ENUM';

function addItem() {
  items.value.push(blankItem());
}
function removeItem(index: number) {
  items.value.splice(index, 1);
}
function move(index: number, delta: number) {
  const target = index + delta;
  if (target < 0 || target >= items.value.length) return;
  const [row] = items.value.splice(index, 1);
  items.value.splice(target, 0, row);
}

function validate(): string | null {
  if (!base.templateName.trim()) return '请填写模板名称';
  if (!base.categoryId) return '请选择设备分类';
  if (!items.value.length) return '至少添加一个检查项';
  for (let i = 0; i < items.value.length; i++) {
    const row = items.value[i];
    const no = `第 ${i + 1} 项`;
    if (!row.itemName.trim()) return `${no}：请填写检查项名称`;
    if (row.itemType === 'NUMBER' && (row.minValue == null || row.maxValue == null)) {
      return `${no}：数值型必须填写标准范围`;
    }
    if (row.itemType === 'NUMBER' && row.minValue != null && row.maxValue != null
      && row.minValue > row.maxValue) {
      return `${no}：标准范围下限不能大于上限`;
    }
    if (row.itemType === 'ENUM' && !row.enumOptions.trim()) return `${no}：枚举型必须填写选项`;
  }
  return null;
}

async function save() {
  const err = validate();
  if (err) {
    ElMessage.warning(err);
    return;
  }
  saving.value = true;
  try {
    const payload = {
      templateCode: base.templateCode.trim() || null,
      templateName: base.templateName.trim(),
      categoryId: base.categoryId,
      description: base.description.trim() || null,
      items: items.value.map((row, index) => ({
        itemName: row.itemName.trim(),
        itemType: row.itemType,
        unit: isNumber(row) ? row.unit.trim() || null : null,
        minValue: isNumber(row) ? row.minValue : null,
        maxValue: isNumber(row) ? row.maxValue : null,
        normalValue: row.normalValue.trim() || null,
        enumOptions: isEnum(row) ? row.enumOptions.trim() || null : null,
        required: row.required ? 1 : 0,
        checkMethod: row.checkMethod.trim() || null,
        standardRef: row.standardRef.trim() || null,
        abnormalRule: row.abnormalRule.trim() || null,
        abnormalLevel: row.abnormalLevel,
        autoWorkOrder: row.autoWorkOrder ? 1 : 0,
        seqNo: index + 1,
      })),
    };
    if (editId.value) {
      await http.put(`/api/inspection/templates/${editId.value}`, payload);
      ElMessage.success('模板已更新（检查项整组替换，版本号 +1）');
    } else {
      const newId = await http.post<never, number>('/api/inspection/templates', payload);
      ElMessage.success('模板已创建');
      router.replace(`/inspect-template-edit/${newId}`);
    }
    router.push('/inspect-template');
  } catch (e) {
    ElMessage.error((e as Error).message || '保存失败');
  } finally {
    saving.value = false;
  }
}

onMounted(async () => {
  try {
    categories.value = await http.get<never, CategoryNode[]>('/api/asset/categories/tree');
    if (editId.value) {
      const d = await http.get<never, {
        templateName?: string; templateCode?: string; categoryId?: number;
        description?: string; items?: {
          itemName: string; itemType: string; unit?: string; minValue?: number;
          maxValue?: number; normalValue?: string; enumOptions?: string; required?: number;
          checkMethod?: string; standardRef?: string; abnormalRule?: string;
          abnormalLevel?: string; autoWorkOrder?: number; photoRequired?: number;
        }[];
      }>(`/api/inspection/templates/${editId.value}`);
      base.templateName = d.templateName || '';
      base.templateCode = d.templateCode || '';
      base.categoryId = d.categoryId ?? null;
      base.description = d.description || '';
      items.value = (d.items || []).map((it) => ({
        itemName: it.itemName,
        itemType: it.itemType,
        unit: it.unit || '',
        minValue: it.minValue ?? null,
        maxValue: it.maxValue ?? null,
        normalValue: it.normalValue || '',
        enumOptions: it.enumOptions || '',
        required: (it.required ?? 1) === 1,
        checkMethod: it.checkMethod || '',
        standardRef: it.standardRef || '',
        abnormalRule: it.abnormalRule || '',
        abnormalLevel: it.abnormalLevel || 'MEDIUM',
        autoWorkOrder: (it.autoWorkOrder ?? 0) === 1,
        photoRequired: (it.photoRequired ?? 0) === 1,
      }));
    } else {
      addItem();
    }
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <div v-loading="loading">
    <el-card shadow="never" class="blk">
      <template #header><b>基本信息</b></template>
      <el-form label-position="top">
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="模板编码（留空自动生成）">
              <el-input v-model="base.templateCode" placeholder="如 TPL-PUMP-DAILY" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="模板名称" required>
              <el-input v-model="base.templateName" maxlength="100" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="设备分类" required>
              <el-select v-model="base.categoryId" style="width:100%" filterable>
                <el-option v-for="c in categoryOptions" :key="c.id" :label="c.label" :value="c.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="模板描述">
          <el-input v-model="base.description" type="textarea" :rows="2" maxlength="300" />
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="blk">
      <template #header>
        <div class="head"><b>检查项（{{ items.length }}）</b>
          <el-button type="primary" size="small" @click="addItem">+ 添加检查项</el-button>
        </div>
      </template>
      <el-empty v-if="!items.length" description="还没有检查项" :image-size="60" />
      <el-card v-for="(row, index) in items" :key="index" shadow="never" class="item-edit"
        :class="{ 'auto-wo': row.autoWorkOrder }">
        <div class="item-toolbar">
          <span class="mono">第 {{ index + 1 }} 项</span>
          <div>
            <el-button size="small" text :disabled="index === 0" @click="move(index, -1)">↑</el-button>
            <el-button size="small" text :disabled="index === items.length - 1" @click="move(index, 1)">↓</el-button>
            <el-button size="small" text type="danger" @click="removeItem(index)">删除</el-button>
          </div>
        </div>
        <el-row :gutter="10">
          <el-col :span="10">
            <el-form-item label="检查项名称" required label-position="top">
              <el-input v-model="row.itemName" maxlength="100" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="类型" label-position="top">
              <el-select v-model="row.itemType">
                <el-option v-for="t in ITEM_TYPES" :key="t.value" :label="t.label" :value="t.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="4" v-if="isNumber(row)">
            <el-form-item label="单位" label-position="top">
              <el-input v-model="row.unit" maxlength="16" />
            </el-form-item>
          </el-col>
          <el-col :span="4">
            <el-form-item label="严重程度" label-position="top">
              <el-select v-model="row.abnormalLevel">
                <el-option v-for="l in ABNORMAL_LEVELS" :key="l.value" :label="l.label" :value="l.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="10" v-if="isNumber(row)">
          <el-col :span="6">
            <el-form-item label="标准下限" label-position="top" required>
              <el-input-number v-model="row.minValue" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="标准上限" label-position="top" required>
              <el-input-number v-model="row.maxValue" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="10" v-if="isEnum(row)">
          <el-col :span="24">
            <el-form-item label="枚举选项（逗号分隔）" label-position="top" required>
              <el-input v-model="row.enumOptions" placeholder="如：正常,轻微磨损,严重磨损" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="标准值/正常描述" label-position="top">
              <el-input v-model="row.normalValue" maxlength="100" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="检查方法" label-position="top">
              <el-input v-model="row.checkMethod" maxlength="100" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="10">
          <el-col :span="12">
            <el-form-item label="依据标准/规程" label-position="top">
              <el-input v-model="row.standardRef" maxlength="100" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="异常判定规则" label-position="top">
              <el-input v-model="row.abnormalRule" maxlength="100" />
            </el-form-item>
          </el-col>
        </el-row>
        <div class="switches">
          <el-checkbox v-model="row.required">必检项（不允许跳过）</el-checkbox>
          <el-checkbox v-model="row.photoRequired">需现场拍照</el-checkbox>
          <el-checkbox v-model="row.autoWorkOrder">异常自动转维修工单</el-checkbox>
        </div>
      </el-card>
    </el-card>

    <div class="save-bar">
      <el-button @click="router.push('/inspect-template')">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">
        {{ editId ? '保存修改' : '创建模板' }}
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.blk { margin-bottom: 14px; }
.head { display: flex; justify-content: space-between; align-items: center; }
.item-toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.item-edit { border: 1px solid var(--el-border-color-lighter); margin-bottom: 10px; }
.item-edit.auto-wo { border-left: 3px solid var(--el-color-danger); }
.switches { display: flex; gap: 16px; flex-wrap: wrap; }
.save-bar { display: flex; gap: 10px; justify-content: flex-end; margin-bottom: 30px; }
.mono { font-family: ui-monospace, Menlo, Consolas, monospace; font-size: 12.5px; color: #64748b; }
</style>
