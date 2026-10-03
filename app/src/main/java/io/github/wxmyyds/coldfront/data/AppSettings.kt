package io.github.wxmyyds.coldfront.data

/** One validated DataStore snapshot. Absence of a snapshot is represented by null in the UI. */
data class AppSettings(
    val dynamicColor: Boolean,
    val darkMode: String,
    val palette: String,
    val predictiveBack: Boolean,
    val appLanguage: String,
)
