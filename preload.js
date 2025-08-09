const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('electronAPI', {
    selectFolder: () => ipcRenderer.invoke('select-folder'),
    connectDevice: (ip, port) => ipcRenderer.invoke('connect-device', { ip, port })
});
