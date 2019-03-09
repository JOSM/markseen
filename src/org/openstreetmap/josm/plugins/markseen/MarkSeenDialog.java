package org.openstreetmap.josm.plugins.markseen;

import java.awt.BorderLayout;
import java.awt.Dimension;

import javax.swing.BorderFactory;
import javax.swing.BoundedRangeModel;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JButton;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import java.util.Collections;
import java.util.Hashtable;

import static org.openstreetmap.josm.tools.I18n.tr;
import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.actions.ToggleAction;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.NavigatableComponent;
import org.openstreetmap.josm.gui.bbox.BBoxChooser;
import org.openstreetmap.josm.gui.dialogs.ToggleDialog;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.ImageProvider;

/**
 * Essentially a modified copy of {@link MinimapDialog} rather than an subclass because it's keen on its privacy.
 */
public class MarkSeenDialog extends ToggleDialog implements NavigatableComponent.ZoomChangeListener, PropertyChangeListener, ChangeListener {
    private boolean skipZoomEvents;

    private final MarkSeenSlippyMapBBoxChooser slippyMap;
    private final QuadTreeMeta quadTreeMeta;
    private final JSlider recordMinZoomSlider;
    private final JLabel recordMinZoomSliderLabel;
    private final JToggleButton recordToggleButton;
    private final JButton clearButton;
    private final JToolBar toolBar;
    private final JPanel innerPanel;
    private final JToggleButton showToolBarToggleButton;
    private boolean initialized = false;

    private static final String recordMinZoomSliderToolTip = tr("Do not record area as seen when viewport is larger than this");

    /**
     * Constructs a new {@code MarkSeenDialog}.
     */
    public MarkSeenDialog(
        QuadTreeMeta quadTreeMeta_,
        JosmAction clearAction_,
        ToggleAction recordAction_,
        BoundedRangeModel recordMinZoom_
    ) {
        super(tr("MarkSeen Viewer"), "markseen.png", tr("Shows viewed map areas on a familiar small map"), null, 150);
        this.quadTreeMeta = quadTreeMeta_;
        this.recordMinZoomSlider = new JSlider(recordMinZoom_);
        this.recordMinZoomSliderLabel = new JLabel(tr("Max viewport size"));
        this.recordToggleButton = new JToggleButton(recordAction_);
        this.recordToggleButton.setText(null);
        this.clearButton = new JButton(clearAction_);
        this.clearButton.setText(null);
        this.toolBar = new JToolBar();
        this.innerPanel = new JPanel(new BorderLayout());

        this.recordMinZoomSlider.getModel().addChangeListener(this);

        final boolean showToolBarInitially = Config.getPref().getBoolean("markseen.dialog.showToolBar", true);
        this.showToolBarToggleButton = new JToggleButton(ImageProvider.get("misc", "buttonshow"), showToolBarInitially);
        this.showToolBarToggleButton.setToolTipText(tr("Toggle toolbar visbility"));
        this.showToolBarToggleButton.setBorder(BorderFactory.createEmptyBorder());
        this.showToolBarToggleButton.getModel().addChangeListener(this);
        this.updateShowToolBarToggleButton();

        this.slippyMap = new MarkSeenSlippyMapBBoxChooser(this.quadTreeMeta);
    }
    private synchronized void initialize() {
        if (!this.initialized) {
            this.slippyMap.setSizeButtonVisible(false);
            this.slippyMap.addPropertyChangeListener(BBoxChooser.BBOX_PROP, this);

            this.recordToggleButton.addPropertyChangeListener("enabled", this);
            // set initial state
            this.slippyMap.setShowBBoxCrossed(!this.recordToggleButton.isEnabled());

            final Hashtable<Integer, JLabel> labelTable = new Hashtable<Integer, JLabel>();
            labelTable.put(4, new JLabel(tr("16m")));
            labelTable.put(10, new JLabel(tr("1km")));
            labelTable.put(19, new JLabel(tr("500km")));
            labelTable.put(this.recordMinZoomSlider.getModel().getMaximum(), new JLabel(tr("\u221E")));
            this.recordMinZoomSlider.setLabelTable(labelTable);

            this.recordMinZoomSlider.setPaintLabels(true);
            this.recordMinZoomSlider.setSnapToTicks(true);
            this.recordMinZoomSlider.setMinimumSize(
                new Dimension(100, (int) this.recordMinZoomSlider.getMinimumSize().getHeight())
            );
            this.recordMinZoomSlider.setToolTipText(recordMinZoomSliderToolTip);
            this.recordMinZoomSliderLabel.setLabelFor(this.recordMinZoomSlider);
            this.recordMinZoomSliderLabel.setMinimumSize(
                new Dimension(64, (int) this.recordMinZoomSliderLabel.getMinimumSize().getHeight())
            );
            this.recordMinZoomSliderLabel.setToolTipText(recordMinZoomSliderToolTip);

            createLayout(innerPanel, false, Collections.emptyList());
            this.toolBar.add(this.recordToggleButton);
            this.toolBar.add(this.clearButton);
            this.toolBar.addSeparator();
            this.toolBar.add(this.recordMinZoomSliderLabel);
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
        this.zoomChanged();
    }
    @Override
    public void hideDialog() {
        NavigatableComponent.removeZoomChangeListener(this);
        super.hideDialog();
    }
    @Override
    public void zoomChanged() {
        if (!skipZoomEvents && MainApplication.isDisplayingMapView()) {
            MapView mv = MainApplication.getMap().mapView;
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
        final Object source = evt.getSource();
        if (source == this.slippyMap) {
            if (!skipZoomEvents) {
                skipZoomEvents = true;
                MainApplication.getMap().mapView.zoomTo(slippyMap.getBoundingBox());
                skipZoomEvents = false;
            }
        } else if (source == this.recordToggleButton) {
            this.slippyMap.setShowBBoxCrossed(!this.recordToggleButton.isEnabled());
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
            Config.getPref().putBoolean("markseen.dialog.showToolBar", this.showToolBarToggleButton.getModel().isSelected());
        } else if (e.getSource() == this.recordMinZoomSlider.getModel()) {
            if (
                this.recordMinZoomSlider.getValueIsAdjusting()
                && this.recordMinZoomSlider.getModel().getValue() != this.recordMinZoomSlider.getModel().getMaximum()
            ) {
                this.slippyMap.setScaleHintBounds(
                    MarkSeenRoot.estimateCurrentBoundsScaledForZoom(this.recordMinZoomSlider.getModel().getValue())
                );
            } else {
                this.slippyMap.setScaleHintBounds(null);
            }
        } else {
            throw new RuntimeException("Unknown/unexpected ChangeEvent source");
        }
    }
}
