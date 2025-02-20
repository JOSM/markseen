// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.markseen;

import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;

public class MarkSeenPlugin extends Plugin {
    private final MarkSeenRoot markSeenRoot;

    public MarkSeenPlugin(PluginInformation info) {
        super(info);
        this.markSeenRoot = new MarkSeenRoot();
    }

    @Override
    public void mapFrameInitialized(MapFrame oldFrame, MapFrame newFrame) {
        this.markSeenRoot.mapFrameInitialized(oldFrame, newFrame);
    }
}
