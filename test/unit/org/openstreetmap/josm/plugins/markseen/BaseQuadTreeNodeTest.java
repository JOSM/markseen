// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.markseen;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.IOException;
import java.util.List;

import org.openstreetmap.josm.data.Bounds;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Ignore;

@Ignore
public class BaseQuadTreeNodeTest extends BaseRectTest {
    public BaseQuadTreeNodeTest(int scenarioIndex_, Integer seenRectOrderSeed_, Integer referenceTileOrderSeed_)
    throws IOException {
        super(scenarioIndex_, seenRectOrderSeed_, referenceTileOrderSeed_);
    }

    @FunctionalInterface
    interface AfterRectMarkAction {
        void act(int i, int j, Bounds bounds, double minTilesAcross);
    }

    protected void markRects(
        QuadTreeMeta quadTreeMeta,
        Object[][] seenRects_,
        Integer orderSeed
    ) {
        this.markRects(quadTreeMeta, seenRects_, orderSeed, false);
    }

    protected void markRects(
        QuadTreeMeta quadTreeMeta,
        Object[][] seenRects_,
        Integer orderSeed,
        boolean tolerateExtremeAspectRatioException
    ) {
        this.markRects(quadTreeMeta, seenRects_, orderSeed, tolerateExtremeAspectRatioException, null);
    }

    protected void markRects(
        QuadTreeMeta quadTreeMeta,
        Object[][] seenRects_,
        Integer orderSeed,
        boolean tolerateExtremeAspectRatioException,
        AfterRectMarkAction afterRectMarkAction
    ) {
        List<Integer> remapping = getRemapping(seenRects_.length, orderSeed);

        for (int i = 0; i < seenRects_.length; i++) {
            int j = remapping.get(i);
            Object[] seenRectInfo = seenRects_[j];
            System.out.format("(%d of %d) Marking seen rect %d\n", i, seenRects_.length, j);
            Bounds bounds = (Bounds) seenRectInfo[0];
            double minTilesAcross = (double) seenRectInfo[1];

            try {
                quadTreeMeta.quadTreeRoot.markBoundsSeen(bounds, minTilesAcross);
            } catch (QuadTreeNode.ExtremeAspectRatioException e) {
                if (!tolerateExtremeAspectRatioException) {
                    throw e;
                }
            }
            quadTreeMeta.quadTreeRoot.checkIntegrity();

            if (afterRectMarkAction != null) {
                afterRectMarkAction.act(i, j, bounds, minTilesAcross);
                quadTreeMeta.quadTreeRoot.checkIntegrity();
            }
        }
    }

    protected void inspectReferenceTiles(QuadTreeMeta quadTreeMeta, Object[][] referenceTiles_, Integer orderSeed) {
        this.inspectReferenceTiles(quadTreeMeta, referenceTiles_, orderSeed, true);
    }

    protected void inspectReferenceTiles(
        QuadTreeMeta quadTreeMeta,
        Object[][] referenceTiles_,
        Integer orderSeed,
        boolean assertContents
    ) {
        this.inspectReferenceTiles(quadTreeMeta, referenceTiles_, orderSeed, assertContents, null);
    }

    protected void inspectReferenceTiles(
        QuadTreeMeta quadTreeMeta,
        Object[][] referenceTiles_,
        Integer orderSeed,
        boolean assertContents,
        Object constReferenceMask
    ) {
        this.inspectReferenceTiles(quadTreeMeta, referenceTiles_, orderSeed, assertContents, constReferenceMask, true);
    }

    protected void inspectReferenceTiles(
        QuadTreeMeta quadTreeMeta,
        Object[][] referenceTiles_,
        Integer orderSeed,
        boolean assertContents,
        Object constReferenceMask,
        boolean write
    ) {
        this.inspectReferenceTiles(quadTreeMeta, referenceTiles_, orderSeed, assertContents, constReferenceMask, write, null);
    }

    @FunctionalInterface
    interface InspectExtraAssertion {
        void assert_(
            QuadTreeNode node,
            int i,
            int j,
            BufferedImage resultMask,
            Object refMask,
            byte[] resultMaskBytes,
            byte[] refMaskBytes,
            boolean refMaskOptAliasable
        );
    }

    protected void inspectReferenceTiles(
        QuadTreeMeta quadTreeMeta,
        Object[][] referenceTiles_,
        Integer orderSeed,
        boolean assertContents,
        Object constReferenceMask,
        boolean write,
        InspectExtraAssertion extraAssertion
    ) {
        assertTrue("assertContents will not work reliably in non-default order",
            orderSeed == null || constReferenceMask != null || !assertContents);
        final List<Integer> remapping = getRemapping(referenceTiles_.length, orderSeed);

        for (int i = 0; i < referenceTiles_.length; i++) {
            int j = remapping.get(i);
            Object[] referenceTileInfo = referenceTiles_[j];
            System.out.format("(%d of %d) Checking reference tile %d\n", i, referenceTiles_.length, j);
            int zoom = (int) referenceTileInfo[0];
            int tilex = (int) referenceTileInfo[1];
            int tiley = (int) referenceTileInfo[2];
            Object refMask = constReferenceMask != null ? constReferenceMask : referenceTileInfo[3];
            byte[] refMaskBytes = getRefMaskBytes(quadTreeMeta, refMask);
            boolean refMaskOptAliasable = referenceTileInfo.length >= 5 ? (boolean) referenceTileInfo[4] : false;

            assertFalse(
                "refMask must be boolean if refMaskOptAliasable is true",
                refMaskOptAliasable && !Boolean.class.isInstance(refMask)
            );

            QuadTreeNode node = quadTreeMeta.quadTreeRoot.getNodeForTile(
                tilex,
                tiley,
                zoom,
                write
            );
            BufferedImage resultMask = node.getMask(write, write);
            quadTreeMeta.quadTreeRoot.checkIntegrity();

            byte[] resultMaskBytes = ((DataBufferByte) resultMask.getData().getDataBuffer()).getData();

            if (assertContents) {
                try {
                    assertArrayEquals(
                        resultMaskBytes,
                        refMaskBytes
                    );
                } catch (final AssertionError e) {
                    System.out.format("assertArrayEquals failed on reference tile %d\n", j);
                    System.out.println(
                        "ref = " + javax.xml.bind.DatatypeConverter.printHexBinary(refMaskBytes) +
                        ", result = " + javax.xml.bind.DatatypeConverter.printHexBinary(resultMaskBytes)
                    );
                    throw e;
                }
            }
            if (extraAssertion != null) {
                extraAssertion.assert_(
                    node,
                    i,
                    j,
                    resultMask,
                    refMask,
                    resultMaskBytes,
                    refMaskBytes,
                    refMaskOptAliasable
                );
            }
        }
    }
}
