package org.openstreetmap.josm.plugins.markseen;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import javax.swing.JToggleButton;
import javax.swing.BoundedRangeModel;
import javax.swing.DefaultBoundedRangeModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import static org.openstreetmap.josm.tools.I18n.tr;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.ToggleAction;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.NavigatableComponent;
import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;
import org.openstreetmap.josm.tools.ColorHelper;
import org.openstreetmap.josm.tools.Shortcut;


public class MarkSeenPlugin extends Plugin implements NavigatableComponent.ZoomChangeListener, ChangeListener {
    private class MarkSeenToggleRecordAction extends ToggleAction {
        public MarkSeenToggleRecordAction() {
            super(tr("Record seen areas"),
                null, /* no icon */
                tr("Mark seen areas of map in MarkSeen viewer"),
                Shortcut.registerShortcut("menu:view:markseenrecord", tr("Toggle MarkSeen recording"), KeyEvent.VK_M, Shortcut.CTRL_SHIFT),
                false /* register toolbar */
            );
            this.setSelected(Main.pref.getBoolean("markseen.recordActive", false));
        }

//         @Override
//         protected void updateEnabledState() {
//             setEnabled(Main.getLayerManager().getEditLayer() != null);
//         }

        @Override
        public void actionPerformed(ActionEvent e) {
            // TODO possibly find a way to allow toggling while "disabled"
            toggleSelectedState(e);
            notifySelectedState();
            MarkSeenPlugin.this.zoomChanged();
            Main.pref.put("markseen.recordActive", this.isSelected());
        }
    }

    private final QuadTreeMeta quadTreeMeta;
    private final ToggleAction recordAction;
    private final BoundedRangeModel recordMinZoom;

    public MarkSeenPlugin(PluginInformation info) {
        super(info);
        this.quadTreeMeta = new QuadTreeMeta(
            Main.pref.getInteger("markseen.quadTreeTileSize", 256),
            ColorHelper.html2color(Main.pref.get("color.markseen.seenarea", "#ff00ff")),
            Main.pref.getDouble("markseen.maskOpacity", 0.5)
        );
        this.recordAction = new MarkSeenToggleRecordAction();
        this.recordMinZoom = new DefaultBoundedRangeModel(
            Main.pref.getInteger("markseen.recordMinZoom", 11),
            0,
            4,
            26
        );
        this.recordMinZoom.addChangeListener(this);
    }

    @Override
    public void mapFrameInitialized(MapFrame oldFrame, MapFrame newFrame) {
        if (oldFrame == null && newFrame != null) {
            newFrame.addToggleDialog(new MarkSeenDialog(this.quadTreeMeta, this.recordAction, this.recordMinZoom));
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

            this.updateRecordActionEnabled(currentBounds);

            if (this.recordAction.isEnabled() && this.recordAction.isSelected()) {
                this.quadTreeMeta.requestSeenBoundsMark(currentBounds, Main.pref.getDouble("markseen.minTilesAcross", 4.));
            }
        }
    }

    protected void updateRecordActionEnabled(Bounds currentBounds) {
        final int recordMaxSpan = 1<<this.recordMinZoom.getValue();
        this.recordAction.setEnabled(currentBounds.getMin().greatCircleDistance(currentBounds.getMax()) < recordMaxSpan);
    }

    @Override
    public void stateChanged(ChangeEvent e) {
        if (e.getSource() == this.recordMinZoom) {
            if (Main.isDisplayingMapView()) {
                MapView mv = Main.map.mapView;
                final Bounds currentBounds = new Bounds(
                        mv.getLatLon(0, mv.getHeight()),
                        mv.getLatLon(mv.getWidth(), 0)
                );
                this.updateRecordActionEnabled(currentBounds);
            }
            Main.pref.putInteger("markseen.recordMinZoom", this.recordMinZoom.getValue());
        } else {
            throw new RuntimeException("Unknown/unexpected ChangeEvent source");
        }
    }
}
