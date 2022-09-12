import Three.PerspectiveCamera
import Three.Scene
import csstype.PointerEvents
import csstype.Position
import csstype.pct
import emotion.react.css
import kotlinx.browser.document
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.dom.clear
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.ResizeObserver
import org.w3c.dom.ResizeObserverCallback
import org.w3c.dom.TagName
import org.w3c.dom.createElement
import react.FC
import react.MutableRefObject
import react.Props
import react.ReactNode
import react.ReactPortal
import react.RefObject
import react.dom.createPortal
import react.dom.html.ReactHTML.canvas
import react.dom.html.ReactHTML.div
import react.useContext
import react.useState
import react.useRef
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import react.useEffect
import react.useRefCallback
import kotlin.math.max

data class MarkerIdentity(
    val longitude: Double,
    val latitude: Double,
    val uniqueMarkerId: String
)

external interface GlobeProps: Props {
    var markers: Set<MarkerIdentity>?
    var markerComponentFactory: ((MarkerIdentity, RefObject<GlobeDOMBundle>) -> ReactNode)?
    var markerFocusRef: MutableRefObject<(List<MarkerIdentity>) -> Unit>?
    var animateToPointRef: MutableRefObject<(GlobeTargetPoint) -> Job>?
    var autoRotate: Boolean
    var initialZoom: Double?
    var enableZoom: Boolean?
}

const val GLOBE_RADIUS = 100
const val GLOBE_SEGMENT_COUNT = 75
const val DEFAULT_CAMERA_DISTANCE: Double = GLOBE_RADIUS * 5.0


private data class MarkerRef(val domElement: HTMLDivElement, val obj: CSS2DRenderModule.CSS2DObject)

fun projectGeographicPosition(longitude: Double, latitude: Double, height: Double, point3d: Three.Vector3) {
    val phi = (90 - latitude) * PI / 180;
    val theta = (longitude + 180) * PI / 180;

    point3d.x = -1 * height * sin(phi) * cos(theta)
    point3d.z = height * sin(phi) * sin(theta)
    point3d.y = height * cos(phi)
}

data class GlobeDOMBundle(
    val containerDiv: HTMLDivElement? = null,
    val overlayDiv: HTMLDivElement? = null,
    val canvas: HTMLCanvasElement? = null
)

data class GlobeTargetPoint(val longitude: Double, val latitude: Double)

data class GlobeRenderBundle(
    val scene: Scene,
    val camera: PerspectiveCamera,
    val orbitControls: OrbitControlsModule.OrbitControls,
    val renderer3d: Three.WebGLRenderer,
    val renderer2d: CSS2DRenderModule.CSS2DRenderer,
    val globeMesh: Three.Mesh<Three.SphereBufferGeometry, Three.MeshBasicMaterial>
)

