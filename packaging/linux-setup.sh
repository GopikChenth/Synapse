#!/bin/bash
# Prepares Linux & Hyprland environment for Synapse desktop runner
mkdir -p "$HOME/Synapse/bin"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Priority 1: Check Synapse Core binary
SYSTEM_SYNAPSE_CORE=$(which synapse-core 2>/dev/null || echo "")
if [ -x "$SYSTEM_SYNAPSE_CORE" ]; then
    ln -sf "$SYSTEM_SYNAPSE_CORE" "$HOME/Synapse/bin/synapse-core"
    echo "[Synapse] Linked $SYSTEM_SYNAPSE_CORE -> $HOME/Synapse/bin/synapse-core"
elif [ -f "$SCRIPT_DIR/../bin/synapse-core" ]; then
    cp "$SCRIPT_DIR/../bin/synapse-core" "$HOME/Synapse/bin/synapse-core"
    chmod +x "$HOME/Synapse/bin/synapse-core"
    echo "[Synapse] Installed native synapse-core -> $HOME/Synapse/bin/synapse-core"
elif [ -f "$HOME/Projects/Synapse Core/bin/synapse-core" ]; then
    cp "$HOME/Projects/Synapse Core/bin/synapse-core" "$HOME/Synapse/bin/synapse-core"
    chmod +x "$HOME/Synapse/bin/synapse-core"
    echo "[Synapse] Installed Synapse Core from projects folder -> $HOME/Synapse/bin/synapse-core"
fi

# Priority 2: Fallback to syncthing
SYSTEM_SYNCTHING=$(which syncthing 2>/dev/null || echo "/usr/bin/syncthing")
if [ -x "$SYSTEM_SYNCTHING" ]; then
    ln -sf "$SYSTEM_SYNCTHING" "$HOME/Synapse/bin/syncthing.exe"
    echo "[Synapse] Linked $SYSTEM_SYNCTHING -> $HOME/Synapse/bin/syncthing.exe"
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
