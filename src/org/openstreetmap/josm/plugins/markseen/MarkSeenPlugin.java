package org.openstreetmap.josm.plugins.markseen;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.NavigatableComponent;
import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;


public class MarkSeenPlugin extends Plugin implements NavigatableComponent.ZoomChangeListener {
    private final QuadTreeMeta quadTreeMeta;

    public MarkSeenPlugin(PluginInformation info) {
        super(info);
        this.quadTreeMeta = new QuadTreeMeta(256);
    }

    @Override
    public void mapFrameInitialized(MapFrame oldFrame, MapFrame newFrame) {
        if (oldFrame == null && newFrame != null) {
            newFrame.addToggleDialog(new MarkSeenDialog(this.quadTreeMeta));
        }
        NavigatableComponent.addZoomChangeListener(this);
    }

    @Override
    public void zoomChanged() {
        if (Main.isDisplayingMapView()) {
            MapView mv = Main.map.mapView;
            final Bounds currentBounds = new Bounds(
                    mv.getLatLon(0, mv.getHeight()),
                    mv.getLatLon(mv.getWidth(), 0)
            );

            this.quadTreeMeta.quadTreeRWLock.writeLock().lock();
            this.quadTreeMeta.quadTreeRoot.markBoundsSeen(currentBounds, 4);
            this.quadTreeMeta.quadTreeRWLock.writeLock().unlock();
        }
    }
}
