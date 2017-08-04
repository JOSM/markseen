package org.openstreetmap.josm.plugins.markseen;

import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;


public class MarkSeenPlugin extends Plugin {
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
    }
}
