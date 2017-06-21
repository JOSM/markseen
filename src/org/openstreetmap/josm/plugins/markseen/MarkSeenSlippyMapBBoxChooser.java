package org.openstreetmap.josm.plugins.markseen;

import org.openstreetmap.josm.gui.bbox.SlippyMapBBoxChooser;


public class MarkSeenSlippyMapBBoxChooser extends SlippyMapBBoxChooser {
    public MarkSeenSlippyMapBBoxChooser() {
        tileController = new MarkSeenTileController(
            tileController.getTileSource(),
            tileController.getTileCache(),
            this
        );
    }
}
