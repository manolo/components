package com.vaadin.client.widgets.escalator;

import jsinterop.annotations.JsPackage;
import jsinterop.annotations.JsType;

@JsType(isNative = true, name = "HTMLElement", namespace = JsPackage.GLOBAL)
public class JsniElement {
    public native void addEventListener(String name, JsniHandler handler);
    public native void removeEventListener(String name, JsniHandler handler);
    public String tagName;
    public native boolean contains(Object e);
}
