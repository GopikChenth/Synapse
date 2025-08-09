const { app, BrowserWindow, ipcMain, dialog } = require('electron');
const path = require('path');
const watchFolder = require('./core/watcher');
const { createServer, connectToPeer } = require('./core/network');
const state = require('./core/state');

function createWindow() {
    const win = new BrowserWindow({
        width: 900,
        height: 700,
        webPreferences: {
            preload: path.join(__dirname, 'preload.js')
        }
    });
    win.loadFile(path.join(__dirname, 'renderer/index.html'));
}

// IPC: Pick folder
ipcMain.handle('select-folder', async () => {
    const result = await dialog.showOpenDialog({ properties: ['openDirectory'] });
    if (!result.canceled) {
        state.setFolder(result.filePaths[0]);
        watchFolder(result.filePaths[0], (event, filePath) => {
            console.log(`[Main] Detected change: ${event} - ${filePath}`);
        });
        return result.filePaths[0];
    }
    return null;
});

// IPC: Connect to device
ipcMain.handle('connect-device', async (event, { ip, port }) => {
    state.setPeer(ip, port);
    connectToPeer(ip, port);
    return `Connecting to ${ip}:${port}...`;
});

app.whenReady().then(() => {
    createServer(); // Start listening for incoming connections
    createWindow();
});
