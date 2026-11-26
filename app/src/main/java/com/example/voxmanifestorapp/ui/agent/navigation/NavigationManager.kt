package com.example.voxmanifestorapp.ui.agent.navigation

/**
 * Manages navigation callbacks for the ConversationAgent.
 * Handles screen navigation between Main and Concept screens.
 */
class NavigationManager {
    
    // Agent's ability to navigate to concept screens
    private var navigateToConceptScreen: ((Int) -> Unit)? = null
    
    // Agent's ability to navigate back from concept screen
    private var navigateBack: (() -> Unit)? = null

    /**
     * Sets the navigation callback for concept screen navigation
     * @param navigate Lambda function that receives manifestation ID and performs navigation
     */
    fun setConceptNavigation(navigate: (Int) -> Unit) {
        navigateToConceptScreen = navigate
    }

    /**
     * Sets the back navigation callback
     * @param navigate Lambda function that performs back navigation
     */
    fun setNavigateBack(navigate: () -> Unit) {
        navigateBack = navigate
    }

    /**
     * Navigates to concept screen for the given manifestation ID
     * @param manifestationId The ID of the manifestation to open in concept screen
     */
    fun navigateToConceptScreen(manifestationId: Int) {
        navigateToConceptScreen?.invoke(manifestationId)
    }

    /**
     * Navigates back from concept screen
     */
    fun navigateBack() {
        navigateBack?.invoke()
    }

    /**
     * Checks if concept navigation is available
     */
    fun isConceptNavigationAvailable(): Boolean = navigateToConceptScreen != null

    /**
     * Checks if back navigation is available
     */
    fun isBackNavigationAvailable(): Boolean = navigateBack != null
}