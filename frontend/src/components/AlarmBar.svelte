<script>
  import { createEventDispatcher } from 'svelte';
  import { api } from '../lib/api.js';

  const dispatch = createEventDispatcher();

  let elevators = [];
  let elevatorId = '';
  let location = '';
  let trappedCount = 1;
  let callInterrupted = false;
  let sending = false;
  let msg = '';
  let msgKind = 'ok';

  api.elevators().then(list => {
    elevators = list;
    elevatorId = list[0]?.elevatorId || '';
    location = list[0]?.location || '';
  });

  function pickElevator(id) {
    elevatorId = id;
    location = elevators.find(e => e.elevatorId === id)?.location || '';
  }

  async function sendAlarm() {
    if (!elevatorId) { msg = '请选择电梯'; msgKind = 'err'; return; }
    sending = true; msg = '';
    try {
      const ev = await api.mockAlarm({ elevatorId, location, trappedCount: Number(trappedCount), callInterrupted });
      msg = `报警已接入：事件 ${ev.eventNo}（同一电梯重复报警将自动合并）`;
      msgKind = 'ok';
      callInterrupted = false;
      dispatch('changed');
    } catch (e) {
      msg = e.message; msgKind = 'err';
    } finally {
      sending = false;
    }
  }
</script>

<div class="alarm-bar">
  <div class="title">🚨 报警接入 <span class="hint">模拟五方对讲/告警平台推送</span></div>
  <div class="form">
    <div class="field">
      <label>电梯</label>
      <select value={elevatorId} on:change={(e) => pickElevator(e.target.value)}>
        {#each elevators as el}
          <option value={el.elevatorId}>{el.elevatorId} · {el.location}</option>
        {/each}
      </select>
    </div>
    <div class="field grow">
      <label>位置</label>
      <input bind:value={location} />
    </div>
    <div class="field small-field">
      <label>被困人数</label>
      <input type="number" min="1" bind:value={trappedCount} />
    </div>
    <div class="field check">
      <label>&nbsp;</label>
      <label class="chk"><input type="checkbox" bind:checked={callInterrupted} /> 通话中断</label>
    </div>
    <div class="field btn">
      <label>&nbsp;</label>
      <button class="danger" disabled={sending} on:click={sendAlarm}>
        {sending ? '接入中…' : '接入困人报警'}
      </button>
    </div>
  </div>
  {#if msg}
    <div class="msg {msgKind === 'err' ? 'err' : 'ok'}">{msg}</div>
  {/if}
</div>

<style>
  .alarm-bar {
    background: var(--panel); border: 1px solid var(--border); border-radius: 10px;
    padding: 12px 16px 8px;
  }
  .title { font-weight: 600; margin-bottom: 10px; }
  .hint { color: var(--muted); font-weight: 400; font-size: 12px; margin-left: 8px; }
  .form { display: flex; gap: 10px; align-items: flex-end; flex-wrap: wrap; }
  .field { min-width: 130px; }
  .field.grow { flex: 1; min-width: 180px; }
  .small-field { max-width: 100px; min-width: 90px; }
  .field.btn { min-width: 130px; }
  .chk { display: flex; align-items: center; gap: 6px; color: var(--text); white-space: nowrap; margin: 0; }
  .chk input { width: auto; }
  .msg { margin: 8px 0 6px; font-size: 12.5px; padding: 6px 10px; border-radius: 6px; }
  .msg.ok { background: rgba(63,185,80,.12); color: #7ee787; }
  .msg.err { background: rgba(248,81,73,.12); color: #ffb4b0; }
</style>
