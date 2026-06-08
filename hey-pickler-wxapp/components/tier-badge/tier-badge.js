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
        LEGEND: 'Legend',
        SUPER: 'Super',
        SHINING: 'Shining'
      }
      return tierMap[this.data.tier] || 'Unknown'
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