val Globe = FC<GlobeProps> { props ->
    var currentMarkerRefs: MutableMap<MarkerIdentity, MarkerRef> by useState(::HashMap)
    val globeRenderBundleRef = useRef<GlobeRenderBundle>()
    val resizeObserverRef = useRef<ResizeObserver>(null)

    val prevFocusedRef = useRef<List<MarkerIdentity>>()
    useEffect(props.markerFocusRef, currentMarkerRefs) {
        val focuser: (List<MarkerIdentity>) -> Unit = { nowFocused ->
            prevFocusedRef.current?.let { prevFocusedMarkers ->
                prevFocusedMarkers.forEach { prevFocusedMarker ->
                    currentMarkerRefs[prevFocusedMarker]?.obj?.renderOrder = 0
                }
            }
            prevFocusedRef.current = nowFocused
            for ((i, marker) in nowFocused.withIndex()) {
                // List is sorted from least focused to most focused.
                currentMarkerRefs[marker]?.obj?.let { markerObj ->
                    markerObj.renderOrder = currentMarkerRefs.size + i
                    console.log("Updated Render Order!")
                }
            }
        }

        props.markerFocusRef?.current = focuser
    }

    val handleDOMChange: (GlobeDOMBundle?, GlobeDOMBundle?) -> Unit = { oldDOMBundle, newDOMBundle ->
        // Automatically resize the canvas element anytime the container div changes size or shape
        resizeObserverRef.current?.disconnect()
        resizeObserverRef.current = null
        if (newDOMBundle?.containerDiv != null && newDOMBundle.canvas != null) {
            val onResize: ResizeObserverCallback = cb@{ _, _ ->
                newDOMBundle.canvas.height = newDOMBundle.containerDiv.getBoundingClientRect().height.roundToInt()
                newDOMBundle.canvas.width = newDOMBundle.containerDiv.getBoundingClientRect().width.roundToInt()
            }
            val newResizeObserver = ResizeObserver(onResize)
            newResizeObserver.observe(newDOMBundle.containerDiv)
            resizeObserverRef.current = newResizeObserver
        }

        // Teardown the old three.js context associated with the previous DOM bundle.
        val old3DRenderBundle = globeRenderBundleRef.current
        if (old3DRenderBundle != null) {
            console.log("Tearing down stale three.js rendering context")
            old3DRenderBundle.apply {
                orbitControls.dispose()
                globeMesh.geometry.dispose()
                globeMesh.material.map.dispose()
                globeMesh.material.dispose()
                scene.clear()
                renderer3d.dispose()
                oldDOMBundle?.overlayDiv?.clear()
            }
            globeRenderBundleRef.current = null
        }

        // Create a new three.js context which is bound to the new DOM bundle.
        if (newDOMBundle?.canvas != null && newDOMBundle.overlayDiv != null) {
            val scene = Scene()
            val camera = PerspectiveCamera()
            val globeMesh = Three.Mesh<Three.SphereBufferGeometry, Three.MeshBasicMaterial>(
                geometry = Three.SphereBufferGeometry(GLOBE_RADIUS, GLOBE_SEGMENT_COUNT, GLOBE_SEGMENT_COUNT),
                material = Three.MeshBasicMaterial().apply {
                    map = Three.TextureLoader().load("earth-blue-marble.jpg")
                }
            )

            scene.add(globeMesh)

            val orbitControls = OrbitControlsModule.OrbitControls(camera, newDOMBundle.canvas)
            orbitControls.autoRotate = props.autoRotate
            orbitControls.autoRotateSpeed = 1.0
            orbitControls.enableDamping = true
            orbitControls.enablePan = false
            orbitControls.minDistance = 147.0
            orbitControls.maxDistance = 1724.0
            orbitControls.enableZoom = props.enableZoom != false
            orbitControls.addEventListener("change") {
                updateMarkerVisibility(camera, globeMesh)
                orbitControls.rotateSpeed =  orbitControls.getDistance() / 1000
            }

            val renderer2d = CSS2DRenderModule.CSS2DRenderer(object : CSS2DRendererProps {
                override var element: HTMLElement = newDOMBundle.overlayDiv
            })
            val renderer3d = Three.WebGLRenderer(object : WebGLRendererProps {
                override var canvas: HTMLCanvasElement = newDOMBundle.canvas
                override var alpha = true
            })

            camera.position.z = DEFAULT_CAMERA_DISTANCE + (-1 * (props.initialZoom ?: 0.0))

            var prevRatio: Double? = null
            renderer3d.setAnimationLoop {
                orbitControls.update()
                val currentRatio: Double = newDOMBundle.canvas.width.toDouble() / newDOMBundle.canvas.height
                if (prevRatio != currentRatio) {
                    camera.aspect = currentRatio
                    prevRatio = currentRatio
                    camera.updateProjectionMatrix()
                }
                renderer2d.setSize(newDOMBundle.canvas.width, newDOMBundle.canvas.height)
                renderer3d.setSize(newDOMBundle.canvas.width, newDOMBundle.canvas.height)

                renderer2d.render(scene, camera)
                renderer3d.render(scene, camera)
            }


            globeRenderBundleRef.current =
                GlobeRenderBundle(scene, camera, orbitControls, renderer3d, renderer2d, globeMesh)

            currentMarkerRefs = HashMap()

            console.log("React has re-initialized some portion of the DOM which is managed by three.js." +
                    " Tearing down old three.js context and creating a new one.")
        }
    }

    useEffect(props.animateToPointRef) {
        props.animateToPointRef?.current = animator@{ target: GlobeTargetPoint ->
            val camera = globeRenderBundleRef.current?.camera

            if (camera == null) return@animator CompletableDeferred(Unit)

            val updateCameraPositionFunction: (Three.Vector3) -> Unit = { newPosition ->
                globeRenderBundleRef.current?.camera?.position?.replaceAll(from = newPosition)
                globeRenderBundleRef.current?.orbitControls?.update()
            }

            val tween = createTween<Three.Vector3>(
                initial = Three.Vector3().apply { this.replaceAll(from = camera.position) },
                final = Three.Vector3().apply {
                    projectGeographicPosition(target.longitude, target.latitude, GLOBE_RADIUS * 2.0, this)
                },
                duration = 1000,
                onUpdate = updateCameraPositionFunction,
                easing = TweenModule.Easing.Quadratic.Out
            )

            return@animator tween.startAsync()
        }
    }

    useEffect(props.autoRotate) {
        globeRenderBundleRef.current?.orbitControls?.autoRotate = props.autoRotate
    }

    // HTML Elements
    val domBundleRef = useRef<GlobeDOMBundle>()
    val containerRef = useRefCallback<HTMLDivElement>() { newContainerElement ->
        val oldDOMBundle = (domBundleRef.current ?: GlobeDOMBundle())
        val newDOMBundle = oldDOMBundle.copy(containerDiv = newContainerElement)
        domBundleRef.current = newDOMBundle
        handleDOMChange(oldDOMBundle, newDOMBundle)
    }
    val overlayDivRef = useRefCallback<HTMLDivElement>() { newOverlayElement ->
        val oldDOMBundle = (domBundleRef.current ?: GlobeDOMBundle())
        val newDOMBundle = oldDOMBundle.copy(overlayDiv = newOverlayElement)
        domBundleRef.current = newDOMBundle
        handleDOMChange(oldDOMBundle, newDOMBundle)
    }
    val canvasRef = useRefCallback<HTMLCanvasElement>() { newCanvasElement ->
        val oldDOMBundle = (domBundleRef.current ?: GlobeDOMBundle())
        val newDOMBundle = oldDOMBundle.copy(canvas = newCanvasElement)
        domBundleRef.current = newDOMBundle
        handleDOMChange(oldDOMBundle, newDOMBundle)
    }

    var markerPortals by useState<List<ReactPortal>>()

    useEffect(props.markers, currentMarkerRefs) {
        val markers = props.markers ?: emptySet()
        val globeObj = globeRenderBundleRef.deref().globeMesh
        val camera = globeRenderBundleRef.deref().camera
        val delta = diff(currentMarkerRefs.keys, markers)
        for (subtractedKey in delta.subtracted) {
            val markerRef = currentMarkerRefs.remove(subtractedKey) ?: continue
            markerRef.domElement.remove()
            globeObj.remove(markerRef.obj)
            console.log("Removed globe marker DOM element")
        }
        for (addedKey in delta.added) {
            val domElement = document.createElement(TagName("div")) as HTMLDivElement
            val obj = CSS2DRenderModule.CSS2DObject(domElement)
            projectGeographicPosition(addedKey.longitude, addedKey.latitude, GLOBE_RADIUS + 0.05, obj.position)
            currentMarkerRefs[addedKey] = MarkerRef(domElement, obj)
            globeObj.add(obj)
            console.log("Inserted new DOM element for globe marker")
        }

        updateMarkerVisibility(camera, globeObj)

        markerPortals = currentMarkerRefs
            .map { (position, ref) -> createPortal(props.markerComponentFactory?.invoke(position, domBundleRef), ref.domElement) }
    }


    div {
        ref = containerRef
        css {
            height = 100.pct
            width = 100.pct
        }

        canvas {
            ref = canvasRef
            css {
                position = Position.absolute
            }
        }

        div {
            ref = overlayDivRef
            css {
                @Suppress("CAST_NEVER_SUCCEEDS", "UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
                pointerEvents = "none" as PointerEvents
                height = 100.pct
                width = 100.pct
                position = Position.absolute
            }
        }
    }

    markerPortals?.forEach { +it }
}