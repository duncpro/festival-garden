import kotlinx.browser.window
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job

typealias EasingFunction = (Double) -> Double

@JsModule("@tweenjs/tween.js")
external object TweenModule {
    object Easing {
        object Linear {
            val None: EasingFunction
        }
        object Quadratic {
            val Out: EasingFunction
            val InOut: EasingFunction
        }
    }
    class Tween<T>(initial: T) {
        fun to(final: T, duration: Int): Tween<T>
        fun easing(easingFunction: EasingFunction): Tween<T>
        fun onUpdate(eventHandler: (T) -> Unit): Tween<T>
        fun start(): Tween<T>
        fun stop(): Tween<T>
        fun onComplete(eventHandler: () -> Unit): Tween<T>
        fun onStop(eventHandler: () -> Unit): Tween<T>
    }

    fun update(time: Double)
}

fun <T> createTween(
    initial: T,
    final: T,
    duration: Int,
    easing: EasingFunction,
    onUpdate: (T) -> Unit
): TweenModule.Tween<T> = TweenModule.Tween<T>(initial)
    .to(final, duration)
    .easing(easing)
    .onUpdate { newPosition -> onUpdate(newPosition) }

fun <T> TweenModule.Tween<T>.startAsync(): Job {
    val completable = CompletableDeferred<Unit>()
    this.onStop { completable.complete(Unit) }
        .onComplete { completable.complete(Unit) }
    this.start()
    return completable
}

fun animateTweens(time: Double) {
    TweenModule.update(time)
    window.requestAnimationFrame { newTime -> animateTweens(newTime) }
}
fun startTweenAnimationLoop() {
    window.requestAnimationFrame { time -> animateTweens(time) }
}

data class Vector1(val n: Double)

data class Vector2(val x: Double, val y: Double)