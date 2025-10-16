// Background service worker
// Используем chrome.storage.session вместо переменной в памяти
// чтобы ID окна сохранялся между перезапусками service worker

// Функция для получения ID окна patcher из storage
async function getPatcherWindowId() {
  const result = await chrome.storage.session.get('patcherWindowId');
  return result.patcherWindowId || null;
}

// Функция для сохранения ID окна patcher в storage
async function setPatcherWindowId(windowId) {
  if (windowId === null) {
    await chrome.storage.session.remove('patcherWindowId');
  } else {
    await chrome.storage.session.set({ patcherWindowId: windowId });
  }
}

// Функция для получения размеров экрана и позиционирования окна
async function createOrFocusPatcherWindow() {
  // Получаем ID окна из storage
  const patcherWindowId = await getPatcherWindowId();
  
  // Проверяем, существует ли уже окно
  if (patcherWindowId !== null) {
    try {
      const existingWindow = await chrome.windows.get(patcherWindowId);
      // Окно существует - фокусируемся на нём
      await chrome.windows.update(patcherWindowId, { focused: true });
      return;
    } catch (error) {
      // Окно было закрыто - создаём новое
      await setPatcherWindowId(null);
    }
  }

  // Размеры панели
  const panelWidth = 425;
  const panelHeight = 1000;
  
  // Получаем информацию о дисплее
  const displays = await chrome.system.display.getInfo();
  const primaryDisplay = displays.find(d => d.isPrimary) || displays[0];
  
  // Создаём новое окно панели без указания позиции
  // Браузер сам разместит его, но с правильными размерами
  const screenWidth = primaryDisplay.workArea.width;
  const screenHeight = primaryDisplay.workArea.height;
  const screenLeft = primaryDisplay.workArea.left;
  const screenTop = primaryDisplay.workArea.top;
  
  // Позиционируем окно в правом верхнем углу экрана
  // Добавляем небольшой отступ от края для корректного отображения
  const left = screenLeft + screenWidth - panelWidth + 5;
  const top = screenTop + 10;

  // Создаём новое окно панели
  const panelWindow = await chrome.windows.create({
    url: chrome.runtime.getURL('popup.html'),
    type: 'popup',
    width: panelWidth,
    height: panelHeight,
    left: Math.round(left),
    top: Math.round(top),
    focused: true
  });

  // Сохраняем ID окна в storage
  await setPatcherWindowId(panelWindow.id);
}

// Обработчик клика по иконке расширения
chrome.action.onClicked.addListener(async () => {
  await createOrFocusPatcherWindow();
});

// Слушаем сообщения от content script
chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  if (message.type === 'PATCH_DETECTED') {
    console.log('Patch detected:', message.content);
    
    // Сохраняем патч для дальнейшего использования
    chrome.storage.local.set({
      lastPatch: message.content,
      lastPatchTime: Date.now()
    });
    
    // Открываем или фокусируем окно patcher
    createOrFocusPatcherWindow().then(async () => {
      // Даём окну время загрузиться перед отправкой сообщения
      setTimeout(async () => {
        const patcherWindowId = await getPatcherWindowId();
        if (patcherWindowId) {
          // Отправляем патч во все вкладки popup окна
          chrome.tabs.query({ windowId: patcherWindowId }, (tabs) => {
            tabs.forEach(tab => {
              chrome.tabs.sendMessage(tab.id, {
                type: 'PATCH_DETECTED',
                content: message.content
              }).catch(err => {
                console.log('Tab not ready yet, will use storage');
              });
            });
          });
        }
      }, 200);
    });
    
    sendResponse({ success: true });
  }
  return true;
});

// Отслеживаем закрытие окна панели
chrome.windows.onRemoved.addListener(async (windowId) => {
  const patcherWindowId = await getPatcherWindowId();
  if (windowId === patcherWindowId) {
    await setPatcherWindowId(null);
  }
});

// Проверяем при запуске, не осталось ли окно от предыдущей сессии
chrome.runtime.onStartup.addListener(async () => {
  await setPatcherWindowId(null);
});