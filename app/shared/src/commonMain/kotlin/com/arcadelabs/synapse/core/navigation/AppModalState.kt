package com.arcadelabs.synapse.core.navigation

import com.arcadelabs.synapse.core.domain.models.Folder
import com.arcadelabs.synapse.core.domain.models.PendingDevice
import com.arcadelabs.synapse.core.domain.models.PendingFolderOffer
import com.arcadelabs.synapse.features.devices.ui.DeviceUiModel

/**
 * Type-safe, mutually exclusive modal dialog and overlay state machine.
 * Replaces 14+ scattered and independent boolean/nullable states in App and DesktopApp.
 *
 * UX: Eliminates dialog stacking, focus fighting, z-index anomalies, and stale prefilled data bleed.
 * DX: Guarantees at compile-time that all payload requirements (e.g. Folder for editing)
 *     are supplied when transitioning into that modal state.
 * AX: Single inspectable property `modalState`. Agents and tests can assert the active dialog
 *     and trigger dismiss/action flows deterministically with single-call actions.
 */
sealed interface AppModalState {

    /** No modal dialog or sheet is currently presented */
    data object None : AppModalState

    /** Presenting the dialog to create or share a new folder */
    data class CreateFolder(
        val prefilledId: String = "",
        val prefilledLabel: String = "",
        val prefilledSharedDevices: List<String> = emptyList()
    ) : AppModalState

    /** Presenting the dialog to edit settings or versioning of an existing folder */
    data class EditFolder(
        val folder: Folder
    ) : AppModalState

    /** Presenting the dialog to manually pair or add a remote device */
    data class AddDevice(
        val prefilledId: String = "",
        val prefilledName: String = ""
    ) : AppModalState

    /** Presenting the dialog to inspect or edit an already paired remote device */
    data class EditDevice(
        val device: DeviceUiModel
    ) : AppModalState

    /** Presenting the QR code and alphanumeric device ID dialog for this node */
    data object ShowDeviceId : AppModalState

    /** Presenting the raw JSON configuration import/export dialog */
    data object ImportExportConfig : AppModalState

    /** Presenting confirmation prompt to restart the backend daemon */
    data object ConfirmRestart : AppModalState

    /** Presenting confirmation prompt to shutdown the daemon / quit application */
    data object ConfirmShutdown : AppModalState

    /** Incoming peer pairing request prompt from a remote node */
    data class IncomingDevicePairing(
        val deviceId: String,
        val device: PendingDevice
    ) : AppModalState

    /** Incoming folder share offer prompt from a remote node */
    data class IncomingFolderOffer(
        val folderId: String,
        val offer: PendingFolderOffer
    ) : AppModalState
}

val AppModalState.isPresented: Boolean
    get() = this !is AppModalState.None
