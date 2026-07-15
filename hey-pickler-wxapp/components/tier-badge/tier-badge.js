// Tier badge component
//
// 配色/名/icon 全部由后端 VO 驱动（tierColor / tierIcon / tierName），
// 不再依赖前端金属渐变 CSS 类 —— 与 admin TierBadge.vue 对齐，运营改 tier_config 即生效。
// type 仅作 tap 事件 payload 透传，不再参与配色（双轨色差由后端按 track 返回）。
var terms = require('../../utils/terms')
var TIER_NAME = terms.TIER_NAME

var FALLBACK_COLOR = '#6B7280'

// 根据背景 hex 计算可读文字色：浅底→深字(#1F2937)，深底→白字。
// 已用 V19 双轨 seed 12 档验算阈值 0.40（PARTY #FBBF24/#F59E0B→深字，STAR 红紫蓝→白字）。
function readableText(hex) {
  if (!hex) return '#FFFFFF'
  var h = hex.replace('#', '')
  if (h.length === 3) {
    h = h.charAt(0) + h.charAt(0) + h.charAt(1) + h.charAt(1) + h.charAt(2) + h.charAt(2)
  }
  if (h.length < 6) return '#FFFFFF'
  var r = parseInt(h.substring(0, 2), 16) / 255
  var g = parseInt(h.substring(2, 4), 16) / 255
  var b = parseInt(h.substring(4, 6), 16) / 255
  function lin(c) {
    return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4)
  }
  var L = 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)
  return L > 0.40 ? '#1F2937' : '#FFFFFF'
}

Component({
  properties: {
    tier: {
      type: String,
      value: 'BRONZE'
    },
    type: {
      type: String,
      value: 'STAR'
    },
    // 后端已返回 tierName 时优先使用（fallback 本地 TIER_NAME）
    tierName: {
      type: String,
      value: ''
    },
    // 段位色 hex，来自后端 VO（TierResolver.colorFor）；缺失回退中性灰
    tierColor: {
      type: String,
      value: ''
    },
    // 段位图标 emoji，来自后端 VO（TierResolver.iconFor）
    tierIcon: {
      type: String,
      value: ''
    },
    size: {
      type: String,
      value: 'medium' // small, medium, large
    }
  },

  data: {
    badgeStyle: 'background:' + FALLBACK_COLOR + ';',
    textStyle: 'color:#FFFFFF;',
    tierNameText: '青铜',
    tierIconText: '',
    sizeClass: 'tier-medium'
  },

  observers: {
    'tier, tierName, tierColor, tierIcon, size': function (tier, tierName, tierColor, tierIcon, size) {
      var sizeClassMap = {
        small: 'tier-small',
        medium: 'tier-medium',
        large: 'tier-large'
      }

      // 优先后端 tierName，fallback 本地 TIER_NAME
      var resolvedName = tierName || TIER_NAME[tier] || TIER_NAME.BRONZE
      var bg = tierColor || FALLBACK_COLOR

      this.setData({
        badgeStyle: 'background:' + bg + ';',
        textStyle: 'color:' + readableText(bg) + ';',
        tierNameText: resolvedName,
        tierIconText: tierIcon || '',
        sizeClass: sizeClassMap[size] || 'tier-medium'
      })
    }
  },

  methods: {
    handleTap: function () {
      this.triggerEvent('tap', {
        tier: this.data.tier,
        type: this.data.type
      })
    }
  }
})
