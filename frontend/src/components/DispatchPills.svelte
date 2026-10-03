<script>
  export let inc;

  function pill(d) {
    if (!d) return { text: `${''}未派`, cls: 'gray' };
    if (d.state === 'ARRIVED') {
      return {
        text: `${d.teamLabel}已到场 ${d.elapsedMinutes}m${d.late ? ' ⚠迟到' : ''}`,
        cls: d.late ? 'amber' : 'green'
      };
    }
    if (d.state === 'ACKNOWLEDGED') return { text: `${d.teamLabel}已接单`, cls: 'blue' };
    return { text: `${d.teamLabel}已派单`, cls: 'gray' };
  }

  const maint = inc.dispatches?.find(d => d.team === 'MAINTENANCE');
  const sec = inc.dispatches?.find(d => d.team === 'SECURITY');
  const pills = [pill(maint), pill(sec)];
</script>

<div class="pills">
  {#each pills as p}
    <span class="badge {p.cls}">{p.text}</span>
  {/each}
</div>

<style>
  .pills { display: flex; flex-direction: column; gap: 4px; align-items: flex-start; }
</style>
