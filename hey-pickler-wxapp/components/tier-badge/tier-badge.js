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

  computed: {
    tierClass() {
      const tierMap = {
        LEGEND: 'tier-legend',
        SUPER: 'tier-super',
        SHINING: 'tier-shining'
      }
      return tierMap[this.data.tier] || 'tier-shining'
    },

    tierName() {
      const tierMap = {
        LEGEND: '传奇',
        SUPER: '超级',
        SHINING: '闪耀'
      }
      return tierMap[this.data.tier] || '未知'
    },

    typeName() {
      const typeMap = {
        STAR: '明星',
        PARTY: '派对'
      }
      return typeMap[this.data.type] || ''
    },

    sizeClass() {
      const sizeMap = {
        small: 'tier-small',
        medium: 'tier-medium',
        large: 'tier-large'
      }
      return sizeMap[this.data.size] || 'tier-medium'
    }
  },

  methods: {
    handleTap() {
      this.triggerEvent('tap', {
        tier: this.data.tier,
        type: this.data.type
      })
    }
  }
})
