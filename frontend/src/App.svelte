<script>
  import { onMount, onDestroy } from 'svelte';
  import { api } from './api.js';
  import IncidentList from './components/IncidentList.svelte';
  import IncidentDetail from './components/IncidentDetail.svelte';
  import SimPanel from './components/SimPanel.svelte';

  let incidents = [];
  let elevators = [];
  let selectedId = null;
  let selected = null;
  let includeClosed = false;
  let loading = true;
  let error = '';
  let showSim = false;
  let toasts = [];
  let timer = null;
  let toastSeq = 0;

  function notify(message, kind = 'info') {
    const id = ++toastSeq;
    toasts = [...toasts, { id, message, kind }];
    setTimeout(() => {
      toasts = toasts.filter(t => t.id !== id);
    }, 5000);
  }

  export function toastError(err, prefix = '操作失败') {
    const detail = err.details?.length ? `\n${err.details.join('\n')}` : '';
    notify(`${prefix}：${err.message}${detail}`, 'error');
  }

  async function refresh() {
    try {
      const [list, eles] = await Promise.all([
        api.listIncidents(includeClosed),
        elevators.length ? Promise.resolve(elevators) : api.listElevators()
      ]);
      incidents = list;
      elevators = eles;
      error = '';
    } catch (e) {
      error = e.message;
    } finally {
      loading = false;
    }
    if (selectedId !== null) {
      try {
        selected = await api.getIncident(selectedId);
      } catch (e) {
        selected = null;
        selectedId = null;
      }
    }
  }

  function openIncident(id) {
    selectedId = id;
    refresh();
  }

  function back() {
    selectedId = null;
    selected = null;
    refresh();
  }

  async function toggleIncludeClosed() {
    includeClosed = !includeClosed;
    loading = true;
    await refresh();
  }

  onMount(async () => {
    await refresh();
    timer = setInterval(refresh, 4000);
  });
  onDestroy(() => clearInterval(timer));
</script>

<div class="app">
  <header class="topbar">
    <div class="brand">
      <span class="logo">🛗</span>
      <div>
        <h1>电梯困人报警与救援交接系统</h1>
        <span class="muted">楼宇值班室 · 接警 / 派单 / 到场跟进 / 双方交接 / 复检放行</span>
      </div>
    </div>
    <div class="top-actions">
      <button class="ghost" on:click={() => showSim = !showSim}>
        {showSim ? '关闭模拟接口' : '模拟接口'}
      </button>
      <button class="ghost" on:click={toggleIncludeClosed}>
        {includeClosed ? '隐藏已关闭' : '查看已关闭'}
      </button>
      <button class="ghost" on:click={refresh}>刷新</button>
    </div>
  </header>

  {#if showSim}
    <SimPanel {elevators} {refresh} {notify} />
  {/if}

  {#if error}
    <div class="error-banner">后端连接失败：{error}（请确认 Quarkus 已在 8080 端口启动）</div>
  {/if}

  <main>
    {#if selected}
      <IncidentDetail incident={selected} {refresh} {notify} on:back={back} />
    {:else}
      <IncidentList {incidents} {loading} on:open={(e) => openIncident(e.detail)} />
    {/if}
  </main>
</div>

<div class="toast-wrap">
  {#each toasts as t (t.id)}
    <div class="toast {t.kind}" style="white-space: pre-line;">{t.message}</div>
  {/each}
</div>

<style>
  .app { max-width: 1180px; margin: 0 auto; padding: 20px 24px 60px; }
  .topbar {
    display: flex; justify-content: space-between; align-items: center;
    gap: 16px; margin-bottom: 18px; flex-wrap: wrap;
  }
  .brand { display: flex; gap: 12px; align-items: center; }
  .logo { font-size: 34px; }
  .topbar h1 { font-size: 19px; margin-bottom: 2px; }
  .top-actions { display: flex; gap: 8px; }
  .error-banner {
    background: var(--danger-bg); border: 1px solid var(--danger);
    color: #f87171; padding: 10px 14px; border-radius: 8px; margin-bottom: 14px;
  }
  main { min-height: 60vh; }
</style>
