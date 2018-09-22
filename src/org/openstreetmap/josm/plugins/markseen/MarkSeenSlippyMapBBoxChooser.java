package org.openstreetmap.josm.plugins.markseen;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Rectangle;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.gui.bbox.SlippyMapBBoxChooser;


public class MarkSeenSlippyMapBBoxChooser extends SlippyMapBBoxChooser implements QuadTreeMeta.QuadTreeModifiedListener {
    private Bounds scaleHintBounds = null;

    public MarkSeenSlippyMapBBoxChooser(QuadTreeMeta quadTreeMeta_) {
        this.tileController = new MarkSeenTileController(
            quadTreeMeta_,
            this.tileController.getTileSource(),
            this.tileController.getTileCache(),
            this
        );

        quadTreeMeta_.addModifiedListener(this);
    }

    @Override
    public void quadTreeModified() {
        // it should be noted that this can be called from any thread, but many sources seem to think that it's safe
        // to call .repaint() on a JComponent on a foreign thread, so...
        this.repaint();
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);

        if (this.scaleHintBounds != null) {
            final Rectangle r = new Rectangle(
                this.getMapPosition(this.scaleHintBounds.getMinLat(), this.scaleHintBounds.getMinLon(), false)
            );
            r.add(this.getMapPosition(this.scaleHintBounds.getMaxLat(), this.scaleHintBounds.getMaxLon(), false));

            g.setColor(Color.BLACK);
            g.drawRect(r.x, r.y, r.width, r.height);
        }
    }

    public void setScaleHintBounds(final Bounds newBounds) {
        this.scaleHintBounds = newBounds;
        this.repaint();
    }
}
