<script>
  import { onMount, onDestroy } from 'svelte';
  import { api } from './lib/api.js';
  import AlarmBar from './components/AlarmBar.svelte';
  import EventList from './components/EventList.svelte';
  import EventDetail from './components/EventDetail.svelte';

  let events = [];
  let selectedId = null;
  let loading = true;
  let error = '';
  let timer;

  async function refresh(keepSelection = true) {
    try {
      events = await api.listEvents();
      if ((selectedId === null || !events.some(e => e.id === selectedId)) && events.length) {
        const top = [...events].sort(prioritySort)[0];
        selectedId = top.id;
      }
      error = '';
    } catch (err) {
      error = err.message;
    } finally {
      loading = false;
    }
  }

  // 未关闭且升级/报警中的事件排最前
  $: sorted = [...events].sort((a, b) => {
    if ((a.status === 'CLOSED') !== (b.status === 'CLOSED')) return a.status === 'CLOSED' ? 1 : -1;
    const lv = (e) => e.escalationLevel === 'L2' ? 2 : e.escalationLevel === 'L1' ? 1 : 0;
    if (lv(a) !== lv(b)) return lv(b) - lv(a);
    return new Date(b.lastAlarmAt) - new Date(a.lastAlarmAt);
  });
  $: selected = events.find(e => e.id === selectedId) || null;

  onMount(() => {
    refresh();
    timer = setInterval(() => refresh(), 3000);
  });
  onDestroy(() => clearInterval(timer));

  async function changed() {
    await refresh();
  }
  function select(id) {
    selectedId = id;
  }
</script>

<div class="app">
  <header>
    <h1>🛗 电梯困人报警与救援交接系统</h1>
    <span class="sub">楼宇值班室 · 报警受理 / 派队 / 到场跟进 / 双岗交接 / 复检恢复</span>
  </header>

  <AlarmBar onChanged={changed} />

  {#if error}
    <div class="error-bar">⚠ 与后端连接失败：{error}（请确认后端已启动于 8080 端口，3 秒后自动重试）</div>
  {/if}

  <main>
    <EventList events={sorted} {selectedId} onSelect={select} {loading} />
    {#if selected}
      {#key selected.id}
        <EventDetail event={selected} onChanged={changed} />
      {/key}
    {:else}
      <div class="empty">
        {#if loading}加载中…{:else}暂无救援事件，可通过上方「模拟报警接入」发起困人报警{/if}
      </div>
    {/if}
  </main>
</div>

<style>
  .app { max-width: 1360px; margin: 0 auto; padding: 16px 20px 40px; }
  header { display: flex; align-items: baseline; gap: 16px; margin-bottom: 14px; flex-wrap: wrap; }
  h1 { font-size: 20px; margin: 0; }
  .sub { color: var(--muted); font-size: 12px; }
  main { display: grid; grid-template-columns: 380px 1fr; gap: 14px; margin-top: 14px; align-items: start; }
  .empty {
    background: var(--panel); border: 1px dashed var(--border); border-radius: 10px;
    padding: 60px 20px; text-align: center; color: var(--muted);
  }
  .error-bar {
    background: rgba(248,81,73,.12); border: 1px solid var(--red); color: #ffb4b0;
    padding: 8px 14px; border-radius: 8px; font-size: 13px; margin-top: 10px;
  }
  @media (max-width: 900px) {
    main { grid-template-columns: 1fr; }
  }
</style>
