/*
 * VaVsta Messenger (форк Element X).
 */

package io.element.android.libraries.mediaplayer.test

import io.element.android.libraries.audio.api.ProximityAudioRouter
import io.element.android.tests.testutils.lambda.lambdaError

class FakeProximityAudioRouter(
    private val startResult: () -> Unit = { lambdaError() },
    private val stopResult: () -> Unit = { lambdaError() },
) : ProximityAudioRouter {
    override fun start() {
        startResult()
    }

    override fun stop() {
        stopResult()
    }
}
