import com.duncpro.festivalgarden.interchange.InterchangeMusicFestival
import csstype.AnimationDirection
import csstype.AnimationIterationCount
import csstype.AnimationTimingFunction
import csstype.Color
import csstype.Length
import csstype.PropertiesBuilder
import csstype.dropShadow
import csstype.px
import csstype.s
import emotion.css.keyframes
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.request
import io.ktor.client.request.url
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.HttpMethod
import io.ktor.http.appendPathSegments
import io.ktor.http.isSuccess
import io.ktor.websocket.CloseReason
import kotlinx.browser.window
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield
import kotlinx.js.Object
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.Image
import org.w3c.dom.StorageEvent
import org.w3c.dom.events.Event
import org.w3c.dom.events.EventType
import org.w3c.dom.events.MOUSE_DOWN
import org.w3c.dom.events.MOUSE_MOVE
import org.w3c.dom.events.MOUSE_UP
import org.w3c.dom.events.MouseEvent
import org.w3c.dom.events.STORAGE
import org.w3c.dom.events.addEventHandler
import react.CSSProperties
import react.RefObject
import react.useState
import react.useEffect
import react.useEffectOnce
import kotlin.coroutines.coroutineContext
import react.useRef
import kotlin.js.Date
import kotlin.js.json
import react.RefCallback
import react.useRefCallback
import react.MutableRefObject
import react.StateInstance

fun <T: Any> RefObject<T>.deref() = this.current ?: throw IllegalStateException("Cannot dereference RefObject" +
        " since the value it references no longer exists")

data class SetDifference<T>(val subtracted: Set<T>, val added: Set<T>)

fun <K> diff(old: Set<K>, new: Set<K>): SetDifference<K> {
    val subtracted = HashSet<K>()
    val added = HashSet<K>()
    for (key in old union new) {
        if (old.contains(key) && !new.contains(key)) subtracted.add(key)
        if (!old.contains(key) && new.contains(key)) added.add(key)
    }
    return SetDifference(subtracted, added)
}

fun useCoroutineScope(): RefObject<CoroutineScope> {
    val ref = useRef<CoroutineScope>(null)
    useEffectOnce {
        val coroutineScope = CoroutineScope(SupervisorJob())
        ref.current = coroutineScope
        cleanup { coroutineScope.cancel() }
    }
    return ref
}

/**
 * Executes the given asynchronous effect exactly one time, when the component mounts.
 * If the component is unmounted before the task can complete, it is cancelled at unmount time.
 * Additionally, the task may be cancelled by invoking the cancellation function returned by this function.
 *
 * Since execution of the given task may continue momentarily after the component has been unmounted,
 * [RefObject.deref] should never be used. Null-safe operations on [RefObject.current] should be used instead.
 */
fun useAsyncEffectOnce(effect: suspend () -> Unit): () -> Unit {
    val coroutineScope = useCoroutineScope()
    val jobRef = useRef<Job>(null)
    useEffectOnce {
        val job = coroutineScope.deref().launch { effect() }
        jobRef.current = job
        cleanup { job.cancel() }
    }
    return { jobRef.current?.cancel() }
}

/**
 * Executes the given suspending effect anytime at least one of the given dependencies change.
 * If the effect has not completed when the dependencies are changed, the stale effect is cancelled,
 * and a new effect with the updated dependencies is launched. This hook can be used for safe data-fetching.
 * While the effect is asynchronous in general, it is in-fact synchronous with respect to itself.
 *
 * Unlike the traditional useEffect hook, this one does not provide a cleanup function.
 * Cleanup is handled implicitly using Kotlin Coroutines Structured Currency.
 */
fun useAsyncEffect(vararg dependencies: Any?, effect: suspend () -> Unit) {
    val coroutineScope = useCoroutineScope()

    val mutexRef = useRef<Mutex>()
    useEffectOnce { mutexRef.current = Mutex() }

    useEffect(*dependencies) {
        val job = coroutineScope.deref().launch {
            mutexRef.current!!.withLock {
                effect()
            }
        }
        cleanup { job.cancel() }
    }
}

fun useRepeatingTask(vararg dependencies: Any?, effect: suspend () -> Unit) {
    useAsyncEffect(*dependencies) {
        while (true) {
            yield()
            effect()
        }
    }
}

