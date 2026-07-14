<template>
  <span
    class="tier-badge"
    :class="[sizeClass]"
    :style="{ backgroundColor: tierColor || '#6B7280' }"
  >
    <span
      v-if="tierIcon"
      class="tier-badge__icon"
    >{{ tierIcon }}</span>
    {{ tierName || '-' }}
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'

/**
 * 段位徽章：颜色与名称均由后端 VO 驱动（tierColor/tierName/starTierColor/starTierName 等），
 * 不再依赖前端 TIER_COLOR 硬编码表。
 *
 * 用法：
 *   <TierBadge :tier-color="row.tierColor" :tier-name="row.tierName || formatTierName(row.tier)" size="small" />
 *   <TierBadge :tier-color="user?.starTierColor" :tier-name="user?.starTierName" />
 */
const props = withDefaults(defineProps<{
  /** 段位色（hex），来自后端 VO；缺失回退灰色 */
  tierColor?: string
  /** 段位展示名（中文），来自后端 VO 或前端 formatTierName 兜底 */
  tierName?: string
  /** 段位图标 emoji/字符，可选 */
  tierIcon?: string
  /** 尺寸：small 用于表格行内，default 用于卡片/详情 */
  size?: 'small' | 'default'
}>(), {
  tierColor: '',
  tierName: '',
  tierIcon: '',
  size: 'default'
})

const sizeClass = computed(() => `tier-badge--${props.size}`)
</script>

<style scoped>
.tier-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 10px;
  border-radius: 4px;
  color: #fff;
  font-size: 12px;
  line-height: 1.4;
  white-space: nowrap;
}

.tier-badge--small {
  padding: 1px 8px;
  font-size: 11px;
}

.tier-badge__icon {
  font-size: 1em;
  line-height: 1;
}
</style>
