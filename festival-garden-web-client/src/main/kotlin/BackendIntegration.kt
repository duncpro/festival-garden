import com.duncpro.festivalgarden.interchange.LoginResponseBody
import csstype.HtmlAttributes
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestPipeline
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.HttpReceivePipeline
import io.ktor.http.HttpStatusCode
import io.ktor.http.appendPathSegments
import io.ktor.http.isSuccess
import kotlinx.browser.localStorage
import kotlinx.browser.window
import react.FC
import react.MutableRefObject
import react.PropsWithChildren
import react.Ref
import react.RefObject
import react.createContext
import react.dom.aria.AriaRole
import react.useContext
import react.useEffect
import react.useEffectOnce
import react.useMemo
import react.useRef
import react.useState
import io.ktor.serialization.kotlinx.json.json as installJsonContentNegotiation

val FG_BACKEND_URL: String = js("bundle.deployment.config.backendUrl").toString()

interface AuthorizationContextValue {
    /**
     * Invoked by the Festival Garden HTTP API client anytime an authorization failure occurs.
     * After invocation the nearest ancestor AuthGuard is presented and re-authorization with Spotify
     * is immediately attempted.
     */
    fun invalidate()
}

val SpotifyAuthorizationContext = createContext<AuthorizationContextValue>(object : AuthorizationContextValue {
    override fun invalidate() {} // If no AuthGuard is present, then invalidation means nothing.
})

fun getCurrentAuthenticationToken() = localStorage.getItem("authToken")

suspend fun login(httpClient: HttpClient): LoginResponseBody {
    val response = httpClient.get { url.appendPathSegments("login") }
    if (!response.status.isSuccess()) throw Exception("Unexpected response while authenticating: ${response.status}")
    val body = response.body<LoginResponseBody>()
    window.localStorage.setItem("authToken", body.festivalGardenAuthToken)
    return body
}

fun useFestivalGardenAPIClient(): RefObject<HttpClient> {
    val ref = useRef<HttpClient>(null)
    val authContext = useContext(SpotifyAuthorizationContext)

    useEffectOnce {
        val httpClient = HttpClient(Js) {
            defaultRequest { url(FG_BACKEND_URL) }
            install(ContentNegotiation) {
                installJsonContentNegotiation()
            }
            install(HttpRequestRetry) {
                retryIf { _, httpResponse ->  httpResponse.status.value == 503  }
                delayMillis { response?.headers?.get("Retry-After")?.toLong() ?: 2000 }
            }
        }
        httpClient.receivePipeline.intercept(HttpReceivePipeline.Before) { response ->
            // If there is an ancestor AuthGuard in the component tree, clear it.
            if (response.status == HttpStatusCode.Unauthorized) authContext.invalidate()
        }
        httpClient.requestPipeline.intercept(HttpRequestPipeline.Before) {
            getCurrentAuthenticationToken()?.let { context.headers.append("Authorization", "Bearer $it") }
        }
        ref.current = httpClient
        cleanup { httpClient.close() }
    }

    return ref
}

enum class RequestState {
    PENDING,
    SUCCESS,
    FAILED
}

inline fun <reified T> useGetEndpoint(path: String): Triple<RequestState, T?, () -> Unit> {
    val httpClientRef = useFestivalGardenAPIClient()
    var status by useState<RequestState>(RequestState.PENDING)
    var responseBody by useState<T?>(null)
    val (version, setVersion) = useState<Int>(0)
    val prevPathRef = useRef<String>()

    useAsyncEffect(path, version) {
        val httpClient = httpClientRef.current ?: return@useAsyncEffect
        if (prevPathRef.current != path) {
            responseBody = null
            prevPathRef.current = path
            status = RequestState.PENDING
        }
        val response = httpClient.get { url.appendPathSegments(path.split("/")) }
        if (!response.status.isSuccess()) {
            status = RequestState.FAILED
            return@useAsyncEffect
        }
        status = RequestState.SUCCESS
        responseBody = response.body<T>()
    }

    return Triple(status, responseBody) {
        setVersion { it + 1 }
    }
}

val SpotifyAuthorizationGuard = FC<PropsWithChildren> { props ->
    val httpClientRef = useFestivalGardenAPIClient()
    var guardState by useState<GuardViewState>(GuardViewState.Loading)
    var confirmedAuthToken by useState<String?>(null)

    useAsyncEffect(confirmedAuthToken) {
        if (confirmedAuthToken != null) return@useAsyncEffect
        guardState = GuardViewState.Loading
        // If not present, component must have been unmounted, which would clear the HttpClient ref.
        val httpClient = httpClientRef.current ?: return@useAsyncEffect
        try {
            val (authenticationToken, spotifyAuthorizationRedirect) = login(httpClient)
            if (spotifyAuthorizationRedirect == null /* isAuthorizedWithSpotify */) {
                confirmedAuthToken = authenticationToken
                guardState = GuardViewState.Pass
            } else {
                window.location.assign(spotifyAuthorizationRedirect)
            }

        } catch (e: Throwable) {
            guardState = GuardViewState.Failed(e)
            throw e
        }
    }

    val authContextValue = useMemo {
        object : AuthorizationContextValue {
            override fun invalidate() {
                confirmedAuthToken = null
            }
        }
    }

    Guard {
        this.state = guardState
        SpotifyAuthorizationContext.Provider {
            value = authContextValue
            +props.children
        }
    }
}