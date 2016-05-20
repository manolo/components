/*
 * Copyright 2000-2014 Vaadin Ltd.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.vaadin.client.widgets.escalator;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Node;
import com.google.gwt.dom.client.Style;
import com.google.gwt.dom.client.Style.Display;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.dom.client.TableCellElement;
import com.google.gwt.dom.client.TableRowElement;
import com.google.gwt.dom.client.TableSectionElement;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.RequiresResize;
import com.google.gwt.user.client.ui.UIObject;
import com.google.gwt.user.client.ui.Widget;

import com.vaadin.client.BrowserInfo;
import com.vaadin.client.DeferredWorker;
import com.vaadin.client.Profiler;
import com.vaadin.client.WidgetUtil;
import com.vaadin.client.ui.SubPartAware;
import com.vaadin.client.widget.escalator.Cell;
import com.vaadin.client.widget.escalator.ColumnConfiguration;
import com.vaadin.client.widget.escalator.FlyweightRow;
import com.vaadin.client.widget.escalator.PositionFunction;
import com.vaadin.client.widget.escalator.PositionFunction.AbsolutePosition;
import com.vaadin.client.widget.escalator.PositionFunction.Translate3DPosition;
import com.vaadin.client.widget.escalator.PositionFunction.TranslatePosition;
import com.vaadin.client.widget.escalator.PositionFunction.WebkitTranslate3DPosition;
import com.vaadin.client.widget.escalator.RowContainer;
import com.vaadin.client.widget.escalator.RowContainer.BodyRowContainer;
import com.vaadin.client.widget.escalator.RowVisibilityChangeEvent;
import com.vaadin.client.widget.escalator.RowVisibilityChangeHandler;
import com.vaadin.client.widget.escalator.ScrollbarBundle;
import com.vaadin.client.widget.escalator.ScrollbarBundle.HorizontalScrollbarBundle;
import com.vaadin.client.widget.escalator.ScrollbarBundle.VerticalScrollbarBundle;
import com.vaadin.client.widget.grid.events.ScrollEvent;
import com.vaadin.client.widget.grid.events.ScrollHandler;
import com.vaadin.shared.ui.grid.HeightMode;
import com.vaadin.shared.ui.grid.Range;
import com.vaadin.shared.ui.grid.ScrollDestination;

import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/*-

 Maintenance Notes! Reading these might save your day.
 (note for editors: line width is 80 chars, including the
 one-space indentation)


 == Row Container Structure

 AbstractRowContainer
 |-- AbstractStaticRowContainer
 | |-- HeaderRowContainer
 | `-- FooterContainer
 `---- BodyRowContainerImpl

 AbstractRowContainer is intended to contain all common logic
 between RowContainers. It manages the bookkeeping of row
 count, makes sure that all individual cells are rendered
 the same way, and so on.

 AbstractStaticRowContainer has some special logic that is
 required by all RowContainers that don't scroll (hence the
 word "static"). HeaderRowContainer and FooterRowContainer
 are pretty thin special cases of a StaticRowContainer
 (mostly relating to positioning of the root element).

 BodyRowContainerImpl could also be split into an additional
 "AbstractScrollingRowContainer", but I felt that no more
 inner classes were needed. So it contains both logic
 required for making things scroll about, and equivalent
 special cases for layouting, as are found in
 Header/FooterRowContainers.


 == The Three Indices

 Each RowContainer can be thought to have three levels of
 indices for any given displayed row (but the distinction
 matters primarily for the BodyRowContainerImpl, because of 
 the way it scrolls through data):

 - Logical index
 - Physical (or DOM) index
 - Visual index

 LOGICAL INDEX is the index that is linked to the data
 source. If you want your data source to represent a SQL
 database with 10 000 rows, the 7 000:th row in the SQL has a
 logical index of 6 999, since the index is 0-based (unless
 that data source does some funky logic).

 PHYSICAL INDEX is the index for a row that you see in a
 browser's DOM inspector. If your row is the second <tr>
 element within a <tbody> tag, it has a physical index of 1
 (because of 0-based indices). In Header and
 FooterRowContainers, you are safe to assume that the logical
 index is the same as the physical index. But because the
 BodyRowContainerImpl never displays large data sources 
 entirely in the DOM, a physical index usually has no 
 apparent direct relationship with its logical index.

 VISUAL INDEX is the index relating to the order that you
 see a row in, in the browser, as it is rendered. The
 topmost row is 0, the second is 1, and so on. The visual
 index is similar to the physical index in the sense that
 Header and FooterRowContainers can assume a 1:1
 relationship between visual index and logical index. And
 again, BodyRowContainerImpl has no such relationship. The
 body's visual index has additionally no apparent
 relationship with its physical index. Because the <tr> tags
 are reused in the body and visually repositioned with CSS
 as the user scrolls, the relationship between physical
 index and visual index is quickly broken. You can get an
 element's visual index via the field
 BodyRowContainerImpl.visualRowOrder.

 Currently, the physical and visual indices are kept in sync
 _most of the time_ by a deferred rearrangement of rows.
 They become desynced when scrolling. This is to help screen
 readers to read the contents from the DOM in a natural
 order. See BodyRowContainerImpl.DeferredDomSorter for more
 about that.

 */

/**
 * A low-level table-like widget that features a scrolling virtual viewport and
 * lazily generated rows.
 * 
 * @since 7.4
 * @author Vaadin Ltd
 */
