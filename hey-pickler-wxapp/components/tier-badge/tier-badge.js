// Tier badge component
var terms = require('../../utils/terms')
var TIER_NAME = terms.TIER_NAME

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
    size: {
      type: String,
      value: 'medium' // small, medium, large
    }
  },

  data: {
    tierClass: 'tier-bronze',
    tierNameText: '青铜',
    sizeClass: 'tier-medium'
  },

  observers: {
    'tier, type, tierName, size': function (tier, type, tierName, size) {
      var tierClassMap = {
        BRONZE: 'tier-bronze',
        SILVER: 'tier-silver',
        GOLD: 'tier-gold',
        PLATINUM: 'tier-platinum',
        DIAMOND: 'tier-diamond',
        MASTER: 'tier-master'
      }
      var sizeClassMap = {
        small: 'tier-small',
        medium: 'tier-medium',
        large: 'tier-large'
      }

      // 优先后端 tierName，fallback 本地 TIER_NAME
      var resolvedName = tierName || TIER_NAME[tier] || TIER_NAME.BRONZE

      this.setData({
        tierClass: tierClassMap[tier] || 'tier-bronze',
        tierNameText: resolvedName,
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
