package com.vaadin.client.widgets.escalator;

import com.google.gwt.dom.client.Element;

import java.util.HashMap;
import java.util.Map;

class ElementPositionBookkeeper {
    /**
     * 
     */
    private final Escalator escalator;

    /**
     * @param escalator
     */
    ElementPositionBookkeeper(Escalator escalator) {
        this.escalator = escalator;
    }

    /**
     * A map containing cached values of an element's current top position.
     */
    private final Map<Element, Double> elementTopPositionMap = new HashMap<Element, Double>();
    private final Map<Element, Double> elementLeftPositionMap = new HashMap<Element, Double>();

    public void set(final Element e, final double x, final double y) {
        assert e != null : "Element was null";
        this.escalator.position.set(e, x, y);
        elementTopPositionMap.put(e, Double.valueOf(y));
        elementLeftPositionMap.put(e, Double.valueOf(x));
    }

    public double getTop(final Element e) {
        Double top = elementTopPositionMap.get(e);
        if (top == null) {
            throw new IllegalArgumentException("Element " + e
                    + " was not found in the position bookkeeping");
        }
        return top.doubleValue();
    }

    public double getLeft(final Element e) {
        Double left = elementLeftPositionMap.get(e);
        if (left == null) {
            throw new IllegalArgumentException("Element " + e
                    + " was not found in the position bookkeeping");
        }
        return left.doubleValue();
    }

    public void remove(Element e) {
        elementTopPositionMap.remove(e);
        elementLeftPositionMap.remove(e);
    }
}