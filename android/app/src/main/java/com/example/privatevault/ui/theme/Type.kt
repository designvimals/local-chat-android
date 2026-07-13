package com.example.privatevault.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.privatevault.R

@OptIn(ExperimentalTextApi::class)
private fun googleSansFlexFont(weight: FontWeight): Font = Font(
    resId = R.font.google_sans_flex_variable,
    weight = weight,
    style = FontStyle.Normal,
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight.weight),
        FontVariation.width(100f),
        FontVariation.opticalSizing(18.sp),
        FontVariation.Setting("ROND", 16f)
    )
)

val GoogleSansFlexFamily = FontFamily(
    googleSansFlexFont(FontWeight.W100),
    googleSansFlexFont(FontWeight.W300),
    googleSansFlexFont(FontWeight.W400),
    googleSansFlexFont(FontWeight.W500),
    googleSansFlexFont(FontWeight.W600),
    googleSansFlexFont(FontWeight.W700),
    googleSansFlexFont(FontWeight.W900)
)

private val defaults = Typography()

val PrivateTypography = Typography(
    displayLarge = defaults.displayLarge.copy(fontFamily = GoogleSansFlexFamily),
    displayMedium = defaults.displayMedium.copy(fontFamily = GoogleSansFlexFamily),
    displaySmall = defaults.displaySmall.copy(fontFamily = GoogleSansFlexFamily),
    headlineLarge = defaults.headlineLarge.copy(fontFamily = GoogleSansFlexFamily),
    headlineMedium = defaults.headlineMedium.copy(fontFamily = GoogleSansFlexFamily),
    headlineSmall = defaults.headlineSmall.copy(fontFamily = GoogleSansFlexFamily),
    titleLarge = defaults.titleLarge.copy(fontFamily = GoogleSansFlexFamily, fontWeight = FontWeight.SemiBold),
    titleMedium = defaults.titleMedium.copy(fontFamily = GoogleSansFlexFamily, fontWeight = FontWeight.SemiBold),
    titleSmall = defaults.titleSmall.copy(fontFamily = GoogleSansFlexFamily, fontWeight = FontWeight.Medium),
    bodyLarge = defaults.bodyLarge.copy(fontFamily = GoogleSansFlexFamily, lineHeight = 23.sp),
    bodyMedium = defaults.bodyMedium.copy(fontFamily = GoogleSansFlexFamily),
    bodySmall = defaults.bodySmall.copy(fontFamily = GoogleSansFlexFamily),
    labelLarge = defaults.labelLarge.copy(fontFamily = GoogleSansFlexFamily, fontWeight = FontWeight.SemiBold),
    labelMedium = defaults.labelMedium.copy(fontFamily = GoogleSansFlexFamily, fontWeight = FontWeight.Medium),
    labelSmall = defaults.labelSmall.copy(fontFamily = GoogleSansFlexFamily, fontWeight = FontWeight.Medium)
)
