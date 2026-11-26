package com.example.voxmanifestorapp.data

import ManifestationRepository
import android.content.Context
import androidx.room.Room
import com.example.voxmanifestorapp.BuildConfig
import com.example.voxmanifestorapp.ui.agent.AgentCortex
import com.example.voxmanifestorapp.ui.agent.AgentViewModel
import com.example.voxmanifestorapp.ui.agent.BrainService
import com.example.voxmanifestorapp.ui.agent.ConversationAgent
import com.example.voxmanifestorapp.ui.main.MainScreenState
import com.example.voxmanifestorapp.ui.utils.SoundPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

interface AppContainer : SharedAppContainer {
    val database: ManifestationDatabase
    val manifestationRepository: ManifestationRepository
    val brainService: BrainService
    val conceptRepository: ConceptRepository
    val conversationRepository: ConversationRepository
    val mainScreenState: MainScreenState
    val agentCortex: AgentCortex
    val soundPlayer: SoundPlayer
    val conversationAgent: ConversationAgent
    val agentViewModel: AgentViewModel
}

class AppDataContainer(context: Context) : SharedAppDataContainer(context), AppContainer {
    override val database: ManifestationDatabase by lazy {
        Room.databaseBuilder(
            context.applicationContext,
            ManifestationDatabase::class.java,
            "manifestation-database"
        ).build()
    }

    override val manifestationRepository: ManifestationRepository by lazy {
        ManifestationRepository(database.manifestationDao())
    }

    override val brainService: BrainService by lazy {
        BrainService(BuildConfig.GEMINI_API_KEY)
    }

    override val conceptRepository: ConceptRepository by lazy {
        ConceptRepository(database.conceptDao())
    }

    override val conversationRepository: ConversationRepository by lazy {
        ConversationRepository(database.conversationLogDao())
    }

    override val mainScreenState: MainScreenState by lazy {
        MainScreenState()
    }

    override val agentCortex: AgentCortex by lazy {
        AgentCortex()
    }

    override val soundPlayer: SoundPlayer by lazy {
        SoundPlayer(context)
    }

    override val conversationAgent: ConversationAgent by lazy {
        ConversationAgent(
            repository = manifestationRepository,
            ttsRepo = textToSpeechRepository,
            brainService = brainService,
            statusService = statusMessageService,
            scope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
            conceptRepository = conceptRepository,
            mainScreenState = mainScreenState,
            agentCortex = agentCortex,
            conversationRepository = conversationRepository,
            soundPlayer = soundPlayer
        )
    }

    override val agentViewModel: AgentViewModel by lazy {
        AgentViewModel(
            agentCortex = agentCortex,
            soundPlayer = soundPlayer,
            conversationRepository = conversationRepository,
            conversationAgent = conversationAgent
        )
    }
}
