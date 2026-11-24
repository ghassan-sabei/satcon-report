const { app, BrowserWindow, Menu, shell } = require('electron');

// Same screens and same sync (Firebase) as the phone. The page is loaded from the site and
// kept by its service worker, so the app opens without internet after the first run and
// picks up every update to the site by itself; a new installer is only needed if this file changes.
const APP_URL = 'https://ghassan-sabei.github.io/satcon-report/sandoq/';

// one window only: two windows would write the same local data over each other
if (!app.requestSingleInstanceLock()) { app.quit(); }

let win = null;
function createWindow() {
  Menu.setApplicationMenu(null);
  win = new BrowserWindow({
    width: 1100,
    height: 820,
    minWidth: 420,
    minHeight: 600,
    title: 'Cash Box',
    autoHideMenuBar: true,
    webPreferences: { contextIsolation: true, nodeIntegration: false }
  });
  win.webContents.setWindowOpenHandler(({ url }) => {
    if (url.startsWith('blob:')) return { action: 'allow' };
    shell.openExternal(url);
    return { action: 'deny' };
  });
  win.loadURL(APP_URL);
}

app.on('second-instance', () => {
  if (win) { if (win.isMinimized()) win.restore(); win.focus(); }
});
app.whenReady().then(createWindow);
app.on('window-all-closed', () => app.quit());
