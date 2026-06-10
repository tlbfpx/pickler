// Tier badge component
Component({
  properties: {
    tier: {
      type: String,
      value: 'SHINING'
    },
    type: {
      type: String,
      value: 'STAR'
    },
    size: {
      type: String,
      value: 'medium' // small, medium, large
    }
  },

  data: {
    tierClass: 'tier-shining',
    tierName: '闪耀',
    typeName: '明星',
    sizeClass: 'tier-medium'
  },

  observers: {
    'tier, type, size': function (tier, type, size) {
      var tierClassMap = {
        LEGEND: 'tier-legend',
        SUPER: 'tier-super',
        SHINING: 'tier-shining'
      }
      var tierNameMap = {
        LEGEND: '传奇',
        SUPER: '超级',
        SHINING: '闪耀'
      }
      var typeNameMap = {
        STAR: '明星',
        PARTY: '派对'
      }
      var sizeClassMap = {
        small: 'tier-small',
        medium: 'tier-medium',
        large: 'tier-large'
      }

      this.setData({
        tierClass: tierClassMap[tier] || 'tier-shining',
        tierName: tierNameMap[tier] || '未知',
        typeName: typeNameMap[type] || '',
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
