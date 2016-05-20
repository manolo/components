package com.vaadin.elements.grid;

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.EventTarget;
import com.google.gwt.dom.client.TableSectionElement;
import com.google.gwt.query.client.GQuery;
import com.google.gwt.user.client.Event;

import com.vaadin.client.BrowserInfo;
import com.vaadin.client.WidgetUtil;
import com.vaadin.client.widget.grid.selection.SelectionModelMulti;
import com.vaadin.client.widgets.Overlay;
import com.vaadin.client.widgets.grid.Grid;

public class ViolatedGrid extends Grid<Object> {

    public static class ViolatedOverlay extends Overlay {
        private Element partnerElement;

        @Override
        public void addAutoHidePartner(Element partner) {
            partnerElement = partner;
        }

        @Override
        public void setPopupPositionAndShow(PositionCallback callback) {
            this.getElement().getStyle().setOpacity(0);
            show();
            adjustPosition(this.getElement(), partnerElement);
            this.getElement().getStyle().setOpacity(1);
        }

        private native void adjustPosition(Element p, Element r) /*-{
         var overlayRect = p.getBoundingClientRect();
         var partnerRect = r.getBoundingClientRect();

         p.style.left = (partnerRect.right - overlayRect.left - overlayRect.width) + 'px';
         p.style.top = (partnerRect.top - overlayRect.top + partnerRect.height) + 'px';
        }-*/;
    }

    public ViolatedGrid() {
        super();

        if (WidgetUtil.getNativeScrollbarSize() == 0) {
            // "invisible" scrollbars
            if (BrowserInfo.get().isSafari()) {
                // This hack is needed to fix scrollbars for OS X Safari
                // [https://github.com/vaadin/vaadin-grid/issues/28]
                //
                // Dynamic scrollbars refuse to work on the stacking root
                // directly so
                // the wrapper is needed for creating an additional stacking
                // context.
                GQuery scrollers = GQuery
                        .$(".vaadin-grid-scroller", this)
                        .css("position", "relative")
                        .wrap("<div style='position: absolute; z-index: 10' />");
                scrollers.filter(".vaadin-grid-scroller-vertical").parent()
                        .css("right", "0");
                scrollers.filter(".vaadin-grid-scroller-horizontal").parent()
                        .css("bottom", "0");
            } else if (BrowserInfo.get().isChrome()) {
                // Fix for [https://github.com/vaadin/vaadin-grid/issues/29]
                GQuery.$(".vaadin-grid-scroller", this).attr("invisible", "");
            }
        }
    }

    /**
     * The method is overridden for now to avoid IE related bugs and performance
     * issues.
     */
    @Override
    public void setHeightByRows(double bodyRows)
            throws IllegalArgumentException {
        // Header height
        double headerRowHeight = getEscalator().getHeader()
                .getDefaultRowHeight();
        int headerRows = isHeaderVisible() ? getHeaderRowCount() : 0;
        double headerHeight = headerRowHeight * headerRows;

        // Body height
        double bodyRowHeight = getEscalator().getBody().getDefaultRowHeight();
        double bodyHeight = bodyRowHeight * bodyRows;

        // Footer height
        double footerRowHeight = getEscalator().getFooter()
                .getDefaultRowHeight();
        int footerRows = isFooterVisible() ? getFooterRowCount() : 0;
        double footerHeight = footerRowHeight * footerRows;

        setHeight(headerHeight + bodyHeight + footerHeight + "px");
    }



    @Override
    public void onBrowserEvent(Event event) {

        // clicking on the select all checkbox moves focus away from the grid
        // causing the :focus transition effect to be reapplied. Forcing focus
        // on the grid will mitigate the issue.
        focusGridIfSelectAllClicked(event);

        Element targetElement = (Element)event.getEventTarget().cast();
        if (targetElement != WidgetUtil.getFocusedElement() || isElementOutsideStaticSection(targetElement)) {
            super.onBrowserEvent(event);
        }
    }

    private boolean isElementOutsideStaticSection(Element element) {
        TableSectionElement headerElement = getEscalator().getHeader().getElement();
        TableSectionElement footerElement = getEscalator().getFooter().getElement();

        return !headerElement.isOrHasChild(element) && !footerElement.isOrHasChild(element);
    }

    private void focusGridIfSelectAllClicked(Event event) {
        EventTarget target = event.getEventTarget();
        if (Element.is(target)) {
            Element targetElement = Element.as(target);

            // Currently targeting all gwt-checkboxes, might need refinement in
            // the future.
            if("label".equals(targetElement.getTagName())
                    && targetElement.getParentElement().hasClassName("gwt-CheckBox")) {
                getElement().focus();
            }
        }
    }

    @Override
    public void removeColumn(
            com.vaadin.client.widgets.grid.Column<?, Object> column) {
        int initialFrozenColumnCount = getFrozenColumnCount();
        int maxFrozenColumnCount = getColumnCount() - 1;
        if (getSelectionModel() instanceof SelectionModelMulti) {
            maxFrozenColumnCount--;
        }

        // Make sure that frozen column count does not exceed the total column
        // count. This is currently not checked by Grid.
        if (maxFrozenColumnCount < initialFrozenColumnCount) {
            setFrozenColumnCount(maxFrozenColumnCount);
        }

        try {
            super.removeColumn(column);
        } catch (Exception e) {
            // If column removal fails for any reason, we need to set the frozen
            // column count to the initial value.
            setFrozenColumnCount(initialFrozenColumnCount);
        }
    }
}
