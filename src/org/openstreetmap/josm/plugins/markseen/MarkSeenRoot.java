package org.openstreetmap.josm.plugins.markseen;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import javax.swing.BoundedRangeModel;
import javax.swing.DefaultBoundedRangeModel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import static org.openstreetmap.josm.tools.I18n.tr;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.actions.ToggleAction;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.gui.MainMenu;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.NavigatableComponent;
import org.openstreetmap.josm.tools.ColorHelper;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Shortcut;


public class MarkSeenRoot implements NavigatableComponent.ZoomChangeListener, ChangeListener {
    private static final String recordActionBaseToolTipText = tr("Mark seen areas of map in MarkSeen viewer");

    private class MarkSeenToggleRecordAction extends ToggleAction implements PropertyChangeListener {
        public MarkSeenToggleRecordAction() {
            super(tr("Record seen areas"),
                new ImageProvider("icons/24x24/record.png"),
                recordActionBaseToolTipText,
                Shortcut.registerShortcut(
                    "menu:view:markseen:record",
                    tr("Toggle MarkSeen recording"),
                    KeyEvent.CHAR_UNDEFINED,
                    Shortcut.NONE
                ),
                true, /* register toolbar */
                "MarkSeen/record",
                false
            );
            this.setSelected(Main.pref.getBoolean("markseen.recordActive", false));
            this.addPropertyChangeListener(this);
        }

        @Override
        public void propertyChange(PropertyChangeEvent e) {
            if (e.getPropertyName().equals("enabled") && e.getSource() == this) {
                String r = recordActionBaseToolTipText;
                if (!(Boolean)e.getNewValue()) {
                    r += tr(" (disabled while viewport larger than set limit)");
                }
                this.setTooltip(r);
            }
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            // TODO possibly find a way to allow toggling while "disabled"
            toggleSelectedState(e);
            notifySelectedState();
            MarkSeenRoot.this.zoomChanged();
            Main.pref.putBoolean("markseen.recordActive", this.isSelected());
        }
    }

    private class MarkSeenClearAction extends JosmAction {
        public MarkSeenClearAction() {
            super(
                tr("Clear"),
                new ImageProvider("icons/24x24/clear.png"),
                tr("Clear record of seen areas"),
                Shortcut.registerShortcut(
                    "menu:view:markseen:clear",
                    tr("Clear MarkSeen areas"),
                    KeyEvent.CHAR_UNDEFINED,
                    Shortcut.NONE
                ),
                true, /* register toolbar */
                "MarkSeen/clear",
                false
            );
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            MarkSeenRoot.this.quadTreeMeta.requestClear();
        }
    }

    private class MarkSeenSetMaxViewportAction extends JosmAction {
        public MarkSeenSetMaxViewportAction() {
            super(
                tr("Set max viewport size from current"),
                new ImageProvider("icons/24x24/setmaxviewportfromcurrent.png"),
                tr("Set current viewport size as the maximum for recording as seen"),
                Shortcut.registerShortcut(
                    "menu:view:markseen:setmaxviewportfromcurrent",
                    tr("Set max viewport size from current"),
                    KeyEvent.CHAR_UNDEFINED,
                    Shortcut.NONE
                ),
                true, /* register toolbar */
                "MarkSeen/SetMaxViewportFromCurrent",
                false
            );
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            final double currentViewportSize = viewportSizeFromBounds(MarkSeenRoot.this.getCurrentBounds());
            final int newMinZoom = (int) Math.ceil(Math.log(currentViewportSize) / Math.log(2));

            MarkSeenRoot.this.recordMinZoom.setValue(newMinZoom);
        }
    }

    private final QuadTreeMeta quadTreeMeta;
    private final JosmAction clearAction;
    private final ToggleAction recordAction;
    private final BoundedRangeModel recordMinZoom;
    private final JosmAction setMaxViewportAction;

    private final JMenu markSeenMainMenu;

    private final JMenuItem mainMenuRecordItem;
    private final JMenuItem mainMenuClearItem;
    private final JMenuItem mainMenuSetMaxViewportItem;

    private final static int recordMinZoomMin = 4;
    private final static int recordMinZoomMax = 26;

