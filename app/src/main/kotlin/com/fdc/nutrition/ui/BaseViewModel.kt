package com.fdc.nutrition.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** Shared helpers: one-off messages and safe launching that surfaces errors to the user. */
abstract class BaseViewModel : ViewModel() {
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    protected fun message(text: String) { _messages.trySend(text) }

    protected fun launchSafe(block: suspend CoroutineScope.() -> Unit) = viewModelScope.launch {
        try { block() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { message(e.message ?: "Something went wrong") }
    }
}
