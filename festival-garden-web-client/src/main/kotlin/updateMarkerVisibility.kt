private val ray = Three.Ray()
private val cameraPosition = Three.Vector3()
private val globeCenter = Three.Vector3()
private val globe = Three.Sphere()
private val markerPosition = Three.Vector3()
private val intersection = Three.Vector3()

fun updateMarkerVisibility(camera: Three.PerspectiveCamera, globeMesh: Three.Mesh<*, *>) {
    camera.copyWorldPositionTo(cameraPosition)
    globeMesh.copyWorldPositionTo(globeCenter)
    globe.set(globeCenter, GLOBE_RADIUS)
    ray.origin.replaceAll(from = cameraPosition)

    for (marker in globeMesh.children) {
        marker.copyWorldPositionTo(markerPosition)
        ray.lookAt(markerPosition)
        val didIntersect = ray.intersectSphere(globe, intersection) != null

        if (!didIntersect) {
            marker.visible = true
            continue
        }

        val distanceToIntersection = intersection.distanceTo(cameraPosition)
        val distanceToMarker = markerPosition.distanceTo(cameraPosition)
        val isMarkerBehindEarth = distanceToMarker > distanceToIntersection
        marker.visible = !isMarkerBehindEarth
    }
}