    private MarkSeenDialog dialog;

    public MarkSeenRoot() {
        this.quadTreeMeta = new QuadTreeMeta(
            Main.pref.getInt("markseen.quadTreeTileSize", 256),
            ColorHelper.html2color(Main.pref.get("color.markseen.seenarea", "#ff00ff")),
            Main.pref.getDouble("markseen.maskOpacity", 0.5)
        );
        this.clearAction = new MarkSeenClearAction();
        this.recordAction = new MarkSeenToggleRecordAction();
        this.setMaxViewportAction = new MarkSeenSetMaxViewportAction();
        this.recordMinZoom = new DefaultBoundedRangeModel(
            Math.max(recordMinZoomMin, Math.min(Main.pref.getInt("markseen.recordMinZoom", 11), recordMinZoomMax)),
            0,
            recordMinZoomMin,
            recordMinZoomMax
        );
        this.recordMinZoom.addChangeListener(this);

        this.markSeenMainMenu = new JMenu(tr("MarkSeen"));
        this.markSeenMainMenu.setIcon(new ImageProvider("icons/16x16/markseen.png").get());
        this.mainMenuRecordItem = new JCheckBoxMenuItem(this.recordAction);
        this.mainMenuRecordItem.setAccelerator(this.recordAction.getShortcut().getKeyStroke());
        this.recordAction.addButtonModel(this.mainMenuRecordItem.getModel());
        this.markSeenMainMenu.add(this.mainMenuRecordItem);
        this.mainMenuClearItem = MainMenu.add(this.markSeenMainMenu, this.clearAction, false);
        this.mainMenuSetMaxViewportItem = MainMenu.add(this.markSeenMainMenu, this.setMaxViewportAction, false);
    }

    public void mapFrameInitialized(MapFrame oldFrame, MapFrame newFrame) {
        if (oldFrame == null && newFrame != null) {
            MainMenu mainMenu = MainApplication.getMenu(); 
            this.dialog = new MarkSeenDialog(this.quadTreeMeta, this.clearAction, this.recordAction, this.recordMinZoom);
            newFrame.addToggleDialog(this.dialog);

            NavigatableComponent.addZoomChangeListener(this);

            mainMenu.viewMenu.add(this.markSeenMainMenu, Math.min(2, mainMenu.viewMenu.getItemCount()));

            this.updateRecordActionEnabled(getCurrentBounds());
        }
    }

    @Override
    public void zoomChanged() {
        if (MainApplication.isDisplayingMapView()) {
            final Bounds currentBounds = getCurrentBounds();

            this.updateRecordActionEnabled(currentBounds);

            if (this.recordAction.isEnabled() && this.recordAction.isSelected()) {
                this.quadTreeMeta.requestSeenBoundsMark(currentBounds, Main.pref.getDouble("markseen.minTilesAcross", 4.));
            }
        }
    }

    private static Bounds getCurrentBounds() {
        MapView mv = MainApplication.getMap().mapView;
        return new Bounds(
            mv.getLatLon(0, mv.getHeight()),
            mv.getLatLon(mv.getWidth(), 0)
        );
    }

    protected static double viewportSizeFromBounds(final Bounds bounds) {
        return bounds.getMin().greatCircleDistance(bounds.getMax());
    }

    protected void updateRecordActionEnabled(final Bounds currentBounds) {
        if (this.recordMinZoom.getValue() == this.recordMinZoom.getMaximum()) {
            // "infinity" setting - always recordable
            this.recordAction.setEnabled(true);
        } else {
            final int recordMaxSpan = 1<<this.recordMinZoom.getValue();
            this.recordAction.setEnabled(viewportSizeFromBounds(currentBounds) < recordMaxSpan);
        }
    }

    @Override
    public void stateChanged(ChangeEvent e) {
        if (e.getSource() == this.recordMinZoom) {
            if (MainApplication.isDisplayingMapView()) {
                this.updateRecordActionEnabled(getCurrentBounds());
            }
            Main.pref.putInt("markseen.recordMinZoom", this.recordMinZoom.getValue());
        } else {
            throw new RuntimeException("Unknown/unexpected ChangeEvent source");
        }
    }
}
