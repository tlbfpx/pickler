// Event card component
var util = require('../../utils/util')

Component({
  properties: {
    event: {
      type: Object,
      value: null
    },
    showType: {
      type: Boolean,
      value: true
    },
    showStatus: {
      type: Boolean,
      value: true
    }
  },

  data: {
    statusText: '',
    statusColor: '#999',
    eventTime: '',
    deadline: '',
    isFull: false,
    isOpen: false,
    typeText: '赛事',
    typeClass: 'type-party',
    remainingTime: ''
  },

  observers: {
    'event': function (event) {
      if (!event) return

      var statusText = util.formatEventStatus(event.status)
      var statusColor = util.getEventStatusColor(event.status)
      var eventTime = util.formatDate(event.eventTime, 'MM-DD HH:mm')
      var deadline = util.formatDate(event.registrationDeadline, 'MM-DD HH:mm')
      var isFull = event.currentParticipants >= event.maxParticipants
      var isOpen = util.isRegistrationOpen(event)

      var typeMap = { STAR: '明星赛事', PARTY: '派对活动' }
      var typeText = typeMap[event.type] || '赛事'
      var typeClass = event.type === 'STAR' ? 'type-star' : 'type-party'

      // Remaining time
      var remainingTime = ''
      if (event.registrationDeadline) {
        var now = new Date()
        var end = new Date(event.registrationDeadline)
        var diff = end.getTime() - now.getTime()
        if (diff > 0) {
          var days = Math.floor(diff / (1000 * 60 * 60 * 24))
          var hours = Math.floor((diff % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60))
          var minutes = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60))
          if (days > 0) {
            remainingTime = days + '天' + hours + '小时'
          } else if (hours > 0) {
            remainingTime = hours + '小时' + minutes + '分钟'
          } else {
            remainingTime = minutes + '分钟'
          }
        }
      }

      this.setData({
        statusText: statusText,
        statusColor: statusColor,
        eventTime: eventTime,
        deadline: deadline,
        isFull: isFull,
        isOpen: isOpen,
        typeText: typeText,
        typeClass: typeClass,
        remainingTime: remainingTime
      })
    }
  },

  methods: {
    handleTap: function () {
      this.triggerEvent('tap', {
        event: this.data.event
      })
    },

    handleRegister: function (e) {
      e.stopPropagation()
      this.triggerEvent('register', {
        event: this.data.event
      })
    }
  }
})
