// Content script для claude.ai
console.log('Robust Patcher: Content script loaded');

// Глобальное хранилище для отслеживания обработанных блоков
const processedBlocks = new WeakSet();

// Функция для добавления кнопок к блоку кода
function addPatchButtons(codeBlock) {
  // Проверяем, не обработан ли уже этот блок
  if (processedBlocks.has(codeBlock)) {
    return;
  }
  
  // Получаем текст кода
  const code = codeBlock.textContent || codeBlock.innerText;
  
  // Проверяем, является ли это патчем
  if (!code.includes('=== PATCH START ===') || !code.includes('=== PATCH END ===')) {
    return;
  }
  
  // Ищем кнопку Copy в родительских элементах
  let container = codeBlock.parentElement;
  let copyButton = null;
  let attempts = 0;
  
  while (container && attempts < 10) {
    copyButton = container.querySelector('button:has(.bi-clipboard), button:has([class*="copy" i])');
    if (!copyButton) {
      // Ищем кнопку с текстом "Copy"
      const buttons = container.querySelectorAll('button');
      for (const btn of buttons) {
        if (btn.textContent.includes('Copy')) {
          copyButton = btn;
          break;
        }
      }
    }
    if (copyButton) break;
    container = container.parentElement;
    attempts++;
  }
  
  if (!copyButton) {
    console.log('Robust Patcher: Copy button not found for patch');
    return;
  }
  
  // Помечаем блок как обработанный
  processedBlocks.add(codeBlock);
  
  // Проверяем, не добавлены ли уже кнопки
  const existingApplyButton = copyButton.parentElement.querySelector('.robust-patcher-apply-btn');
  if (existingApplyButton) {
    return; // Кнопки уже добавлены
  }
  
  // Создаём кнопку "Apply Patch"
  const applyButton = document.createElement('button');
  applyButton.className = copyButton.className.replace('rounded-l-lg', '').trim() + ' robust-patcher-apply-btn';
  applyButton.innerHTML = `
    <div class="relative">
      <div class="">Apply Patch</div>
    </div>
  `;
  
  applyButton.addEventListener('click', async (e) => {
    e.preventDefault();
    e.stopPropagation();
    
    // Копируем в буфер и отправляем сообщение
    try {
      await navigator.clipboard.writeText(code);
      
      // Отправляем сообщение popup (с проверкой)
      if (typeof chrome !== 'undefined' && chrome.runtime && chrome.runtime.sendMessage) {
        chrome.runtime.sendMessage({
          type: 'PATCH_DETECTED',
          content: code
        }, (response) => {
          if (chrome.runtime.lastError) {
            console.error('Message send error:', chrome.runtime.lastError);
          }
        });
      }
      
      // Визуальная обратная связь
      const originalText = applyButton.querySelector('div > div').textContent;
      applyButton.querySelector('div > div').textContent = '✓ Copied & Sent';
      applyButton.style.backgroundColor = '#10b981';
      applyButton.style.color = 'white';
      
      setTimeout(() => {
        applyButton.querySelector('div > div').textContent = originalText;
        applyButton.style.backgroundColor = '';
        applyButton.style.color = '';
      }, 2000);
    } catch (err) {
      console.error('Robust Patcher: Failed to process patch', err);
    }
  });
  
  // Создаём кнопку "Validate"
  const validateButton = document.createElement('button');
  validateButton.className = (copyButton.className + ' rounded-r-lg robust-patcher-validate-btn').replace('rounded-l-lg', '').trim();
  validateButton.innerHTML = `
    <div class="relative">
      <div class="">Validate</div>
    </div>
  `;
  
  validateButton.addEventListener('click', async (e) => {
    e.preventDefault();
    e.stopPropagation();
    
    // Простая валидация
    const isValid = code.includes('=== PATCH START ===') && 
                   code.includes('=== PATCH END ===') &&
                   code.includes('NAME:') &&
                   code.includes('ACTION:');
    
    const originalText = validateButton.querySelector('div > div').textContent;
    if (isValid) {
      validateButton.querySelector('div > div').textContent = '✓ Valid';
      validateButton.style.backgroundColor = '#10b981';
      validateButton.style.color = 'white';
    } else {
      validateButton.querySelector('div > div').textContent = '✗ Invalid';
      validateButton.style.backgroundColor = '#ef4444';
      validateButton.style.color = 'white';
    }
    
    setTimeout(() => {
      validateButton.querySelector('div > div').textContent = originalText;
      validateButton.style.backgroundColor = '';
      validateButton.style.color = '';
    }, 2000);
  });
  
  // Вставляем кнопки после кнопки Copy
  copyButton.parentElement.insertBefore(applyButton, copyButton.nextSibling);
  applyButton.parentElement.insertBefore(validateButton, applyButton.nextSibling);
  
  console.log('Robust Patcher: Buttons added to patch block');
}

// Функция для поиска всех кодблоков
function findCodeBlocks() {
  // Ищем все code элементы, которые содержат патчи
  const codeElements = document.querySelectorAll('code.language-plaintext, code.language-text, code');
  
  codeElements.forEach(codeBlock => {
    const text = codeBlock.textContent || '';
    if (text.includes('PATCH START') && text.includes('PATCH END')) {
      addPatchButtons(codeBlock);
    }
  });
}

// MutationObserver для отслеживания новых блоков
const observer = new MutationObserver((mutations) => {
  findCodeBlocks();
});

// Начинаем наблюдение
observer.observe(document.body, {
  childList: true,
  subtree: true
});

// Первоначальная проверка через небольшую задержку
setTimeout(findCodeBlocks, 2000);
setTimeout(findCodeBlocks, 5000);

console.log('Robust Patcher: Monitoring for patches...');