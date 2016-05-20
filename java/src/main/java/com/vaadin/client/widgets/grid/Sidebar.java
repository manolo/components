package com.vaadin.client.widgets.grid;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.core.shared.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.event.logical.shared.CloseEvent;
import com.google.gwt.event.logical.shared.CloseHandler;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HasEnabled;
import com.google.gwt.user.client.ui.MenuBar;
import com.google.gwt.user.client.ui.MenuItem;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.Widget;

import com.vaadin.client.WidgetUtil;
import com.vaadin.client.widget.escalator.RowContainer;
import com.vaadin.client.widgets.Overlay;

/**
 * Sidebar displaying toggles for hidable columns and custom widgets
 * provided by the application.
 * <p>
 * The button for opening the sidebar is automatically visible inside the
 * grid, if it contains any column hiding options or custom widgets. The
 * column hiding toggles and custom widgets become visible once the sidebar
 * has been opened.
 * 
 * @since 7.5.0
 */
class Sidebar extends Composite implements HasEnabled {

    private final ClickHandler openCloseButtonHandler = new ClickHandler() {

        @Override
        public void onClick(ClickEvent event) {
            if (!isOpen()) {
                open();
            } else {
                close();
            }
        }
    };

    private final FlowPanel rootContainer;

    private final FlowPanel content;

    final MenuBar menuBar;

    private final Button openCloseButton;

    private final Grid<?> grid;

    private Overlay overlay;

    Sidebar(Grid<?> grid) {
        this.grid = grid;

        rootContainer = new FlowPanel();
        initWidget(rootContainer);

        openCloseButton = new Button();

        openCloseButton.addClickHandler(openCloseButtonHandler);

        rootContainer.add(openCloseButton);

        content = new FlowPanel() {
            @Override
            public boolean remove(Widget w) {
                // Check here to catch child.removeFromParent() calls
                boolean removed = super.remove(w);
                if (removed) {
                    updateVisibility();
                }

                return removed;
            }
        };

        createOverlay();

        menuBar = new MenuBar(true) {

            @Override
            public MenuItem insertItem(MenuItem item, int beforeIndex)
                    throws IndexOutOfBoundsException {
                if (getParent() == null) {
                    content.insert(this, 0);
                    updateVisibility();
                }
                return super.insertItem(item, beforeIndex);
            }

            @Override
            public void removeItem(MenuItem item) {
                super.removeItem(item);
                if (getItems().isEmpty()) {
                    menuBar.removeFromParent();
                }
            }

            @Override
            public void onBrowserEvent(Event event) {
                // selecting a item with enter will lose the focus and
                // selected item, which means that further keyboard
                // selection won't work unless we do this:
                if (event.getTypeInt() == Event.ONKEYDOWN
                        && event.getKeyCode() == KeyCodes.KEY_ENTER) {
                    final MenuItem item = getSelectedItem();
                    super.onBrowserEvent(event);
                    Scheduler.get().scheduleDeferred(
                            new ScheduledCommand() {

                                @Override
                                public void execute() {
                                    selectItem(item);
                                    focus();
                                }
                            });

                } else {
                    super.onBrowserEvent(event);
                }
            }

        };
        KeyDownHandler keyDownHandler = new KeyDownHandler() {

            @Override
            public void onKeyDown(KeyDownEvent event) {
                if (event.getNativeKeyCode() == KeyCodes.KEY_ESCAPE) {
                    close();
                }
            }
        };
        openCloseButton.addDomHandler(keyDownHandler,
                KeyDownEvent.getType());
        menuBar.addDomHandler(keyDownHandler, KeyDownEvent.getType());
    }

    /**
     * Creates and initializes the overlay.
     */
    private void createOverlay() {
        overlay = GWT.create(Overlay.class);
        overlay.setOwner(grid);
        overlay.setAutoHideEnabled(true);
        overlay.addStyleDependentName("popup");
        overlay.add(content);
        overlay.addAutoHidePartner(rootContainer.getElement());
        overlay.addCloseHandler(new CloseHandler<PopupPanel>() {
            @Override
            public void onClose(CloseEvent<PopupPanel> event) {
                removeStyleName("open");
                addStyleName("closed");
            }
        });
    }

    /**
     * Opens the sidebar if not yet opened. Opening the sidebar has no
     * effect if it is empty.
     */
    public void open() {
        if (!isOpen() && isInDOM()) {
            addStyleName("open");
            removeStyleName("closed");
            overlay.showRelativeTo(rootContainer);
        }
    }

    /**
     * Closes the sidebar if not yet closed.
     */
    public void close() {
        overlay.hide();
    }

    /**
     * Returns whether the sidebar is open or not.
     * 
     * @return <code>true</code> if open, <code>false</code> if not
     */
    public boolean isOpen() {
        return overlay != null && overlay.isShowing();
    }

    @Override
    public void setStylePrimaryName(String styleName) {
        super.setStylePrimaryName(styleName);
        overlay.setStylePrimaryName(styleName);
        content.setStylePrimaryName(styleName + "-content");
        openCloseButton.setStylePrimaryName(styleName + "-button");
        if (isOpen()) {
            addStyleName("open");
            removeStyleName("closed");
        } else {
            removeStyleName("open");
            addStyleName("closed");
        }
    }

    @Override
    public void addStyleName(String style) {
        super.addStyleName(style);
        overlay.addStyleName(style);
    }

    @Override
    public void removeStyleName(String style) {
        super.removeStyleName(style);
        overlay.removeStyleName(style);
    }

    private void setHeightToHeaderCellHeight() {
        RowContainer header = grid.getEscalator().getHeader();
        if (header.getRowCount() == 0
                || !header.getRowElement(0).hasChildNodes()) {
            Grid.getLogger()
                    .info("No header cell available when calculating sidebar button height");
            openCloseButton.setHeight(header.getDefaultRowHeight() + "px");

            return;
        }

        Element firstHeaderCell = header.getRowElement(0)
                .getFirstChildElement();
        double height = WidgetUtil
                .getRequiredHeightBoundingClientRectDouble(firstHeaderCell)
                - (WidgetUtil.measureVerticalBorder(getElement()) / 2);
        openCloseButton.setHeight(height + "px");
    }

    private void updateVisibility() {
        final boolean hasWidgets = content.getWidgetCount() > 0;
        final boolean isVisible = isInDOM();
        if (isVisible && !hasWidgets) {
            Grid.setParent(this, null);
            getElement().removeFromParent();
        } else if (!isVisible && hasWidgets) {
            close();
            grid.getElement().appendChild(getElement());
            Grid.setParent(this, grid);
            // border calculation won't work until attached
            setHeightToHeaderCellHeight();
        }
    }

    boolean isInDOM() {
        return getParent() != null;
    }

    @Override
    protected void onAttach() {
        super.onAttach();
        // make sure the button will get correct height if the button should
        // be visible when the grid is rendered the first time.
        Scheduler.get().scheduleDeferred(new ScheduledCommand() {

            @Override
            public void execute() {
                setHeightToHeaderCellHeight();
            }
        });
    }

    @Override
    public boolean isEnabled() {
        return openCloseButton.isEnabled();
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (!enabled && isOpen()) {
            close();
        }

        openCloseButton.setEnabled(enabled);
    }
}