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

  data: {
    rankDisplay: '',
    rankClass: 'rank-normal',
    changeIcon: '-',
    changeClass: 'change-same',
    changeValue: 0
  },

  observers: {
    'rank, user': function (rank, user) {
      var rankDisplay = ''
      if (rank <= 3) {
        var icons = ['🥇', '🥈', '🥉']
        rankDisplay = icons[rank - 1] || rank
      } else {
        rankDisplay = rank
      }

      var rankClass = 'rank-normal'
      if (rank <= 3) rankClass = 'rank-top'
      else if (rank <= 10) rankClass = 'rank-high'

      var changeIcon = '-'
      var changeClass = 'change-same'
      var changeValue = 0
      if (user) {
        var change = user.change || 0
        changeValue = Math.abs(change)
        if (change > 0) {
          changeIcon = '↑'
          changeClass = 'change-up'
        } else if (change < 0) {
          changeIcon = '↓'
          changeClass = 'change-down'
        }
      }

      this.setData({
        rankDisplay: rankDisplay,
        rankClass: rankClass,
        changeIcon: changeIcon,
        changeClass: changeClass,
        changeValue: changeValue
      })
    }
  },

  methods: {
    handleTap: function () {
      this.triggerEvent('tap', {
        user: this.data.user,
        rank: this.data.rank
      })
    }
  }
})
