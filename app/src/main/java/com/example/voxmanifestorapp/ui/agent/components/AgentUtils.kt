package com.example.voxmanifestorapp.ui.agent.components

import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared utilities, constants, and styling for agent UI components.
 * This file contains common values used across multiple agent components.
 */

/**
 * Animation specifications used across agent components
 */
object AgentAnimations {
    /**
     * Standard slide animation duration in milliseconds
     */
    const val SLIDE_DURATION_MS = 300
    
    /**
     * Standard tween animation specification for slide animations
     */
    val slideAnimationSpec = tween<Int>(durationMillis = SLIDE_DURATION_MS)
    
    /**
     * Standard tween animation specification for fade animations
     */
    val fadeAnimationSpec = tween<Float>(durationMillis = SLIDE_DURATION_MS)
}

/**
 * Shared styling constants for agent components
 */
object AgentStyling {
    /**
     * Standard corner radius for panels and cards
     */
    val CORNER_RADIUS = 16.dp
    
    /**
     * Standard tab dimensions
     */
    val TAB_WIDTH = 25.dp
    val TAB_HEIGHT = 80.dp
    
    /**
     * Standard panel properties
     */
    val PANEL_WIDTH = 400.dp
    val PANEL_HEIGHT = 400.dp
    val CONTENT_PADDING = 16.dp
    
    /**
     * Standard shapes for panels and components
     */
    val PANEL_SHAPE_LEFT = RoundedCornerShape(topEnd = CORNER_RADIUS, bottomEnd = CORNER_RADIUS)
    val PANEL_SHAPE_RIGHT = RoundedCornerShape(topStart = CORNER_RADIUS, bottomStart = CORNER_RADIUS)
    val TAB_SHAPE_LEFT = RoundedCornerShape(topEnd = CORNER_RADIUS, bottomEnd = CORNER_RADIUS)
    val TAB_SHAPE_RIGHT = RoundedCornerShape(topStart = CORNER_RADIUS, bottomStart = CORNER_RADIUS)
    val CARD_SHAPE = RoundedCornerShape(8.dp)
    
    /**
     * Standard colors used across agent components
     */
    object Colors {
        val PANEL_BACKGROUND = Color.Black.copy(alpha = 0.9f)
        val CONVERSATION_PANEL_BACKGROUND = Color(0xFF394A59) // Dark slate blue
        val CARD_BACKGROUND = Color(0xFF263238) // Darker shade for cards
        val BORDER_COLOR = Color(0xFF3F51B5)
        val WHITE_TEXT = Color.White
        val WHITE_TEXT_SECONDARY = Color.White.copy(alpha = 0.7f)
        val WHITE_DIVIDER = Color.White.copy(alpha = 0.2f)
        val BUTTON_BLUE = Color(0xFF2196F3)
        val SUCCESS_GREEN = Color.Green
        val SUCCESS_GREEN_BACKGROUND = Color(0x1A4CAF50)
        val ERROR_RED = Color.Red
        val ERROR_RED_BACKGROUND = Color(0x22FF0000)
        val GRAY = Color.Gray
        val TRANSPARENT = Color.Transparent
    }
    
    /**
     * Standard button and icon sizes
     */
    val BUTTON_SIZE = 48.dp
    val ICON_SIZE = 24.dp
    val LARGE_ICON_SIZE = 40.dp
}

/**
 * Layout constants for consistent spacing
 */
object AgentLayout {
    /**
     * Standard spacing values
     */
    val SMALL_SPACING = 4.dp
    val MEDIUM_SPACING = 8.dp
    val LARGE_SPACING = 16.dp
    
    /**
     * Standard heights and dimensions
     */
    val TOOLBAR_HEIGHT = 56.dp
    val SESSION_CARD_HEIGHT = 200.dp
    
    /**
     * Z-index values for layering
     */
    const val PANEL_Z_INDEX = 10f
}

/**
 * Common utility functions for agent components
 */
object AgentUtils {
    /**
     * Calculates the Y offset for positioning a tab at a specific position
     * along a container's height.
     * 
     * @param containerHeight The total height of the container
     * @param tabHeight The height of the tab itself
     * @param position The relative position (0.0f to 1.0f) where the tab should be placed
     * @return The Y offset for positioning the tab
     */
    fun calculateTabOffset(containerHeight: Dp, tabHeight: Dp, position: Float): Dp {
        return (containerHeight * position) - (tabHeight / 2)
    }
    
    /**
     * Common screen height fraction values for consistent panel sizing
     */
    object ScreenFractions {
        const val QUARTER = 0.25f
        const val THIRD = 0.33f
        const val HALF = 0.5f
        const val TWO_THIRDS = 0.67f
        const val THREE_QUARTERS = 0.75f
    }
    
    /**
     * Common tab positions for panels
     */
    object TabPositions {
        const val TOP = 0.2f
        const val CENTER = 0.5f
        const val BOTTOM = 0.8f
    }
}