package com.tonic.player

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

class LibraryStore(ctx: Context) {
    private val file = File(ctx.filesDir, "library.json")
    private val state = MutableStateFlow(load())
    val lib: StateFlow<Library> = state

    private fun load(): Library = try {
        if (file.exists()) libFromJson(file.readText()) else Library()
    } catch (e: Exception) {
        Library()
    }

    fun update(f: (Library) -> Library) {
        state.value = f(state.value)
        file.writeText(libToJson(state.value))
    }
}
