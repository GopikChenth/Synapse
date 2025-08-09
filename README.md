
```bash
mkdir Synapse
cd Synapse
npm init -y
npm install electron ws
npm install electron --save-dev
npm install electron-reload --save-dev
```


```
Synapse/
├── core/
│   ├── watcher.js        # Watches folders for changes
│   ├── network.js        # Handles peer connections
│   ├── transfer.js       # Handles file sending/receiving
│   └── state.js          # Saves metadata
├── electron/
│   ├── main.js           # Electron backend
│   ├── preload.js
│   └── renderer/         # UI
│       ├── index.html
│       └── app.js
└── package.json
```

