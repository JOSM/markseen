package org.openstreetmap.josm.plugins.markseen;

import javax.swing.BoundedRangeModel;
import javax.swing.JMenuItem;
import javax.swing.JSlider;
import javax.swing.JComponent;
import javax.swing.JToggleButton;
import java.awt.Graphics2D;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.Point;

import java.util.List;
import java.lang.AssertionError;

import static java.util.Arrays.asList;
import static java.lang.String.format;

import org.openstreetmap.gui.jmapviewer.Tile;
import org.openstreetmap.gui.jmapviewer.interfaces.TileSource;
import org.openstreetmap.gui.jmapviewer.tilesources.TMSTileSource;
import org.openstreetmap.gui.jmapviewer.tilesources.TileSourceInfo;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.ToggleAction;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.MapViewState;
import org.openstreetmap.josm.gui.bbox.SlippyMapBBoxChooser;

import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.testutils.JOSMTestRules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import mockit.Deencapsulation;
import mockit.Mock;
import mockit.MockUp;


public class MarkSeenRootTest {
    public static class MockSlippyMapBBoxChooser extends MockUp<SlippyMapBBoxChooser> {
        public final TMSTileSource dummyTileSource;
        public MockSlippyMapBBoxChooser() {
            this.dummyTileSource = new TMSTileSource(new TileSourceInfo("dummy", "http://dummy/", "dummy"));
        }

        @Mock
        private List<TileSource> getAllTileSources() {
            return asList(this.dummyTileSource);
        }
    }


    //
    // MapView and MapViewState need to be mocked to make them appear to certain methods to be "visible" and so have their
    // dimensions populated
    //
    public static class MockMapViewState extends MockUp<MapViewState> {
        @Mock
        private static Point findTopLeftInWindow(JComponent position) {
            return new Point();
        }

        @Mock
        private static Point findTopLeftOnScreen(JComponent position) {
            return new Point();
        }
    }

    public static class MockMapView extends MockUp<MapView> {
        @Mock
        private boolean isVisibleOnScreen() {
            return true;
        }
    }


    private static BufferedImage originalErrorImage;
    private static BufferedImage originalLoadingImage;

    @Rule public JOSMTestRules test = new JOSMTestRules().commands().preferences().projection().platform().fakeAPI();

