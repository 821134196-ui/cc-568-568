<script>
  import { fmtClock } from '../format.js';

  export let events = [];

  const PHASE_DOT = {
    ALARM: 'red',
    RESPONSE: 'blue',
    RESCUE: 'amber',
    ESCALATION: 'red',
    HANDOVER: 'purple',
    RECHECK: 'green'
  };
</script>

<div class="panel">
  <h3 class="block-title">事件时间线</h3>
  {#if events.length === 0}
    <p class="muted">暂无记录</p>
  {:else}
    <ol class="timeline">
      {#each [...events].reverse() as e (e.id)}
        <li>
          <span class="dot {PHASE_DOT[e.phase] || 'gray'}"></span>
          <div class="item">
            <div class="line">
              <span class="phase badge gray">{e.phaseLabel}</span>
              <span class="msg" class:escalation={e.phase === 'ESCALATION'}>{e.message}</span>
            </div>
            <div class="meta muted">
              <span class="mono">{fmtClock(e.occurredAt)}</span>
              · {e.actor || '系统'}
            </div>
          </div>
        </li>
      {/each}
    </ol>
  {/if}
</div>

<style>
  .block-title { font-size: 14px; margin-bottom: 12px; }
  .timeline { list-style: none; margin: 0; padding: 0; }
  .timeline li { display: flex; gap: 10px; padding: 7px 0; position: relative; }
  .dot {
    width: 9px; height: 9px; border-radius: 50%; margin-top: 6px; flex: none;
    background: var(--text-dim);
  }
  .dot.red { background: var(--danger); box-shadow: 0 0 6px rgba(239,68,68,.6); }
  .dot.blue { background: var(--primary); }
  .dot.amber { background: var(--warn); }
  .dot.green { background: var(--ok); }
  .dot.purple { background: #a855f7; }
  .item { flex: 1; }
  .line { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
  .msg { font-size: 13px; }
  .msg.escalation { color: #f87171; font-weight: 500; }
  .meta { font-size: 11px; margin-top: 2px; display: flex; gap: 5px; }
</style>
