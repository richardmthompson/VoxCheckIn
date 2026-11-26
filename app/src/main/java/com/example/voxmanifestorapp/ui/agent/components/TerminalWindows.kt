package com.example.voxmanifestorapp.ui.agent.components

import android.icu.text.BreakIterator
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.voxmanifestorapp.R
import com.example.voxmanifestorapp.ui.main.TerminalEntry
import com.example.voxmanifestorapp.ui.main.TerminalEntryType
import com.example.voxmanifestorapp.ui.main.formattedTime
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.text.StringCharacterIterator
import java.util.Date
import java.util.Locale

/**
 * Centralized terminal color configuration.
 *
 * This data class provides consistent colors across all terminal components.
 * Modify these defaults to change the entire terminal theme at once.
 */
data class TerminalColors(
    val background: Color,
    val titleBar: Color,
    val titleBarOutline: Color,
    val terminalOutline: Color,
    val titleText: Color,
    val contentText: Color,
    val iconTint: Color,
)

object TerminalDefaults {
    val dimensions = TerminalDimensions()

    @Composable
    fun colors(
        background: Color = MaterialTheme.colorScheme.background,
        titleBar: Color = MaterialTheme.colorScheme.primaryContainer,
        titleBarOutline: Color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f),
        terminalOutline: Color = MaterialTheme.colorScheme.outline,
        titleText: Color = MaterialTheme.colorScheme.secondary,
        contentText: Color = MaterialTheme.colorScheme.onSurface,
        iconTint: Color = MaterialTheme.colorScheme.secondary
    ) = TerminalColors(
        background = background,
        titleBar = titleBar,
        titleBarOutline = titleBarOutline,
        terminalOutline = terminalOutline,
        titleText = titleText,
        contentText = contentText,
        iconTint = iconTint
    )

}

/**
 * Reusable terminal window composable with title bar and content area.
 *
 * This component creates a sophisticated terminal-style window with:
 * - Dark blue title bar with monospace text
 * - Almost-black content surface
 * - Subtle rounded corners (8dp)
 * - Shadow elevation for depth
 *
 * **Critical Implementation Detail:**
 * - Uses tonalElevation = 0.dp to prevent Material3 from applying color tints
 *   to the carefully chosen terminal colors
 *
 * @param title The title text to display in the title bar
 * @param modifier Modifier to apply to the root Surface
 * @param content Composable lambda for the window's content area
 */
@Composable
fun TerminalWindow(
    title: String,
    modifier: Modifier = Modifier,
    colors: TerminalColors = TerminalDefaults.colors(),
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        color = colors.background,         // Terminal window background
        tonalElevation = 0.dp,             // CRITICAL: Prevents Material3 color tinting
        shadowElevation = 4.dp,            // Subtle shadow for depth
        shape = RectangleShape
    ) {
        Column {
            // Title Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(colors.titleBar)  // Title bar background color
                    .border(
                        width = 1.dp,
                        color = colors.terminalOutline,
                        shape = RoundedCornerShape(2.dp)
                    )
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Speech icon
                    Icon(
                        painter = painterResource(id = R.drawable.speech),
                        contentDescription = "Speech",
                        modifier = Modifier.size(TerminalDefaults.dimensions.iconSize),
                        tint = colors.iconTint
                    )

                    Spacer(modifier = Modifier.width(TerminalDefaults.dimensions.iconSpacing))

                    Text(
                        text = title,
                        color = colors.titleText,         // Title text color
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            // Content Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .border(
                        width = 1.dp,
                        color = colors.terminalOutline,
                        shape = RoundedCornerShape(2.dp)
                    )
                    .padding(12.dp)
            ) {
                content()
            }
        }
    }
}

/**
 * Centralized terminal dimension configuration.
 *
 * This data class provides consistent sizing across all terminal components,
 * following the Material3 Defaults pattern for easy customization.
 */
data class TerminalDimensions(
    val titleBarHeight: Dp = 38.dp,
    val contentPadding: Dp = 12.dp,
    val iconSize: Dp = 28.dp,
    val iconSpacing: Dp = 16.dp,
    val cornerRadius: Dp = 8.dp
)


