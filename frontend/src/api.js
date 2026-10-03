const BASE = '/api';

async function request(method, path, body) {
  const res = await fetch(BASE + path, {
    method,
    headers: body !== undefined ? { 'Content-Type': 'application/json' } : undefined,
    body: body !== undefined ? JSON.stringify(body) : undefined
  });
  if (!res.ok) {
    let payload = null;
    try { payload = await res.json(); } catch { /* 非 JSON 错误 */ }
    const message = payload?.error || `请求失败 (${res.status})`;
    const err = new Error(message);
    err.details = payload?.details || [];
    err.status = res.status;
    throw err;
  }
  return res.status === 204 ? null : res.json();
}

export const api = {
  listIncidents: (includeClosed = false) =>
    request('GET', `/incidents?includeClosed=${includeClosed}`),
  getIncident: (id) => request('GET', `/incidents/${id}`),
  listElevators: () => request('GET', '/elevators'),

  dispatch: (id, payload) => request('POST', `/incidents/${id}/dispatch`, payload),
  interruptCall: (id) => request('POST', `/incidents/${id}/call/interrupt`),
  restoreCall: (id) => request('POST', `/incidents/${id}/call/restore`),
  completeRescue: (id, passengersUnwell) =>
    request('POST', `/incidents/${id}/rescue-complete`, { passengersUnwell }),
  arrangeMedical: (id) => request('POST', `/incidents/${id}/medical`),
  setCheck: (id, payload) => request('POST', `/incidents/${id}/checks`, payload),
  close: (id) => request('POST', `/incidents/${id}/close`),
  recheck: (id, payload) => request('POST', `/incidents/${id}/recheck`, payload),
  resume: (id) => request('POST', `/incidents/${id}/resume`),

  // 本地模拟接口
  simAlarm: (payload) => request('POST', '/sim/alarms', payload),
  simAck: (incidentId, team) => request('POST', '/sim/ack', { incidentId, team }),
  simArrive: (incidentId, team, delayMinutes) =>
    request('POST', '/sim/arrive', { incidentId, team, delayMinutes: delayMinutes ?? null }),
  simEscalationCheck: () => request('POST', '/sim/escalation-check')
};
