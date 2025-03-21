package com.nextlevelprogrammers.surakshakawach.utils

object SOSActivationManager {

    private var sosCallback: (() -> Unit)? = null

    fun setSOSCallback(callback: () -> Unit) {
        sosCallback = callback
    }

    fun activateSOS() {
        sosCallback?.invoke()
    }

    fun clearCallback() {
        sosCallback = null
    }
}