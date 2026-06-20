# Synapse Design Specification

This document details the core design system, typography tokens, color palette, dimensions, shapes, and animation specifications used throughout the Synapse application to ensure visual coherence, modern responsiveness, and premium aesthetics.

---

## 1. Typography System

All text elements in the application must use standard Material 3 typography definitions. Avoid ad-hoc font sizes or hardcoded custom fonts unless defined below.

| Typography Token | Role / Use Case | Weight | Font Size | Line Height |
| :--- | :--- | :--- | :--- | :--- |
| `typography.titleLarge` | TopAppBar title, major dialog header | Bold | 22 sp | 28 sp |
| `typography.titleMedium` | Cards title, section header, drawer app title | Bold | 16 sp | 24 sp |
| `typography.bodyMedium` | General form labels, main settings text, input fields | Medium / Regular | 14 sp | 20 sp |
| `typography.bodySmall` | Secondary list labels, file paths, status details, timestamps | Regular | 12 sp | 16 sp |
| `typography.labelSmall` | Input field helper labels, tiny categories | Medium | 11 sp | 16 sp |

---

## 2. Color Palette (Material 3 Theme Synchronized)

Do not hardcode specific hexadecimal colors inside UI layouts. Colors must be resolved dynamically from the `MaterialTheme.colorScheme` to support light/dark theme switching seamlessly.

*   **Primary / Accent**: `colorScheme.primary` (FABs, primary active actions).
*   **Secondary / Status**:
    *   `Connected`: Safe emerald tint (`Color(0xFF10B981)`)
    *   `Paused`: Warning amber tint (`Color(0xFFF59E0B)`)
    *   `Disconnected`: Alert crimson tint (`Color(0xFFEF4444)`)
*   **Text / Foreground**:
    *   Primary content: `colorScheme.onSurface`
    *   Secondary details (subtitles, metadata): `colorScheme.onSurfaceVariant`
*   **Backgrounds / Surfaces**:
    *   Main scaffold container: `colorScheme.background`
    *   Navigation Drawer sheet background: `colorScheme.surface`
    *   TopAppBar container: `colorScheme.surfaceVariant`
*   **Dividers / Borders**:
    *   Separators & section borders: `colorScheme.outlineVariant`

---

## 3. Layout & Dimension Guidelines

*   **Paddings / Spacing**: Use a standard 4dp grid hierarchy.
    *   `16.dp`: Standard page margins, inner dialog paddings.
    *   `12.dp`: Vertical list item paddings, horizontal card inner items.
    *   `8.dp`: Spacing between stacked controls, item separators.
    *   `4.dp`: Tiny layout offsets.
*   **Compact Navigation Drawer**:
    *   Width: Set to **`65%`** of the screen width (`modifier = Modifier.fillMaxWidth(0.65f)`) for a natural, responsive layout.
    *   Drawer Item Spacing: `12.dp` horizontal padding, transparent container backgrounds for unselected items.
*   **Lists**:
    *   List Items (Folders, Devices) are styled as flat, clean clickable rows without heavy card elevation outlines to promote a smooth, modern UI.

---

## 4. Corner Shapes & Elevation

*   **FAB (Floating Action Button)**:
    *   Shape: `FloatingActionButtonDefaults.extendedFabShape`
    *   Elevation: Dynamic (0.dp during transitions to prevent shadow lag, 6.dp when static).
*   **Buttons**:
    *   Shape: Fully rounded capsule / pill shape.
*   **Cards & List Containers**:
    *   Shape: `RoundedCornerShape(12.dp)`
*   **Overlays / Bottom Sheets / Dialogs**:
    *   Shape: `RoundedCornerShape(28.dp)`

---

## 5. Animation Specification (Fast Spatial Motion)

*   **FAB Scaling Transitions**:
    *   `enter`: Snappy spring scale-in with a playful bounce.
    *   `exit`: Snappy spring scale-out.
    *   **Spring Spec**: `spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)`
*   **Page Swiping / Horizontal Pager**:
    *   Uses fast, responsive spatial scrolling with physics-based drag-to-settle.
*   **Expressive Segmented Buttons**:
    *   **Shape Morphing**: Animates corners from default segment boundaries (outer curves, flat inner borders) to fully rounded pill shape (`24.dp`) when active.
    *   **Layout Weight Morphing**: Dynamically animates button weights (`1.6f` selected vs `0.7f` unselected) and toggles text visibility inside `AnimatedVisibility` so active selection expands smoothly while unselected options contract.
    *   **Spring Spec**: Synchronized using `spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)`.
