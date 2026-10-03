<script>
  import { onMount, onDestroy } from 'svelte';
  import { api } from '../lib/api.js';
  import { STATUS_TEXT, STATUS_COLOR, CALL_TEXT, EQUIP_TEXT, fmtTime, fmtDur } from '../lib/format.js';
  import Timeline from './Timeline.svelte';

  export let event;
  import { createEventDispatcher } from 'svelte';
  const dispatch = createEventDispatcher();

  let actionError = '';
  let now = Date.now();
  let timer;
  onMount(() => { timer = setInterval(() => now = Date.now(), 1000); });
  onDestroy(() => clearInterval(timer));

  // 分派表单
  let mTeam = '永通电梯维保一队';
  let guard = '李保安';
  let eta = 20;

  // 交接表单（维保 / 保安）
  let mName = '', gName = '';
  let mDoor = false, mExit = false, mStop = false;
  let gDoor = false, gExit = false, gStop = false;

  // 不适 / 复检
  let unwellNote = '';
  let inspector = '王检验';

  async function run(fn) {
    actionError = '';
    try {
      await fn();
      dispatch('changed');
    } catch (e) {
      actionError = e.message;
    }
  }

  $: d = event.dispatch;
  $: h = event.handover;
  $: isClosed = event.status === 'CLOSED';

  $: remaining = event.slaDueAt ? new Date(event.slaDueAt).getTime() - now : null;
  $: slaText = remaining === null ? '' : remaining > 0 ? '剩余 ' + fmtDur(remaining) : '已迟到 ' + fmtDur(-remaining);

  const handoverOk = (name, a, b, c) => name.trim() && a && b && c;
</script>