public class Escalator extends Widget implements RequiresResize,
        DeferredWorker, SubPartAware {

    // todo comments legend
    /*
     * [[optimize]]: There's an opportunity to rewrite the code in such a way
     * that it _might_ perform better (rememeber to measure, implement,
     * re-measure)
     */
    /*
     * [[mpixscroll]]: This code will require alterations that are relevant for
     * supporting the scrolling through more pixels than some browsers normally
     * would support. (i.e. when we support more than "a million" pixels in the
     * escalator DOM). NOTE: these bits can most often also be identified by
     * searching for code that call scrollElem.getScrollTop();.
     */
    /*
     * [[spacer]]: Code that is important to make spacers work.
     */

    /**
     * ScrollDestination case-specific handling logic.
     */
    static double getScrollPos(final ScrollDestination destination,
            final double targetStartPx, final double targetEndPx,
            final double viewportStartPx, final double viewportEndPx,
            final double padding) {

        final double viewportLength = viewportEndPx - viewportStartPx;

        switch (destination) {

        /*
         * Scroll as little as possible to show the target element. If the
         * element fits into view, this works as START or END depending on the
         * current scroll position. If the element does not fit into view, this
         * works as START.
         */
        case ANY: {
            final double startScrollPos = targetStartPx - padding;
            final double endScrollPos = targetEndPx + padding - viewportLength;

            if (startScrollPos < viewportStartPx) {
                return startScrollPos;
            } else if (targetEndPx + padding > viewportEndPx) {
                return endScrollPos;
            } else {
                // NOOP, it's already visible
                return viewportStartPx;
            }
        }

        /*
         * Scrolls so that the element is shown at the end of the viewport. The
         * viewport will, however, not scroll before its first element.
         */
        case END: {
            return targetEndPx + padding - viewportLength;
        }

        /*
         * Scrolls so that the element is shown in the middle of the viewport.
         * The viewport will, however, not scroll beyond its contents, given
         * more elements than what the viewport is able to show at once. Under
         * no circumstances will the viewport scroll before its first element.
         */
        case MIDDLE: {
            final double targetMiddle = targetStartPx
                    + (targetEndPx - targetStartPx) / 2;
            return targetMiddle - viewportLength / 2;
        }

        /*
         * Scrolls so that the element is shown at the start of the viewport.
         * The viewport will, however, not scroll beyond its contents.
         */
        case START: {
            return targetStartPx - padding;
        }

        /*
         * Throw an error if we're here. This can only mean that
         * ScrollDestination has been carelessly amended..
         */
        default: {
            throw new IllegalArgumentException(
                    "Internal: ScrollDestination has been modified, "
                            + "but Escalator.getScrollPos has not been updated "
                            + "to match new values.");
        }
        }

    }

    // abs(atan(y/x))*(180/PI) = n deg, x = 1, solve y
    /**
     * The solution to
     * <code>|tan<sup>-1</sup>(<i>x</i>)|&times;(180/&pi;)&nbsp;=&nbsp;30</code>
     * .
     * <p>
     * This constant is placed in the Escalator class, instead of an inner
     * class, since even mathematical expressions aren't allowed in non-static
     * inner classes for constants.
     */
    private static final double RATIO_OF_30_DEGREES = 1 / Math.sqrt(3);
    /**
     * The solution to
     * <code>|tan<sup>-1</sup>(<i>x</i>)|&times;(180/&pi;)&nbsp;=&nbsp;40</code>
     * .
     * <p>
     * This constant is placed in the Escalator class, instead of an inner
     * class, since even mathematical expressions aren't allowed in non-static
     * inner classes for constants.
     */
    private static final double RATIO_OF_40_DEGREES = Math.tan(2 * Math.PI / 9);

    private static final String DEFAULT_WIDTH = "500.0px";
    private static final String DEFAULT_HEIGHT = "400.0px";

    FlyweightRow flyweightRow = new FlyweightRow();

    /** The {@code <thead/>} tag. */
    final TableSectionElement headElem = TableSectionElement.as(DOM
            .createTHead());
    /** The {@code <tbody/>} tag. */
    final TableSectionElement bodyElem = TableSectionElement.as(DOM
            .createTBody());
    /** The {@code <tfoot/>} tag. */
    final TableSectionElement footElem = TableSectionElement.as(DOM
            .createTFoot());

    /**
     * TODO: investigate whether this field is now unnecessary, as
     * {@link ScrollbarBundle} now caches its values.
     * 
     * @deprecated maybe...
     */
    @Deprecated double tBodyScrollTop = 0;

    /**
     * TODO: investigate whether this field is now unnecessary, as
     * {@link ScrollbarBundle} now caches its values.
     * 
     * @deprecated maybe...
     */
    @Deprecated double tBodyScrollLeft = 0;

    final VerticalScrollbarBundle verticalScrollbar = new VerticalScrollbarBundle();
    final HorizontalScrollbarBundle horizontalScrollbar = new HorizontalScrollbarBundle();

    final HeaderRowContainerImpl header = new HeaderRowContainerImpl(this, headElem);
    final BodyRowContainerImpl body = new BodyRowContainerImpl(this, bodyElem);
    final FooterRowContainerImpl footer = new FooterRowContainerImpl(this, footElem);

    final Scroller scroller = new Scroller(this);

    final ColumnConfigurationImpl columnConfiguration = new ColumnConfigurationImpl(this);
    final DivElement tableWrapper;

    final DivElement horizontalScrollbarDeco = DivElement.as(DOM
            .createDiv());
    final DivElement headerDeco = DivElement.as(DOM.createDiv());
    final DivElement footerDeco = DivElement.as(DOM.createDiv());
    final DivElement spacerDecoContainer = DivElement.as(DOM
            .createDiv());

    PositionFunction position;

    /** The cached width of the escalator, in pixels. */
    double widthOfEscalator = 0;
    /** The cached height of the escalator, in pixels. */
    double heightOfEscalator = 0;

    /** The height of Escalator in terms of body rows. */
    private double heightByRows = 10.0d;

    /** The height of Escalator, as defined by {@link #setHeight(String)} */
    private String heightByCss = "";

    private HeightMode heightMode = HeightMode.CSS;

    private boolean layoutIsScheduled = false;
    private ScheduledCommand layoutCommand = new ScheduledCommand() {
        @Override
        public void execute() {
            recalculateElementSizes();
            layoutIsScheduled = false;
        }
    };

    final ElementPositionBookkeeper positions = new ElementPositionBookkeeper(this);

    /**
     * Creates a new Escalator widget instance.
     */
    public Escalator() {

        detectAndApplyPositionFunction();
        getLogger().info(
                "Using " + position.getClass().getSimpleName()
                        + " for position");

        final Element root = DOM.createDiv();
        setElement(root);

        setupScrollbars(root);

        tableWrapper = DivElement.as(DOM.createDiv());

        root.appendChild(tableWrapper);

        final Element table = DOM.createTable();
        tableWrapper.appendChild(table);

        table.appendChild(headElem);
        table.appendChild(bodyElem);
        table.appendChild(footElem);

        Style hCornerStyle = headerDeco.getStyle();
        hCornerStyle.setWidth(verticalScrollbar.getScrollbarThickness(),
                Unit.PX);
        hCornerStyle.setDisplay(Display.NONE);
        root.appendChild(headerDeco);

        Style fCornerStyle = footerDeco.getStyle();
        fCornerStyle.setWidth(verticalScrollbar.getScrollbarThickness(),
                Unit.PX);
        fCornerStyle.setDisplay(Display.NONE);
        root.appendChild(footerDeco);

        Style hWrapperStyle = horizontalScrollbarDeco.getStyle();
        hWrapperStyle.setDisplay(Display.NONE);
        hWrapperStyle.setHeight(horizontalScrollbar.getScrollbarThickness(),
                Unit.PX);
        root.appendChild(horizontalScrollbarDeco);

        setStylePrimaryName("v-escalator");

        spacerDecoContainer.setAttribute("aria-hidden", "true");

        // init default dimensions
        setHeight(null);
        setWidth(null);
    }

    private void setupScrollbars(final Element root) {

        ScrollHandler scrollHandler = new ScrollHandler() {
            @Override
            public void onScroll(ScrollEvent event) {
                scroller.onScroll();
                fireEvent(new ScrollEvent());
            }
        };

        int scrollbarThickness = WidgetUtil.getNativeScrollbarSize();
        if (BrowserInfo.get().isIE()) {
            /*
             * IE refuses to scroll properly if the DIV isn't at least one pixel
             * larger than the scrollbar controls themselves. But, probably
             * because of subpixel rendering, in Grid, one pixel isn't enough,
             * so we'll add two instead.
             */
            if (BrowserInfo.get().isIE9()) {
                scrollbarThickness += 2;
            } else {
                scrollbarThickness += 1;
            }
        }

        root.appendChild(verticalScrollbar.getElement());
        verticalScrollbar.addScrollHandler(scrollHandler);
        verticalScrollbar.setScrollbarThickness(scrollbarThickness);

        if (BrowserInfo.get().isIE8()) {
            /*
             * IE8 will have to compensate for a misalignment where it pops the
             * scrollbar outside of its box. See Bug 3 in
             * http://edskes.net/ie/ie8overflowandexpandingboxbugs.htm
             */
            Style vScrollStyle = verticalScrollbar.getElement().getStyle();
            vScrollStyle.setRight(
                    verticalScrollbar.getScrollbarThickness() - 1, Unit.PX);
        }

        root.appendChild(horizontalScrollbar.getElement());
        horizontalScrollbar.addScrollHandler(scrollHandler);
        horizontalScrollbar.setScrollbarThickness(scrollbarThickness);
        horizontalScrollbar
                .addVisibilityHandler(new ScrollbarBundle.VisibilityHandler() {

                    private boolean queued = false;

                    @Override
                    public void visibilityChanged(
                            ScrollbarBundle.VisibilityChangeEvent event) {
                        if (queued) {
                            return;
                        }
                        queued = true;

                        /*
                         * We either lost or gained a scrollbar. In any case, we
                         * need to change the height, if it's defined by rows.
                         */
                        Scheduler.get().scheduleFinally(new ScheduledCommand() {

                            @Override
                            public void execute() {
                                applyHeightByRows();
                                queued = false;
                            }
                        });
                    }
                });

        /*
         * Because of all the IE hacks we've done above, we now have scrollbars
         * hiding underneath a lot of DOM elements.
         * 
         * This leads to problems with OSX (and many touch-only devices) when
         * scrollbars are only shown when scrolling, as the scrollbar elements
         * are hidden underneath everything. We trust that the scrollbars behave
         * properly in these situations and simply pop them out with a bit of
         * z-indexing.
         */
        if (WidgetUtil.getNativeScrollbarSize() == 0) {
            verticalScrollbar.getElement().getStyle().setZIndex(90);
            horizontalScrollbar.getElement().getStyle().setZIndex(90);
        }
    }

    @Override
    protected void onLoad() {
        super.onLoad();

        header.autodetectRowHeightLater();
        body.autodetectRowHeightLater();
        footer.autodetectRowHeightLater();

        header.paintInsertRows(0, header.getRowCount());
        footer.paintInsertRows(0, footer.getRowCount());

        // recalculateElementSizes();

        Scheduler.get().scheduleDeferred(new Command() {
            @Override
            public void execute() {
                /*
                 * Not a faintest idea why we have to defer this call, but
                 * unless it is deferred, the size of the escalator will be 0x0
                 * after it is first detached and then reattached to the DOM.
                 * This only applies to a bare Escalator; inside a Grid
                 * everything works fine either way.
                 * 
                 * The three autodetectRowHeightLater calls above seem obvious
                 * suspects at first. However, they don't seem to have anything
                 * to do with the issue, as they are no-ops in the
                 * detach-reattach case.
                 */
                recalculateElementSizes();
            }
        });

        /*
         * Note: There's no need to explicitly insert rows into the body.
         * 
         * recalculateElementSizes will recalculate the height of the body. This
         * has the side-effect that as the body's size grows bigger (i.e. from 0
         * to its actual height), more escalator rows are populated. Those
         * escalator rows are then immediately rendered. This, in effect, is the
         * same thing as inserting those rows.
         * 
         * In fact, having an extra paintInsertRows here would lead to duplicate
         * rows.
         */

        boolean columnsChanged = false;
        for (ColumnConfigurationImpl.Column column : columnConfiguration.columns) {
            boolean columnChanged = column.measureAndSetWidthIfNeeded();
            if (columnChanged) {
                columnsChanged = true;
            }
        }
        if (columnsChanged) {
            header.reapplyColumnWidths();
            body.reapplyColumnWidths();
            footer.reapplyColumnWidths();
        }

        verticalScrollbar.onLoad();
        horizontalScrollbar.onLoad();
        scroller.onLoad();
    }

    @Override
    protected void onUnload() {
        scroller.onUnload();

        /*
         * We can call paintRemoveRows here, because static ranges are simple to
         * remove.
         */
        header.paintRemoveRows(0, header.getRowCount());
        footer.paintRemoveRows(0, footer.getRowCount());

        /*
         * We can't call body.paintRemoveRows since it relies on rowCount to be
         * updated correctly. Since it isn't, we'll simply and brutally rip out
         * the DOM elements (in an elegant way, of course).
         */
        int rowsToRemove = body.getDomRowCount();
        for (int i = 0; i < rowsToRemove; i++) {
            int index = rowsToRemove - i - 1;
            TableRowElement tr = bodyElem.getRows().getItem(index);
            body.paintRemoveRow(tr, index);
            positions.remove(tr);
        }
        body.visualRowOrder.clear();
        body.setTopRowLogicalIndex(0);

        super.onUnload();
    }

    private void detectAndApplyPositionFunction() {
        /*
         * firefox has a bug in its translate operation, showing white space
         * when adjusting the scrollbar in BodyRowContainer.paintInsertRows
         */
        if (Window.Navigator.getUserAgent().contains("Firefox")) {
            position = new AbsolutePosition();
            return;
        }

        final Style docStyle = Document.get().getBody().getStyle();
        if (hasProperty(docStyle, "transform")) {
            if (hasProperty(docStyle, "transformStyle")) {
                position = new Translate3DPosition();
            } else {
                position = new TranslatePosition();
            }
        } else if (hasProperty(docStyle, "webkitTransform")) {
            position = new WebkitTranslate3DPosition();
        } else {
            position = new AbsolutePosition();
        }
    }

    Logger getLogger() {
        return Logger.getLogger(getClass().getName());
    }

    private static native boolean hasProperty(Style style, String name)
    /*-{
        return style[name] !== undefined;
    }-*/;

    /**
     * Check whether there are both columns and any row data (for either
     * headers, body or footer).
     * 
     * @return <code>true</code> iff header, body or footer has rows && there
     *         are columns
     */
    boolean hasColumnAndRowData() {
        return (header.getRowCount() > 0 || body.getRowCount() > 0 || footer
                .getRowCount() > 0) && columnConfiguration.getColumnCount() > 0;
    }

    /**
     * Check whether there are any cells in the DOM.
     * 
     * @return <code>true</code> iff header, body or footer has any child
     *         elements
     */
    boolean hasSomethingInDom() {
        return headElem.hasChildNodes() || bodyElem.hasChildNodes()
                || footElem.hasChildNodes();
    }

    /**
     * Returns the row container for the header in this Escalator.
     * 
     * @return the header. Never <code>null</code>
     */
    public RowContainer getHeader() {
        return header;
    }

    /**
     * Returns the row container for the body in this Escalator.
     * 
     * @return the body. Never <code>null</code>
     */
    public BodyRowContainer getBody() {
        return body;
    }

    /**
     * Returns the row container for the footer in this Escalator.
     * 
     * @return the footer. Never <code>null</code>
     */
    public RowContainer getFooter() {
        return footer;
    }

    /**
     * Returns the configuration object for the columns in this Escalator.
     * 
     * @return the configuration object for the columns in this Escalator. Never
     *         <code>null</code>
     */
    public ColumnConfiguration getColumnConfiguration() {
        return columnConfiguration;
    }

    @Override
    public void setWidth(final String width) {
        if (width != null && !width.isEmpty()) {
            super.setWidth(width);
        } else {
            super.setWidth(DEFAULT_WIDTH);
        }

        recalculateElementSizes();
    }

    /**
     * {@inheritDoc}
     * <p>
     * If Escalator is currently not in {@link HeightMode#CSS}, the given value
     * is remembered, and applied once the mode is applied.
     * 
     * @see #setHeightMode(HeightMode)
     */
    @Override
    public void setHeight(String height) {
        /*
         * TODO remove method once RequiresResize and the Vaadin layoutmanager
         * listening mechanisms are implemented
         */

        if (height != null && !height.isEmpty()) {
            heightByCss = height;
        } else {
            heightByCss = DEFAULT_HEIGHT;
        }

        if (getHeightMode() == HeightMode.CSS) {
            setHeightInternal(height);
        }
    }

    private void setHeightInternal(final String height) {
        final int escalatorRowsBefore = body.visualRowOrder.size();

        if (height != null && !height.isEmpty()) {
            super.setHeight(height);
        } else {
            super.setHeight(DEFAULT_HEIGHT);
        }

        recalculateElementSizes();

        if (escalatorRowsBefore != body.visualRowOrder.size()) {
            fireRowVisibilityChangeEvent();
        }
    }

    /**
     * Returns the vertical scroll offset. Note that this is not necessarily the
     * same as the {@code scrollTop} attribute in the DOM.
     * 
     * @return the logical vertical scroll offset
     */
    public double getScrollTop() {
        return verticalScrollbar.getScrollPos();
    }

    /**
     * Sets the vertical scroll offset. Note that this will not necessarily
     * become the same as the {@code scrollTop} attribute in the DOM.
     * 
     * @param scrollTop
     *            the number of pixels to scroll vertically
     */
    public void setScrollTop(final double scrollTop) {
        verticalScrollbar.setScrollPos(scrollTop);
    }

    /**
     * Returns the logical horizontal scroll offset. Note that this is not
     * necessarily the same as the {@code scrollLeft} attribute in the DOM.
     * 
     * @return the logical horizontal scroll offset
     */
    public double getScrollLeft() {
        return horizontalScrollbar.getScrollPos();
    }

    /**
     * Sets the logical horizontal scroll offset. Note that will not necessarily
     * become the same as the {@code scrollLeft} attribute in the DOM.
     * 
     * @param scrollLeft
     *            the number of pixels to scroll horizontally
     */
    public void setScrollLeft(final double scrollLeft) {
        horizontalScrollbar.setScrollPos(scrollLeft);
    }

    /**
     * Returns the scroll width for the escalator. Note that this is not
     * necessary the same as {@code Element.scrollWidth} in the DOM.
     * 
     * @since 7.5.0
     * @return the scroll width in pixels
     */
    public double getScrollWidth() {
        return horizontalScrollbar.getScrollSize();
    }

    /**
     * Returns the scroll height for the escalator. Note that this is not
     * necessary the same as {@code Element.scrollHeight} in the DOM.
     * 
     * @since 7.5.0
     * @return the scroll height in pixels
     */
    public double getScrollHeight() {
        return verticalScrollbar.getScrollSize();
    }

    /**
     * Scrolls the body horizontally so that the column at the given index is
     * visible and there is at least {@code padding} pixels in the direction of
     * the given scroll destination.
     * 
     * @param columnIndex
     *            the index of the column to scroll to
     * @param destination
     *            where the column should be aligned visually after scrolling
     * @param padding
     *            the number pixels to place between the scrolled-to column and
     *            the viewport edge.
     * @throws IndexOutOfBoundsException
     *             if {@code columnIndex} is not a valid index for an existing
     *             column
     * @throws IllegalArgumentException
     *             if {@code destination} is {@link ScrollDestination#MIDDLE}
     *             and padding is nonzero; or if the indicated column is frozen;
     *             or if {@code destination == null}
     */
    public void scrollToColumn(final int columnIndex,
            final ScrollDestination destination, final int padding)
            throws IndexOutOfBoundsException, IllegalArgumentException {
        validateScrollDestination(destination, padding);
        verifyValidColumnIndex(columnIndex);

        if (columnIndex < columnConfiguration.frozenColumns) {
            throw new IllegalArgumentException("The given column index "
                    + columnIndex + " is frozen.");
        }

        scroller.scrollToColumn(columnIndex, destination, padding);
    }

    private void verifyValidColumnIndex(final int columnIndex)
            throws IndexOutOfBoundsException {
        if (columnIndex < 0
                || columnIndex >= columnConfiguration.getColumnCount()) {
            throw new IndexOutOfBoundsException("The given column index "
                    + columnIndex + " does not exist.");
        }
    }

    /**
     * Scrolls the body vertically so that the row at the given index is visible
     * and there is at least {@literal padding} pixels to the given scroll
     * destination.
     * 
     * @param rowIndex
     *            the index of the logical row to scroll to
     * @param destination
     *            where the row should be aligned visually after scrolling
     * @param padding
     *            the number pixels to place between the scrolled-to row and the
     *            viewport edge.
     * @throws IndexOutOfBoundsException
     *             if {@code rowIndex} is not a valid index for an existing row
     * @throws IllegalArgumentException
     *             if {@code destination} is {@link ScrollDestination#MIDDLE}
     *             and padding is nonzero; or if {@code destination == null}
     * @see #scrollToRowAndSpacer(int, ScrollDestination, int)
     * @see #scrollToSpacer(int, ScrollDestination, int)
     */
    public void scrollToRow(final int rowIndex,
            final ScrollDestination destination, final int padding)
            throws IndexOutOfBoundsException, IllegalArgumentException {
        Scheduler.get().scheduleFinally(new ScheduledCommand() {
            @Override
            public void execute() {
                validateScrollDestination(destination, padding);
                verifyValidRowIndex(rowIndex);
                scroller.scrollToRow(rowIndex, destination, padding);
            }
        });
    }

    private void verifyValidRowIndex(final int rowIndex) {
        if (rowIndex < 0 || rowIndex >= body.getRowCount()) {
            throw new IndexOutOfBoundsException("The given row index "
                    + rowIndex + " does not exist.");
        }
    }

    /**
     * Scrolls the body vertically so that the spacer at the given row index is
     * visible and there is at least {@literal padding} pixesl to the given
     * scroll destination.
     * 
     * @since 7.5.0
     * @param spacerIndex
     *            the row index of the spacer to scroll to
     * @param destination
     *            where the spacer should be aligned visually after scrolling
     * @param padding
     *            the number of pixels to place between the scrolled-to spacer
     *            and the viewport edge
     * @throws IllegalArgumentException
     *             if {@code spacerIndex} is not an opened spacer; or if
     *             {@code destination} is {@link ScrollDestination#MIDDLE} and
     *             padding is nonzero; or if {@code destination == null}
     * @see #scrollToRow(int, ScrollDestination, int)
     * @see #scrollToRowAndSpacer(int, ScrollDestination, int)
     */
    public void scrollToSpacer(final int spacerIndex,
            ScrollDestination destination, final int padding)
            throws IllegalArgumentException {
        validateScrollDestination(destination, padding);
        body.scrollToSpacer(spacerIndex, destination, padding);
    }

    /**
     * Scrolls vertically to a row and the spacer below it.
     * <p>
     * If a spacer is not open at that index, this method behaves like
     * {@link #scrollToRow(int, ScrollDestination, int)}
     * 
     * @since 7.5.0
     * @param rowIndex
     *            the index of the logical row to scroll to. -1 takes the
     *            topmost spacer into account as well.
     * @param destination
     *            where the row should be aligned visually after scrolling
     * @param padding
     *            the number pixels to place between the scrolled-to row and the
     *            viewport edge.
     * @see #scrollToRow(int, ScrollDestination, int)
     * @see #scrollToSpacer(int, ScrollDestination, int)
     * @throws IllegalArgumentException
     *             if {@code destination} is {@link ScrollDestination#MIDDLE}
     *             and {@code padding} is not zero; or if {@code rowIndex} is
     *             not a valid row index, or -1; or if
     *             {@code destination == null}; or if {@code rowIndex == -1} and
     *             there is no spacer open at that index.
     */
    public void scrollToRowAndSpacer(final int rowIndex,
            final ScrollDestination destination, final int padding)
            throws IllegalArgumentException {
        Scheduler.get().scheduleDeferred(new ScheduledCommand() {
            @Override
            public void execute() {
                validateScrollDestination(destination, padding);
                if (rowIndex != -1) {
                    verifyValidRowIndex(rowIndex);
                }

                // row range
                final Range rowRange;
                if (rowIndex != -1) {
                    int rowTop = (int) Math.floor(body.getRowTop(rowIndex));
                    int rowHeight = (int) Math.ceil(body.getDefaultRowHeight());
                    rowRange = Range.withLength(rowTop, rowHeight);
                } else {
                    rowRange = Range.withLength(0, 0);
                }

                // get spacer
                final SpacerContainer.SpacerImpl spacer = body.spacerContainer
                        .getSpacer(rowIndex);

                if (rowIndex == -1 && spacer == null) {
                    throw new IllegalArgumentException(
                            "Cannot scroll to row index "
                                    + "-1, as there is no spacer open at that index.");
                }

                // make into target range
                final Range targetRange;
                if (spacer != null) {
                    final int spacerTop = (int) Math.floor(spacer.getTop());
                    final int spacerHeight = (int) Math.ceil(spacer.getHeight());
                    Range spacerRange = Range.withLength(spacerTop,
                            spacerHeight);

                    targetRange = rowRange.combineWith(spacerRange);
                } else {
                    targetRange = rowRange;
                }

                // get params
                int targetStart = targetRange.getStart();
                int targetEnd = targetRange.getEnd();
                double viewportStart = getScrollTop();
                double viewportEnd = viewportStart + body.getHeightOfSection();

                double scrollPos = getScrollPos(destination, targetStart,
                        targetEnd, viewportStart, viewportEnd, padding);

                setScrollTop(scrollPos);
            }
        });
    }

    private static void validateScrollDestination(
            final ScrollDestination destination, final int padding) {
        if (destination == null) {
            throw new IllegalArgumentException("Destination cannot be null");
        }

        if (destination == ScrollDestination.MIDDLE && padding != 0) {
            throw new IllegalArgumentException(
                    "You cannot have a padding with a MIDDLE destination");
        }
    }

    /**
     * Recalculates the dimensions for all elements that require manual
     * calculations. Also updates the dimension caches.
     * <p>
     * <em>Note:</em> This method has the <strong>side-effect</strong>
     * automatically makes sure that an appropriate amount of escalator rows are
     * present. So, if the body area grows, more <strong>escalator rows might be
     * inserted</strong>. Conversely, if the body area shrinks,
     * <strong>escalator rows might be removed</strong>.
     */
    void recalculateElementSizes() {
        if (!isAttached()) {
            return;
        }

        Profiler.enter("Escalator.recalculateElementSizes");
        widthOfEscalator = Math.max(0, WidgetUtil
                .getRequiredWidthBoundingClientRectDouble(getElement()));
        heightOfEscalator = Math.max(0, WidgetUtil
                .getRequiredHeightBoundingClientRectDouble(getElement()));

        header.recalculateSectionHeight();
        body.recalculateSectionHeight();
        footer.recalculateSectionHeight();

        scroller.recalculateScrollbarsForVirtualViewport();
        body.verifyEscalatorCount();
        body.reapplySpacerWidths();
        Profiler.leave("Escalator.recalculateElementSizes");
    }

    /**
     * Snap deltas of x and y to the major four axes (up, down, left, right)
     * with a threshold of a number of degrees from those axes.
     * 
     * @param deltaX
     *            the delta in the x axis
     * @param deltaY
     *            the delta in the y axis
     * @param thresholdRatio
     *            the threshold in ratio (0..1) between x and y for when to snap
     * @return a two-element array: <code>[snappedX, snappedY]</code>
     */
    private static double[] snapDeltas(final double deltaX,
            final double deltaY, final double thresholdRatio) {

        final double[] array = new double[2];
        if (deltaX != 0 && deltaY != 0) {
            final double aDeltaX = Math.abs(deltaX);
            final double aDeltaY = Math.abs(deltaY);
            final double yRatio = aDeltaY / aDeltaX;
            final double xRatio = aDeltaX / aDeltaY;

            array[0] = (xRatio < thresholdRatio) ? 0 : deltaX;
            array[1] = (yRatio < thresholdRatio) ? 0 : deltaY;
        } else {
            array[0] = deltaX;
            array[1] = deltaY;
        }

        return array;
    }

    /**
     * Adds an event handler that gets notified when the range of visible rows
     * changes e.g. because of scrolling, row resizing or spacers
     * appearing/disappearing.
     * 
     * @param rowVisibilityChangeHandler
     *            the event handler
     * @return a handler registration for the added handler
     */
    public HandlerRegistration addRowVisibilityChangeHandler(
            RowVisibilityChangeHandler rowVisibilityChangeHandler) {
        return addHandler(rowVisibilityChangeHandler,
                RowVisibilityChangeEvent.TYPE);
    }

    void fireRowVisibilityChangeEvent() {
        if (!body.visualRowOrder.isEmpty()) {
            int visibleRangeStart = body.getLogicalRowIndex(body.visualRowOrder
                    .getFirst());
            int visibleRangeEnd = body.getLogicalRowIndex(body.visualRowOrder
                    .getLast()) + 1;

            int visibleRowCount = visibleRangeEnd - visibleRangeStart;
            fireEvent(new RowVisibilityChangeEvent(visibleRangeStart,
                    visibleRowCount));
        } else {
            fireEvent(new RowVisibilityChangeEvent(0, 0));
        }
    }

    /**
     * Gets the logical index range of currently visible rows.
     * 
     * @return logical index range of visible rows
     */
    public Range getVisibleRowRange() {
        if (!body.visualRowOrder.isEmpty()) {
            return Range.withLength(body.getTopRowLogicalIndex(),
                    body.visualRowOrder.size());
        } else {
            return Range.withLength(0, 0);
        }
    }

    /**
     * Returns the widget from a cell node or <code>null</code> if there is no
     * widget in the cell
     * 
     * @param cellNode
     *            The cell node
     */
    static Widget getWidgetFromCell(Node cellNode) {
        Node possibleWidgetNode = cellNode.getFirstChild();
        if (possibleWidgetNode != null
                && possibleWidgetNode.getNodeType() == Node.ELEMENT_NODE) {
            @SuppressWarnings("deprecation")
            com.google.gwt.user.client.Element castElement = (com.google.gwt.user.client.Element) possibleWidgetNode
                    .cast();
            Widget w = WidgetUtil.findWidget(castElement, null);

            // Ensure findWidget did not traverse past the cell element in the
            // DOM hierarchy
            if (cellNode.isOrHasChild(w.getElement())) {
                return w;
            }
        }
        return null;
    }

    @Override
    public void setStylePrimaryName(String style) {
        super.setStylePrimaryName(style);

        verticalScrollbar.setStylePrimaryName(style);
        horizontalScrollbar.setStylePrimaryName(style);

        UIObject.setStylePrimaryName(tableWrapper, style + "-tablewrapper");
        UIObject.setStylePrimaryName(headerDeco, style + "-header-deco");
        UIObject.setStylePrimaryName(footerDeco, style + "-footer-deco");
        UIObject.setStylePrimaryName(horizontalScrollbarDeco, style
                + "-horizontal-scrollbar-deco");
        UIObject.setStylePrimaryName(spacerDecoContainer, style
                + "-spacer-deco-container");

        header.setStylePrimaryName(style);
        body.setStylePrimaryName(style);
        footer.setStylePrimaryName(style);
    }

    /**
     * Sets the number of rows that should be visible in Escalator's body, while
     * {@link #getHeightMode()} is {@link HeightMode#ROW}.
     * <p>
     * If Escalator is currently not in {@link HeightMode#ROW}, the given value
     * is remembered, and applied once the mode is applied.
     * 
     * @param rows
     *            the number of rows that should be visible in Escalator's body
     * @throws IllegalArgumentException
     *             if {@code rows} is &leq; 0,
     *             {@link Double#isInifinite(double) infinite} or
     *             {@link Double#isNaN(double) NaN}.
     * @see #setHeightMode(HeightMode)
     */
    public void setHeightByRows(double rows) throws IllegalArgumentException {
        if (rows <= 0) {
            throw new IllegalArgumentException(
                    "The number of rows must be a positive number.");
        } else if (Double.isInfinite(rows)) {
            throw new IllegalArgumentException(
                    "The number of rows must be finite.");
        } else if (Double.isNaN(rows)) {
            throw new IllegalArgumentException("The number must not be NaN.");
        }

        heightByRows = rows;
        applyHeightByRows();
    }

    /**
     * Gets the amount of rows in Escalator's body that are shown, while
     * {@link #getHeightMode()} is {@link HeightMode#ROW}.
     * <p>
     * By default, it is 10.
     * 
     * @return the amount of rows that are being shown in Escalator's body
     * @see #setHeightByRows(double)
     */
    public double getHeightByRows() {
        return heightByRows;
    }

    /**
     * Reapplies the row-based height of the Grid, if Grid currently should
     * define its height that way.
     */
    void applyHeightByRows() {
        if (heightMode != HeightMode.ROW) {
            return;
        }

        double headerHeight = header.getHeightOfSection();
        double footerHeight = footer.getHeightOfSection();
        double bodyHeight = body.getDefaultRowHeight() * heightByRows;
        double scrollbar = horizontalScrollbar.showsScrollHandle() ? horizontalScrollbar
                .getScrollbarThickness() : 0;

        double totalHeight = headerHeight + bodyHeight + scrollbar
                + footerHeight;
        setHeightInternal(totalHeight + "px");
    }

    /**
     * Defines the mode in which the Escalator widget's height is calculated.
     * <p>
     * If {@link HeightMode#CSS} is given, Escalator will respect the values
     * given via {@link #setHeight(String)}, and behave as a traditional Widget.
     * <p>
     * If {@link HeightMode#ROW} is given, Escalator will make sure that the
     * {@link #getBody() body} will display as many rows as
     * {@link #getHeightByRows()} defines. <em>Note:</em> If headers/footers are
     * inserted or removed, the widget will resize itself to still display the
     * required amount of rows in its body. It also takes the horizontal
     * scrollbar into account.
     * 
     * @param heightMode
     *            the mode in to which Escalator should be set
     */
    public void setHeightMode(HeightMode heightMode) {
        /*
         * This method is a workaround for the fact that Vaadin re-applies
         * widget dimensions (height/width) on each state change event. The
         * original design was to have setHeight an setHeightByRow be equals,
         * and whichever was called the latest was considered in effect.
         * 
         * But, because of Vaadin always calling setHeight on the widget, this
         * approach doesn't work.
         */

        if (heightMode != this.heightMode) {
            this.heightMode = heightMode;

            switch (this.heightMode) {
            case CSS:
                setHeight(heightByCss);
                break;
            case ROW:
                setHeightByRows(heightByRows);
                break;
            default:
                throw new IllegalStateException("Unimplemented feature "
                        + "- unknown HeightMode: " + this.heightMode);
            }
        }
    }

    /**
     * Returns the current {@link HeightMode} the Escalator is in.
     * <p>
     * Defaults to {@link HeightMode#CSS}.
     * 
     * @return the current HeightMode
     */
    public HeightMode getHeightMode() {
        return heightMode;
    }

    /**
     * Returns the {@link RowContainer} which contains the element.
     * 
     * @param element
     *            the element to check for
     * @return the container the element is in or <code>null</code> if element
     *         is not present in any container.
     */
    public RowContainer findRowContainer(Element element) {
        if (getHeader().getElement() != element
                && getHeader().getElement().isOrHasChild(element)) {
            return getHeader();
        } else if (getBody().getElement() != element
                && getBody().getElement().isOrHasChild(element)) {
            return getBody();
        } else if (getFooter().getElement() != element
                && getFooter().getElement().isOrHasChild(element)) {
            return getFooter();
        }
        return null;
    }

    /**
     * Sets whether a scroll direction is locked or not.
     * <p>
     * If a direction is locked, the escalator will refuse to scroll in that
     * direction.
     * 
     * @param direction
     *            the orientation of the scroll to set the lock status
     * @param locked
     *            <code>true</code> to lock, <code>false</code> to unlock
     */
    public void setScrollLocked(ScrollbarBundle.Direction direction,
            boolean locked) {
        switch (direction) {
        case HORIZONTAL:
            horizontalScrollbar.setLocked(locked);
            break;
        case VERTICAL:
            verticalScrollbar.setLocked(locked);
            break;
        default:
            throw new UnsupportedOperationException("Unexpected value: "
                    + direction);
        }
    }

    /**
     * Checks whether or not an direction is locked for scrolling.
     * 
     * @param direction
     *            the direction of the scroll of which to check the lock status
     * @return <code>true</code> iff the direction is locked
     */
    public boolean isScrollLocked(ScrollbarBundle.Direction direction) {
        switch (direction) {
        case HORIZONTAL:
            return horizontalScrollbar.isLocked();
        case VERTICAL:
            return verticalScrollbar.isLocked();
        default:
            throw new UnsupportedOperationException("Unexpected value: "
                    + direction);
        }
    }

    /**
     * Adds a scroll handler to this escalator
     * 
     * @param handler
     *            the scroll handler to add
     * @return a handler registration for the registered scroll handler
     */
    public HandlerRegistration addScrollHandler(ScrollHandler handler) {
        return addHandler(handler, ScrollEvent.TYPE);
    }

    @Override
    public boolean isWorkPending() {
        return body.domSorter.isRunning() || verticalScrollbar.isWorkPending()
                || horizontalScrollbar.isWorkPending() || layoutIsScheduled;
    }

    @Override
    public void onResize() {
        if (isAttached() && !layoutIsScheduled) {
            layoutIsScheduled = true;
            Scheduler.get().scheduleFinally(layoutCommand);
        }
    }

    /**
     * Gets the maximum number of body rows that can be visible on the screen at
     * once.
     * 
     * @return the maximum capacity
     */
    public int getMaxVisibleRowCount() {
        return body.getMaxEscalatorRowCapacity();
    }

    /**
     * Gets the escalator's inner width. This is the entire width in pixels,
     * without the vertical scrollbar.
     * 
     * @return escalator's inner width
     */
    public double getInnerWidth() {
        return WidgetUtil
                .getRequiredWidthBoundingClientRectDouble(tableWrapper);
    }

    /**
     * Resets all cached pixel sizes and reads new values from the DOM. This
     * methods should be used e.g. when styles affecting the dimensions of
     * elements in this escalator have been changed.
     */
    public void resetSizesFromDom() {
        header.autodetectRowHeightNow();
        body.autodetectRowHeightNow();
        footer.autodetectRowHeightNow();

        for (int i = 0; i < columnConfiguration.getColumnCount(); i++) {
            columnConfiguration.setColumnWidth(i,
                    columnConfiguration.getColumnWidth(i));
        }
    }

    Range getViewportPixels() {
        int from = (int) Math.floor(verticalScrollbar.getScrollPos());
        int to = (int) body.getHeightOfSection();
        return Range.withLength(from, to);
    }

    @Override
    @SuppressWarnings("deprecation")
    public com.google.gwt.user.client.Element getSubPartElement(String subPart) {
        SubPartArguments args = SubPartArguments.create(subPart);

        Element tableStructureElement = getSubPartElementTableStructure(args);
        if (tableStructureElement != null) {
            return DOM.asOld(tableStructureElement);
        }

        Element spacerElement = getSubPartElementSpacer(args);
        if (spacerElement != null) {
            return DOM.asOld(spacerElement);
        }

        return null;
    }

    private Element getSubPartElementTableStructure(SubPartArguments args) {

        String type = args.getType();
        int[] indices = args.getIndices();

        // Get correct RowContainer for type from Escalator
        RowContainer container = null;
        if (type.equalsIgnoreCase("header")) {
            container = getHeader();
        } else if (type.equalsIgnoreCase("cell")) {
            // If wanted row is not visible, we need to scroll there.
            Range visibleRowRange = getVisibleRowRange();
            if (indices.length > 0 && !visibleRowRange.contains(indices[0])) {
                try {
                    scrollToRow(indices[0], ScrollDestination.ANY, 0);
                } catch (IllegalArgumentException e) {
                    getLogger().log(Level.SEVERE, e.getMessage());
                }
                // Scrolling causes a lazy loading event. No element can
                // currently be retrieved.
                return null;
            }
            container = getBody();
        } else if (type.equalsIgnoreCase("footer")) {
            container = getFooter();
        }

        if (null != container) {
            if (indices.length == 0) {
                // No indexing. Just return the wanted container element
                return container.getElement();
            } else {
                try {
                    return getSubPart(container, indices);
                } catch (Exception e) {
                    getLogger().log(Level.SEVERE, e.getMessage());
                }
            }
        }
        return null;
    }

    private Element getSubPart(RowContainer container, int[] indices) {
        Element targetElement = container.getRowElement(indices[0]);

        // Scroll wanted column to view if able
        if (indices.length > 1 && targetElement != null) {
            if (getColumnConfiguration().getFrozenColumnCount() <= indices[1]) {
                scrollToColumn(indices[1], ScrollDestination.ANY, 0);
            }

            targetElement = getCellFromRow(TableRowElement.as(targetElement),
                    indices[1]);

            for (int i = 2; i < indices.length && targetElement != null; ++i) {
                targetElement = (Element) targetElement.getChild(indices[i]);
            }
        }

        return targetElement;
    }

    private static Element getCellFromRow(TableRowElement rowElement, int index) {
        int childCount = rowElement.getCells().getLength();
        if (index < 0 || index >= childCount) {
            return null;
        }

        TableCellElement currentCell = null;
        boolean indexInColspan = false;
        int i = 0;

        while (!indexInColspan) {
            currentCell = rowElement.getCells().getItem(i);

            // Calculate if this is the cell we are looking for
            int colSpan = currentCell.getColSpan();
            indexInColspan = index < colSpan + i;

            // Increment by colspan to skip over hidden cells
            i += colSpan;
        }
        return currentCell;
    }

    private Element getSubPartElementSpacer(SubPartArguments args) {
        if ("spacer".equals(args.getType()) && args.getIndicesLength() == 1) {
            return body.spacerContainer.getSubPartElement(args.getIndex(0));
        } else {
            return null;
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public String getSubPartName(com.google.gwt.user.client.Element subElement) {

        /*
         * The spacer check needs to be before table structure check, because
         * (for now) the table structure will take spacer elements into account
         * as well, when it shouldn't.
         */

        String spacer = getSubPartNameSpacer(subElement);
        if (spacer != null) {
            return spacer;
        }

        String tableStructure = getSubPartNameTableStructure(subElement);
        if (tableStructure != null) {
            return tableStructure;
        }

        return null;
    }

    private String getSubPartNameTableStructure(Element subElement) {

        List<RowContainer> containers = Arrays.asList(getHeader(), getBody(),
                getFooter());
        List<String> containerType = Arrays.asList("header", "cell", "footer");

        for (int i = 0; i < containers.size(); ++i) {
            RowContainer container = containers.get(i);
            boolean containerRow = (subElement.getTagName().equalsIgnoreCase(
                    "tr") && subElement.getParentElement() == container
                    .getElement());
            if (containerRow) {
                /*
                 * Wanted SubPart is row that is a child of containers root to
                 * get indices, we use a cell that is a child of this row
                 */
                subElement = subElement.getFirstChildElement();
            }

            Cell cell = container.getCell(subElement);
            if (cell != null) {
                // Skip the column index if subElement was a child of root
                return containerType.get(i) + "[" + cell.getRow()
                        + (containerRow ? "]" : "][" + cell.getColumn() + "]");
            }
        }
        return null;
    }

    private String getSubPartNameSpacer(Element subElement) {
        return body.spacerContainer.getSubPartName(subElement);
    }

    private void logWarning(String message) {
        getLogger().warning(message);
    }

    /**
     * This is an internal method for calculating minimum width for Column
     * resize.
     * 
     * @return minimum width for column
     */
    public double getMinCellWidth(int colIndex) {
        return columnConfiguration.getMinCellWidth(colIndex);
    }
}
