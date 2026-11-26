package com.example.voxmanifestorapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.voxmanifestorapp.R

val anybodyFont = FontFamily(
    Font(R.font.anybody, FontWeight.Normal)
)
val robotoFlexFont = FontFamily(
    Font(R.font.robotoflex)
)

// Set of Material typography styles to start with
val Typography = Typography(

    // this used for app title bar
    displaySmall = TextStyle(
        fontFamily = anybodyFont,
        fontWeight = FontWeight.Bold,
        fontStyle = FontStyle.Italic,
        fontSize = 30.sp,
        letterSpacing = 4.sp
    ),
    // this used for terminal title bar
    labelMedium = TextStyle(
        fontFamily = robotoFlexFont,
        fontWeight = FontWeight.W800,
        fontStyle = FontStyle.Italic,
        fontSize = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    // used for agent process panel header
    titleMedium = TextStyle(
        fontFamily = robotoFlexFont,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 18.sp,
        fontStyle = FontStyle.Italic
    )
    /* Other default text styles to override
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
    */
)