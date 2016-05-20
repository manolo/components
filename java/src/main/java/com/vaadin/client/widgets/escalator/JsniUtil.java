package com.vaadin.client.widgets.escalator;

import com.google.gwt.dom.client.Element;

/**
 * A utility class that contains utility methods that are usually called
 * from JSNI.
 * <p>
 * The methods are moved in this class to minimize the amount of JSNI code
 * as much as feasible.
 */
class JsniUtil {
    
    public static boolean eventOnBody(Escalator escalator, JsniEvent ev) {
        return ((JsniElement)(Object)escalator.bodyElem).contains(ev.target);
    }

    public static void moveScrollFromEvent(final Escalator escalator,
            final double deltaX, final double deltaY,
            final JsniEvent event) {

        // Prevent scrolling on Headers/Footers
        if (!eventOnBody(escalator, event)) {
             return;
        }

        boolean movex = !Double.isNaN(deltaX);
        boolean movey = !Double.isNaN(deltaY);
        if (movex || movey) {
            escalator.bodyElem.addClassName("scrolling");
            if (movex) {
                escalator.horizontalScrollbar.setScrollPosByDelta(deltaX);
            }
            if (movey) {
                escalator.verticalScrollbar.setScrollPosByDelta(deltaY);
            }
            escalator.body.domSorter.reschedule();

            final boolean warrantedYScroll = deltaY != 0
                    && escalator.verticalScrollbar.showsScrollHandle();
            final boolean warrantedXScroll = deltaX != 0
                    && escalator.horizontalScrollbar.showsScrollHandle();
            if (warrantedYScroll || warrantedXScroll) {
                event.preventDefault();
            }
        }
    }
    
    public static native  void setStylePrimaryName(Element elem, String style) /*-{
        @com.google.gwt.user.client.ui.UIObject::setStylePrimaryName(Lcom/google/gwt/dom/client/Element;Ljava/lang/String;)(elem, style);
    }-*/;
    
    public static native void debugger() /*-{
        debugger;
    }-*/;
    
}