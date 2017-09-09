package org.openstreetmap.josm.plugins.markseen;

import javax.swing.BoundedRangeModel;
import javax.swing.JMenuItem;
import javax.swing.JSlider;
import javax.swing.JToggleButton;

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
    public void testInitPrefRecordActiveDisabled() throws ReflectiveOperationException {
        Main.pref.putInteger("markseen.recordMinZoom", 2);  // deliberately out of range
        Main.pref.put("markseen.recordActive", true);
        Main.map.mapView.zoomTo(new Bounds(26.27, -18.23, 26.29, -18.16));

        MarkSeenRoot markSeenRoot = new MarkSeenRoot();
        markSeenRoot.mapFrameInitialized(null, Main.map);

        final ToggleAction recordAction = (ToggleAction)TestUtils.getPrivateField(markSeenRoot, "recordAction");
        final JMenuItem mainMenuRecordItem = (JMenuItem)TestUtils.getPrivateField(markSeenRoot, "mainMenuRecordItem");
        final BoundedRangeModel recordMinZoom = (BoundedRangeModel)TestUtils.getPrivateField(markSeenRoot, "recordMinZoom");
        final MarkSeenDialog dialog = (MarkSeenDialog)TestUtils.getPrivateField(markSeenRoot, "dialog");

        final JSlider recordMinZoomSlider = (JSlider)TestUtils.getPrivateField(dialog, "recordMinZoomSlider");
        final JToggleButton recordToggleButton = (JToggleButton)TestUtils.getPrivateField(dialog, "recordToggleButton");

        dialog.showDialog();

        assertEquals(4, recordMinZoom.getValue());
        assertEquals(4, recordMinZoomSlider.getValue());

        assertTrue(recordAction.isSelected());
        assertTrue(mainMenuRecordItem.isSelected());
        assertTrue(recordToggleButton.isSelected());

        assertFalse(recordAction.isEnabled());
        assertFalse(mainMenuRecordItem.isEnabled());
        assertFalse(recordToggleButton.isEnabled());

        // should have no effect
        Main.map.mapView.zoomTo(new Bounds(26.27, -18.23, 26.39, -18.06));

        assertTrue(recordAction.isSelected());
        assertTrue(mainMenuRecordItem.isSelected());
        assertTrue(recordToggleButton.isSelected());

        assertFalse(recordAction.isEnabled());
        assertFalse(mainMenuRecordItem.isEnabled());
        assertFalse(recordToggleButton.isEnabled());

        // should have no effect on recording state
        recordMinZoomSlider.setValue(12);

        assertEquals(12, recordMinZoom.getValue());

        assertTrue(recordAction.isSelected());
        assertTrue(mainMenuRecordItem.isSelected());
        assertTrue(recordToggleButton.isSelected());

        assertFalse(recordAction.isEnabled());
        assertFalse(mainMenuRecordItem.isEnabled());
        assertFalse(recordToggleButton.isEnabled());

        // should enable recording
        Main.map.mapView.zoomTo(new Bounds(26.27, -18.23, 26.275, -18.22));

        assertTrue(recordAction.isSelected());
        assertTrue(mainMenuRecordItem.isSelected());
        assertTrue(recordToggleButton.isSelected());

        assertTrue(recordAction.isEnabled());
        assertTrue(mainMenuRecordItem.isEnabled());
        assertTrue(recordToggleButton.isEnabled());

        // should deactivate recording
        recordToggleButton.doClick();

        assertFalse(recordAction.isSelected());
        assertFalse(mainMenuRecordItem.isSelected());
        assertFalse(recordToggleButton.isSelected());

        assertTrue(recordAction.isEnabled());
        assertTrue(mainMenuRecordItem.isEnabled());
        assertTrue(recordToggleButton.isEnabled());

        // should be unrecorded pan
        Main.map.mapView.zoomTo(new Bounds(26.265, -18.23, 26.27, -18.22));

        assertFalse(recordAction.isSelected());
        assertFalse(mainMenuRecordItem.isSelected());
        assertFalse(recordToggleButton.isSelected());

        assertTrue(recordAction.isEnabled());
        assertTrue(mainMenuRecordItem.isEnabled());
        assertTrue(recordToggleButton.isEnabled());
    }
}
