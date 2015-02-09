package com.vaadin.prototype.wc.gwt.client.widgets;

import com.google.gwt.core.client.js.JsExport;
import com.google.gwt.i18n.client.NumberFormat;

/**
 * All methods here will be exported as window.vaadin.Utils, example:
 * window.vaadin.Utils.formatCurrency(new Date().getTime())
 */
@JsExport()
public class Utils {
    @JsExport
    public static String formatCurrency(double val) {
        NumberFormat fmt = NumberFormat.getCurrencyFormat();
        return fmt.format(val);
    }
}
