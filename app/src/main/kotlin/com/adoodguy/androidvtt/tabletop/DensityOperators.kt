package com.adoodguy.androidvtt.tabletop

import androidx.compose.ui.unit.Density

/**
 * Converts a density-scaled numeric stroke width to pixels when a composable's
 * LocalDensity value shadows DrawScope.density inside a Canvas lambda.
 */
internal operator fun Float.times(other: Density): Float = this * other.density
