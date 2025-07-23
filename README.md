mkdir Synapsehttps://github.com/GopikChenth/Synapse/blob/main/README.md
cd Synapse
npm init -y
npm install electron ws
npm install electron --save-dev
npm install electron-reload --save-dev

Synapse/
├── node_modules/
├── main.js         # Main Electron process
├── package.json
├── index.html      # Frontend UI
├── preload.js      # Optional (for contextBridge APIs)
├── renderer.js     # Optional (for frontend logic)
└── README.md
