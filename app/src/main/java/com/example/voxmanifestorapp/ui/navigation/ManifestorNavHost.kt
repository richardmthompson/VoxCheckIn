import AppViewModelProvider
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.voxmanifestorapp.ManifestorApplication
import com.example.voxmanifestorapp.ui.main.MainScreen
import com.example.voxmanifestorapp.ui.main.MainViewModel

@Composable
fun ManifestorNavHost(
    onRequestPermission: () -> Unit,
) {
    val container = (LocalContext.current.applicationContext as ManifestorApplication).container
    val voiceModel = container.voiceManagedViewModel
    val agent = container.conversationAgent
    val statusMessageService = container.statusMessageService
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    LaunchedEffect(voiceModel, agent, statusMessageService) {
        voiceModel.setStatusService(statusMessageService)
        agent.setVoiceManager(voiceModel)
    }
    DisposableEffect(agent, backDispatcher) {
        // The app-scoped agent must not keep the Activity's dispatcher after disposal.
        var activeDispatcher = backDispatcher
        agent.navigationManager.setNavigateBack { activeDispatcher?.onBackPressed() }
        onDispose { activeDispatcher = null }
    }

    val mainViewModel: MainViewModel = viewModel(factory = AppViewModelProvider.Factory)
    MainScreen(
        onRequestPermission = onRequestPermission,
        viewModel = mainViewModel,
        voiceModel = voiceModel,
        statusMessageService = statusMessageService,
    )
}
