<script>
  import { api } from '../api.js';

  export let incident;
  export let refresh;
  export let notify;

  let busy = false;
  let signers = {}; // key -> 核对人
  let unwell = false;

  async function act(promise, okMsg) {
    busy = true;
    try {
      await promise;
      notify(okMsg, 'success');
      await refresh();
    } catch (e) {
      notify(e.message + (e.details?.length ? '\n' + e.details.map(d => '· ' + d).join('\n') : ''), 'error');
    } finally {
      busy = false;
    }
  }

  async function toggle(check) {
    const next = !check.checked;
    await act(
      api.setCheck(incident.id, {
        key: check.key,
        checked: next,
        checkedBy: next ? (signers[check.key] || '').trim() : null,
        remark: null
      }),
      next ? `「${check.label}」已核对` : `「${check.label}」已撤销`
    );
  }

  const completeRescue = () => act(api.completeRescue(incident.id, unwell), '救援完成已登记');
  const arrangeMedical = () => act(api.arrangeMedical(incident.id), '医疗救助已安排');
  const close = () => act(api.close(incident.id), '事件已关闭（电梯仍停用待复检）');

  const maintChecks = incident.checks.filter(c => c.party === '维保');
  const securityChecks = incident.checks.filter(c => c.party === '保安');
</script>

<div class="panel">
  <h3 class="block-title">救援完成与双方交接核对</h3>

  {#if incident.status === 'ON_SCENE'}
    <div class="rescue-bar">
      <label class="inline">
        <input type="checkbox" bind:checked={unwell} />
        获救人员出现身体不适（将立即升级 L2 并呼叫 120）
      </label>
      <button class="primary" disabled={busy} on:click={completeRescue}>人员已离梯 · 完成救援</button>
    </div>
  {:else if incident.status === 'RESCUED' || incident.status === 'CLOSED'}
    {#if incident.passengersUnwell}
      <div class="medical">
        <span class="badge red">获救人员不适</span>
        {#if incident.medicalAssistanceArranged}
          <span class="badge green">医疗救助已安排</span>
        {:else}
          <button class="sm danger" disabled={busy || incident.status === 'CLOSED'} on:click={arrangeMedical}>
            安排医疗救助（交接前必须完成）
          </button>
        {/if}
      </div>
    {/if}

    <div class="check-grid">
      <div class="check-col">
        <h4>🛠 维保核对</h4>
        {#each maintChecks as c (c.key)}
          <label class="check-item" class:done={c.checked}>
            <input type="checkbox" checked={c.checked}
                   disabled={busy || incident.status === 'CLOSED'}
                   on:change={() => toggle(c)} />
            <span class="ck-label">{c.label}</span>
            {#if !c.checked}
              <input class="signer" placeholder="核对人签名"
                     bind:value={signers[c.key]}
                     disabled={busy || incident.status === 'CLOSED'} />
            {:else}
              <span class="signed muted">✓ {c.checkedBy}</span>
            {/if}
          </label>
        {/each}
      </div>
      <div class="check-col">
        <h4>🛡 现场保安核对</h4>
        {#each securityChecks as c (c.key)}
          <label class="check-item" class:done={c.checked}>
            <input type="checkbox" checked={c.checked}
                   disabled={busy || incident.status === 'CLOSED'}
                   on:change={() => toggle(c)} />
            <span class="ck-label">{c.label}</span>
            {#if !c.checked}
              <input class="signer" placeholder="核对人签名"
                     bind:value={signers[c.key]}
                     disabled={busy || incident.status === 'CLOSED'} />
            {:else}
              <span class="signed muted">✓ {c.checkedBy}</span>
            {/if}
          </label>
        {/each}
      </div>
    </div>

    {#if incident.status === 'RESCUED'}
      <div class="close-bar">
        <div class="missing">
          {#if incident.missingItems.length > 0}
            <div class="missing-title">⚠ 交接缺项（{incident.missingItems.length}），事件不可关闭：</div>
            <ul>
              {#each incident.missingItems as m}
                <li>{m}</li>
              {/each}
            </ul>
          {:else}
            <span class="badge green">交接四项齐全，可关闭事件</span>
          {/if}
        </div>
        <button class="ok" disabled={busy || !incident.canClose} on:click={close}>
          交接齐全 · 关闭事件
        </button>
      </div>
      <p class="muted note">关闭仅代表救援交接完成，电梯仍保持停用；须经维保复检合格后才能恢复载客。</p>
    {/if}
  {:else}
    <p class="muted">维保与保安双方到场后，方可完成救援并进行交接核对。</p>
  {/if}
</div>

<style>
  .block-title { font-size: 14px; margin-bottom: 12px; }
  .rescue-bar { display: flex; justify-content: space-between; align-items: center; gap: 12px; flex-wrap: wrap; }
  .inline { display: flex; align-items: center; gap: 8px; color: var(--text); margin: 0; font-size: 13px; }
  .inline input { width: auto; }
  .medical { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; flex-wrap: wrap; }
  .check-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
  .check-col { background: var(--panel-2); border: 1px solid var(--border); border-radius: 8px; padding: 12px; }
  .check-col h4 { font-size: 13px; margin-bottom: 10px; }
  .check-item { display: flex; align-items: center; gap: 8px; padding: 8px 0; border-bottom: 1px dashed var(--border); margin: 0; }
  .check-item:last-child { border-bottom: none; }
  .check-item input[type=checkbox] { width: auto; }
  .ck-label { flex: 1; font-size: 13px; }
  .done .ck-label { color: #4ade80; }
  .signer { width: 110px; padding: 4px 8px; font-size: 12px; }
  .signed { font-size: 12px; white-space: nowrap; }
  .close-bar {
    display: flex; justify-content: space-between; align-items: flex-end; gap: 14px;
    margin-top: 14px; padding-top: 14px; border-top: 1px solid var(--border);
  }
  .missing-title { color: #fbbf24; font-size: 13px; font-weight: 500; margin-bottom: 4px; }
  .missing ul { margin: 4px 0 0; padding-left: 20px; color: #fbbf24; font-size: 12px; }
  .note { font-size: 12px; margin: 8px 0 0; }
</style>
