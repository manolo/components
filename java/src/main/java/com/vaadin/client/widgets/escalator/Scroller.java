package com.vaadin.client.widgets.escalator;

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style;
import com.google.gwt.dom.client.Style.Display;
import com.google.gwt.dom.client.Style.Unit;

import com.vaadin.client.WidgetUtil;
import com.vaadin.client.widget.escalator.PositionFunction.AbsolutePosition;
import com.vaadin.shared.ui.grid.Range;
import com.vaadin.shared.ui.grid.ScrollDestination;

/** An inner class that handles all logic related to scrolling. */
class Scroller {
    
    
    private final Escalator escalator;
    private double lastScrollTop = 0;
    double lastScrollLeft = 0;

    protected final JsniHandler scrollListenerFunction;
    protected final JsniHandler mousewheelListenerFunction;
    protected final JsniHandler touchStartFunction;
    protected final JsniHandler touchMoveFunction;
    protected final JsniHandler touchEndFunction;
    protected final TouchHandlerBundle touchHandlerBundle;

    public Scroller(Escalator escalator) {
        this.escalator = escalator;
        scrollListenerFunction = e -> {
            if (e.target == (Object)escalator.verticalScrollbar.getElement()) {
                escalator.verticalScrollbar.updateScrollPosFromDom();
            } else if (e.target == (Object)escalator.horizontalScrollbar.getElement()) {
                escalator.horizontalScrollbar.updateScrollPosFromDom();
            }
        };
        mousewheelListenerFunction = e -> {
            double deltaX = e.deltaX != 0 ? e.deltaX : -0.5 * e.wheelDeltaX;
            double deltaY = e.deltaY != 0 ? e.deltaY : -0.5 * e.wheelDeltaY;
            if (e.deltaMode == 1) {
                deltaY *= escalator.body.getDefaultRowHeight();
            }
            JsniUtil.moveScrollFromEvent(escalator, deltaX, deltaY, e);
        };
        
        touchHandlerBundle = new TouchHandlerBundle(escalator);
        
        touchStartFunction = e -> {
            touchHandlerBundle.touchStart(e);
        };
        touchMoveFunction = e -> {
            touchHandlerBundle.touchMove(e);
        };
        touchEndFunction = e -> {
            touchHandlerBundle.touchEnd(e);
        };
    }
    
    public void onLoad() {
        addListener(escalator.horizontalScrollbar.getElement(), "scroll", scrollListenerFunction);
        addListener(escalator.verticalScrollbar.getElement(), "scroll", scrollListenerFunction);
        addListener(escalator.getElement(), "mousewheel", mousewheelListenerFunction);
        addListener(escalator.getElement(), "touchstart", touchStartFunction);
        addListener(escalator.getElement(), "touchmove", touchMoveFunction);
        addListener(escalator.getElement(), "touchend", touchEndFunction);
        addListener(escalator.getElement(), "touchcancel", touchEndFunction);
    }
    
    public void onUnload() {
        removeListener(escalator.verticalScrollbar.getElement(), "scroll", scrollListenerFunction);
        removeListener(escalator.horizontalScrollbar.getElement(), "scroll", scrollListenerFunction);
        removeListener(escalator.getElement(), "mousewheel", mousewheelListenerFunction);
        removeListener(escalator.getElement(), "touchstart", touchStartFunction);
        removeListener(escalator.getElement(), "touchmove", touchMoveFunction);
        removeListener(escalator.getElement(), "touchend", touchEndFunction);
        removeListener(escalator.getElement(), "touchcancel", touchEndFunction);
    }
    
    private void addListener(Element e, String name, Object handler) {
        ((JsniElement)(Object)e).addEventListener(name, (JsniHandler)handler);
    }
    
