import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import react.FC
import react.PropsWithChildren
import react.useState

val AssetPreloadGuard = FC<PropsWithChildren> { props ->
    var state by useState<GuardViewState>(GuardViewState.Loading)

    useAsyncEffectOnce {
        state = GuardViewState.Loading
        console.log("Loading Assets!")
        try {
            coroutineScope {
                awaitAll(
                    loadImageAsync("background.jpg"),
                    loadImageAsync("earth-blue-marble.jpg"),
                )
            }
            state = GuardViewState.Pass
        } catch (e: Throwable) {
            state = GuardViewState.Failed(e)
            console.error(e)
        }
    }

    Guard {
        this.state = state
        +props.children
    }
}