fun useLocalStorageState(key: String): String? {
    val (currentValue, setCurrentValue) = useState(window.localStorage.getItem(key))
    useEffectOnce {
        val onStorageEvent: (StorageEvent) -> Unit = { event ->
            if (event.key == key) {
                setCurrentValue(event.newValue)
            }
        }
        val unsubscribe = window.addEventHandler(StorageEvent.STORAGE, onStorageEvent)
        setCurrentValue(window.localStorage.getItem(key))
        cleanup { unsubscribe() }
    }
    return currentValue
}

enum class ActionState {
    UNAVAILABLE,
    PREFLIGHT,
    PENDING,
    SUCCESS,
    FAILED;
}

/**
 * This hook exposes some async function as a non-suspending launch function and status variable.
 */
fun useAsyncAction(task: suspend () -> Unit): Pair<() -> Unit, ActionState> {
    val coroutineScopeRef = useCoroutineScope()
    var state by useState(ActionState.UNAVAILABLE)
    val launcherRef = useRef<() -> Unit>()

    useEffectOnce {
        val coroutineScope = coroutineScopeRef.deref()

        launcherRef.current = {
            coroutineScope.launch {
                state = ActionState.PENDING
                try {
                    task()
                    state = ActionState.SUCCESS
                } catch (e: Throwable) {
                    state = ActionState.FAILED
                    throw e
                }
            }
        }

        state = ActionState.PREFLIGHT

        cleanup { state = ActionState.UNAVAILABLE }
    }

    return Pair({ launcherRef.current?.invoke() }, state)
}

fun useExclusiveAsyncAction(task: suspend () -> Unit): Pair<() -> Unit, ActionState> {
    val lockRef = useRef<Mutex>(null)
    useEffectOnce { lockRef.current = Mutex() }
    return useAsyncAction {
        val lock = lockRef.current ?: return@useAsyncAction
        val didLock = lock.tryLock()
        if (didLock) {
            try {
                task()
            } finally {
                lock.unlock()
            }
        }
    }
}

fun <A: Any?> useExclusiveAsyncFunction(task: suspend (A) -> Unit): Pair<(A) -> Unit, ActionState> {
    val launcherRef = useRef<(A) -> Unit>()
    var actionState by useState<ActionState>(ActionState.PREFLIGHT)
    val coroutineScope = useCoroutineScope()
    useEffectOnce {
        val lock = Mutex()
        launcherRef.current = { arg ->
            coroutineScope.deref().launch {
                val didLock = lock.tryLock()
                if (!didLock) return@launch
                actionState = ActionState.PENDING
                try {
                    task(arg)
                    actionState = ActionState.SUCCESS
                } catch (e: Throwable) {
                    actionState = ActionState.FAILED
                    throw e
                } finally {
                    lock.unlock()
                }
            }
        }
    }
    return Pair({ a -> launcherRef.current?.invoke(a) }, actionState)
}

enum class PollingState {
    POLLING,
    NOT_POLLING
}

data class PollingStatus(val state: PollingState, val successfulRequestCount: Int)

/**
 * Repeatedly invokes an HTTP Endpoint on a given interval.
 * This functions returns a triple where the first element is the latest successful response body
 * and the second element is the [ActionState] of the request for the current interval,
 * and the third element is a cancellation function which stops the poll for the remaining lifetime of the component.
 * When the component is re-mounted it will begin to poll again until cancelled.
 */
inline fun <reified T> usePoll(path: String, minIntervalMillis: Long): Triple<T?, PollingStatus, () -> Unit> {
    val httpClientRef = useFestivalGardenAPIClient()
    var latestResponseBody by useState<T?>(null)
    var pollingState by useState<PollingState>(PollingState.NOT_POLLING)
    val (successfulRequestCount, setSuccessfulRequestCount) = useState<Int>(0)

    val (makeRequest, nextRequestState) = useAsyncAction {
        val httpClient = httpClientRef.current ?: return@useAsyncAction
        val response = httpClient.request {
            method = HttpMethod.Get
            accept(Json)
            url.appendPathSegments(path.split("/"))
        }
        if (!response.status.isSuccess()) throw Exception("Request to $path failed with status: ${response.status}")
        latestResponseBody = response.body()
        setSuccessfulRequestCount { prev -> prev + 1 }
    }

    val cancel = useAsyncEffectOnce {
        pollingState = PollingState.POLLING
        coroutineContext.job.invokeOnCompletion { pollingState = PollingState.NOT_POLLING }
        while (coroutineContext.isActive) {
            makeRequest()
            delay(minIntervalMillis)
        }
    }

    return Triple(latestResponseBody, PollingStatus(pollingState, successfulRequestCount), cancel)
}


