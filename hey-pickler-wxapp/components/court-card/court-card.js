// Court card component (venue booking browse)
import { COURT_TYPE } from '../../utils/terms'

Component({
  properties: {
    court: {
      type: Object,
      value: null
    },
    selected: {
      type: Boolean,
      value: false
    }
  },

  data: {
    typeText: '',
    typeColor: '#9CA3AF',
    slotText: '',
    statusText: '',
    isActive: true
  },

  observers: {
    'court': function (court) {
      if (!court) return

      var typeText = ''
      var typeColor = '#9CA3AF'
      if (court.courtType && COURT_TYPE[court.courtType]) {
        typeText = COURT_TYPE[court.courtType].label
        typeColor = COURT_TYPE[court.courtType].color
      }

      var slotText = ''
      if (court.slotMinutes) {
        slotText = court.slotMinutes + '分钟/格'
      }

      var isActive = court.status === 'ACTIVE'
      var statusText = isActive ? '可预订' : '暂停'

      this.setData({
        typeText: typeText,
        typeColor: typeColor,
        slotText: slotText,
        statusText: statusText,
        isActive: isActive
      })
    }
  },

  methods: {
    handleTap: function () {
      this.triggerEvent('select', {
        court: this.data.court
      })
    }
  }
})
