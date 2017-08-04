package org.openstreetmap.josm.plugins.markseen;

import static org.openstreetmap.josm.tools.I18n.tr;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Collections;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.NavigatableComponent;
import org.openstreetmap.josm.gui.bbox.BBoxChooser;
import org.openstreetmap.josm.gui.dialogs.ToggleDialog;

/**
 * Essentially a modified copy of {@link MinimapDialog} rather than an subclass because it's keen on its privacy.
 */
public class MarkSeenDialog extends ToggleDialog implements NavigatableComponent.ZoomChangeListener, PropertyChangeListener {
    private MarkSeenSlippyMapBBoxChooser slippyMap;
    private boolean skipEvents;
    private final QuadTreeMeta quadTreeMeta;
    /**
     * Constructs a new {@code MarkSeenDialog}.
     */
    public MarkSeenDialog(QuadTreeMeta quadTreeMeta_) {
        super(tr("MarkSeen Viewer"), "minimap", tr("Shows viewed map areas on a familiar small map"), null, 150);
        this.quadTreeMeta = quadTreeMeta_;
    }
    private synchronized void initialize() {
        if (slippyMap != null) {
            return;
        }
        slippyMap = new MarkSeenSlippyMapBBoxChooser(this.quadTreeMeta);
        createLayout(slippyMap, false, Collections.emptyList());
        slippyMap.setSizeButtonVisible(false);
        slippyMap.addPropertyChangeListener(BBoxChooser.BBOX_PROP, this);
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
        if (!skipEvents && Main.isDisplayingMapView()) {
            MapView mv = Main.map.mapView;
            final Bounds currentBounds = new Bounds(
                    mv.getLatLon(0, mv.getHeight()),
                    mv.getLatLon(mv.getWidth(), 0)
            );
            skipEvents = true;
            slippyMap.markBoundsSeen(currentBounds, 4);
            slippyMap.setBoundingBox(currentBounds);
            slippyMap.zoomOut(); // to give a better overview
            skipEvents = false;
        }
    }
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (!skipEvents) {
            skipEvents = true;
            Main.map.mapView.zoomTo(slippyMap.getBoundingBox());
            skipEvents = false;
        }
    }
}
