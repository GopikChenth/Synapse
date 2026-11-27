package com.arcadelabs.synapse.features.devices.ui

import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.arcadelabs.synapse.core.designsystem.SynapseIcons
import com.arcadelabs.synapse.core.designsystem.components.DesktopPage
import org.koin.compose.viewmodel.koinViewModel

/** Desktop devices page: the shared M3 device list in a desktop page layout. */
@Composable
fun DesktopDevicesScreen(
    onAddDeviceClick: (String, String) -> Unit,
    onEditDeviceClick: (DeviceUiModel) -> Unit = {},
    viewModel: DeviceViewModel = koinViewModel()
) {
    DesktopPage(
        title = "Devices",
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onAddDeviceClick("", "") },
                icon = { Icon(SynapseIcons.Add, contentDescription = null) },
                text = { Text("Add device") }
            )
        }
    ) {
        DevicesScreen(
            onAddDeviceClick = onAddDeviceClick,
            onEditDeviceClick = onEditDeviceClick,
            viewModel = viewModel
        )
    }
}
