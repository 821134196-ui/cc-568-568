<script>
  import { api } from '../api.js';
  import { fmtClock } from '../format.js';

  export let incident;
  export let refresh;
  export let notify;

  let maintName = '安迅·王建国';
  let secName = '保安班·李志强';
  let maintEta = 30;
  let secEta = 15;
  let busy = false;

  async function act(promise, okMsg) {
    busy = true;
    try {
      await promise;
      notify(okMsg, 'success');
      await refresh();
    } catch (e) {
      notify(e.message + (e.details?.length ? '\n' + e.details.join('\n') : ''), 'error');
    } finally {
      busy = false;
    }
  }

  const dispatch = () =>
    act(api.dispatch(incident.id, {
      maintenanceAssignee: maintName,
      securityAssignee: secName,
      maintenanceEtaMinutes: Number(maintEta),
      securityEtaMinutes: Number(secEta)
    }), '已分派维保与现场保安');

  const ack = (team) => act(api.simAck(incident.id, team), `${team === 'MAINTENANCE' ? '维保' : '保安'}已接单回执`);
  const arrive = (team) => act(api.simArrive(incident.id, team), `${team === 'MAINTENANCE' ? '维保' : '保安'}到场已登记`);
  const interrupt = () => act(api.interruptCall(incident.id), '已记录通话中断并升级');
  const restore = () => act(api.restoreCall(incident.id), '通话已恢复');
</script>

<div class="panel">
  <h3 class="block-title">任务分派与到场跟进</h3>

  {#if incident.status === 'ALARMED'}
    <div class="dispatch-form">
      <div class="row">
        <div class="f grow">
          <label>维保接单人（{incident.maintenanceVendor}）</label>
          <input bind:value={maintName} placeholder="维保人员/班组" />
        </div>
        <div class="f eta">
          <label>到场时限(分)</label>
          <input type="number" min="1" bind:value={maintEta} />
        </div>
      </div>
      <div class="row">
        <div class="f grow">
          <label>现场保安接单人</label>
          <input bind:value={secName} placeholder="保安人员/班组" />
        </div>
        <div class="f eta">
          <label>到场时限(分)</label>
          <input type="number" min="1" bind:value={secEta} />
        </div>
      </div>
      <button class="primary" disabled={busy} on:click={dispatch}>▶ 分派救援任务</button>
    </div>
  {:else}
    <div class="teams">
      {#each incident.dispatches as d (d.team)}
        <div class="team-card">
          <div class="team-head">
            <strong>{d.teamLabel}</strong>
            <span class="badge {d.state === 'ARRIVED' ? (d.late ? 'amber' : 'green') : d.state === 'ACKNOWLEDGED' ? 'blue' : 'gray'}">
              {d.stateLabel}{d.state === 'ARRIVED' && d.late ? '（迟到）' : ''}
            </span>
          </div>
          <div class="muted who">{d.assignee} · 时限 {d.etaLimitMinutes} 分钟</div>
          <div class="times muted">
            <span>派单 {fmtClock(d.dispatchedAt)}</span>
            {#if d.acknowledgedAt}<span>接单 {fmtClock(d.acknowledgedAt)}</span>{/if}
            {#if d.arrivedAt}
              <span class="{d.late ? 'late' : 'ontime'}">
                到场 {fmtClock(d.arrivedAt)}（{d.elapsedMinutes} 分钟）
              </span>
            {/if}
          </div>
          <div class="actions">
            {#if d.state === 'ASSIGNED'}
              <button class="sm" disabled={busy} on:click={() => ack(d.team)}>模拟回执</button>
            {/if}
            {#if d.state !== 'ARRIVED' && incident.status !== 'CLOSED'}
              <button class="sm primary" disabled={busy} on:click={() => arrive(d.team)}>模拟到场</button>
            {/if}
          </div>
        </div>
      {/each}
    </div>
  {/if}

  <div class="call-bar">
    <span>五方通话：<span class="badge {incident.callStatus === 'INTERRUPTED' ? 'red' : incident.callStatus === 'RESTORED' ? 'blue' : 'green'}">{incident.callStatusLabel}</span></span>
    {#if incident.status !== 'CLOSED'}
      {#if incident.callStatus === 'INTERRUPTED'}
        <button class="sm" disabled={busy} on:click={restore}>通话恢复</button>
      {:else}
        <button class="sm danger" disabled={busy} on:click={interrupt}>标记通话中断（触发升级）</button>
      {/if}
    {/if}
  </div>
</div>

<style>
  .block-title { font-size: 14px; margin-bottom: 12px; }
  .dispatch-form .row { display: flex; gap: 10px; margin-bottom: 10px; }
  .f { min-width: 0; }
  .f.grow { flex: 1; }
  .f.eta { width: 110px; }
  .teams { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
  .team-card { background: var(--panel-2); border: 1px solid var(--border); border-radius: 8px; padding: 10px 12px; }
  .team-head { display: flex; justify-content: space-between; align-items: center; }
  .who { font-size: 12px; margin: 4px 0 6px; }
  .times { display: flex; flex-direction: column; gap: 2px; font-size: 12px; }
  .late { color: #fbbf24; }
  .ontime { color: #4ade80; }
  .actions { display: flex; gap: 6px; margin-top: 8px; }
  .call-bar {
    display: flex; justify-content: space-between; align-items: center;
    margin-top: 12px; padding-top: 12px; border-top: 1px solid var(--border);
    font-size: 13px;
  }
</style>
