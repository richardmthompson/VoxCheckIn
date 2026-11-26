package com.example.voxmanifestorapp.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** Color scheme guides
 * Common pairs:
 *   - background / onBackground - screen background / main body text
 *   - surface / onSurface - card background / card text
 *   - primary / onPrimary - button background / button text
 *   - surfaceVariant / onSurfaceVariant - panel background / panel text
 *
 *  Material3 automatically picks the on* color when you use certain composables:
 * *   Surface(color = MaterialTheme.colorScheme.surface) {
 *       Text("I automatically use onSurface color")
 *
 */

/**
 *   | Material 3 Role        | Actual Color       | Hex/Value | Usage                                                           |
 *   |------------------------|--------------------|-----------|-----------------------------------------------------------------|
 *   | BACKGROUND & CONTENT   |                    |           |                                                                 |
 *   | background             | DarkestGray        | #1C1C1C   | Terminal window backgrounds (darkest, largest area)             |
 *   | onBackground           | VoxAccentGold      | #D4AF37   | Agent terminal text (gold)                                      |
 *   | SURFACES (CARDS)       |                    |           |                                                                 |
 *   | surface                | CardGray           | #263238   | Darker session cards in panels                                  |
 *   | onSurface              | lightBeige         | #F8F4E8   | Text on darker cards                                            |
 *   | surfaceVariant         | DarkGray           | #21252E   | Title bars & toolbars (AppTitle, AgentToolbar, ModeStatusBar)   |
 *   | onSurfaceVariant       | (not defined)      | -         | Use primary directly for title/toolbar text                     |
 *   | PRIMARY: GOLD ACCENT   |                    |           |                                                                 |
 *   | primary                | VoxAccentGold      | #D4AF37   | Gold button, title/toolbar text, primary actions                |
 *   | onPrimary              | Color.Black        | #000000   | Text on gold buttons                                            |
 *   | primaryContainer       | PanelGray          | #394A59   | Terminal title bars (GENIE/USER headers)                        |
 *   | onPrimaryContainer     | lightBeige         | #F8F4E8   | Text on terminal title bars                                     |
 *   | SECONDARY: BLUE ACCENT |                    |           |                                                                 |
 *   | secondary              | TerminalAccentBlue | #7AA2F7   | Blue button, status icons, User terminal text (blue)            |
 *   | onSecondary            | Color.Black        | #000000   | Text on blue buttons                                            |
 *   | secondaryContainer     | PanelGray          | #394A59   | Panel backgrounds (AgentProcessPanel, ConversationHistoryPanel) |
 *   | onSecondaryContainer   | lightBeige         | #F8F4E8   | Text on panel backgrounds                                       |
 *   | TERTIARY (FUTURE USE)  |                    |           |                                                                 |
 *   | tertiary               | TerminalAccentBlue | #7AA2F7   | Reserved (same as secondary for now)                            |
 *   | onTertiary             | Color.Black        | #000000   | Reserved                                                        |
 *   | tertiaryContainer      | PanelGray          | #394A59   | Reserved (same as containers for now)                           |
 *   | onTertiaryContainer    | lightBeige         | #F8F4E8   | Reserved                                                        |
 *   | OUTLINES & BORDERS     |                    |           |                                                                 |
 *   | outline                | outlineColor       | #7BB3D9   | Lighter outline - terminal borders                              |
 *   | outlineVariant         | DarkNight          | #0f1113   | Darker outline - panel borders                                  |
 *   | ERROR                  |                    |           |                                                                 |
 *   | error                  | Muted red          | #CC4444   | Interrupt button, error states                                  |
 *
 */
private val DarkColorScheme = darkColorScheme(
    // for title background and toolbar backgrounds
    surfaceVariant = DarkGray,
    // for disabled buttons, text, etc.
    onSurfaceVariant = Color.Gray,

    // Large Backgrounds, e.g. Terminal backgrounds
    background = DarkestGray,
    onBackground = VoxAccentGold,

    // Primary containers: Terminal titlebars
    primaryContainer = PanelGray,

    // secondary container: default panel colors
    secondaryContainer = GunmetalGray,
    onSecondaryContainer = DarkGray,

    // surface = sub-cards within containers
    surface = TerminalGray,
    onSurface = lightBeige,

    // surfaceContainer = ?

    // tertiaryContainer = lighter cards
    tertiaryContainer = LightBlueCard,

    // Primary: most prominent components across the UI
    primary = VoxAccentGold,
    onPrimary = Color.Black,

    // secondary accent color
    secondary = TerminalAccentBlue,
    onSecondary = TokyoNight,

    // Tertiary: Terminal blue accent
    tertiary = MatrixGreen,
    onTertiary = Color.Black,

    // Borders/outlines
    outline = DarkNight,
    outlineVariant = SkyBlue,

    // Muted red — pure #FF0000 is harsher than the chamber's palette warrants.
    error = Color(0xFFCC4444),
)

private val LightColorScheme = lightColorScheme(

    primaryContainer = Color(0xFFF5E6D3), // Default to concept screen color, can be overridden
    outline = Color(0xFF3F51B5),  // Default to concept border color
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
)

@Composable
fun VoxManifestorAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
/* override dynamic theming.  we only have a dark theme for now!
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
 */
    val colorScheme = DarkColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}