    private void removeListener(Element e, String name, Object handler) {
        ((JsniElement)(Object)e).removeEventListener(name, (JsniHandler)handler);
    }

    
    /**
     * Recalculates the virtual viewport represented by the scrollbars, so
     * that the sizes of the scroll handles appear correct in the browser
     */
    public void recalculateScrollbarsForVirtualViewport() {
        double scrollContentHeight = this.escalator.body.calculateTotalRowHeight()
                + this.escalator.body.spacerContainer.getSpacerHeightsSum();
        double scrollContentWidth = this.escalator.columnConfiguration.calculateRowWidth();
        double tableWrapperHeight = this.escalator.heightOfEscalator;
        double tableWrapperWidth = this.escalator.widthOfEscalator;

        boolean verticalScrollNeeded = scrollContentHeight > tableWrapperHeight
                + WidgetUtil.PIXEL_EPSILON
                - this.escalator.header.getHeightOfSection()
                - this.escalator.footer.getHeightOfSection();
        boolean horizontalScrollNeeded = scrollContentWidth > tableWrapperWidth
                + WidgetUtil.PIXEL_EPSILON;

        // One dimension got scrollbars, but not the other. Recheck time!
        if (verticalScrollNeeded != horizontalScrollNeeded) {
            if (!verticalScrollNeeded && horizontalScrollNeeded) {
                verticalScrollNeeded = scrollContentHeight > tableWrapperHeight
                        + WidgetUtil.PIXEL_EPSILON
                        - this.escalator.header.getHeightOfSection()
                        - this.escalator.footer.getHeightOfSection()
                        - this.escalator.horizontalScrollbar.getScrollbarThickness();
            } else {
                horizontalScrollNeeded = scrollContentWidth > tableWrapperWidth
                        + WidgetUtil.PIXEL_EPSILON
                        - this.escalator.verticalScrollbar.getScrollbarThickness();
            }
        }

        // let's fix the table wrapper size, since it's now stable.
        if (verticalScrollNeeded) {
            tableWrapperWidth -= this.escalator.verticalScrollbar.getScrollbarThickness();
            tableWrapperWidth = Math.max(0, tableWrapperWidth);
        }
        if (horizontalScrollNeeded) {
            tableWrapperHeight -= this.escalator.horizontalScrollbar
                    .getScrollbarThickness();
            tableWrapperHeight = Math.max(0, tableWrapperHeight);
        }
        this.escalator.tableWrapper.getStyle().setHeight(tableWrapperHeight, Unit.PX);
        this.escalator.tableWrapper.getStyle().setWidth(tableWrapperWidth, Unit.PX);

        double footerHeight = this.escalator.footer.getHeightOfSection();
        double headerHeight = this.escalator.header.getHeightOfSection();
        double vScrollbarHeight = Math.max(0, tableWrapperHeight
                - footerHeight - headerHeight);
        this.escalator.verticalScrollbar.setOffsetSize(vScrollbarHeight);
        this.escalator.verticalScrollbar.setScrollSize(scrollContentHeight);

        /*
         * If decreasing the amount of frozen columns, and scrolled to the
         * right, the scroll position might reset. So we need to remember
         * the scroll position, and re-apply it once the scrollbar size has
         * been adjusted.
         */
        double prevScrollPos = this.escalator.horizontalScrollbar.getScrollPos();

        double unfrozenPixels = this.escalator.columnConfiguration
                .getCalculatedColumnsWidth(Range.between(
                        this.escalator.columnConfiguration.getFrozenColumnCount(),
                        this.escalator.columnConfiguration.getColumnCount()));
        double frozenPixels = scrollContentWidth - unfrozenPixels;
        double hScrollOffsetWidth = tableWrapperWidth - frozenPixels;
        this.escalator.horizontalScrollbar.setOffsetSize(hScrollOffsetWidth);
        this.escalator.horizontalScrollbar.setScrollSize(unfrozenPixels);
        this.escalator.horizontalScrollbar.getElement().getStyle()
                .setLeft(frozenPixels, Unit.PX);
        this.escalator.horizontalScrollbar.setScrollPos(prevScrollPos);

        /*
         * only show the scrollbar wrapper if the scrollbar itself is
         * visible.
         */
        if (this.escalator.horizontalScrollbar.showsScrollHandle()) {
            this.escalator.horizontalScrollbarDeco.getStyle().clearDisplay();
        } else {
            this.escalator.horizontalScrollbarDeco.getStyle().setDisplay(Display.NONE);
        }

        /*
         * only show corner background divs if the vertical scrollbar is
         * visible.
         */
        Style hCornerStyle = this.escalator.headerDeco.getStyle();
        Style fCornerStyle = this.escalator.footerDeco.getStyle();
        if (this.escalator.verticalScrollbar.showsScrollHandle()) {
            hCornerStyle.clearDisplay();
            fCornerStyle.clearDisplay();

            if (this.escalator.horizontalScrollbar.showsScrollHandle()) {
                double offset = this.escalator.horizontalScrollbar.getScrollbarThickness();
                fCornerStyle.setBottom(offset, Unit.PX);
            } else {
                fCornerStyle.clearBottom();
            }
        } else {
            hCornerStyle.setDisplay(Display.NONE);
            fCornerStyle.setDisplay(Display.NONE);
        }
    }

