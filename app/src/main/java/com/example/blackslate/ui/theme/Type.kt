package com.example.blackslate.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.blackslate.R

val HeadlineFont = FontFamily(Font(R.font.ntype82_headline))
val BodyFont = FontFamily(Font(R.font.helvetica))

val Typography = Typography(
    displayLarge = TextStyle(fontFamily = HeadlineFont, fontSize = 72.sp, lineHeight = 76.sp),
    displayMedium = TextStyle(fontFamily = HeadlineFont, fontSize = 48.sp, lineHeight = 54.sp),
    headlineLarge = TextStyle(fontFamily = HeadlineFont, fontSize = 38.sp, lineHeight = 44.sp),
    headlineMedium = TextStyle(fontFamily = HeadlineFont, fontSize = 28.sp, lineHeight = 34.sp),
    titleLarge = TextStyle(fontFamily = HeadlineFont, fontSize = 25.sp, lineHeight = 30.sp),
    bodyLarge = TextStyle(fontFamily = BodyFont, fontSize = 18.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontFamily = BodyFont, fontSize = 15.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    labelMedium = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Medium, fontSize = 13.sp, letterSpacing = 1.2.sp),
    labelSmall = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 1.8.sp)
)
