/*
 * VaVsta Messenger (форк Element X).
 */

package io.element.android.features.login.impl.screens.createaccount

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.libraries.architecture.NodeInputs
import io.element.android.libraries.architecture.inputs

@ContributesNode(AppScope::class)
@AssistedInject
class CreateAccountNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    presenterFactory: CreateAccountPresenter.Factory,
) : Node(buildContext, plugins = plugins) {
    data class Inputs(
        val homeserverUrl: String,
    ) : NodeInputs

    private val inputs: Inputs = inputs()
    private val presenter = presenterFactory.create(
        CreateAccountPresenter.Params(
            homeserverUrl = inputs.homeserverUrl,
        )
    )

    @Composable
    override fun View(modifier: Modifier) {
        val state = presenter.present()
        CreateAccountView(
            state = state,
            modifier = modifier,
            onBackClick = ::navigateUp,
        )
    }
}
