<script>
  import { createEventDispatcher } from 'svelte';
  import { fmtTime } from '../format.js';
  import DispatchPills from './DispatchPills.svelte';

  export let incidents = [];
  export let loading = false;

  const dispatch = createEventDispatcher();

  const STATUS_CLASS = {
    ALARMED: 'red',
    DISPATCHED: 'amber',
    ON_SCENE: 'blue',
    RESCUED: 'amber',
    CLOSED: 'green'
  };

  const CALL_CLASS = {
    CONNECTED: 'green',
    INTERRUPTED: 'red',
    RESTORED: 'blue'
  };
</script>

<div class="panel list-panel">
  <div class="list-head">
    <h2>接警工作台</h2>
    <span class="muted">共 {incidents.length} 起事件 · 每 4 秒自动刷新</span>
  </div>

  {#if loading}
    <p class="muted empty">加载中…</p>
  {:else if incidents.length === 0}
    <div class="empty-state">
      <div class="empty-icon">✅</div>
      <p>当前无未关闭的困人报警</p>
      <span class="muted">点击右上角「模拟接口」可推送一条测试报警</span>
    </div>
  {:else}
    <table>
      <thead>
        <tr>
          <th style="width:52px;">#</th>
          <th>电梯位置</th>
          <th style="width:80px;">被困人数</th>
          <th style="width:96px;">通话状态</th>
          <th>响应跟进</th>
          <th style="width:96px;">事件状态</th>
          <th style="width:140px;">报警时间</th>
        </tr>
      </thead>
      <tbody>
        {#each incidents as inc (inc.id)}
          <tr class="clickable" on:click={() => dispatch('open', inc.id)}>
            <td class="mono">{inc.id}</td>
            <td>
              <div class="loc-main">
                <strong>{inc.location}</strong>
                {#if inc.alarmCount > 1}
                  <span class="badge amber" title="重复报警已合并到本次救援">重复报警 ×{inc.alarmCount}</span>
                {/if}
                {#if inc.escalationLevel > 0}
                  <span class="badge red">⬆ 已升级 L{inc.escalationLevel}</span>
                {/if}
              </div>
              <div class="muted sub">{inc.building} · {inc.elevatorCode} · {inc.maintenanceVendor}</div>
            </td>
            <td>
              <span class="trapped">{inc.trappedCount}</span> 人
            </td>
            <td>
              <span class="badge {CALL_CLASS[inc.callStatus]}">{inc.callStatusLabel}</span>
            </td>
            <td>
              <DispatchPills inc={inc} />
            </td>
            <td>
              <span class="badge {STATUS_CLASS[inc.status]}">{inc.statusLabel}</span>
              {#if inc.elevatorStatus === 'OUT_OF_SERVICE'}
                <div class="locked">🔒 停用中</div>
              {/if}
            </td>
            <td class="mono muted">{fmtTime(inc.openedAt)}</td>
          </tr>
        {/each}
      </tbody>
    </table>
  {/if}
</div>

<style>
  .list-head { display: flex; justify-content: space-between; align-items: baseline; margin-bottom: 12px; }
  .list-head h2 { font-size: 16px; }
  .empty { padding: 30px 0; text-align: center; }
  .empty-state { text-align: center; padding: 48px 0 40px; }
  .empty-icon { font-size: 40px; margin-bottom: 10px; }
  .empty-state p { margin: 6px 0; font-size: 15px; }
  .loc-main { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
  .sub { font-size: 12px; margin-top: 3px; }
  .trapped { font-size: 18px; font-weight: 700; color: #f87171; }
  .locked { font-size: 11px; color: #fbbf24; margin-top: 4px; }
</style>
