// Ranking item component
Component({
  properties: {
    rank: {
      type: Number,
      value: 0
    },
    user: {
      type: Object,
      value: null
    },
    showTier: {
      type: Boolean,
      value: true
    },
    showChange: {
      type: Boolean,
      value: true
    }
  },

  computed: {
    rankDisplay() {
      if (this.data.rank <= 3) {
        const icons = ['🥇', '🥈', '🥉']
        return icons[this.data.rank - 1]
      }
      return this.data.rank
    },

    rankClass() {
      if (this.data.rank <= 3) return 'rank-top'
      if (this.data.rank <= 10) return 'rank-high'
      return 'rank-normal'
    },

    changeIcon() {
      const change = this.data.user?.change || 0
      if (change > 0) return '↑'
      if (change < 0) return '↓'
      return '-'
    },

    changeClass() {
      const change = this.data.user?.change || 0
      if (change > 0) return 'change-up'
      if (change < 0) return 'change-down'
      return 'change-same'
    }
  },

  methods: {
    handleTap() {
      this.triggerEvent('tap', {
        user: this.data.user,
        rank: this.data.rank
      })
    }
  }
})
