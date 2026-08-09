#!/bin/bash
# Prepares Linux environment for Synapse desktop runner
mkdir -p "$HOME/Synapse/bin"
SYSTEM_SYNCTHING=$(which syncthing 2>/dev/null || echo "/usr/bin/syncthing")
if [ -x "$SYSTEM_SYNCTHING" ]; then
    ln -sf "$SYSTEM_SYNCTHING" "$HOME/Synapse/bin/syncthing.exe"
    echo "[Synapse] Linked $SYSTEM_SYNCTHING -> $HOME/Synapse/bin/syncthing.exe"
else
    echo "[Synapse] Warning: 'syncthing' binary not found. Please install it (e.g. sudo pacman -S syncthing)."
fi
