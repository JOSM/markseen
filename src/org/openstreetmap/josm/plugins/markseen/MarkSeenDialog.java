package org.openstreetmap.josm.plugins.markseen;

import java.awt.BorderLayout;

import javax.swing.BorderFactory;
import javax.swing.BoundedRangeModel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import static org.openstreetmap.josm.tools.I18n.tr;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Collections;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.ToggleAction;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.NavigatableComponent;
import org.openstreetmap.josm.gui.bbox.BBoxChooser;
import org.openstreetmap.josm.gui.dialogs.ToggleDialog;
import org.openstreetmap.josm.tools.ImageProvider;

/**
 * Essentially a modified copy of {@link MinimapDialog} rather than an subclass because it's keen on its privacy.
 */
public class MarkSeenDialog extends ToggleDialog implements NavigatableComponent.ZoomChangeListener, PropertyChangeListener, ChangeListener {
    private MarkSeenSlippyMapBBoxChooser slippyMap;
    private boolean skipZoomEvents;

    private final QuadTreeMeta quadTreeMeta;
    private final JSlider recordMinZoomSlider;
    private final JToggleButton recordToggleButton;
    private final JToolBar toolBar;
    private final JPanel innerPanel;
    private final JToggleButton showToolBarToggleButton;
    private boolean initialized = false;

    /**
     * Constructs a new {@code MarkSeenDialog}.
     */
    public MarkSeenDialog(QuadTreeMeta quadTreeMeta_, ToggleAction recordAction_, BoundedRangeModel recordMinZoom_) {
        super(tr("MarkSeen Viewer"), "minimap", tr("Shows viewed map areas on a familiar small map"), null, 150);
        this.quadTreeMeta = quadTreeMeta_;
        this.recordMinZoomSlider = new JSlider(recordMinZoom_);
        this.recordToggleButton = new JToggleButton(recordAction_);
        this.toolBar = new JToolBar();
        this.innerPanel = new JPanel(new BorderLayout());

        final boolean showToolBarInitially = Main.pref.getBoolean("markseen.dialog.showToolBar", true);
        this.showToolBarToggleButton = new JToggleButton(ImageProvider.get("misc", "buttonshow"), showToolBarInitially);
        this.showToolBarToggleButton.setToolTipText(tr("Toggle toolbar visbility"));
        this.showToolBarToggleButton.setBorder(BorderFactory.createEmptyBorder());
        this.showToolBarToggleButton.getModel().addChangeListener(this);
        this.updateShowToolBarToggleButton();

        this.slippyMap = new MarkSeenSlippyMapBBoxChooser(this.quadTreeMeta);
    }
    private synchronized void initialize() {
        if (!this.initialized) {
            slippyMap.setSizeButtonVisible(false);
            slippyMap.addPropertyChangeListener(BBoxChooser.BBOX_PROP, this);
            createLayout(innerPanel, false, Collections.emptyList());
            this.toolBar.add(this.recordToggleButton);
            this.toolBar.add(this.recordMinZoomSlider);
            this.innerPanel.add(this.toolBar, BorderLayout.NORTH);
            this.innerPanel.add(this.slippyMap, BorderLayout.CENTER);

            this.titleBar.add(this.showToolBarToggleButton, this.titleBar.getComponentCount()-2);

            this.initialized = true;
        }
    }
    @Override
    public void showDialog() {
        initialize();
        NavigatableComponent.addZoomChangeListener(this);
        super.showDialog();
    }
    @Override
    public void hideDialog() {
        NavigatableComponent.removeZoomChangeListener(this);
        super.hideDialog();
    }
    @Override
    public void zoomChanged() {
        if (!skipZoomEvents && Main.isDisplayingMapView()) {
            MapView mv = Main.map.mapView;
            final Bounds currentBounds = new Bounds(
                    mv.getLatLon(0, mv.getHeight()),
                    mv.getLatLon(mv.getWidth(), 0)
            );
            skipZoomEvents = true;
            slippyMap.setBoundingBox(currentBounds);
            slippyMap.zoomOut(); // to give a better overview
            skipZoomEvents = false;
        }
    }
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (!skipZoomEvents) {
            skipZoomEvents = true;
            Main.map.mapView.zoomTo(slippyMap.getBoundingBox());
            skipZoomEvents = false;
        }
    }

    private void updateShowToolBarToggleButton() {
        final boolean isSelected = this.showToolBarToggleButton.getModel().isSelected();
        this.showToolBarToggleButton.setIcon(
            ImageProvider.get("misc", isSelected ? "buttonshow" : "buttonhide")
        );
        this.toolBar.setVisible(isSelected);
    }

    @Override
    public void stateChanged(ChangeEvent e) {
        if (e.getSource() == this.showToolBarToggleButton.getModel()) {
            this.updateShowToolBarToggleButton();
            Main.pref.put("markseen.dialog.showToolBar", this.showToolBarToggleButton.getModel().isSelected());
        } else {
            throw new RuntimeException("Unknown/unexpected ChangeEvent source");
        }
    }
}
