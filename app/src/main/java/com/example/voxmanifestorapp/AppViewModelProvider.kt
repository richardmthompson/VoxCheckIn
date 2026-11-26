import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.voxmanifestorapp.ManifestorApplication
import com.example.voxmanifestorapp.ui.main.MainViewModel

object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer {
            val container = manifestorApplication().container
            MainViewModel(
                repository = container.manifestationRepository,
                ttsRepo = container.textToSpeechRepository,
                mainScreenState = container.mainScreenState,
                agentCortex = container.agentCortex,
                agentViewModel = container.agentViewModel,
                conversationRepository = container.conversationRepository,
                voiceManagedViewModel = container.voiceManagedViewModel
            )
        }
    }
}

private fun CreationExtras.manifestorApplication(): ManifestorApplication =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ManifestorApplication)