/**
 * Specialized terminal for displaying agent dialogue.
 *
 * Shows the agent's latest utterance in a terminal-styled window with:
 * - Fixed "AGENT DIALOGUE" title
 * - Monospace text rendering
 * - Fallback message when no utterance available
 *
 * @param latestUtterance The agent's most recent message, or null for default
 * @param modifier Modifier to apply to the terminal window
 * @param colors Color configuration for the terminal (defaults to TerminalDefaults.colors)
 */
@Composable
fun AgentTerminal(
    agentTerminalContent: List<TerminalEntry>,
    modifier: Modifier = Modifier,
    colors: TerminalColors = TerminalDefaults.colors()
) {
    // create state to control the position of the list scroll
    val listState = rememberLazyListState()

    // auto scroll to bottom when new entries are added
    LaunchedEffect(agentTerminalContent.size) {
        listState.animateScrollToItem(agentTerminalContent.size - 1)

        snapshotFlow {          // monitors ongoing listState's final item to trigger scroll behaviour
            listState.layoutInfo.visibleItemsInfo
                .lastOrNull()?.size     // gets the vertical size of that last list item
        }.collect {
            listState.animateScrollToItem(agentTerminalContent.size - 1)
        }
    }

    TerminalWindow(
        title = "GENIE",
        modifier = modifier,
        colors = colors
    ) {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(agentTerminalContent) { index, entry ->
                val isLastItem = index == agentTerminalContent.lastIndex
                val isSpeech = entry.entryType == TerminalEntryType.AgentSpeech

                if (isLastItem && isSpeech) {
                    AnimatedAgentTerminalEntryRow(
                        entry = entry,
                        colors = colors,
                    )
                } else {
                    AgentTerminalEntryRow(entry, colors)
                }
            }
        }


    }
}

