<script>
  import { api } from '../api.js';

  export let elevators = [];
  export let refresh;
  export let notify;

  let elevatorCode = '';
  let trappedCount = 1;
  let note = '';
  let busy = false;

  // 模拟回执针对最近一起事件
  async function sendAlarm() {
    if (!elevatorCode) {
      notify('请先选择电梯', 'error');
      return;
    }
    busy = true;
    try {
      const res = await api.simAlarm({
        elevatorCode,
        trappedCount: Number(trappedCount),
        note: note || null
      });
      if (res.merged) {
        notify(`第 ${res.alarmCount} 次报警已合并到事件 #${res.incidentId}，未重复派队`, 'success');
      } else {
        notify(`新报警已接收，创建事件 #${res.incidentId}（${res.incident.location}）`, 'success');
      }
      note = '';
      await refresh();
    } catch (e) {
      notify(e.message, 'error');
    } finally {
      busy = false;
    }
  }
</script>

<div class="panel sim">
  <h3>📡 本地模拟接口</h3>
  <div class="muted hint">
    模拟物联平台/五方通话推送困人报警（POST /api/sim/alarms）。
    对同一台电梯在事件未关闭前再次推送，将验证<strong>重复报警合并</strong>。
  </div>
  <div class="sim-form">
    <div class="field">
      <label>电梯</label>
      <select bind:value={elevatorCode}>
        <option value="">请选择…</option>
        {#each elevators as e}
          <option value={e.code}>{e.code} · {e.location}</option>
        {/each}
      </select>
    </div>
    <div class="field narrow">
      <label>被困人数</label>
      <input type="number" min="0" bind:value={trappedCount} />
    </div>
    <div class="field grow">
      <label>通话备注（可选）</label>
      <input placeholder="如：老人一名，情绪稳定" bind:value={note} />
    </div>
    <div class="field btn">
      <button class="primary" disabled={busy} on:click={sendAlarm}>推送困人报警</button>
    </div>
  </div>
</div>

<style>
  .sim { margin-bottom: 16px; border-color: #3b4a72; }
  .sim h3 { font-size: 14px; margin-bottom: 6px; }
  .hint { font-size: 12px; margin-bottom: 12px; }
  .sim-form { display: flex; gap: 12px; align-items: flex-end; flex-wrap: wrap; }
  .field { min-width: 220px; }
  .field.narrow { min-width: 100px; width: 100px; }
  .field.grow { flex: 1; min-width: 200px; }
  .field.btn { min-width: 0; }
</style>
