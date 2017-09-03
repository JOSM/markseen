package org.openstreetmap.josm.plugins.markseen;

import javax.swing.BoundedRangeModel;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.ToggleAction;
import org.openstreetmap.josm.data.Bounds;

import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.testutils.JOSMTestRules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;


public class MarkSeenRootTest {
    @Rule public JOSMTestRules test = new JOSMTestRules().commands().preferences().projection().platform().fakeAPI();

    @Before
    public void setUp() {
        Main.map.setSize(800, 800);
        Main.map.mapView.setBounds(0, 0, 700, 700);
    }

    @Test
    public void testInitPrefRecordDisabled() throws ReflectiveOperationException {
        Main.pref.putInteger("markseen.recordMinZoom", 2);  // deliberately out of range
        Main.pref.put("markseen.recordActive", true);
        Main.map.mapView.zoomTo(new Bounds(26.27, -18.23, 26.29, -18.16));

        MarkSeenRoot markSeenRoot = new MarkSeenRoot();
        markSeenRoot.mapFrameInitialized(null, Main.map);

        ToggleAction recordAction = (ToggleAction)TestUtils.getPrivateField(markSeenRoot, "recordAction");
        BoundedRangeModel recordMinZoom = (BoundedRangeModel)TestUtils.getPrivateField(markSeenRoot, "recordMinZoom");

        assertEquals(4, recordMinZoom.getValue());
        assertTrue(recordAction.isSelected());

        assertFalse(recordAction.isEnabled());
    }
}