@Composable
fun AnimatedAgentTerminalEntryRow(
    entry: TerminalEntry,
    colors: TerminalColors,
) {
    val contentToAnimate = entry.content


    // remember this iterator across compositions,
    // but if contentToAnimate changes, make a new one to animate that.
    val breakIterator = remember(contentToAnimate) {
        BreakIterator.getCharacterInstance()
    }

    // initiate animatedContent with empty string when contentToAnimate re-sets
    var animatedContent by remember(contentToAnimate) {
        mutableStateOf("")
    }

    // initiate the process of animating the content, for the new content coming in...
    LaunchedEffect(contentToAnimate) {
        delay(1000)

        // BreakIterator process expects a StringCharacterIterator data type (not just a string)
        val textHolder = StringCharacterIterator(contentToAnimate)
        breakIterator.text = textHolder

        var nextIndex = breakIterator.next()

        // loop that progressively adds all letters to variable over time (animation)
        while (nextIndex != BreakIterator.DONE) {

            animatedContent = contentToAnimate.subSequence(0, nextIndex).toString()
            nextIndex = breakIterator.next()
            delay(50)
        }

    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val annotatedText = buildAnnotatedString {
        append(
            makePrompt(
                entry.formattedTime(),
                timeColor = colors.contentText,
                promptText = "Genie",
                promptColor = secondaryColor
            )
        )
        withStyle(style = SpanStyle(color = primaryColor)) {
            append(animatedContent)
        }
    }

    Text(
        text = annotatedText,
        style = MaterialTheme.typography.bodyLarge
    )
}

@Composable
fun AgentTerminalEntryRow(entry: TerminalEntry, colors: TerminalColors) {
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val primaryColor = MaterialTheme.colorScheme.primary

    val annotatedText = buildAnnotatedString {
        when (entry.entryType) {
            TerminalEntryType.AgentSpeech -> {
                // Timestamp and prompt in onSurface color
                append(
                    makePrompt(
                        entry.formattedTime(),
                        timeColor = colors.contentText,
                        promptText = "Genie",
                        promptColor = secondaryColor
                    )
                )
                // Actual speech content in primary color
                withStyle(style = SpanStyle(color = primaryColor)) {
                    append(entry.content)
                }
            }

            TerminalEntryType.AgentStatus -> {
                // All status text in onSurface color
                withStyle(style = SpanStyle(color = secondaryColor)) {
                    append("*** ${entry.content}")
                }
            }

            else -> {
                // Shouldn't happen
            }
        }
    }

    Text(
        text = annotatedText,
        style = MaterialTheme.typography.bodyLarge
    )
}

fun makePrompt(
    formattedTime: String,
    timeColor: Color,
    promptText: String,
    promptColor: Color
): AnnotatedString {
    return buildAnnotatedString {
        /** removing timestamp for now
        withStyle(style = SpanStyle(color = timeColor)) {
            append("[${formattedTime}] ")
        }
        */
        withStyle(style = SpanStyle(color = promptColor)) {
            append("$promptText> ")
        }
    }
}

/**
 * Specialized terminal for displaying user speech input.
 *
 * Combines the functionality of VoxRecogZone with terminal styling:
 * - Fixed "USER INPUT" title
 * - Speech icon on the left
 * - Real-time transcription text
 * - Terminal color scheme instead of Card styling
 *
 * @param rawTextState Real-time speech-to-text transcription
 * @param modifier Modifier to apply to the terminal window
 * @param colors Color configuration for the terminal (defaults to TerminalDefaults.colors)
 */
@Composable
fun UserTerminal(
    latestUserUtterance: String,
    userTerminalContent: List<TerminalEntry>,
    modifier: Modifier = Modifier,
    colors: TerminalColors = TerminalDefaults.colors()
) {
    TerminalWindow(
        title = "USER",
        modifier = modifier,
        colors = colors
    ) {
        val userTextColor = MaterialTheme.colorScheme.secondary

        // create state to control the position of the list scroll
        val listState = rememberLazyListState()

        // auto scroll to bottom when new entries are added
        LaunchedEffect(userTerminalContent.size) {
            listState.animateScrollToItem(userTerminalContent.size)

            snapshotFlow {          // monitors ongoing listState's final item to trigger scroll behaviour
                listState.layoutInfo.visibleItemsInfo
                    .lastOrNull()?.size     // gets the vertical size of that last list item
            }.collect {
                listState.animateScrollToItem(userTerminalContent.size)
            }
        }

        LazyColumn(
            modifier = modifier.fillMaxSize(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(userTerminalContent) {
                UserTerminalEntryRow(entry = it)
            }
            /** this was a mistake and needs to be removed
            if (latestUserUtterance.isNotEmpty()) {
                item {
                    UserTerminalLiveRow(latestUserUtterance)
                }
            }
            */
        }
    }
}

@Composable
fun UserTerminalLiveRow(latestUserUtterance: String) {
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val primaryColor = MaterialTheme.colorScheme.primary

    // Current time (for live speech)
    val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

    // Blinking cursor animation
    var showCursor by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)  // Blink every 500ms
            showCursor = !showCursor
        }
    }

    val annotatedText = buildAnnotatedString {
        append(
            makePrompt(
                formattedTime = currentTime,
                timeColor = secondaryColor,
                promptText = "User",
                promptColor = primaryColor
            )
        )
        withStyle(style = SpanStyle(color = secondaryColor)) {
            append(latestUserUtterance)
            if (showCursor) {
                append("█")     // block cursor
            }
        }
    }

    Text(
        text = annotatedText,
        style = MaterialTheme.typography.bodyLarge
    )
}

@Composable
fun UserTerminalEntryRow(entry: TerminalEntry) {
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val primaryColor = MaterialTheme.colorScheme.primary

    val annotatedText = buildAnnotatedString {
        when (entry.entryType) {
            TerminalEntryType.UserSpeech -> {
                // Timestamp and prompt in onSurface color
                append(
                    makePrompt(
                        entry.formattedTime(),
                        timeColor = secondaryColor,
                        promptText = "User",
                        promptColor = primaryColor
                    )
                )
                // Actual speech content in primary color
                withStyle(style = SpanStyle(color = secondaryColor)) {
                    append(entry.content)
                }
            }

            else -> {
                // Shouldn't happen
            }
        }
    }

    Text(
        text = annotatedText,
        style = MaterialTheme.typography.bodyLarge
    )
}
