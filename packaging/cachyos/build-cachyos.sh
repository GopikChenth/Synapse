#!/bin/bash
# CachyOS Synapse Package Build Helper Script

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${SCRIPT_DIR}"

echo "==> Building Synapse for CachyOS (x86-64 Microarchitecture Optimized)..."
makepkg -si --noconfirm "$@"

echo "==> Synapse (CachyOS Optimized) installed successfully!"
