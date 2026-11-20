package com.lumenchord.pianoweave.util

import android.content.SharedPreferences

/** In-memory [SharedPreferences]; edits apply on apply()/commit(). */
internal class FakePrefs : SharedPreferences {
    val values = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = values.toMutableMap()
    override fun getString(key: String, defValue: String?) = values[key] as String? ?: defValue
    override fun getStringSet(key: String, defValues: MutableSet<String>?) =
        @Suppress("UNCHECKED_CAST") (values[key] as MutableSet<String>?) ?: defValues
    override fun getInt(key: String, defValue: Int) = values[key] as Int? ?: defValue
    override fun getLong(key: String, defValue: Long) = values[key] as Long? ?: defValue
    override fun getFloat(key: String, defValue: Float) = values[key] as Float? ?: defValue
    override fun getBoolean(key: String, defValue: Boolean) = values[key] as Boolean? ?: defValue
    override fun contains(key: String) = key in values
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        val changes = mutableMapOf<String, Any?>()
        val removed = mutableSetOf<String>()
        var clear = false
        override fun putString(key: String, value: String?) = apply { changes[key] = value }
        override fun putStringSet(key: String, values: MutableSet<String>?) = apply { changes[key] = values }
        override fun putInt(key: String, value: Int) = apply { changes[key] = value }
        override fun putLong(key: String, value: Long) = apply { changes[key] = value }
        override fun putFloat(key: String, value: Float) = apply { changes[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { changes[key] = value }
        override fun remove(key: String) = apply { removed += key }
        override fun clear() = apply { clear = true }
        override fun commit(): Boolean {
            if (clear) values.clear()
            removed.forEach { values.remove(it) }
            values.putAll(changes)
            return true
        }
        override fun apply() {
            commit()
        }
    }
}
