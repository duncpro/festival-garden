import kotlinx.browser.document
import org.w3c.dom.Element
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.TagName
import org.w3c.dom.createElement
import react.ReactNode
import react.ReactPortal
import react.RefCallback
import react.dom.createPortal
import react.useRef
import react.useRefCallback
import react.useState
import react.useEffect

@JsModule("@popperjs/core")
external object Popper {
    fun createPopper(element: Element, tooltip: Element): PopperInstance
}

external interface PopperInstance {
    fun destroy()
    fun update()
}

fun <E: Element> usePopper(tooltip: ReactNode?): Pair<RefCallback<E>, ReactPortal?>  {
    val popper = useRef<PopperInstance>()
    val tooltipElementRef = useRef<HTMLDivElement>()
    var tooltipPortal by useState<ReactPortal>()

    // Render if contents of the tooltip changes.
    useEffect(tooltip) {
        val tooltipDOMElement = tooltipElementRef.current
        if (tooltipDOMElement != null) {
            tooltipPortal = createPortal(tooltip, tooltipDOMElement)
        }
        popper.current?.update()
    }

    val targetElementRef = useRefCallback<E> { targetElement ->
        popper.current?.destroy()
        popper.current = null

        tooltipElementRef.current?.remove()
        tooltipElementRef.current = null

        // If React removes the HTMLElement containing the trigger then we
        // no longer display a popover, therefore we should re-render, without
        // the tooltip portal.
        if (targetElement == null) {
            tooltipPortal = null
        }

        if (targetElement != null) {
            val tooltipElement = document.createElement(TagName("div")) as HTMLDivElement
            targetElement.parentElement!!.append(tooltipElement)
            tooltipElementRef.current = tooltipElement
            val newPopper = Popper.createPopper(targetElement, tooltipElement)
            popper.current = newPopper
            // We must re-render when react assigns a new HTMLElement to the trigger element
            tooltipPortal = createPortal(tooltip, tooltipElement)
        }
    }
    return Pair(targetElementRef, tooltipPortal)
}