    /**
     * Logical scrolling event handler for the entire widget.
     */
    public void onScroll() {

        final double scrollTop = this.escalator.verticalScrollbar.getScrollPos();
        final double scrollLeft = this.escalator.horizontalScrollbar.getScrollPos();
        if (lastScrollLeft != scrollLeft) {
            for (int i = 0; i < this.escalator.columnConfiguration.frozenColumns; i++) {
                this.escalator.header.updateFreezePosition(i, scrollLeft);
                this.escalator.body.updateFreezePosition(i, scrollLeft);
                this.escalator.footer.updateFreezePosition(i, scrollLeft);
            }

            this.escalator.position.set(this.escalator.headElem, -scrollLeft, 0);

            /*
             * TODO [[optimize]]: cache this value in case the instanceof
             * check has undesirable overhead. This could also be a
             * candidate for some deferred binding magic so that e.g.
             * AbsolutePosition is not even considered in permutations that
             * we know support something better. That would let the compiler
             * completely remove the entire condition since it knows that
             * the if will never be true.
             */
            if (this.escalator.position instanceof AbsolutePosition) {
                /*
                 * we don't want to put "top: 0" on the footer, since it'll
                 * render wrong, as we already have
                 * "bottom: $footer-height".
                 */
                this.escalator.footElem.getStyle().setLeft(-scrollLeft, Unit.PX);
            } else {
                this.escalator.position.set(this.escalator.footElem, -scrollLeft, 0);
            }

            lastScrollLeft = scrollLeft;
        }

        this.escalator.body.setBodyScrollPosition(scrollLeft, scrollTop);

        lastScrollTop = scrollTop;
        this.escalator.body.updateEscalatorRowsOnScroll();
        this.escalator.body.spacerContainer.updateSpacerDecosVisibility();
        /*
         * TODO [[optimize]]: Might avoid a reflow by first calculating new
         * scrolltop and scrolleft, then doing the escalator magic based on
         * those numbers and only updating the positions after that.
         */
    }

    public void scrollToColumn(final int columnIndex,
            final ScrollDestination destination, final int padding) {
        assert columnIndex >= this.escalator.columnConfiguration.frozenColumns : "Can't scroll to a frozen column";

        /*
         * To cope with frozen columns, we just pretend those columns are
         * not there at all when calculating the position of the target
         * column and the boundaries of the viewport. The resulting
         * scrollLeft will be correct without compensation since the DOM
         * structure effectively means that scrollLeft also ignores the
         * frozen columns.
         */
        final double frozenPixels = this.escalator.columnConfiguration
                .getCalculatedColumnsWidth(Range.withLength(0,
                        this.escalator.columnConfiguration.frozenColumns));

        final double targetStartPx = this.escalator.columnConfiguration
                .getCalculatedColumnsWidth(Range.withLength(0, columnIndex))
                - frozenPixels;
        final double targetEndPx = targetStartPx
                + this.escalator.columnConfiguration.getColumnWidthActual(columnIndex);

        final double viewportStartPx = this.escalator.getScrollLeft();
        double viewportEndPx = viewportStartPx
                + WidgetUtil
                        .getRequiredWidthBoundingClientRectDouble(this.escalator.getElement())
                - frozenPixels;
        if (this.escalator.verticalScrollbar.showsScrollHandle()) {
            viewportEndPx -= WidgetUtil.getNativeScrollbarSize();
        }

        final double scrollLeft = Escalator.getScrollPos(destination, targetStartPx,
                targetEndPx, viewportStartPx, viewportEndPx, padding);

        /*
         * note that it doesn't matter if the scroll would go beyond the
         * content, since the browser will adjust for that, and everything
         * fall into line accordingly.
         */
        this.escalator.setScrollLeft(scrollLeft);
    }

    public void scrollToRow(final int rowIndex,
            final ScrollDestination destination, final double padding) {

        final double targetStartPx = (this.escalator.body.getDefaultRowHeight() * rowIndex)
                + this.escalator.body.spacerContainer
                        .getSpacerHeightsSumUntilIndex(rowIndex);
        final double targetEndPx = targetStartPx
                + this.escalator.body.getDefaultRowHeight();

        final double viewportStartPx = this.escalator.getScrollTop();
        final double viewportEndPx = viewportStartPx
                + this.escalator.body.getHeightOfSection();

        final double scrollTop = Escalator.getScrollPos(destination, targetStartPx,
                targetEndPx, viewportStartPx, viewportEndPx, padding);

        /*
         * note that it doesn't matter if the scroll would go beyond the
         * content, since the browser will adjust for that, and everything
         * falls into line accordingly.
         */
        this.escalator.setScrollTop(scrollTop);
    }
}