<script>
  import { TL_TEXT, fmtTime } from '../lib/format.js';

  export let entries = [];

  const TYPE_COLOR = {
    ALARM: 'red',
    ALARM_MERGED: 'amber',
    DISPATCHED: 'blue',
    ACK: 'gray',
    ARRIVED: 'blue',
    CALL_CHANGED: 'amber',
    UPGRADED: 'red',
    RESCUE_UPDATE: 'purple',
    LOCKOUT: 'amber',
    UNWELL: 'red',
    REINSPECTED: 'green',
    RESUME_BLOCKED: 'red',
    CLOSE_BLOCKED: 'red',
    RESUMED: 'green',
    CLOSED: 'green'
  };
</script>

<section class="panel">
  <h3>事件时间线</h3>
  {#if entries.length === 0}
    <div class="muted small">暂无记录</div>
  {:else}
    <ul>
      {#each [...entries].reverse() as t}
        <li>
          <span class="dot" data-c={TYPE_COLOR[t.type] || 'gray'}></span>
          <div class="body">
            <div class="line">
              <span class="tag" data-c={TYPE_COLOR[t.type] || 'gray'}>{TL_TEXT[t.type] || t.type}</span>
              <span class="actor">{t.actor}</span>
              <span class="at">{fmtTime(t.at)}</span>
            </div>
            <div class="detail">{t.detail}</div>
          </div>
        </li>
      {/each}
    </ul>
  {/if}
</section>

<style>
  .panel { background: var(--panel); border: 1px solid var(--border); border-radius: 10px; padding: 14px 16px; }
  h3 { margin: 0 0 12px; font-size: 15px; }
  ul { list-style: none; margin: 0; padding: 0; }
  li { position: relative; padding: 0 0 14px 20px; }
  li::before {
    content: ''; position: absolute; left: 4px; top: 14px; bottom: -2px;
    width: 2px; background: var(--border);
  }
  li:last-child::before { display: none; }
  .dot { position: absolute; left: 0; top: 5px; width: 10px; height: 10px; border-radius: 50%; }
  .dot[data-c="red"] { background: var(--red); }
  .dot[data-c="amber"] { background: var(--amber); }
  .dot[data-c="blue"] { background: var(--primary); }
  .dot[data-c="green"] { background: var(--green); }
  .dot[data-c="purple"] { background: var(--purple); }
  .dot[data-c="gray"] { background: #647483; }
  .line { display: flex; align-items: center; gap: 8px; }
  .tag { font-size: 11px; padding: 1px 8px; border-radius: 9px; font-weight: 600; }
  .tag[data-c="red"] { background: rgba(248,81,73,.18); color: #ff7b72; }
  .tag[data-c="amber"] { background: rgba(210,153,34,.18); color: #e3b341; }
  .tag[data-c="blue"] { background: rgba(47,129,247,.18); color: #79b8ff; }
  .tag[data-c="green"] { background: rgba(63,185,80,.18); color: #7ee787; }
  .tag[data-c="purple"] { background: rgba(163,113,247,.18); color: #c8a8ff; }
  .tag[data-c="gray"] { background: rgba(100,116,131,.2); color: #94a7b8; }
  .actor { font-size: 12px; color: #b8c5d0; }
  .at { margin-left: auto; font-size: 11px; color: var(--muted); }
  .detail { font-size: 12.5px; color: var(--muted); margin-top: 3px; }
</style>
