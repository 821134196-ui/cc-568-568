export const STATUS_TEXT = {
  ALARMED: '待分派',
  DISPATCHED: '已分派·到场中',
  ON_SCENE: '现场救援中',
  RESCUED: '人员已获救',
  CLOSED: '事件已关闭'
};

export const STATUS_COLOR = {
  ALARMED: 'red',
  DISPATCHED: 'amber',
  ON_SCENE: 'blue',
  RESCUED: 'purple',
  CLOSED: 'green'
};

export const CALL_TEXT = {
  CONNECTED: '通话正常',
  INTERRUPTED: '通话中断',
  RESTORED: '通话恢复'
};

export const EQUIP_TEXT = {
  RUNNING: '运行中',
  LOCKED_OUT: '停用中（待复检）',
  INSPECTED: '复检合格·待恢复',
  RESUMED: '已恢复载客'
};

export const TL_TEXT = {
  ALARM: '报警接入',
  ALARM_MERGED: '重复报警合并',
  DISPATCHED: '任务分派',
  ACK: '接单回执',
  ARRIVED: '到场',
  CALL_CHANGED: '通话状态',
  UPGRADED: '升级',
  RESCUE_UPDATE: '交接核对',
  LOCKOUT: '设备停用',
  UNWELL: '人员不适',
  REINSPECTED: '设备复检',
  RESUME_BLOCKED: '阻止恢复载客',
  CLOSE_BLOCKED: '阻止关闭',
  RESUMED: '恢复载客',
  CLOSED: '事件关闭'
};

export function fmtTime(iso) {
  if (!iso) return '—';
  const d = new Date(iso);
  const p = (n) => String(n).padStart(2, '0');
  return `${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`;
}

export function fmtDur(ms) {
  if (ms < 0) ms = 0;
  const s = Math.floor(ms / 1000);
  const m = Math.floor(s / 60);
  return m > 0 ? `${m}分${s % 60}秒` : `${s}秒`;
}
