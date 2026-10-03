<script>
  export let incident;
</script>

{#if incident.escalationLevel > 0}
  <div class="banner">
    <div class="title">
      ⬆ 已升级至 L{incident.escalationLevel}
      {incident.escalationLevel >= 2 ? '（项目经理 + 120 急救）' : '（值班主管督办）'}
    </div>
    <div class="reasons">{incident.escalationReasons}</div>
  </div>
{/if}

<style>
  .banner {
    background: var(--danger-bg);
    border: 1px solid var(--danger);
    border-radius: 8px;
    padding: 10px 14px;
    margin-bottom: 14px;
  }
  .title { color: #f87171; font-weight: 600; font-size: 13px; }
  .reasons { color: #fca5a5; font-size: 12px; margin-top: 3px; }
</style>
