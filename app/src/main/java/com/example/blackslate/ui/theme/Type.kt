package com.example.blackslate.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.blackslate.R

val DisplayFont = FontFamily(Font(R.font.orange_avenue_regular))
val OutlineFont = FontFamily(Font(R.font.orange_avenue_outline))

val Typography = Typography(
    displayLarge = TextStyle(fontFamily = DisplayFont, fontSize = 72.sp, lineHeight = 76.sp),
    displayMedium = TextStyle(fontFamily = DisplayFont, fontSize = 48.sp, lineHeight = 54.sp),
    headlineLarge = TextStyle(fontFamily = DisplayFont, fontSize = 38.sp, lineHeight = 44.sp),
    headlineMedium = TextStyle(fontFamily = DisplayFont, fontSize = 28.sp, lineHeight = 34.sp),
    titleLarge = TextStyle(fontFamily = DisplayFont, fontSize = 25.sp, lineHeight = 30.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 18.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 13.sp, letterSpacing = 1.2.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 1.8.sp)
)
