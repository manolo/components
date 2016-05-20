package com.vaadin.client.widgets.grid;

import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.ui.Widget;

import com.vaadin.client.WidgetUtil;
import com.vaadin.client.widget.escalator.Spacer;
import com.vaadin.client.widget.escalator.SpacerUpdater;
import com.vaadin.client.widget.grid.HeightAwareDetailsGenerator;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

class GridSpacerUpdater<T> implements SpacerUpdater {

    /**
     * 
     */
    private final Grid<T> grid;

    /**
     * @param grid
     */
    GridSpacerUpdater(Grid<T> grid) {
        this.grid = grid;
    }

    private static final String STRIPE_CLASSNAME = "stripe";

    private final Map<Element, Widget> elementToWidgetMap = new HashMap<Element, Widget>();

    @Override
    public void init(Spacer spacer) {
        initTheming(spacer);

        int rowIndex = spacer.getRow();

        Widget detailsWidget = null;
        try {
            detailsWidget = this.grid.detailsGenerator.getDetails(rowIndex);
        } catch (Throwable e) {
            Grid.getLogger().log(
                    Level.SEVERE,
                    "Exception while generating details for row "
                            + rowIndex, e);
        }

        final double spacerHeight;
        Element spacerElement = spacer.getElement();
        if (detailsWidget == null) {
            spacerElement.removeAllChildren();
            spacerHeight = Grid.DETAILS_ROW_INITIAL_HEIGHT;
        } else {
            Element element = detailsWidget.getElement();
            spacerElement.appendChild(element);
            Grid.setParent(detailsWidget, grid);
            Widget previousWidget = elementToWidgetMap.put(element,
                    detailsWidget);

            assert previousWidget == null : "Overwrote a pre-existing widget on row "
                    + rowIndex + " without proper removal first.";

            /*
             * Once we have the content properly inside the DOM, we should
             * re-measure it to make sure that it's the correct height.
             * 
             * This is rather tricky, since the row (tr) will get the
             * height, but the spacer cell (td) has the borders, which
             * should go on top of the previous row and next row.
             */
            double contentHeight;
            if (this.grid.detailsGenerator instanceof HeightAwareDetailsGenerator) {
                HeightAwareDetailsGenerator sadg = (HeightAwareDetailsGenerator) this.grid.detailsGenerator;
                contentHeight = sadg.getDetailsHeight(rowIndex);
            } else {
                contentHeight = WidgetUtil
                        .getRequiredHeightBoundingClientRectDouble(element);
            }
            double borderTopAndBottomHeight = WidgetUtil
                    .getBorderTopAndBottomThickness(spacerElement);
            double measuredHeight = contentHeight
                    + borderTopAndBottomHeight;
            assert this.grid.getElement().isOrHasChild(spacerElement) : "The spacer element wasn't in the DOM during measurement, but was assumed to be.";
            spacerHeight = measuredHeight;
        }

        this.grid.getEscalator().getBody().setSpacer(rowIndex, spacerHeight);
    }

    @Override
    public void destroy(Spacer spacer) {
        Element spacerElement = spacer.getElement();

        assert this.grid.getElement().isOrHasChild(spacerElement) : "Trying "
                + "to destroy a spacer that is not connected to this "
                + "Grid's DOM. (row: " + spacer.getRow() + ", element: "
                + spacerElement + ")";

        Widget detailsWidget = elementToWidgetMap.remove(spacerElement
                .getFirstChildElement());

        if (detailsWidget != null) {
            /*
             * The widget may be null here if the previous generator
             * returned a null widget.
             */

            assert spacerElement.getFirstChild() != null : "The "
                    + "details row to destroy did not contain a widget - "
                    + "probably removed by something else without "
                    + "permission? (row: " + spacer.getRow()
                    + ", element: " + spacerElement + ")";

            Grid.setParent(detailsWidget, null);
            spacerElement.removeAllChildren();
        }
    }

    private void initTheming(Spacer spacer) {
        Element spacerRoot = spacer.getElement();

        if (spacer.getRow() % 2 == 1) {
            spacerRoot.getParentElement().addClassName(STRIPE_CLASSNAME);
        } else {
            spacerRoot.getParentElement().removeClassName(STRIPE_CLASSNAME);
        }
    }

}