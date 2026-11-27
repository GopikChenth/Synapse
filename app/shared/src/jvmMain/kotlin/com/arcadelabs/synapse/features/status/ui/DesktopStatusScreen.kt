package com.arcadelabs.synapse.features.status.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.arcadelabs.synapse.core.designsystem.components.DesktopPage
import org.koin.compose.viewmodel.koinViewModel

/** Desktop status page: the shared M3 status content in a desktop page layout. */
@Composable
fun DesktopStatusScreen(
    onRunBehaviorChanged: ((RunBehavior) -> Unit)? = null,
    viewModel: StatusViewModel = koinViewModel()
) {
    DesktopPage(title = "Status", maxContentWidth = 840.dp) {
        StatusScreenContent(
            onRunBehaviorChanged = onRunBehaviorChanged,
            viewModel = viewModel
        )
    }
}
