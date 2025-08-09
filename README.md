# ⚡ Synapse
Synapse is a minimal Electron-based desktop application featuring WebSocket (`ws`) support and live reloading via `electron-reload`.
This app is designed to be a simple starting point for building real-time, cross-platform desktop applications using JavaScript.
---
## 📦 Folder Structure
Synapse/
├── core/
│   ├── watcher.js        # Watches folders for changes
│   ├── network.js        # Handles peer connections
│   ├── transfer.js       # Handles file sending/receiving
│   └── state.js          # Saves metadata
├── renderer/         # UI
│       ├── index.html
│       └── app.js
├── preload.js
├── main.js           # Electron backend
└── package.json
---
## 🚀 Getting Started
### 1. Clone the repository
```
git clone https://github.com/GopikChenth/Synapse.git
cd Synapse 
```
### 2. Initialize the project
```
mkdir Synapse
cd Synapse
npm init -y
```
### 3. Clone the repository
```
npm install electron ws
npm install electron --save-dev
npm install electron-reload --save-dev
```
### Run the App
```
npm start
```

