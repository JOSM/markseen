package org.openstreetmap.josm.plugins.markseen;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.gui.bbox.SlippyMapBBoxChooser;


public class MarkSeenSlippyMapBBoxChooser extends SlippyMapBBoxChooser implements QuadTreeMeta.QuadTreeModifiedListener {
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

    public void markBoundsSeen(Bounds bbox, double minTilesAcross) {
        ((MarkSeenTileController) this.tileController).markBoundsSeen(bbox, minTilesAcross);
    }
}