    @BeforeClass
    public static void setUpClass() {
        // Because ERROR_IMAGE and LOADING_IMAGE are both declared as final, we can't swap them out before the test,
        // so we've got to take copies of their *content* to be able to copy back to them afterwards.
        originalErrorImage = new BufferedImage(Tile.ERROR_IMAGE.getWidth(), Tile.ERROR_IMAGE.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = originalErrorImage.createGraphics();
        g.drawImage(Tile.ERROR_IMAGE, 0, 0, null);
        Graphics2D g2 = Tile.ERROR_IMAGE.createGraphics();
        g2.setBackground(Color.WHITE);
        g2.clearRect(0, 0, Tile.ERROR_IMAGE.getWidth(), Tile.ERROR_IMAGE.getHeight());

        originalLoadingImage = new BufferedImage(Tile.LOADING_IMAGE.getWidth(), Tile.LOADING_IMAGE.getHeight(), BufferedImage.TYPE_INT_RGB);
        g = originalErrorImage.createGraphics();
        g.drawImage(Tile.LOADING_IMAGE, 0, 0, null);
        g2 = Tile.LOADING_IMAGE.createGraphics();
        g2.setBackground(Color.WHITE);
        g2.clearRect(0, 0, Tile.LOADING_IMAGE.getWidth(), Tile.LOADING_IMAGE.getHeight());

        MockSlippyMapBBoxChooser mockSlippyMapBBoxChooser = new MockSlippyMapBBoxChooser();
        MockMapView mockMapView = new MockMapView();
        MockMapViewState mockMapViewState = new MockMapViewState();
    }

    @AfterClass
    public static void tearDownClass() {
        // copy back original contents of ERROR_IMAGE and LOADING_IMAGE
        Graphics2D g = Tile.ERROR_IMAGE.createGraphics();
        g.drawImage(originalErrorImage, 0, 0, null);

        g = Tile.LOADING_IMAGE.createGraphics();
        g.drawImage(originalLoadingImage, 0, 0, null);

        probeScratchImage = null;
    }

    @Before
    public void setUp() {
        Main.map.setSize(800, 800);
        Main.map.mapView.setBounds(0, 0, 700, 700);
        Deencapsulation.invoke(Main.map.mapView, "updateLocationState");
    }

    public static void assertFirstNonWhitePixelValue(int[] columnOrRow, int value) {
        for (int i=0; i<columnOrRow.length; i++) {
            assertTrue("More than halfway into image haven't found a non-white pixel", i <= columnOrRow.length/2);

            // mask out "alpha" byte
            int rgb = 0xffffff & columnOrRow[i];

            if (rgb == value) {
                // correct value for first non-white pixel
                break;
            } else if (rgb != 0xffffff) {  // else it should be white
                fail(format("Unexpected non-white pixel %d: 0x%06x", i, rgb));
            }
            // or we'll keep looking
        }
    }

    public static int[] reversedArray(int[] inArray) {
        int[] outArray = new int[inArray.length];
        for (int i=0; i<inArray.length; i++) {
            outArray[outArray.length-(i+1)] = inArray[i];
        }
        return outArray;
    }

    /**
     * For each edge of the image, works its way inwards along the central column/row until it finds the first non-white
     * pixel and ensures its value matches that of the supplied RGB byte-packed int.
     *
     * This is a deliberately lenient way of checking that painting is happening in approximately the right places that
     * should still be robust to e.g. SlippyMapBBoxChooser's zoomlevel-choosing heuristics.
     */
    public static void probePixels(BufferedImage image, int top, int right, int bottom, int left) {
        int[] middleColumn = image.getRGB(image.getWidth()/2, 0, 1, image.getHeight(), null, 0, 1);
        assertFirstNonWhitePixelValue(middleColumn, top);
        assertFirstNonWhitePixelValue(reversedArray(middleColumn), bottom);

        int[] middleRow = image.getRGB(0, image.getHeight()/2, image.getWidth(), 1, null, 0, image.getWidth());
        assertFirstNonWhitePixelValue(middleRow, left);
        assertFirstNonWhitePixelValue(reversedArray(middleRow), right);
    }

    protected MarkSeenRoot markSeenRoot;

    protected ToggleAction recordAction;
    protected JMenuItem mainMenuRecordItem;
    protected BoundedRangeModel recordMinZoom;
    protected MarkSeenDialog dialog;

    protected JSlider recordMinZoomSlider;
    protected JToggleButton recordToggleButton;
    protected SlippyMapBBoxChooser slippyMap;

    protected static BufferedImage probeScratchImage;

    public void probeSlippyMapPixels(int top, int right, int bottom, int left) {
        if (probeScratchImage == null || probeScratchImage.getWidth() != this.slippyMap.getSize().width || probeScratchImage.getHeight() != this.slippyMap.getSize().height) {
            probeScratchImage = new BufferedImage(
                this.slippyMap.getSize().width,
                this.slippyMap.getSize().height,
                BufferedImage.TYPE_INT_RGB
            );
        }
        Graphics2D g = probeScratchImage.createGraphics();
        this.slippyMap.paint(g);
        try {
            probePixels(probeScratchImage, top, right, bottom, left);
        } catch (AssertionError e) {
            e.printStackTrace();
            System.err.println("Writing problematic image to failed.png");
            try {
                javax.imageio.ImageIO.write(probeScratchImage, "png", new java.io.File("failed.png"));
            } catch (java.io.IOException ioe) {
                System.err.println("Failed writing image");
            }
            throw e;
        }
    }

    public void setUpMarkSeenRoot() throws ReflectiveOperationException {
        this.markSeenRoot = new MarkSeenRoot();
        this.markSeenRoot.mapFrameInitialized(null, Main.map);

        this.recordAction = (ToggleAction)TestUtils.getPrivateField(this.markSeenRoot, "recordAction");
        this.mainMenuRecordItem = (JMenuItem)TestUtils.getPrivateField(this.markSeenRoot, "mainMenuRecordItem");
        this.recordMinZoom = (BoundedRangeModel)TestUtils.getPrivateField(this.markSeenRoot, "recordMinZoom");
        this.dialog = (MarkSeenDialog)TestUtils.getPrivateField(this.markSeenRoot, "dialog");

        this.recordMinZoomSlider = (JSlider)TestUtils.getPrivateField(this.dialog, "recordMinZoomSlider");
        this.recordToggleButton = (JToggleButton)TestUtils.getPrivateField(this.dialog, "recordToggleButton");
        this.slippyMap = (SlippyMapBBoxChooser)TestUtils.getPrivateField(this.dialog, "slippyMap");
    }

    public void assertControlStates(int recordMinZoomValue, boolean recordActionSelected, boolean recordActionEnabled) {
        assertEquals(recordMinZoomValue, this.recordMinZoom.getValue());
        assertEquals(recordMinZoomValue, this.recordMinZoomSlider.getValue());

        assertEquals(recordActionSelected, this.recordAction.isSelected());
        assertEquals(recordActionSelected, this.mainMenuRecordItem.isSelected());
        assertEquals(recordActionSelected, this.recordToggleButton.isSelected());

        assertEquals(recordActionEnabled, this.recordAction.isEnabled());
        assertEquals(recordActionEnabled, this.mainMenuRecordItem.isEnabled());
        assertEquals(recordActionEnabled, this.recordToggleButton.isEnabled());
    }

    @Test
    public void testInitPrefRecordActiveDisabled() throws ReflectiveOperationException {
        Main.pref.putInteger("markseen.recordMinZoom", 2);  // deliberately out of range
        Main.pref.put("markseen.recordActive", true);
        Main.map.mapView.zoomTo(new Bounds(26.27, -18.23, 26.29, -18.16));

        this.setUpMarkSeenRoot();

        this.dialog.showDialog();

        this.assertControlStates(4, true, false);

        // should have no effect
        Main.map.mapView.zoomTo(new Bounds(26.27, -18.23, 26.39, -18.06));

        this.assertControlStates(4, true, false);

        // should have no effect on recording state
        recordMinZoomSlider.setValue(12);

        this.assertControlStates(12, true, false);

        // should enable recording
        Main.map.mapView.zoomTo(new Bounds(26.27, -18.23, 26.275, -18.22));

        this.assertControlStates(12, true, true);

        // should deactivate recording
        recordToggleButton.doClick();

        this.assertControlStates(12, false, true);

         // the actual bounds get adjusted to match the mapview's aspect ratio and for the next move we want to try and
         // do a pure pan, ensuring we aren't within an area already considered "seen"
        Bounds actualBounds = Main.map.mapView.getState().getViewArea().getLatLonBoundsBox();

        // should be unrecorded pan
        Main.map.mapView.zoomTo(new Bounds(actualBounds.getMinLat(), actualBounds.getMinLon()+0.005, actualBounds.getMaxLat(), actualBounds.getMaxLon()+0.005));

        this.assertControlStates(12, false, true);
        this.probeSlippyMapPixels(0x0, 0x0, 0x0, 0xff80ff);

        // another unrecorded pure pan
        actualBounds = Main.map.mapView.getState().getViewArea().getLatLonBoundsBox();
        Main.map.mapView.zoomTo(new Bounds(actualBounds.getMinLat()+0.001, actualBounds.getMinLon(), actualBounds.getMaxLat()+0.001, actualBounds.getMaxLon()));

        this.assertControlStates(12, false, true);
        this.probeSlippyMapPixels(0x0, 0x0, 0x0, 0xff80ff);

        // now we activate recording briefly
        recordToggleButton.doClick();

        this.assertControlStates(12, true, true);
        this.probeSlippyMapPixels(0x0, 0x0, 0x0, 0xff80ff);

        // but we desctivate it without a viewport change
        recordToggleButton.doClick();

        this.assertControlStates(12, false, true);
        this.probeSlippyMapPixels(0x0, 0x0, 0x0, 0xff80ff);

        // another unrecorded pure pan back down
        actualBounds = Main.map.mapView.getState().getViewArea().getLatLonBoundsBox();
        Main.map.mapView.zoomTo(new Bounds(actualBounds.getMinLat()-0.001, actualBounds.getMinLon(), actualBounds.getMaxLat()-0.001, actualBounds.getMaxLon()));

        // should have revealed another painted region
        this.assertControlStates(12, false, true);
        this.probeSlippyMapPixels(0xff80ff, 0x0, 0x0, 0xff80ff);
    }
}
