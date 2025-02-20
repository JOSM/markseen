// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.markseen;

import javax.swing.BoundedRangeModel;
import javax.swing.JMenuItem;
import javax.swing.JSlider;
import javax.swing.JButton;
import javax.swing.JToggleButton;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.regex.Matcher;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import org.openstreetmap.josm.actions.ToggleAction;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.bbox.SlippyMapBBoxChooser;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.spi.preferences.Config;

import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.gui.layer.LayerManagerTest.TestLayer;
import org.openstreetmap.josm.testutils.JOSMTestRules;
import org.openstreetmap.josm.testutils.ImagePatternMatching;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import org.awaitility.Awaitility;

import com.google.common.collect.ImmutableMap;

public class MarkSeenRootTest {
    private static BufferedImage originalErrorImage;
    private static BufferedImage originalLoadingImage;

    @Rule public JOSMTestRules test = new JOSMTestRules().main().preferences().projection().fakeImagery().timeout(60000);

    @Before
    public void setUp() {
        // Add a test layer to the layer manager to get the MapFrame & MapView
        MainApplication.getLayerManager().addLayer(new TestLayer());

        GuiHelper.runInEDTAndWaitWithException(() -> {
            MapFrame mainMap = MainApplication.getMap();
            mainMap.setSize(800, 800);
            mainMap.mapView.addNotify();
            mainMap.mapView.doLayout();
            mainMap.mapView.setBounds(0, 0, 700, 700);
        });

        this.slippyMapTasksFinished = () -> !this.slippyMap.getTileController().getTileLoader().hasOutstandingTasks();
    }

    private static IntFunction<String> stripAlpha(Map<Integer, String> map) {
        return p -> map.getOrDefault(p & 0xffffff, "#");
    }

    /**
     * For each edge of the image, works its way inwards along the central column/row until it finds the first non-white
     * pixel and ensures its value matches that of the supplied RGB byte-packed int.
     *
     * This is a deliberately lenient way of checking that painting is happening in approximately the right places that
     * should still be robust to e.g. SlippyMapBBoxChooser's zoomlevel-choosing heuristics.
     */
    public static void probePixels(
        final BufferedImage image,
        final int top,
        final int right,
        final int bottom,
        final int left,
        final boolean centerBlack
    ) {
        ImagePatternMatching.columnMatch(
            image,
            image.getWidth()/2,
            stripAlpha(ImmutableMap.of(0xffffff, "w", top, "t")),
            "w*t.*",
            true
        );
        ImagePatternMatching.columnMatch(
            image,
            image.getWidth()/2,
            stripAlpha(ImmutableMap.of(0xffffff, "w", bottom, "b")),
            ".*bw*",
            true
        );
        ImagePatternMatching.rowMatch(
            image,
            image.getHeight()/2,
            stripAlpha(ImmutableMap.of(0xffffff, "w", left, "l")),
            "w*l.*",
            true
        );
        ImagePatternMatching.rowMatch(
            image,
            image.getHeight()/2,
            stripAlpha(ImmutableMap.of(0xffffff, "w", right, "r")),
            ".*rw*",
            true
        );
        int centerPixel = image.getRGB(image.getWidth()/2, image.getHeight()/2) & 0xffffff;
        if (centerBlack) {
            assertEquals(0, centerPixel);
        } else {
            assertNotEquals(0, centerPixel);
        }
    }

    protected Callable<Boolean> slippyMapTasksFinished;

    protected MarkSeenRoot markSeenRoot;

    protected ToggleAction recordAction;
    protected JMenuItem mainMenuRecordItem;
    protected BoundedRangeModel recordMinZoom;
    protected MarkSeenDialog dialog;

    protected JSlider recordMinZoomSlider;
    protected JToggleButton recordToggleButton;
    protected JButton clearButton;
    protected SlippyMapBBoxChooser slippyMap;

    protected JMenuItem mainMenuSetMaxViewportItem;

    protected static BufferedImage probeScratchImage;

