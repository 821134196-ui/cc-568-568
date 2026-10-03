<script>
  import { api } from '../api.js';
  import { fmtTime } from '../format.js';

  export let incident;
  export let refresh;
  export let notify;

  let inspector = '安迅·王建国';
  let note = '';
  let busy = false;

  async function act(promise, okMsg) {
    busy = true;
    try {
      await promise;
      notify(okMsg, 'success');
      await refresh();
    } catch (e) {
      notify(e.message, 'error');
    } finally {
      busy = false;
    }
  }

  const recheck = (passed) =>
    act(api.recheck(incident.id, { inspector, passed, note: note || null }),
      passed ? '复检合格已登记' : '复检不合格，电梯继续停用');
  const resume = () => act(api.resume(incident.id), '电梯已恢复载客运行');
</script>

<div class="panel">
  <h3 class="block-title">维保复检与恢复载客</h3>

  {#if incident.status !== 'CLOSED'}
    <p class="muted">事件关闭后，由维保对设备进行复检。复检合格是恢复载客的必要条件。</p>
  {:else}
    <div class="lock-state">
      {#if incident.elevatorStatus === 'OUT_OF_SERVICE'}
        <span class="badge red">🔒 设备停用中</span>
        <span class="muted">救援结束 ≠ 设备可运行，未通过复检前禁止恢复载客</span>
      {:else}
        <span class="badge green">✅ 载客运行中</span>
        <span class="muted">已于复检合格后恢复（{fmtTime(incident.recheckedAt)}）</span>
      {/if}
    </div>

    {#if incident.recheckPassed && incident.elevatorStatus === 'OUT_OF_SERVICE'}
      <div class="passed-bar">
        <span class="badge green">复检合格 · {incident.recheckInspector}</span>
        <button class="ok" disabled={busy} on:click={resume}>恢复载客运行</button>
      </div>
    {:else if !incident.recheckPassed}
      <div class="recheck-form">
        <div class="row">
          <div class="f grow">
            <label>复检人（维保）</label>
            <input bind:value={inspector} placeholder="姓名/工号" />
          </div>
          <div class="f grow2">
            <label>复检说明（不合格时填写故障/处理）</label>
            <input bind:value={note} placeholder="如：门锁回路已修复，试运行正常" />
          </div>
        </div>
        <div class="btns">
          <button class="ok" disabled={busy || !inspector.trim()} on:click={() => recheck(true)}>复检合格</button>
          <button class="danger" disabled={busy || !inspector.trim()} on:click={() => recheck(false)}>复检不合格（继续停用）</button>
        </div>
      </div>
    {/if}

    {#if incident.recheckNote}
      <p class="muted rnote">复检备注：{incident.recheckNote}</p>
    {/if}
  {/if}
</div>

<style>
  .block-title { font-size: 14px; margin-bottom: 12px; }
  .lock-state { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; margin-bottom: 12px; }
  .recheck-form .row { display: flex; gap: 10px; margin-bottom: 10px; }
  .f.grow { flex: 1; min-width: 140px; }
  .f.grow2 { flex: 2; min-width: 200px; }
  .btns { display: flex; gap: 8px; }
  .passed-bar { display: flex; justify-content: space-between; align-items: center; gap: 10px; }
  .rnote { font-size: 12px; margin-bottom: 0; }
</style>
