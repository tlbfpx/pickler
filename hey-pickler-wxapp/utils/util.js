/**
 * Format date to string
 * @param {Date|string} date - Date object or date string
 * @param {string} format - Format string (default: 'YYYY-MM-DD HH:mm:ss')
 */
function formatDate(date, format = 'YYYY-MM-DD HH:mm:ss') {
  if (!date) return ''

  const d = new Date(date)
  if (isNaN(d.getTime())) return ''

  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const hours = String(d.getHours()).padStart(2, '0')
  const minutes = String(d.getMinutes()).padStart(2, '0')
  const seconds = String(d.getSeconds()).padStart(2, '0')

  return format
    .replace('YYYY', year)
    .replace('MM', month)
    .replace('DD', day)
    .replace('HH', hours)
    .replace('mm', minutes)
    .replace('ss', seconds)
}

/**
 * Format date to relative time (e.g., "3天前", "2小时前")
 * @param {Date|string} date - Date object or date string
 */
function formatRelativeTime(date) {
  if (!date) return ''

  const d = new Date(date)
  if (isNaN(d.getTime())) return ''

  const now = new Date()
  const diff = now.getTime() - d.getTime()
  const seconds = Math.floor(diff / 1000)
  const minutes = Math.floor(seconds / 60)
  const hours = Math.floor(minutes / 60)
  const days = Math.floor(hours / 24)

  if (days > 7) {
    return formatDate(date, 'YYYY-MM-DD')
  } else if (days > 0) {
    return `${days}天前`
  } else if (hours > 0) {
    return `${hours}小时前`
  } else if (minutes > 0) {
    return `${minutes}分钟前`
  } else {
    return '刚刚'
  }
}

/**
 * Format tier to display name
 * @param {string} tier - Tier code (LEGEND/SUPER/SHINING)
 * @param {string} type - Type (STAR/PARTY)
 */
function formatTier(tier, type = 'STAR') {
  const tierMap = {
    LEGEND: '传奇',
    SUPER: '超级',
    SHINING: '闪耀'
  }

  const typeSuffix = type === 'STAR' ? '明星' : '派对'
  return tierMap[tier] ? `${tierMap[tier]}${typeSuffix}` : '未知'
}

/**
 * Get tier color class
 * @param {string} tier - Tier code (LEGEND/SUPER/SHINING)
 */
function getTierClass(tier) {
  const classMap = {
    LEGEND: 'tier-legend',
    SUPER: 'tier-super',
    SHINING: 'tier-shining'
  }
  return classMap[tier] || 'tier-shining'
}

/**
 * Format event status to display name
 * @param {string} status - Status code
 */
function formatEventStatus(status) {
  const statusMap = {
    DRAFT: '草稿',
    OPEN: '报名中',
    FULL: '已满员',
    IN_PROGRESS: '进行中',
    COMPLETED: '已结束',
    CANCELLED: '已取消'
  }
  return statusMap[status] || status
}

/**
 * Get event status color
 * @param {string} status - Status code
 */
function getEventStatusColor(status) {
  const colorMap = {
    DRAFT: '#999',
    OPEN: '#4CAF50',
    FULL: '#FF9800',
    IN_PROGRESS: '#2196F3',
    COMPLETED: '#666',
    CANCELLED: '#f44336'
  }
  return colorMap[status] || '#999'
}

/**
 * Format points display
 * @param {number} points - Points value
 */
function formatPoints(points) {
  if (points === null || points === undefined) return '0'
  return points.toString()
}

/**
 * Check if event registration is open
 * @param {object} event - Event object
 */
function isRegistrationOpen(event) {
  if (!event) return false
  if (event.status !== 'OPEN') return false
  if (event.currentParticipants >= event.maxParticipants) return false

  const now = new Date()
  const deadline = new Date(event.registrationDeadline)
  return now < deadline
}

/**
 * Calculate remaining registration time
 * @param {Date|string} deadline - Registration deadline
 */
function getRemainingTime(deadline) {
  if (!deadline) return null

  const now = new Date()
  const end = new Date(deadline)
  const diff = end.getTime() - now.getTime()

  if (diff <= 0) return null

  const days = Math.floor(diff / (1000 * 60 * 60 * 24))
  const hours = Math.floor((diff % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60))
  const minutes = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60))

  if (days > 0) {
    return `${days}天${hours}小时`
  } else if (hours > 0) {
    return `${hours}小时${minutes}分钟`
  } else {
    return `${minutes}分钟`
  }
}

/**
 * Show loading toast
 * @param {string} title - Loading message
 */
function showLoading(title = '加载中...') {
  wx.showLoading({
    title,
    mask: true
  })
}

/**
 * Hide loading toast
 */
function hideLoading() {
  wx.hideLoading()
}

/**
 * Show success toast
 * @param {string} title - Success message
 */
function showSuccess(title = '操作成功') {
  wx.showToast({
    title,
    icon: 'success',
    duration: 2000
  })
}

/**
 * Show error toast
 * @param {string} title - Error message
 */
function showError(title = '操作失败') {
  wx.showToast({
    title,
    icon: 'none',
    duration: 2000
  })
}

/**
 * Show confirm dialog
 * @param {object} options - Confirm options
 */
function showConfirm(options = {}) {
  return new Promise((resolve) => {
    wx.showModal({
      title: options.title || '提示',
      content: options.content || '',
      confirmText: options.confirmText || '确定',
      cancelText: options.cancelText || '取消',
      success: (res) => {
        resolve(res.confirm)
      }
    })
  })
}

export default {
  formatDate,
  formatRelativeTime,
  formatTier,
  getTierClass,
  formatEventStatus,
  getEventStatusColor,
  formatPoints,
  isRegistrationOpen,
  getRemainingTime,
  showLoading,
  hideLoading,
  showSuccess,
  showError,
  showConfirm
}
