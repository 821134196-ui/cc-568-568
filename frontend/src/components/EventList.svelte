<script>
  import { STATUS_TEXT, STATUS_COLOR, CALL_TEXT, fmtTime } from '../lib/format.js';

  export let events;
  export let selectedId;
  export let loading;

  import { createEventDispatcher } from 'svelte';
  const dispatch = createEventDispatcher();
</script>

<aside>
  <div class="list-head">救援事件（{events.filter(e => e.status !== 'CLOSED').length} 起进行中）</div>
  {#if loading}
    <div class="state">加载中…</div>
  {:else if events.length === 0}
    <div class="state">暂无事件</div>
  {:else}
    {#each events as e (e.id)}
      <div class="card" class:active={e.id === selectedId} on:click={() => dispatch('select', e.id)}>
        <div class="card-top">
          <span class="badge" data-color={STATUS_COLOR[e.status]}>{STATUS_TEXT[e.status]}</span>
          {#if e.escalationLevel && e.escalationLevel !== 'NONE'}
            <span class="esc">⬆ {e.escalationLevel}</span>
          {/if}
          {#if e.alarmCount > 1}
            <span class="merged">合并 ×{e.alarmCount}</span>
          {/if}
          <span class="time">{fmtTime(e.lastAlarmAt)}</span>
        </div>
        <!-- 报警页面优先显示：位置 / 被困人数 / 通话状态 -->
        <div class="loc">{e.location || e.elevatorId}</div>
        <div class="meta">
          <span class="elevator">{e.elevatorId}</span>
          <span class="people">👤 被困 <b>{e.trappedCount}</b> 人</span>
          <span class="call" data-int={e.callStatus === 'INTERRUPTED'}>
            {e.callStatus === 'CONNECTED' ? '📞' : e.callStatus === 'INTERRUPTED' ? '📵' : '☎️'}
            {CALL_TEXT[e.callStatus]}
          </span>
        </div>
      </div>
    {/each}
  {/if}
</aside>

<style>
  aside { background: var(--panel); border: 1px solid var(--border); border-radius: 10px; padding: 10px; }
  .list-head { font-size: 12px; color: var(--muted); padding: 4px 6px 8px; }
  .state { color: var(--muted); text-align: center; padding: 30px 0; font-size: 13px; }
  .card {
    border: 1px solid var(--border); border-radius: 8px; padding: 10px 12px;
    margin-bottom: 8px; cursor: pointer; background: var(--panel2); transition: border-color .12s;
  }
  .card:hover { border-color: #4a5c6e; }
  .card.active { border-color: var(--primary); box-shadow: 0 0 0 1px var(--primary); }
  .card-top { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
  .time { margin-left: auto; color: var(--muted); font-size: 11px; }
  .badge { font-size: 11px; padding: 2px 8px; border-radius: 10px; font-weight: 600; }
  .badge[data-color="red"] { background: rgba(248,81,73,.18); color: #ff7b72; }
  .badge[data-color="amber"] { background: rgba(210,153,34,.18); color: #e3b341; }
  .badge[data-color="blue"] { background: rgba(47,129,247,.18); color: #79b8ff; }
  .badge[data-color="purple"] { background: rgba(163,113,247,.18); color: #c8a8ff; }
  .badge[data-color="green"] { background: rgba(63,185,80,.18); color: #7ee787; }
  .esc { font-size: 11px; color: #ff7b72; font-weight: 700; }
  .merged { font-size: 11px; color: #e3b341; background: rgba(210,153,34,.12); padding: 1px 7px; border-radius: 10px; }
  .loc { font-weight: 600; font-size: 14.5px; margin-bottom: 5px; }
  .meta { display: flex; gap: 12px; font-size: 12.5px; color: var(--muted); flex-wrap: wrap; }
  .people b { color: var(--text); }
  .call[data-int="true"] { color: #ff7b72; font-weight: 600; }
</style>
