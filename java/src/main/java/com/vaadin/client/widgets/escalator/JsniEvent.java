package com.vaadin.client.widgets.escalator;

import jsinterop.annotations.JsPackage;
import jsinterop.annotations.JsType;


@JsType(isNative = true, name = "Event", namespace = JsPackage.GLOBAL)
public class JsniEvent {
    
    @JsType(isNative = true, name = "Touch", namespace = JsPackage.GLOBAL)
    public static class JsniTouch {
        public Double clientX;
        public Double clientY;
        public Double pageX;
        public Double pageY;
        public Double screenX;
        public Double screenY;
        public Double identifier;
        public JsniElement target;
        public native Double relativeX(JsniElement e);
    }
    
    public JsniElement target;
    public JsniElement currentTarget;
    public String type;
    public Double deltaX;
    public Double wheelDeltaX;
    public Double deltaY;
    public Double wheelDeltaY;
    public Double deltaMode;
    
    public JsniTouch[] touches;
    public native void preventDefault();
    public native boolean isCancelable();
}
