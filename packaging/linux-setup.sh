#!/bin/bash
# Prepares Linux & Hyprland environment for Synapse desktop runner
mkdir -p "$HOME/Synapse/bin"
SYSTEM_SYNCTHING=$(which syncthing 2>/dev/null || echo "/usr/bin/syncthing")
if [ -x "$SYSTEM_SYNCTHING" ]; then
    ln -sf "$SYSTEM_SYNCTHING" "$HOME/Synapse/bin/syncthing.exe"
    echo "[Synapse] Linked $SYSTEM_SYNCTHING -> $HOME/Synapse/bin/syncthing.exe"
else
    echo "[Synapse] Warning: 'syncthing' binary not found. Please install it (e.g. sudo pacman -S syncthing)."
fi

# Hyprland auto-configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [ -d "$HOME/.config/hypr" ] || [ -n "$HYPRLAND_INSTANCE_SIGNATURE" ]; then
    mkdir -p "$HOME/.config/hypr"
    if [ -f "$SCRIPT_DIR/hyprland/synapse.conf" ]; then
        cp "$SCRIPT_DIR/hyprland/synapse.conf" "$HOME/.config/hypr/synapse.conf"
        echo "[Synapse] Installed Hyprland configuration to $HOME/.config/hypr/synapse.conf"
        
        # Check standard hyprland.conf
        HYPR_CONF="$HOME/.config/hypr/hyprland.conf"
        if [ -f "$HYPR_CONF" ] && ! grep -q "synapse.conf" "$HYPR_CONF"; then
            echo "" >> "$HYPR_CONF"
            echo "# Synapse Scratchpad & Hotkeys" >> "$HYPR_CONF"
            echo "source = ~/.config/hypr/synapse.conf" >> "$HYPR_CONF"
            echo "[Synapse] Appended 'source = ~/.config/hypr/synapse.conf' to $HYPR_CONF"
        fi

        # Check Caelestia / Hyprlua user config
        CAELESTIA_CONF="$HOME/.config/caelestia/hypr-user.lua"
        if [ -f "$CAELESTIA_CONF" ] && ! grep -q "synapse" "$CAELESTIA_CONF"; then
            cat << 'EOF' >> "$CAELESTIA_CONF"

-- Synapse Scratchpad and Hotkeys
hl.bind("SUPER, S", hl.dsp.exec_cmd("synapse --toggle"))
hl.bind("SUPER + SHIFT, P", hl.dsp.exec_cmd("synapse --pause"))
hl.bind("SUPER + SHIFT, R", hl.dsp.exec_cmd("synapse --resume"))
hl.bind("SUPER + SHIFT, S", hl.dsp.exec_cmd("synapse --rescan"))

-- Synapse Window Rules
hl.window_rule({
    match = { class = "Synapse" },
    float = true,
    size = "(monitor_w*0.6) (monitor_h*0.7)",
    center = true,
    workspace = "special:synapse",
})
EOF
            echo "[Synapse] Appended Lua bindings to $CAELESTIA_CONF"
        fi
    fi
fi