private data class ScrollMemoryRefValue<E: HTMLElement>(val element: E, val unsubscribe: () -> Unit)
fun <E: HTMLElement> useMemorizedScrollPosition(scrollStateRef: MutableRefObject<Double>?): RefCallback<E> {
    val cleanupRef = useRef<ScrollMemoryRefValue<E>>()
    val targetRef = useRefCallback<E> { newElement ->
        // Remove old event handlers
        cleanupRef.current?.let { cleanupValue ->
            cleanupValue.unsubscribe()
            cleanupRef.current = null
        }

        if (scrollStateRef == null) return@useRefCallback
        if (newElement == null) return@useRefCallback

        // Recover scroll state
        val recoveredScrollTop = scrollStateRef.current
        if (recoveredScrollTop != null) newElement.scrollTop = recoveredScrollTop.toDouble()

        // Update scroll state ref and cleanup event handlers
        cleanupRef.current = ScrollMemoryRefValue(
            element = newElement,
            unsubscribe = newElement.addEventHandler(EventType("scroll")) { _ ->
                scrollStateRef.current = newElement.scrollTop
            }
        )
    }

    return targetRef
}

val InterchangeMusicFestival.prettyStartDate: String get() {
    val localeOptions = json(
        Pair("weekday", "long"),
        Pair("year", "numeric"),
        Pair("month", "long"),
        Pair("day", "numeric")
    ).unsafeCast<Date.LocaleOptions>()

    return Date(this.startDate).toLocaleDateString(emptyArray(), localeOptions)
}

fun <T: Any> MergeRefCallback(vararg receivers: RefCallback<T>): RefCallback<T> {
    return RefCallback<T> { refValue ->
        receivers.forEach { receiver -> receiver.unsafeCast<(T?) -> Unit>().invoke(refValue) }
    }
}

fun <T: Any> MutableRefObject<T>.asRefCallback(): RefCallback<T> =
    RefCallback { this.current = it }

fun <T> StateInstance<T>.get() = this.component1()
fun <T> StateInstance<T>.set(v: T) = this.component2().invoke(v)

enum class DisplayMode { NORMAL, SMALL }
fun useDisplayMode(): DisplayMode {
    fun computeDisplayMode(): DisplayMode =  if (window.innerWidth <= 650) DisplayMode.SMALL else DisplayMode.NORMAL
    var displayMode by useState<DisplayMode>(computeDisplayMode())

    useEffectOnce {
        val unregister = window.addEventHandler(EventType("resize")) {
            displayMode = computeDisplayMode()
        }
        cleanup { unregister() }
    }

    return displayMode
}

class ImageLoadFailedException(url: String): Exception("Image at $url could not be loaded")

fun loadImageAsync(src: String): Deferred<Image> {
    val image = Image()
    val completable = CompletableDeferred<Image>()
    image.onload = { completable.complete(image) }
    image.onerror = { _, _, _, _, _ -> completable.completeExceptionally(ImageLoadFailedException(src)) }
    image.src = src
    return completable
}

fun <T: Event, E: Element> useDOMEvent(eventType: EventType<T>, handler: (T) -> Unit): RefCallback<E> {
    val unregisterRef = useRef<() -> Unit>(null)
    return useRefCallback<E> { element ->
        unregisterRef.current?.invoke()
        unregisterRef.current = element?.addEventHandler(eventType) { event ->
            handler(event)
        }
    }
}

object Animations {
    fun glow(propertiesBuilder: PropertiesBuilder, color: Color? = null, intensity: Length = 15.px) {
        propertiesBuilder.apply {
            animationName = keyframes {
                from {
                    filter = dropShadow(0.px, 0.px, 0.px, color ?: Color.currentcolor)
                }
                to {
                    filter = dropShadow(0.px, 0.px, intensity, color ?: Color.currentcolor)
                }
            }
            animationTimingFunction = AnimationTimingFunction.linear
            animationDuration = 1.s
            animationIterationCount = AnimationIterationCount.infinite
            animationDirection = AnimationDirection.alternate
        }
    }
}