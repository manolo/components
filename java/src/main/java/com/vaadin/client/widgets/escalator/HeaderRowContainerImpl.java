package com.vaadin.client.widgets.escalator;

import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.dom.client.TableSectionElement;

class HeaderRowContainerImpl extends AbstractStaticRowContainer {

    public HeaderRowContainerImpl(Escalator escalator, final TableSectionElement headElement) {
        super(escalator, headElement);
    }

    @Override
    protected void sectionHeightCalculated() {
        double heightOfSection = getHeightOfSection();
        this.escalator.bodyElem.getStyle().setMarginTop(heightOfSection, Unit.PX);
        this.escalator.spacerDecoContainer.getStyle().setMarginTop(heightOfSection,
                Unit.PX);
        this.escalator.verticalScrollbar.getElement().getStyle()
                .setTop(heightOfSection, Unit.PX);
        this.escalator.headerDeco.getStyle().setHeight(heightOfSection, Unit.PX);
    }

    @Override
    protected String getCellElementTagName() {
        return "th";
    }

    @Override
    public void setStylePrimaryName(String primaryStyleName) {
        super.setStylePrimaryName(primaryStyleName);
        JsniUtil.setStylePrimaryName(root, primaryStyleName + "-header");
    }
}