    public void renderAndAssert(final Consumer<BufferedImage> assertion) {
        if (probeScratchImage == null || probeScratchImage.getWidth() != this.slippyMap.getSize().width
        || probeScratchImage.getHeight() != this.slippyMap.getSize().height) {
            probeScratchImage = new BufferedImage(
                this.slippyMap.getSize().width,
                this.slippyMap.getSize().height,
                BufferedImage.TYPE_INT_RGB
            );
        }
        Graphics2D g = probeScratchImage.createGraphics();

        // an initial paint operation to trigger tile fetches etc
        this.slippyMap.paint(g);
        Awaitility.await().atMost(1000, MILLISECONDS).until(this.slippyMapTasksFinished);
        this.slippyMap.paint(g);

        try {
            assertion.accept(probeScratchImage);
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
        MapFrame mainMap = MainApplication.getMap();
        this.markSeenRoot = new MarkSeenRoot();
        this.markSeenRoot.mapFrameInitialized(null, mainMap);

        this.recordAction = (ToggleAction) TestUtils.getPrivateField(this.markSeenRoot, "recordAction");
        this.mainMenuRecordItem = (JMenuItem) TestUtils.getPrivateField(this.markSeenRoot, "mainMenuRecordItem");
        this.recordMinZoom = (BoundedRangeModel) TestUtils.getPrivateField(this.markSeenRoot, "recordMinZoom");
        this.dialog = (MarkSeenDialog) TestUtils.getPrivateField(this.markSeenRoot, "dialog");

        this.recordMinZoomSlider = (JSlider) TestUtils.getPrivateField(this.dialog, "recordMinZoomSlider");
        this.recordToggleButton = (JToggleButton) TestUtils.getPrivateField(this.dialog, "recordToggleButton");
        this.clearButton = (JButton) TestUtils.getPrivateField(this.dialog, "clearButton");
        this.slippyMap = (SlippyMapBBoxChooser) TestUtils.getPrivateField(this.dialog, "slippyMap");

        this.mainMenuSetMaxViewportItem = (JMenuItem) TestUtils.getPrivateField(this.markSeenRoot, "mainMenuSetMaxViewportItem");
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
    public void testInitPrefRecordActiveDisabled() throws Exception {
        MapFrame mainMap = MainApplication.getMap();
        Config.getPref().put("markseen.mapstyle", "White Tiles");
        Config.getPref().putInt("markseen.recordMinZoom", 2);  // deliberately out of range
        Config.getPref().putBoolean("markseen.recordActive", true);
        mainMap.mapView.zoomTo(new Bounds(26.27, -18.23, 26.29, -18.16));

        this.setUpMarkSeenRoot();

        this.dialog.showDialog();

        this.assertControlStates(4, true, false);
        assertTrue(this.recordToggleButton.getToolTipText().contains("disabled"));

        // should have no effect
        mainMap.mapView.zoomTo(new Bounds(26.27, -18.23, 26.39, -18.06));

        this.assertControlStates(4, true, false);

        // should have no effect on recording state
        recordMinZoomSlider.setValue(12);

        this.assertControlStates(12, true, false);

        // should enable recording
        mainMap.mapView.zoomTo(new Bounds(26.27, -18.23, 26.275, -18.22));

        this.assertControlStates(12, true, true);
        assertFalse(this.recordToggleButton.getToolTipText().contains("disabled"));

        // should deactivate recording
        this.recordToggleButton.doClick();

        this.assertControlStates(12, false, true);

        // the actual bounds get adjusted to match the mapview's aspect ratio and for the next move we want to try and
        // do a pure pan, ensuring we aren't within an area already considered "seen"
        Bounds actualBounds = mainMap.mapView.getState().getViewArea().getLatLonBoundsBox();

        // should be unrecorded pan
        mainMap.mapView.zoomTo(new Bounds(actualBounds.getMinLat(), actualBounds.getMinLon()+0.005,
            actualBounds.getMaxLat(), actualBounds.getMaxLon()+0.005));

        this.assertControlStates(12, false, true);
        this.renderAndAssert(i -> probePixels(i, 0x0, 0x0, 0x0, 0xff80ff, false));

        // another unrecorded pure pan
        actualBounds = mainMap.mapView.getState().getViewArea().getLatLonBoundsBox();
        mainMap.mapView.zoomTo(new Bounds(actualBounds.getMinLat()+0.001, actualBounds.getMinLon(),
            actualBounds.getMaxLat()+0.001, actualBounds.getMaxLon()));

        this.assertControlStates(12, false, true);
        this.renderAndAssert(i -> probePixels(i, 0x0, 0x0, 0x0, 0xff80ff, false));

        // now we activate recording briefly
        this.recordToggleButton.doClick();

        this.assertControlStates(12, true, true);
        this.renderAndAssert(i -> probePixels(i, 0x0, 0x0, 0x0, 0xff80ff, false));

        // but we desctivate it without a viewport change
        this.recordToggleButton.doClick();

        this.assertControlStates(12, false, true);
        this.renderAndAssert(i -> probePixels(i, 0x0, 0x0, 0x0, 0xff80ff, false));

        // another unrecorded pure pan back down
        actualBounds = mainMap.mapView.getState().getViewArea().getLatLonBoundsBox();
        mainMap.mapView.zoomTo(new Bounds(actualBounds.getMinLat()-0.001, actualBounds.getMinLon(),
            actualBounds.getMaxLat()-0.001, actualBounds.getMaxLon()));

        // should have revealed another painted region
        this.assertControlStates(12, false, true);
        this.renderAndAssert(i -> probePixels(i, 0xff80ff, 0x0, 0x0, 0xff80ff, false));

        this.recordMinZoom.setValue(8);

        this.assertControlStates(8, false, false);
        assertTrue(this.recordToggleButton.getToolTipText().contains("disabled"));
        this.renderAndAssert(i -> probePixels(i, 0xff80ff, 0x0, 0x0, 0xff80ff, true));
    }

    @Test
    public void testInitPrefRecordActiveEnabled() throws Exception {
        MapFrame mainMap = MainApplication.getMap();
        Config.getPref().put("markseen.mapstyle", "White Tiles");
        Config.getPref().putInt("markseen.recordMinZoom", 10);
        Config.getPref().putBoolean("markseen.recordActive", true);
        Config.getPref().put("color.markseen.seenarea", "#00ffff");
        Config.getPref().putDouble("markseen.maskOpacity", 1.0);
        mainMap.mapView.zoomTo(new Bounds(-0.001, -0.001, 0.001, 0.001));

        this.setUpMarkSeenRoot();
        this.dialog.showDialog();

        this.assertControlStates(10, true, true);
        assertFalse(this.recordToggleButton.getToolTipText().contains("disabled"));
        this.renderAndAssert(i -> probePixels(i, 0x0, 0x0, 0x0, 0x0, false));

        mainMap.mapView.zoomTo(new Bounds(-0.0005, -0.0005, 0.001, 0.001));
        this.assertControlStates(10, true, true);
        // it should be ok that the "initial" position wasn't recorded - the initial mapview position is often not
        // reliable to use
        this.renderAndAssert(i -> probePixels(i, 0x0, 0x0, 0x0, 0x0, false));

        mainMap.mapView.zoomTo(new Bounds(-0.0004, -0.0004, 0.001, 0.001));
        this.assertControlStates(10, true, true);
        this.renderAndAssert(i -> probePixels(i, 0x0, 0x0, 0x00ffff, 0x00ffff, false));

        this.dialog.hideDialog();

        mainMap.mapView.zoomTo(new Bounds(-0.0004, -0.002, 0.001, -0.0005));
        this.assertControlStates(10, true, true);

        this.dialog.showDialog();
        // slippy map should take heed of the movement while the dialog was closed
        this.renderAndAssert(i -> probePixels(i, 0x0, 0x00ffff, 0x0, 0x0, false));

        this.clearButton.doClick();

        Thread.sleep(50);

        this.assertControlStates(10, true, true);
        this.renderAndAssert(i -> probePixels(i, 0x0, 0x0, 0x0, 0x0, false));

        this.recordMinZoom.setValue(6);

        this.assertControlStates(6, true, false);
        assertTrue(this.recordToggleButton.getToolTipText().contains("disabled"));
        this.renderAndAssert(i -> probePixels(i, 0x0, 0x0, 0x0, 0x0, true));
    }

    @Test
    public void testSetMaxViewportFromCurrent() throws Exception {
        MapFrame mainMap = MainApplication.getMap();
        Config.getPref().put("markseen.mapstyle", "White Tiles");
        Config.getPref().putInt("markseen.recordMinZoom", 4);
        Config.getPref().putBoolean("markseen.recordActive", false);
        mainMap.mapView.zoomTo(new Bounds(-20.0, 0, -19.998, 0.002));

        this.setUpMarkSeenRoot();
        this.dialog.showDialog();

        this.assertControlStates(4, false, false);
        assertTrue(this.recordToggleButton.getToolTipText().contains("disabled"));

        GuiHelper.runInEDTAndWaitWithException(() -> this.mainMenuSetMaxViewportItem.doClick());
        this.assertControlStates(9, false, true);

        GuiHelper.runInEDTAndWaitWithException(() -> this.recordToggleButton.doClick());

        mainMap.mapView.zoomTo(new Bounds(-20.0, 0, -19.996, 0.004));
        this.assertControlStates(9, true, false);

        GuiHelper.runInEDTAndWaitWithException(() -> this.mainMenuSetMaxViewportItem.doClick());
        this.assertControlStates(10, true, true);
    }

    @Test
    public void testScaleHintBounds() throws Exception {
        IntFunction<String> palMapFn = stripAlpha(ImmutableMap.of(
            0xffffff, "w",
            0x0, "b",
            0xf0d1d1, "p"
        ));
        MapFrame mainMap = MainApplication.getMap();
        Config.getPref().put("markseen.mapstyle", "White Tiles");
        Config.getPref().putInt("markseen.recordMinZoom", 10);
        Config.getPref().putBoolean("markseen.recordActive", true);
        mainMap.mapView.zoomTo(new Bounds(-77.9024496, -41.6807301, -77.8268961, -41.3232887));

        this.setUpMarkSeenRoot();

        this.dialog.showDialog();

        this.assertControlStates(10, true, false);
        assertTrue(this.recordToggleButton.getToolTipText().contains("disabled"));
        this.renderAndAssert(i -> {
            Matcher r_m = ImagePatternMatching.rowMatch(i, i.getHeight()/2, palMapFn, "w+b(p+)b{1,3}(p+)bw+", true);
            Matcher c_m = ImagePatternMatching.columnMatch(i, i.getWidth()/2, palMapFn, "w+b(p+)b{1,3}(p+)bw+", true);

            // assert certrality of bbox "cross"
            assertTrue(
                Math.max(Math.max(r_m.group(1).length(), r_m.group(2).length()), Math.max(c_m.group(1).length(), c_m.group(2).length()))
                - Math.min(Math.min(r_m.group(1).length(), r_m.group(2).length()), Math.min(c_m.group(1).length(), c_m.group(2).length()))
                < 3
            );
        });

        this.recordMinZoom.setValueIsAdjusting(true);
        this.recordMinZoom.setValue(11);

        this.assertControlStates(11, true, false);
        assertTrue(this.recordToggleButton.getToolTipText().contains("disabled"));
        this.renderAndAssert(i -> {
            Matcher r_m = ImagePatternMatching.rowMatch(i, i.getHeight()/2, palMapFn, "w+b(p+)b(p+b{1,3}p+)b(p+)bw+", true);
            Matcher c_m = ImagePatternMatching.columnMatch(i, i.getWidth()/2, palMapFn, "w+b(p+)b(p+b{1,3}p+)b(p+)bw+", true);

            assertTrue(
                Math.max(Math.max(r_m.group(1).length(), r_m.group(3).length()), Math.max(c_m.group(1).length(), c_m.group(3).length()))
                - Math.min(Math.min(r_m.group(1).length(), r_m.group(3).length()), Math.min(c_m.group(1).length(), c_m.group(3).length()))
                < 3
            );
            assertTrue(
                Math.max(r_m.group(2).length(), c_m.group(2).length())
                - Math.min(r_m.group(2).length(), c_m.group(2).length())
                < 3
            );

            // no point in asserting certrality of bbox "cross" - too small to be reliable
        });

        Bounds shb = (Bounds) TestUtils.getPrivateField(this.slippyMap, "scaleHintBounds");
        assertTrue(Math.abs(shb.getMinLat() - -77.8712701) < 0.008);
        assertTrue(Math.abs(shb.getMinLon() - -41.532751) < 0.008);
        assertTrue(Math.abs(shb.getMaxLat() - -77.8582611) < 0.008);
        assertTrue(Math.abs(shb.getMaxLon() - -41.4708681) < 0.008);

        this.recordMinZoom.setValue(13);

        this.assertControlStates(13, true, false);
        assertTrue(this.recordToggleButton.getToolTipText().contains("disabled"));
        this.renderAndAssert(i -> {
            Matcher r_m = ImagePatternMatching.rowMatch(i, i.getHeight()/2, palMapFn, "w+b(p+)b((p+)b{1,3}(p+))b(p+)bw+", true);
            Matcher c_m = ImagePatternMatching.columnMatch(i, i.getWidth()/2, palMapFn, "w+b(p+)b((p+)b{1,3}(p+))b(p+)bw+", true);

            assertTrue(
                Math.max(Math.max(r_m.group(1).length(), r_m.group(5).length()), Math.max(c_m.group(1).length(), c_m.group(5).length()))
                - Math.min(Math.min(r_m.group(1).length(), r_m.group(5).length()), Math.min(c_m.group(1).length(), c_m.group(5).length()))
                < 3
            );
            assertTrue(
                Math.max(r_m.group(2).length(), c_m.group(2).length())
                - Math.min(r_m.group(2).length(), c_m.group(2).length())
                < 3
            );

            // assert certrality of bbox "cross"
            assertTrue(
                Math.max(Math.max(r_m.group(3).length(), r_m.group(4).length()), Math.max(c_m.group(3).length(), c_m.group(4).length()))
                - Math.min(Math.min(r_m.group(3).length(), r_m.group(4).length()), Math.min(c_m.group(3).length(), c_m.group(4).length()))
                < 3
            );
        });

        shb = (Bounds) TestUtils.getPrivateField(this.slippyMap, "scaleHintBounds");
        assertTrue(Math.abs(shb.getMinLat() - -77.8907578) < 0.008);
        assertTrue(Math.abs(shb.getMinLon() - -41.6255752) < 0.008);
        assertTrue(Math.abs(shb.getMaxLat() - -77.8387218) < 0.008);
        assertTrue(Math.abs(shb.getMaxLon() - -41.3780439) < 0.008);

        this.recordMinZoom.setValue(14);

        this.assertControlStates(14, true, true);
        assertFalse(this.recordToggleButton.getToolTipText().contains("disabled"));
        this.renderAndAssert(i -> {
            Matcher r_m = ImagePatternMatching.rowMatch(i, i.getHeight()/2, palMapFn, "w+b(w+)b(p+)b(w+)bw+", true);
            Matcher c_m = ImagePatternMatching.columnMatch(i, i.getWidth()/2, palMapFn, "w+b(w+)b(p+)b(w+)bw+", true);

            assertTrue(
                Math.max(Math.max(r_m.group(1).length(), r_m.group(3).length()), Math.max(c_m.group(1).length(), c_m.group(3).length()))
                - Math.min(Math.min(r_m.group(1).length(), r_m.group(3).length()), Math.min(c_m.group(1).length(), c_m.group(3).length()))
                < 3
            );
            assertTrue(
                Math.max(r_m.group(2).length(), c_m.group(2).length())
                - Math.min(r_m.group(2).length(), c_m.group(2).length())
                < 3
            );
        });

        shb = (Bounds) TestUtils.getPrivateField(this.slippyMap, "scaleHintBounds");
        assertTrue(Math.abs(shb.getMinLat() - -77.9166935) < 0.008);
        assertTrue(Math.abs(shb.getMinLon() - -41.749341) < 0.008);
        assertTrue(Math.abs(shb.getMaxLat() - -77.8126213) < 0.008);
        assertTrue(Math.abs(shb.getMaxLon() - -41.2542782) < 0.008);

        this.recordMinZoom.setValueIsAdjusting(false);

        this.assertControlStates(14, true, true);
        assertFalse(this.recordToggleButton.getToolTipText().contains("disabled"));
        this.renderAndAssert(i -> {
            Matcher r_m = ImagePatternMatching.rowMatch(i, i.getHeight()/2, palMapFn, "w+b(p+)bw+", true);
            Matcher c_m = ImagePatternMatching.columnMatch(i, i.getWidth()/2, palMapFn, "w+b(p+)bw+", true);

            assertTrue(
                Math.max(r_m.group(1).length(), c_m.group(1).length())
                - Math.min(r_m.group(1).length(), c_m.group(1).length())
                < 3
            );
        });

        assertNull(TestUtils.getPrivateField(this.slippyMap, "scaleHintBounds"));

        this.slippyMap.setZoom(this.slippyMap.getZoom()-3);

        this.recordMinZoom.setValueIsAdjusting(true);
        this.recordMinZoom.setValue(18);

        this.assertControlStates(18, true, true);
        assertFalse(this.recordToggleButton.getToolTipText().contains("disabled"));
        this.renderAndAssert(i -> {
            Matcher r_m = ImagePatternMatching.rowMatch(i, i.getHeight()/2, palMapFn, "w+b(w+)b(p+)b(w+)bw+", true);
            Matcher c_m = ImagePatternMatching.columnMatch(i, i.getWidth()/2, palMapFn, "w+b(w+)b(p+)b(w+)bw+", true);

            // even at high scales this should continue to match the aspec ratio of the viewport but only
            // because the slippy map's projection matches that of the mapview.

            assertTrue(
                Math.max(Math.max(r_m.group(1).length(), r_m.group(3).length()), Math.max(c_m.group(1).length(), c_m.group(3).length()))
                - Math.min(Math.min(r_m.group(1).length(), r_m.group(3).length()), Math.min(c_m.group(1).length(), c_m.group(3).length()))
                < 3
            );
            assertTrue(
                Math.max(r_m.group(2).length(), c_m.group(2).length())
                - Math.min(r_m.group(2).length(), c_m.group(2).length())
                < 3
            );
        });

        shb = (Bounds) TestUtils.getPrivateField(this.slippyMap, "scaleHintBounds");

        assertTrue(Math.abs(shb.getMinLat() - -78.6698339) < 0.008);
        assertTrue(Math.abs(shb.getMinLon() - -45.4624486) < 0.008);
        assertTrue(Math.abs(shb.getMaxLat() - -77.0034155) < 0.008);
        assertTrue(Math.abs(shb.getMaxLon() - -37.5411705) < 0.008);

        this.recordMinZoom.setValue(this.recordMinZoom.getMaximum());

        this.assertControlStates(this.recordMinZoom.getMaximum(), true, true);
        assertFalse(this.recordToggleButton.getToolTipText().contains("disabled"));
        assertNull(TestUtils.getPrivateField(this.slippyMap, "scaleHintBounds"));

        this.recordMinZoom.setValue(this.recordMinZoom.getMaximum()-1);

        this.assertControlStates(this.recordMinZoom.getMaximum()-1, true, true);
        assertFalse(this.recordToggleButton.getToolTipText().contains("disabled"));
        this.renderAndAssert(i -> {
            Matcher r_m = ImagePatternMatching.rowMatch(i, i.getHeight()/2, palMapFn, "w+b(p+)bw+", true);
            Matcher c_m = ImagePatternMatching.columnMatch(i, i.getWidth()/2, palMapFn, "w+b(p+)bw+", true);

            // scaleHintBounds should all be beyond bounds of slippy map, but not fail or affect rendering

            assertTrue(
                Math.max(r_m.group(1).length(), c_m.group(1).length())
                - Math.min(r_m.group(1).length(), c_m.group(1).length())
                < 3
            );
        });
        // probably a slightly nonsensical value at this latitude
        assertNotNull(TestUtils.getPrivateField(this.slippyMap, "scaleHintBounds"));

        this.recordMinZoom.setValue(this.recordMinZoom.getMinimum());

        this.assertControlStates(4, true, false);
        assertTrue(this.recordToggleButton.getToolTipText().contains("disabled"));
    }
}
