<script>
  import { createEventDispatcher } from 'svelte';
  import { fmtTime } from '../format.js';
  import EscalationBanner from './EscalationBanner.svelte';
  import DispatchPanel from './DispatchPanel.svelte';
  import HandoverPanel from './HandoverPanel.svelte';
  import RecheckPanel from './RecheckPanel.svelte';
  import TimelinePanel from './TimelinePanel.svelte';

  export let incident;
  export let refresh;
  export let notify;

  const dispatch = createEventDispatcher();
</script>

<div class="detail">
  <div class="head-row">
    <button class="ghost sm" on:click={() => dispatch('back')}>← 返回列表</button>
    <span class="muted mono">事件 #{incident.id}</span>
  </div>

  <div class="panel hero">
    <div class="hero-main">
      <h2>{incident.location}</h2>
      <div class="muted sub">{incident.building} · {incident.elevatorCode} · 维保：{incident.maintenanceVendor}</div>
    </div>
    <div class="hero-stats">
      <div class="stat">
        <span class="num trapped">{incident.trappedCount}</span>
        <span class="lbl">被困人数</span>
      </div>
      <div class="stat">
        <span class="badge {incident.callStatus === 'INTERRUPTED' ? 'red' : incident.callStatus === 'RESTORED' ? 'blue' : 'green'} big">
          {incident.callStatusLabel}
        </span>
        <span class="lbl">通话状态</span>
      </div>
      <div class="stat">
        <span class="badge {incident.status === 'CLOSED' ? 'green' : incident.status === 'ALARMED' ? 'red' : 'amber'} big">
          {incident.statusLabel}
        </span>
        <span class="lbl">事件状态</span>
      </div>
      <div class="stat">
        <span class="badge {incident.elevatorStatus === 'OUT_OF_SERVICE' ? 'red' : 'green'} big">
          {incident.elevatorStatusLabel}
        </span>
        <span class="lbl">设备状态</span>
      </div>
      <div class="stat">
        <span class="num">{incident.alarmCount}</span>
        <span class="lbl">报警次数</span>
      </div>
    </div>
  </div>

  <EscalationBanner {incident} />

  <div class="grid">
    <div class="col">
      <DispatchPanel {incident} {refresh} {notify} />
      <HandoverPanel {incident} {refresh} {notify} />
      <RecheckPanel {incident} {refresh} {notify} />
    </div>
    <div class="col">
      <TimelinePanel events={incident.timeline} />
      <div class="panel alarms">
        <h3 class="block-title">接警记录（{incident.alarms.length}）</h3>
        {#each incident.alarms as a, idx (a.id)}
          <div class="alarm-row">
            <span class="mono muted">{fmtTime(a.receivedAt)}</span>
            <span>第 {idx + 1} 次 · 被困 {a.trappedCount} 人</span>
            {#if a.merged}
              <span class="badge amber">已合并</span>
            {:else}
              <span class="badge blue">首发</span>
            {/if}
          </div>
        {/each}
      </div>
    </div>
  </div>
</div>

<style>
  .head-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
  .hero { display: flex; justify-content: space-between; gap: 20px; align-items: center; flex-wrap: wrap; margin-bottom: 14px; }
  .hero h2 { font-size: 20px; }
  .sub { font-size: 12px; margin-top: 4px; }
  .hero-stats { display: flex; gap: 26px; }
  .stat { display: flex; flex-direction: column; align-items: center; gap: 6px; }
  .stat .num { font-size: 26px; font-weight: 700; }
  .stat .num.trapped { color: #f87171; }
  .stat .lbl { font-size: 11px; color: var(--text-dim); }
  .badge.big { font-size: 12px; padding: 4px 12px; }
  .grid { display: grid; grid-template-columns: 1.15fr 1fr; gap: 14px; align-items: start; }
  .col { display: flex; flex-direction: column; gap: 14px; min-width: 0; }
  .block-title { font-size: 14px; margin-bottom: 10px; }
  .alarm-row { display: flex; gap: 10px; align-items: center; padding: 6px 0; font-size: 12px; border-bottom: 1px dashed var(--border); }
  .alarm-row:last-child { border-bottom: none; }
</style>
