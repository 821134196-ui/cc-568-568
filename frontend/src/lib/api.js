const BASE = '/api';

async function request(method, path, body) {
  const res = await fetch(BASE + path, {
    method,
    headers: body !== undefined ? { 'Content-Type': 'application/json' } : undefined,
    body: body !== undefined ? JSON.stringify(body) : undefined
  });
  const text = await res.text();
  const data = text ? JSON.parse(text) : null;
  if (!res.ok) {
    throw new Error(data?.error || `请求失败 (${res.status})`);
  }
  return data;
}

export const api = {
  listEvents: () => request('GET', '/events'),
  getEvent: (id) => request('GET', `/events/${id}`),
  elevators: () => request('GET', '/mock/elevators'),
  mockAlarm: (body) => request('POST', '/mock/alarm', body),
  manualAlarm: (body) => request('POST', '/alarms', body),
  dispatch: (id, body) => request('POST', `/events/${id}/dispatch`, body),
  ack: (id, role) => request('POST', `/events/${id}/ack`, { role }),
  arrived: (id, role) => request('POST', `/events/${id}/arrived`, { role }),
  setCall: (id, callStatus) => request('PUT', `/events/${id}/call-status`, { callStatus }),
  handover: (id, role, body) => request('POST', `/events/${id}/handover/${role}`, body),
  unwell: (id, body) => request('POST', `/events/${id}/unwell`, body),
  reinspect: (id, body) => request('POST', `/events/${id}/reinspect`, body),
  resume: (id) => request('POST', `/events/${id}/resume`),
  close: (id) => request('POST', `/events/${id}/close`)
};
