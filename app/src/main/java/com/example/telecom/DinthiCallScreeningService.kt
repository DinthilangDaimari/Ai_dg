package com.example.telecom

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log

/**
 * Standard Android CallScreeningService for Dinthi AI.
 *
 * Intercepts incoming call intents using the legitimate Android Telecom framework.
 * Allows Dinthi AI to check incoming numbers against spam indicators and user screening rules.
 */
class DinthiCallScreeningService : CallScreeningService() {

    companion object {
        private const val TAG = "DinthiCallScreening"

        // Observable callback for active UI screening session
        var onCallInterceptedListener: ((callerNumber: String, isIncoming: Boolean) -> Unit)? = null
    }

    override fun onScreenCall(callDetails: Call.Details) {
        val number = callDetails.handle?.schemeSpecificPart ?: "Unknown"
        val isIncoming = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            callDetails.callDirection == Call.Details.DIRECTION_INCOMING
        } else {
            true
        }

        Log.d(TAG, "Incoming call screened: $number (incoming: $isIncoming)")

        // Notify active screening engine / UI
        onCallInterceptedListener?.invoke(number, isIncoming)

        // Standard response: Allow call to proceed while Dinthi AI initiates screening
        val response = CallResponse.Builder()
            .setDisallowCall(false)
            .setRejectCall(false)
            .setSilenceCall(false)
            .setSkipCallLog(false)
            .setSkipNotification(false)
            .build()

        respondToCall(callDetails, response)
    }
}
