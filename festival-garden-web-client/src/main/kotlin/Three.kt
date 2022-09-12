import Three.Scene
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLElement

external interface WebGLRendererProps {
    @JsName("canvas")
    var canvas: HTMLCanvasElement
    var alpha: Boolean
}

@JsModule("three")
external object Three {
    open class Object3D {
        fun add(obj: Object3D)
        fun clear()
        fun remove(child: Object3D)
        @JsName("getWorldPosition")
        fun copyWorldPositionTo(vector3: Vector3)
        var position: Vector3
        var children: Array<Object3D>
        var name: String?
        var visible: Boolean
        var renderOrder: Int
    }

    open class Scene: Object3D

    open class Vector3(x: Double, y: Double, z: Double) {
        constructor()

        var x: Double
        var y: Double
        var z: Double

        @JsName("copy")
        fun replaceAll(from: Vector3)

        fun distanceTo(destination: Vector3): Double
    }

    open class Camera: Object3D

    class PerspectiveCamera: Camera {
        var aspect: Double
        fun updateProjectionMatrix()
    }

    open class Loader

    class Texture {
        fun dispose()
    }

    class TextureLoader: Loader {
        fun load(url: String): Texture
    }

    interface Renderer {
        fun setSize(width: Int, height: Int)
        fun render(scene: Object3D, camera: Camera)
    }

    class WebGLRenderer(props: WebGLRendererProps) : Renderer {
        fun dispose()
        override fun setSize(width: Int, height: Int)
        override fun render(scene: Object3D, camera: Camera)
        fun setAnimationLoop(loop: () -> Unit)
    }

    open class BufferGeometry {
        fun dispose()
    }

    class SphereBufferGeometry(radius: Int, widthSegments: Int, heightSegments: Int): BufferGeometry

    open class Material {
        fun dispose()
    }

    class MeshBasicMaterial: Material {
        var map: Texture
    }

    class Mesh<G: BufferGeometry, M: Material>(geometry: G, material: Material): Object3D {
        var geometry: G
        var material: M
    }


    class Sphere {
        fun set(center: Vector3, radius: Int)
    }

    class Ray {
        var origin: Vector3
        fun lookAt(objectPosition: Vector3)
        fun intersectSphere(sphere: Sphere, target: Vector3): Vector3?
    }
}

@JsModule("three/examples/jsm/controls/OrbitControls.js")
external object OrbitControlsModule {
    class OrbitControls(camera: Three.Camera, domElement: HTMLElement) {
        fun dispose()
        fun update()
        var rotateSpeed: Double
        var panSpeed: Double
        var autoRotate: Boolean
        var autoRotateSpeed: Double
        var enablePan: Boolean
        var enableDamping: Boolean
        var enableZoom: Boolean
        fun addEventListener(type: String, listener: () -> Unit)
        fun getDistance(): Double
        var minDistance: Double
        var maxDistance: Double
        var zoomSpeed: Double
    }
}

external interface CSS2DRendererProps {
    var element: HTMLElement
}

@JsModule("three/examples/jsm/renderers/CSS2DRenderer.js")
external object CSS2DRenderModule {
    class CSS2DRenderer(props: CSS2DRendererProps) {
        fun render(scene: Scene, camera: Three.Camera)
        fun setSize(width: Int, height: Int)
    }

    class CSS2DObject(element: HTMLElement) : Three.Object3D
}