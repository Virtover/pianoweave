package com.lumenchord.pianoweave.ui.viewmodel

import com.lumenchord.pianoweave.util.isDebuggable
import android.content.Context
import com.lumenchord.pianoweave.util.AppPrefs

/**
 * Which keys the piano roll shows. [All] is the whole keyboard, as always (the default); [Song] the range the song
 * uses, in whole octaves; [Follow] a two-octave window that moves with the music; [Custom] a range of one's own.
 */
enum class KeyboardRange(val label: String) {
    All("All 88"), Song("Song"), Follow("Follow"), Custom("Custom");

    companion object {
        fun fromName(name: String?): KeyboardRange = entries.firstOrNull { it.name == name } ?: All
    }
}

/** Debug builds: the Learn header's pill as the server decides ([Auto]), or forced to credits or to support. */
enum class HeaderPillOverride(val label: String) {
    Auto("Automatic"), Credits("Credits"), Support("Support me");

    fun next(): HeaderPillOverride = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromName(name: String?): HeaderPillOverride = entries.firstOrNull { it.name == name } ?: Auto
    }
}

internal fun PianoWeaveViewModel.setIncludeDrumNotesImpl(context: Context, include: Boolean) {
    if (!context.isDebuggable) return
    includeDrumNotes = include
    AppPrefs.get(context).edit().putBoolean(AppPrefs.DEBUG_DRUM_NOTES, include).apply()
}

internal fun PianoWeaveViewModel.cycleHeaderPillImpl(context: Context) {
    headerPillOverride = headerPillOverride.next()
    AppPrefs.get(context).edit().putString(AppPrefs.DEBUG_HEADER_PILL, headerPillOverride.name).apply()
}

/** The lowest and highest keys of the piano (A0, C8). */
const val PIANO_LOWEST = 21
const val PIANO_HIGHEST = 108

internal fun PianoWeaveViewModel.loadDisplayPreferences(context: Context) {
    val prefs = AppPrefs.get(context)
    isNoteNamesEnabled = prefs.getBoolean(AppPrefs.IS_NOTE_NAMES_ENABLED, true)
    isSongMapEnabled = prefs.getBoolean(AppPrefs.IS_SONG_MAP_ENABLED, false)
    keyboardRange = KeyboardRange.fromName(prefs.getString(AppPrefs.KEYBOARD_RANGE, null))
    customRangeLow = prefs.getInt(AppPrefs.CUSTOM_RANGE_LOW, 33).coerceIn(PIANO_LOWEST, PIANO_HIGHEST - 12)
    customRangeHigh = prefs.getInt(AppPrefs.CUSTOM_RANGE_HIGH, 96).coerceIn(customRangeLow + 12, PIANO_HIGHEST)
    includeDrumNotes = !context.isDebuggable || prefs.getBoolean(AppPrefs.DEBUG_DRUM_NOTES, true)
    headerPillOverride = if (context.isDebuggable) HeaderPillOverride.fromName(prefs.getString(AppPrefs.DEBUG_HEADER_PILL, null))
    else HeaderPillOverride.Auto
}

internal fun PianoWeaveViewModel.setNoteNamesEnabledImpl(context: Context, enabled: Boolean) {
    isNoteNamesEnabled = enabled
    AppPrefs.get(context).edit().putBoolean(AppPrefs.IS_NOTE_NAMES_ENABLED, enabled).apply()
}

internal fun PianoWeaveViewModel.setSongMapEnabledImpl(context: Context, enabled: Boolean) {
    isSongMapEnabled = enabled
    AppPrefs.get(context).edit().putBoolean(AppPrefs.IS_SONG_MAP_ENABLED, enabled).apply()
}

internal fun PianoWeaveViewModel.setKeyboardRangeImpl(context: Context, range: KeyboardRange) {
    keyboardRange = range
    AppPrefs.get(context).edit().putString(AppPrefs.KEYBOARD_RANGE, range.name).apply()
}

/** A custom range of at least an octave, within the piano. */
internal fun PianoWeaveViewModel.setCustomRangeImpl(context: Context, low: Int, high: Int) {
    val lo = low.coerceIn(PIANO_LOWEST, PIANO_HIGHEST - 12)
    val hi = high.coerceIn(lo + 12, PIANO_HIGHEST)
    customRangeLow = lo
    customRangeHigh = hi
    AppPrefs.get(context).edit().putInt(AppPrefs.CUSTOM_RANGE_LOW, lo).putInt(AppPrefs.CUSTOM_RANGE_HIGH, hi).apply()
}
