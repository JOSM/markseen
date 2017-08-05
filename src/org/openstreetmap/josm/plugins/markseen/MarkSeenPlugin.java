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
import org.openstreetmap.josm.tools.ColorHelper;


public class MarkSeenPlugin extends Plugin implements NavigatableComponent.ZoomChangeListener {
    private final QuadTreeMeta quadTreeMeta;

    public MarkSeenPlugin(PluginInformation info) {
        super(info);
        this.quadTreeMeta = new QuadTreeMeta(
            Main.pref.getInteger("markseen.quadTreeTileSize", 256),
            ColorHelper.html2color(Main.pref.get("color.markseen.maskColor", "#ff00ff")),
            Main.pref.getDouble("markseen.maskOpacity", 0.5)
        );
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

            this.quadTreeMeta.requestSeenBoundsMark(currentBounds, Main.pref.getDouble("markseen.minTilesAcross", 4.));
        }
    }
}
