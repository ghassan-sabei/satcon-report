const { app, BrowserWindow, Menu } = require('electron');

// الواجهة نفسها اللي على الهاتف والمتصفح، نفس المزامنة (Firebase)
const APP_URL = 'https://ghassan-sabei.github.io/satcon-report/stock/';

function createWindow() {
  Menu.setApplicationMenu(null);
  const win = new BrowserWindow({
    width: 1280,
    height: 820,
    minWidth: 900,
    minHeight: 600,
    title: 'ستوك',
    autoHideMenuBar: true,
    webPreferences: { contextIsolation: true, nodeIntegration: false }
  });
  win.loadURL(APP_URL);
}

app.whenReady().then(createWindow);

app.on('activate', () => {
  if (BrowserWindow.getAllWindows().length === 0) createWindow();
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});
