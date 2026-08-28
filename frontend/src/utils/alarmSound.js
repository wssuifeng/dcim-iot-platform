/*
 * 类功能:告警音效工具——Web Audio API 合成短促双音（不引音频文件），
 *   告警触发/升级时提醒，恢复不响；开关持久化 localStorage
 *
 * 【自动播放策略】AudioContext 需用户交互后才能出声：
 *   首次调用 playAlarmSound 时懒创建并 resume，若仍被浏览器拒绝则静默跳过
 */
const MUTE_KEY = 'alarm-sound-muted'

let audioCtx = null

export function isAlarmSoundMuted() {
  try {
    return localStorage.getItem(MUTE_KEY) === '1'
  } catch {
    return false
  }
}

export function setAlarmSoundMuted(muted) {
  try {
    localStorage.setItem(MUTE_KEY, muted ? '1' : '0')
  } catch {
    /* 隐私模式等存储不可用时静默降级 */
  }
}

function ensureAudioContext() {
  if (audioCtx) return audioCtx
  const Ctx = window.AudioContext || window.webkitAudioContext
  if (!Ctx) return null
  audioCtx = new Ctx()
  return audioCtx
}

/*
 * 函数功能:播放告警双音（880Hz 短音 + 660Hz 短音，总时长约 0.32s）
 * 参数:无
 * 返回数据:无
 */
export function playAlarmSound() {
  if (isAlarmSoundMuted()) return
  const ctx = ensureAudioContext()
  if (!ctx) return
  if (ctx.state === 'suspended') {
    ctx.resume().catch(() => {})
    // resume 异步，可能本次数静音——可接受，下次交互后正常
  }
  const now = ctx.currentTime
  const tone = (freq, startAt, duration) => {
    const osc = ctx.createOscillator()
    const gain = ctx.createGain()
    osc.type = 'sine'
    osc.frequency.value = freq
    // 快起快落包络，避免爆音
    gain.gain.setValueAtTime(0, startAt)
    gain.gain.linearRampToValueAtTime(0.18, startAt + 0.02)
    gain.gain.linearRampToValueAtTime(0, startAt + duration)
    osc.connect(gain)
    gain.connect(ctx.destination)
    osc.start(startAt)
    osc.stop(startAt + duration + 0.02)
  }
  tone(880, now, 0.12)
  tone(660, now + 0.18, 0.14)
}
