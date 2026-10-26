package io.github.wxmyyds.coldfront.ui

/** A tab kept in composition still shares the Activity lifecycle. The Activity being resumed does not mean that tab is visible. */
internal fun isRgbPreviewActive(isPageActive: Boolean, isResumed: Boolean): Boolean =
    isPageActive && isResumed
