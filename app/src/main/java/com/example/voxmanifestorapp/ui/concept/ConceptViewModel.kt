package com.example.voxmanifestorapp.ui.concept

import ManifestationRepository
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.voxmanifestorapp.data.Concept
import com.example.voxmanifestorapp.data.ConceptBuildingContext
import com.example.voxmanifestorapp.data.ConceptRepository
import com.example.voxmanifestorapp.data.ConceptType
import com.example.voxmanifestorapp.data.DataLoadingState
import com.example.voxmanifestorapp.data.Manifestation
import com.example.voxmanifestorapp.ui.agent.AgentViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ConceptViewModel(
    private val conceptRepository: ConceptRepository,
    private val manifestationRepository: ManifestationRepository,
    private val agentViewModel: AgentViewModel,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    // Use agentViewModel directly
    val currentPlan = agentViewModel.conversationPlan
    val currentAction = agentViewModel.currentAction
    val dialogueState = agentViewModel.dialogueState
    val conversationHistory = agentViewModel.conversationHistory
    val coreLoopState = agentViewModel.coreLoopState
    val checkInState = agentViewModel.checkInState

    // tag for logs
    val TAG = "conceptViewModel"

    // collect manifestation id from navhost-provided saved state
    val manifestationId: Int? = checkNotNull(savedStateHandle["manifestationId"]) {
        "manifestationId couldn't be retrieved from saved state..."
    }
    // current manifestation that is being worked with in concept model
    private val _manifestation = MutableStateFlow<Manifestation?>(null)
    val manifestation = _manifestation.asStateFlow()

    // this is the empty placeholder, shown when loading, or when concept isn't found
    private val EMPTY_CONCEPT = Concept(
        id = -1,
        name = "",
        type = ConceptType.Present,
        items = emptyList()
    )

    private val _presentConcept = MutableStateFlow<Concept>(EMPTY_CONCEPT)
    val presentConcept: StateFlow<Concept> = _presentConcept.asStateFlow()

    private val _desiredConcept = MutableStateFlow<Concept>(EMPTY_CONCEPT)
    val desiredConcept: StateFlow<Concept> = _desiredConcept.asStateFlow()

    // data loading states allow the agent to detect when we've loaded data
    private val _dataLoadingState = MutableStateFlow<DataLoadingState>(DataLoadingState.Loading)
    val dataLoadingState: StateFlow<DataLoadingState> = _dataLoadingState.asStateFlow()

    init {
        Log.d(TAG, "ConceptViewModel initializing")

        _dataLoadingState.value = DataLoadingState.Loading

        // Create a separate job to coordinate the completion
        viewModelScope.launch {
            try {
                // Wait for both loading jobs to complete
                loadManifestationDetails().join()
                Log.d(TAG, "Manifestation job completed")
                loadConcepts().join()
                Log.d(TAG, "Concepts job completed")
                Log.d(TAG, "All data loaded, posting screen ready event")

                // update the observable state variable for the benefit of the agent.
                _dataLoadingState.value = DataLoadingState.Ready

            } catch (e: Exception) {
                Log.e(TAG, "Error during ViewModel initialization: ${e.message}")
                _dataLoadingState.value = DataLoadingState.Error
            }
        }
    }

    /** Delegate agent control functions directly */
    fun sendResponse() = agentViewModel.sendResponse()
    fun requestInterrupt() = agentViewModel.requestInterrupt()
    fun toggleConversation() = agentViewModel.toggleConversation()

    /** Signal agent that screen is closed */
    override fun onCleared() {
        Log.d(TAG, "ConceptViewModel.onCleared() called - screen may be closing")
        super.onCleared()
        Log.d(TAG, "Submitting NotifyConceptScreenExit intent to agent cortex")
        agentViewModel.notifyScreenExit()
        Log.d(TAG, "ConceptViewModel.onCleared() completed")
    }

    //todo: acceptable screen state for release version if manifestationId does come back null.

    fun loadManifestationDetails(): Job {
        Log.d("AgentInit", "loadManifestationDetails() Job starting...")
        return viewModelScope.launch {
            if (manifestationId != null) {
                try {
                    // Get initial data
                    val manifest = manifestationRepository.getManifestation(manifestationId).first()
                    _manifestation.value = manifest
                    Log.d(TAG, "Initial manifestation loaded: $manifest")

                    // Start continuous collection in a separate job that won't block this one
                    viewModelScope.launch {
                        manifestationRepository.getManifestation(manifestationId)
                            .collect { manifestation ->
                                _manifestation.value = manifestation
                            }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading manifestation: ${e.message}")
                    throw e  // Rethrow to ensure the job fails properly
                }
            }
        }
    }

    fun loadConcepts(): Job {
        Log.d("AgentInit", "loadConcepts() Job starting...")
        return viewModelScope.launch {
            if (manifestationId != null) {
                try {
                    // Check/create present concept and get initial data
                    presentExists()
                    conceptRepository.getConceptWithItems(manifestationId, ConceptType.Present).first()?.let {
                        _presentConcept.value = it
                        Log.d(TAG, "Initial present concept loaded with ${it.items.size} items")
                    }

                    // Check/create desired concept and get initial data
                    desiredExists()
                    conceptRepository.getConceptWithItems(manifestationId, ConceptType.Desired).first()?.let {
                        _desiredConcept.value = it
                        Log.d(TAG, "Initial desired concept loaded with ${it.items.size} items")
                    }

                    // Start continuous collection in separate jobs
                    viewModelScope.launch {
                        conceptRepository.getConceptWithItems(manifestationId, ConceptType.Present)
                            .collect { concept ->
                                concept?.let { _presentConcept.value = it }
                            }
                    }

                    viewModelScope.launch {
                        conceptRepository.getConceptWithItems(manifestationId, ConceptType.Desired)
                            .collect { concept ->
                                concept?.let { _desiredConcept.value = it }
                            }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading concepts: ${e.message}")
                    throw e
                }
            }
        }
    }

    /**
     * Builds a picture of the concept screen (the context) to create an initial prompt to the Brain
     */
    fun getConceptBuildingContext(): ConceptBuildingContext? {

        val currentManifestation = manifestation.value ?: return null
        Log.d("CONTEXT", "DEBUG. currentManifestation: ${currentManifestation}")

        // build map of all concept types and their items
        val conceptData = mapOf(
            ConceptType.Present to presentConcept.value.items,
            ConceptType.Desired to desiredConcept.value.items
        )

        Log.d(TAG, """
        Creating ConceptBuildingContext for manifestation: ${currentManifestation.title}
        Present State Items: ${conceptData[ConceptType.Present]?.size ?: 0}
        Desired State Items: ${conceptData[ConceptType.Desired]?.size ?: 0}
    """.trimIndent())

        /* this is the ConceptBuildingContext for Present/Desired state */
        return ConceptBuildingContext(
            manifestationTitle = currentManifestation.title,
            conceptData = conceptData,
            includeMetaData = true,
        )
    }

    /** These functions ensure the concepts exist, and if not, create them.
     */
    private suspend fun presentExists() {
        if (manifestationId == null) return

        // First, check and create concepts if needed
        val presentExists =
            conceptRepository.getConceptWithItems(manifestationId, ConceptType.Present)
                .first()

        if (presentExists != null) {
            Log.d(
                TAG, "Present state concept exists: " +
                        "id=${presentExists.id}, " +
                        "name='${presentExists.name}', " +
                        "type=${presentExists.type}, " +
                        "itemCount=${presentExists.items.size}"
            )
        } else {
            Log.d(TAG, "Creating new Present state concept")
            conceptRepository.saveConcept(
                manifestationId,
                Concept(
                    name = "Present State",
                    type = ConceptType.Present,
                    items = emptyList()
                )
            )
        }
    }

    private suspend fun desiredExists() {
        if (manifestationId == null) return

        val desiredExists =
            conceptRepository.getConceptWithItems(manifestationId, ConceptType.Desired)
                .first()

        if (desiredExists != null) {
            Log.d(
                TAG, "Present state concept exists: " +
                        "id=${desiredExists.id}, " +
                        "name='${desiredExists.name}', " +
                        "type=${desiredExists.type}, " +
                        "itemCount=${desiredExists.items.size}"
            )

        } else {
            Log.d(TAG, "Creating new Desired state concept")
            conceptRepository.saveConcept(
                manifestationId,
                Concept(
                    name = "Desired State",
                    type = ConceptType.Desired,
                    items = emptyList()
                )
            )
        }
    }
}   // viewmodel close