<div class="detail">
  <!-- 头部：位置/人数/通话优先 -->
  <section class="head">
    <div class="head-row">
      <div>
        <span class="badge" data-color={STATUS_COLOR[event.status]}>{STATUS_TEXT[event.status]}</span>
        {#if event.escalationLevel && event.escalationLevel !== 'NONE'}
          <span class="esc">⬆ 已升级 {event.escalationLevel}</span>
        {/if}
        {#if event.maintenanceLate}<span class="late">维保迟到</span>{/if}
      </div>
      <span class="no">{event.eventNo}</span>
    </div>
    <h2>{event.location || event.elevatorId} <small>{event.elevatorId}</small></h2>
    <div class="kv">
      <div class="kv-item">
        <span class="kv-label">被困人数</span>
        <span class="kv-value big">{event.trappedCount} 人</span>
      </div>
      <div class="kv-item">
        <span class="kv-label">通话状态</span>
        <span class="kv-value" class:int={event.callStatus === 'INTERRUPTED'}>
          {event.callStatus === 'CONNECTED' ? '📞' : event.callStatus === 'INTERRUPTED' ? '📵' : '☎️'}
          {CALL_TEXT[event.callStatus]}
        </span>
      </div>
      <div class="kv-item">
        <span class="kv-label">设备状态</span>
        <span class="kv-value" class:lock={event.equipmentStatus === 'LOCKED_OUT'}>
          {EQUIP_TEXT[event.equipmentStatus]}
        </span>
      </div>
      <div class="kv-item">
        <span class="kv-label">报警次数</span>
        <span class="kv-value">{event.alarmCount} 次（合并同一次救援）</span>
      </div>
    </div>
    <div class="call-actions">
      {#if event.callStatus === 'CONNECTED'}
        <button on:click={() => run(() => api.setCall(event.id, 'INTERRUPTED'))}>📵 模拟通话中断</button>
      {:else}
        <button class="success" on:click={() => run(() => api.setCall(event.id, 'RESTORED'))}>☎️ 通话恢复</button>
      {/if}
      <span class="muted small">首次报警 {fmtTime(event.firstAlarmAt)} · 最近 {fmtTime(event.lastAlarmAt)}</span>
    </div>
  </section>

  {#if actionError}
    <div class="action-error">⛔ {actionError}</div>
  {/if}

  <!-- 分派 -->
  {#if event.status === 'ALARMED'}
    <section class="panel">
      <h3>① 任务分派</h3>
      <p class="muted small">值班员需迅速联系维保单位与现场保安；同一电梯重复报警已自动合并，不会重复派队。</p>
      <div class="form-row">
        <div><label>维保单位</label><input bind:value={mTeam} /></div>
        <div><label>现场保安</label><input bind:value={guard} /></div>
        <div class="eta"><label>承诺到场（分钟）</label><input type="number" min="1" bind:value={eta} /></div>
        <div class="ta"><button class="primary" on:click={() => run(() => api.dispatch(event.id,
          { maintenanceTeam: mTeam, guardName: guard, etaMinutes: Number(eta) }))}>分派任务</button></div>
      </div>
    </section>
  {:else if d}
    <section class="panel">
      <h3>① 任务分派与到场跟进</h3>
      <div class="track">
        <div class="party">
          <div class="pname">🔧 维保 · {d.maintenanceTeam}</div>
          <div class="steps">
            <span class="step" class:on={d.dispatchedAt}>已分派</span>
            <span class="step" class:on={d.maintenanceAckAt}>已回执 {d.maintenanceAckAt ? fmtTime(d.maintenanceAckAt) : ''}</span>
            <span class="step" class:on={d.maintenanceArrivedAt}>已到场 {d.maintenanceArrivedAt ? fmtTime(d.maintenanceArrivedAt) : ''}</span>
          </div>
          <div class="btns">
            {#if !d.maintenanceAckAt}
              <button on:click={() => run(() => api.ack(event.id, 'MAINTENANCE'))}>模拟维保接单回执</button>
            {/if}
            {#if !d.maintenanceArrivedAt}
              <button on:click={() => run(() => api.arrived(event.id, 'MAINTENANCE'))}>模拟维保到场打卡</button>
            {/if}
          </div>
        </div>
        <div class="party">
          <div class="pname">🛡️ 保安 · {d.guardName}</div>
          <div class="steps">
            <span class="step" class:on={d.dispatchedAt}>已分派</span>
            <span class="step" class:on={d.guardAckAt}>已回执 {d.guardAckAt ? fmtTime(d.guardAckAt) : ''}</span>
            <span class="step" class:on={d.guardArrivedAt}>已到场 {d.guardArrivedAt ? fmtTime(d.guardArrivedAt) : ''}</span>
          </div>
          <div class="btns">
            {#if !d.guardAckAt}
              <button on:click={() => run(() => api.ack(event.id, 'GUARD'))}>模拟保安回执</button>
            {/if}
            {#if !d.guardArrivedAt}
              <button on:click={() => run(() => api.arrived(event.id, 'GUARD'))}>模拟保安到场</button>
            {/if}
          </div>
        </div>
      </div>
      <div class="sla" class:late={remaining !== null && remaining <= 0 && !d.maintenanceArrivedAt}>
        ⏱ 维保到场 SLA：{fmtTime(event.slaDueAt)} · {slaText}
        {#if remaining !== null && remaining <= 0 && !d.maintenanceArrivedAt}（超时将自动升级）{/if}
      </div>
    </section>
  {/if}

  <!-- 双岗交接核对 -->
  {#if event.status === 'ON_SCENE' || event.status === 'RESCUED' || isClosed}
    <section class="panel">
      <h3>② 救援结束交接核对 <span class="muted small">维保与保安各自独立核对，三项齐全方可关闭</span></h3>
      <div class="handovers">
        <!-- 维保 -->
        <div class="ho" class:done={h?.maintenanceConfirmedAt}>
          <div class="ho-head">🔧 维保核对
            {#if h?.maintenanceConfirmedAt}<span class="ok">✔ 已核对 {fmtTime(h.maintenanceConfirmedAt)}</span>{/if}
          </div>
          {#if h?.maintenanceConfirmedAt}
            <div class="confirm-line">核对人：{h.maintenanceConfirmer} · 已开门 ✔ 人员离梯 ✔ 设备停用 ✔</div>
          {:else}
            <input placeholder="维保核对人签名" bind:value={mName} />
            <label class="opt"><input type="checkbox" bind:checked={mDoor} /> 轿厢门已安全打开</label>
            <label class="opt"><input type="checkbox" bind:checked={mExit} /> 被困人员已全部离梯</label>
            <label class="opt"><input type="checkbox" bind:checked={mStop} /> 设备已停用挂牌</label>
            <button class="primary" disabled={!handoverOk(mName, mDoor, mExit, mStop)}
              on:click={() => run(async () => {
                await api.handover(event.id, 'MAINTENANCE',
                  { confirmer: mName, doorOpened: mDoor, personsExited: mExit, equipmentStopped: mStop });
                mName = ''; mDoor = mExit = mStop = false;
              })}>提交维保核对</button>
            {#if !handoverOk(mName, mDoor, mExit, mStop)}
              <div class="muted small">三项全部勾选并签名后才能提交，缺项不可跳过</div>
            {/if}
          {/if}
        </div>
        <!-- 保安 -->
        <div class="ho" class:done={h?.guardConfirmedAt}>
          <div class="ho-head">🛡️ 现场保安核对
            {#if h?.guardConfirmedAt}<span class="ok">✔ 已核对 {fmtTime(h.guardConfirmedAt)}</span>{/if}
          </div>
          {#if h?.guardConfirmedAt}
            <div class="confirm-line">核对人：{h.guardConfirmer} · 已开门 ✔ 人员离梯 ✔ 设备停用 ✔</div>
          {:else}
            <input placeholder="保安核对人签名" bind:value={gName} />
            <label class="opt"><input type="checkbox" bind:checked={gDoor} /> 轿厢门已安全打开</label>
            <label class="opt"><input type="checkbox" bind:checked={gExit} /> 被困人员已全部离梯</label>
            <label class="opt"><input type="checkbox" bind:checked={gStop} /> 设备已停用并现场看护</label>
            <button class="primary" disabled={!handoverOk(gName, gDoor, gExit, gStop)}
              on:click={() => run(async () => {
                await api.handover(event.id, 'GUARD',
                  { confirmer: gName, doorOpened: gDoor, personsExited: gExit, equipmentStopped: gStop });
                gName = ''; gDoor = gExit = gStop = false;
              })}>提交保安核对</button>
            {#if !handoverOk(gName, gDoor, gExit, gStop)}
              <div class="muted small">三项全部勾选并签名后才能提交，缺项不可跳过</div>
            {/if}
          {/if}
        </div>
      </div>

      <!-- 获救人员不适升级 -->
      {#if event.status === 'RESCUED' || isClosed}
        <div class="unwell">
          {#if h?.unwellAfterRescue}
            <span class="uw-badge">⚠ 获救人员不适（已升级 L2）：{h.unwellNote || '已记录'}</span>
          {:else if !isClosed}
            <input class="uw-input" placeholder="获救人员出现不适？填写情况并上报（将升级）" bind:value={unwellNote} />
            <button on:click={() => run(async () => {
              await api.unwell(event.id, { unwell: true, note: unwellNote });
              unwellNote = '';
            })}>上报不适并升级</button>
          {/if}
        </div>
      {/if}
    </section>
  {/if}

  <!-- 复检与恢复载客 -->
  {#if event.status === 'RESCUED' || isClosed}
    <section class="panel">
      <h3>③ 设备复检与恢复 <span class="muted small">救援结束 ≠ 设备可以重新运行</span></h3>
      <div class="equip">
        <div class="equip-state" class:locked={event.equipmentStatus === 'LOCKED_OUT'}>
          当前：{EQUIP_TEXT[event.equipmentStatus]}
        </div>
        {#if event.equipmentStatus === 'LOCKED_OUT' && !isClosed}
          <div class="reinspect">
            <input class="insp" placeholder="检验人员" bind:value={inspector} />
            <button on:click={() => run(() => api.reinspect(event.id, { inspector, passed: false }))}>复检不合格（继续停用）</button>
            <button class="primary" on:click={() => run(() => api.reinspect(event.id, { inspector, passed: true }))}>复检合格</button>
          </div>
        {/if}
        {#if h?.reinspectionPassed}
          <div class="muted small">复检人 {h.inspector} · {fmtTime(h.reinspectedAt)}</div>
        {/if}
        {#if event.equipmentStatus !== 'RESUMED' && !isClosed}
          <button class="success" disabled={event.equipmentStatus !== 'INSPECTED'}
            on:click={() => run(() => api.resume(event.id))}>
            {event.equipmentStatus === 'INSPECTED' ? '恢复载客运行' : '🔒 未复检，恢复载客已锁定'}
          </button>
          {#if event.equipmentStatus === 'LOCKED_OUT'}
            <span class="muted small">（系统将阻止未经复检恢复载客）</span>
          {/if}
        {:else if event.equipmentStatus === 'RESUMED'}
          <span class="ok">✔ 已于 {fmtTime(h.serviceResumedAt)} 恢复载客</span>
        {/if}
      </div>
    </section>

    <!-- 关闭事件 -->
    <section class="panel close-panel">
      <h3>④ 关闭事件</h3>
      {#if isClosed}
        <div class="ok big-ok">✔ 交接齐全，事件已于 {fmtTime(event.closedAt)} 关闭
          {#if event.equipmentStatus === 'LOCKED_OUT'}，电梯仍保持停用待复检{/if}
        </div>
      {:else}
        <div class="missing">
          {#if !h?.maintenanceConfirmedAt}<span class="miss-item">✗ 维保交接未完成</span>{/if}
          {#if !h?.guardConfirmedAt}<span class="miss-item">✗ 保安交接未完成</span>{/if}
          {#if h?.maintenanceConfirmedAt && h?.guardConfirmedAt}<span class="muted">交接已齐全</span>{/if}
        </div>
        <button class="danger" disabled={!h?.maintenanceConfirmedAt || !h?.guardConfirmedAt}
          on:click={() => run(() => api.close(event.id))}>
          {h?.maintenanceConfirmedAt && h?.guardConfirmedAt ? '关闭事件' : '交接缺项，禁止关闭'}
        </button>
      {/if}
    </section>
  {/if}

  <!-- 时间线 -->
  <Timeline entries={event.timeline} />
</div>

<style>
  .detail { display: flex; flex-direction: column; gap: 12px; }
  section, .head {
    background: var(--panel); border: 1px solid var(--border); border-radius: 10px; padding: 14px 16px;
  }
  .head-row { display: flex; justify-content: space-between; align-items: center; }
  .no { color: var(--muted); font-size: 11px; }
  h2 { margin: 8px 0 12px; font-size: 18px; }
  h2 small { color: var(--muted); font-size: 12px; font-weight: 400; margin-left: 8px; }
  h3 { margin: 0 0 10px; font-size: 15px; }
  .badge { font-size: 11px; padding: 2px 9px; border-radius: 10px; font-weight: 600; }
  .badge[data-color="red"] { background: rgba(248,81,73,.18); color: #ff7b72; }
  .badge[data-color="amber"] { background: rgba(210,153,34,.18); color: #e3b341; }
  .badge[data-color="blue"] { background: rgba(47,129,247,.18); color: #79b8ff; }
  .badge[data-color="purple"] { background: rgba(163,113,247,.18); color: #c8a8ff; }
  .badge[data-color="green"] { background: rgba(63,185,80,.18); color: #7ee787; }
  .esc { color: #ff7b72; font-weight: 700; font-size: 12px; margin-left: 8px; }
  .late { background: rgba(248,81,73,.2); color: #ff7b72; font-size: 11px; padding: 2px 8px; border-radius: 10px; margin-left: 6px; }
  .kv { display: flex; gap: 24px; flex-wrap: wrap; }
  .kv-label { display: block; color: var(--muted); font-size: 11px; margin-bottom: 3px; }
  .kv-value.big { font-size: 17px; font-weight: 700; }
  .kv-value.int { color: #ff7b72; font-weight: 700; }
  .kv-value.lock { color: #e3b341; font-weight: 700; }
  .call-actions { margin-top: 12px; display: flex; align-items: center; gap: 12px; }
  .action-error {
    background: rgba(248,81,73,.12); border: 1px solid var(--red); color: #ffb4b0;
    padding: 10px 14px; border-radius: 8px; font-size: 13px; font-weight: 600;
  }
  .form-row { display: flex; gap: 10px; align-items: flex-end; flex-wrap: wrap; }
  .form-row > div { min-width: 150px; flex: 1; }
  .form-row .eta { max-width: 150px; }
  .form-row .ta { flex: 0 0 auto; }
  .track { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
  @media (max-width: 700px) { .track { grid-template-columns: 1fr; } }
  .party { border: 1px solid var(--border); border-radius: 8px; padding: 10px 12px; background: var(--panel2); }
  .pname { font-weight: 600; margin-bottom: 8px; }
  .steps { display: flex; flex-direction: column; gap: 4px; margin-bottom: 8px; }
  .step { font-size: 12px; color: #5a6a78; }
  .step.on { color: #7ee787; }
  .btns { display: flex; gap: 8px; flex-wrap: wrap; }
  .sla { margin-top: 10px; font-size: 12.5px; color: var(--muted); }
  .sla.late { color: #ff7b72; font-weight: 700; }
  .handovers { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
  @media (max-width: 700px) { .handovers { grid-template-columns: 1fr; } }
  .ho { border: 1px solid var(--border); border-radius: 8px; padding: 12px; background: var(--panel2); display: flex; flex-direction: column; gap: 7px; }
  .ho.done { border-color: rgba(63,185,80,.4); }
  .ho-head { font-weight: 600; }
  .opt { display: flex; align-items: center; gap: 8px; color: var(--text); font-size: 13px; margin: 0; }
  .opt input { width: auto; }
  .ok { color: #7ee787; font-size: 12.5px; font-weight: 600; }
  .confirm-line { font-size: 12.5px; color: #a9c7b0; }
  .unwell { margin-top: 12px; display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
  .uw-input { flex: 1; min-width: 200px; }
  .uw-badge { color: #ff9b95; font-weight: 600; font-size: 13px; }
  .equip { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
  .equip-state { font-weight: 700; padding: 6px 12px; border-radius: 8px; background: var(--panel2); border: 1px solid var(--border); }
  .equip-state.locked { color: #e3b341; border-color: rgba(210,153,34,.5); }
  .reinspect { display: flex; gap: 8px; }
  .insp { width: 130px; }
  .big-ok { font-size: 14px; }
  .missing { display: flex; gap: 10px; margin-bottom: 10px; flex-wrap: wrap; }
  .miss-item { color: #ff7b72; font-size: 12.5px; background: rgba(248,81,73,.1); padding: 3px 10px; border-radius: 10px; }
</style>
