const BACKEND_URL = 'http://localhost:9081';
const EXAMPLE_PATCH = `=== PATCH START ===
NAME: Example
DESCRIPTION: Demo
AUTHOR: User
VERSION: 1.0

--- FILE: test.txt ---
ACTION: create_file
DESCRIPTION: Test

<<< CONTENT
Hello World
CONTENT >>>

=== PATCH END ===`;

const els = {
  patchContent: document.getElementById('patchContent'),
  baseDir: document.getElementById('baseDir'),
  btnDryRun: document.getElementById('btnDryRun'),
  btnApply: document.getElementById('btnApply'),
  loadExample: document.getElementById('loadExample'),
  loadFromClipboard: document.getElementById('loadFromClipboard'),
  statsSection: document.getElementById('statsSection'),
  statsSuccess: document.getElementById('statsSuccess'),
  statsSkipped: document.getElementById('statsSkipped'),
  statsFailed: document.getElementById('statsFailed'),
  metadataSection: document.getElementById('metadataSection'),
  metaName: document.getElementById('metaName'),
  metaDescription: document.getElementById('metaDescription'),
  errorSection: document.getElementById('errorSection'),
  errorMessage: document.getElementById('errorMessage'),
  resultsSection: document.getElementById('resultsSection'),
  resultsContainer: document.getElementById('resultsContainer'),
  emptyState: document.getElementById('emptyState'),
  autoLoadIndicator: document.getElementById('autoLoadIndicator')
};

function parsePatch(content) {
  const lines = content.split('\n');
  let i = 0;
  
  while (i < lines.length && !lines[i].includes('PATCH START')) i++;
  if (i >= lines.length) throw new Error('PATCH START not found');
  i++;
  
  const meta = {};
  while (i < lines.length) {
    const line = lines[i].trim();
    if (line === '' || line.startsWith('---') || line.startsWith('---')) break;
    if (line.includes(':')) {
      const [k, ...v] = line.split(':');
      meta[k.trim()] = v.join(':').trim();
    }
    i++;
  }
  
  return {
    metadata: {
      name: meta.NAME || 'Unnamed',
      description: meta.DESCRIPTION || '',
      author: meta.AUTHOR || 'Unknown',
      version: meta.VERSION || '1.0'
    }
  };
}

async function applyPatch(dryRun = true) {
  hideError();
  hideEmptyState();
  showLoading(dryRun);
  
  const patchContent = els.patchContent.value.trim();
  const baseDir = els.baseDir.value.trim() || '.';
  
  if (!patchContent) {
    showError('Patch content empty');
    hideLoading();
    showEmptyState();
    return;
  }
  
  try {
    const response = await fetch(`${BACKEND_URL}/api/patch/apply`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ patchContent, dryRun, baseDir })
    });
    
    const data = await response.json();
    
    if (!data.success) {
      showError(data.error || 'Unknown error');
      hideLoading();
      return;
    }
    
    displayResults(data.metadata, data.results, data.stats);
  } catch (err) {
    try {
      const parsed = parsePatch(patchContent);
      displayResults(parsed.metadata, [], { success: 0, skipped: 0, failed: 0 });
      showError('⚠️ Backend unavailable. Validation mode only.');
    } catch (parseErr) {
      showError(parseErr.message);
    }
  }
  
  hideLoading();
}

function displayResults(metadata, results, stats) {
  els.metaName.textContent = metadata.name;
  els.metaDescription.textContent = metadata.description;
  els.metadataSection.classList.remove('d-none');
  
  els.statsSuccess.textContent = stats.success || 0;
  els.statsSkipped.textContent = stats.skipped || 0;
  els.statsFailed.textContent = stats.failed || 0;
  els.statsSection.classList.remove('d-none');
  
  els.resultsContainer.innerHTML = '';
  results.filter((r) => r.status != "success").forEach(r => {
    const el = document.createElement('div');
    el.className = `result-item ${r.status}`;
    const icons = {
      success: 'bi-check-circle-fill text-success',
      failed: 'bi-x-circle-fill text-danger',
      skipped: 'bi-star-fill text-warning'
    };
    el.innerHTML = `
      <div class="d-flex align-items-start">
        <i class="bi ${icons[r.status]} me-2"></i>
        <div class="flex-fill">
          <div class="fw-bold">${r.file}</div>
          <div class="text-muted">${r.description || ''}</div>
          <div><code>${r.action}</code></div>
          <div>${r.message}</div>
        </div>
      </div>
    `;
    els.resultsContainer.appendChild(el);
  });
  
  els.resultsSection.classList.remove('d-none');
}

function showError(msg) {
  els.errorMessage.textContent = msg;
  els.errorSection.classList.remove('d-none');
}

function hideError() {
  els.errorSection.classList.add('d-none');
}

function showLoading(isDryRun) {
  const btn = isDryRun ? els.btnDryRun : els.btnApply;
  btn.disabled = true;
  btn.innerHTML = `<span class="spinner-border spinner-border-sm me-1"></span>${isDryRun ? 'Validating...' : 'Applying...'}`;
}

function hideLoading() {
  els.btnDryRun.disabled = false;
  els.btnDryRun.innerHTML = '<i class="bi bi-search"></i> Validate';
  els.btnApply.disabled = false;
  els.btnApply.innerHTML = '<i class="bi bi-play-fill"></i> Apply';
}

function showEmptyState() {
  els.emptyState.classList.remove('d-none');
  els.resultsSection.classList.add('d-none');
  els.metadataSection.classList.add('d-none');
  els.statsSection.classList.add('d-none');
}

function hideEmptyState() {
  els.emptyState.classList.add('d-none');
}

els.loadExample.addEventListener('click', () => {
  els.patchContent.value = EXAMPLE_PATCH;
  hideEmptyState();
});

els.loadFromClipboard.addEventListener('click', async () => {
  try {
    const text = await navigator.clipboard.readText();
    els.patchContent.value = text;
    hideEmptyState();
  } catch (err) {
    showError('Clipboard read failed');
  }
});

els.btnDryRun.addEventListener('click', () => applyPatch(true));

els.btnApply.addEventListener('click', () => {
  if (confirm('Apply patch? This will modify files.')) {
    applyPatch(false);
  }
});

// Слушаем сообщения от content script через chrome.tabs
chrome.runtime.onMessage.addListener((msg, sender, sendResponse) => {
  if (msg.type === 'PATCH_DETECTED') {
    els.patchContent.value = msg.content;
    hideEmptyState();
    els.autoLoadIndicator.classList.remove('d-none');
    
    setTimeout(() => {
      els.autoLoadIndicator.classList.add('d-none');
    }, 3000);
    
    setTimeout(() => applyPatch(true), 500);
    
    sendResponse({ success: true });
  }
  return true;
});

// Загрузка настроек
chrome.storage.sync.get(['baseDir'], (data) => {
  if (data.baseDir) els.baseDir.value = data.baseDir;
});

els.baseDir.addEventListener('change', () => {
  chrome.storage.sync.set({ baseDir: els.baseDir.value });
});

console.log('Robust Patcher popup loaded');