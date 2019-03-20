package org.openstreetmap.josm.plugins.markseen;

import java.io.IOException;
import java.util.List;
import java.lang.AssertionError;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;

import org.openstreetmap.josm.data.Bounds;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Ignore;


@Ignore
public class BaseQuadTreeNodeTest extends BaseRectTest {
    public BaseQuadTreeNodeTest(int scenarioIndex_, Integer seenRectOrderSeed_, Integer referenceTileOrderSeed_)
    throws IOException {
        super(scenarioIndex_, seenRectOrderSeed_, referenceTileOrderSeed_);
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
        List<Integer> remapping = getRemapping(seenRects_.length, orderSeed);

        for (int i = 0; i<seenRects_.length; i++) {
            int j = remapping.get(i);
            Object[] seenRectInfo = seenRects_[j];
            System.out.format("(%d of %d) Marking seen rect %d\n", i, seenRects_.length, j);
            Bounds bounds = (Bounds)seenRectInfo[0];
            double minTilesAcross = (double)seenRectInfo[1];

            try {
                quadTreeMeta.quadTreeRoot.markBoundsSeen(bounds, minTilesAcross);
            } catch (QuadTreeNode.ExtremeAspectRatioException e) {
                if (!tolerateExtremeAspectRatioException) {
                    throw e;
                }
            }
            quadTreeMeta.quadTreeRoot.checkIntegrity();
        }
    }

    protected void inspectReferenceTiles(QuadTreeMeta quadTreeMeta, Object [][] referenceTiles_, Integer orderSeed) {
        this.inspectReferenceTiles(quadTreeMeta, referenceTiles_, orderSeed, true);
    }

    protected void inspectReferenceTiles(
        QuadTreeMeta quadTreeMeta,
        Object [][] referenceTiles_,
        Integer orderSeed,
        boolean assertContents
    ) {
        this.inspectReferenceTiles(quadTreeMeta, referenceTiles_, orderSeed, assertContents, null);
    }

    protected void inspectReferenceTiles(
        QuadTreeMeta quadTreeMeta,
        Object [][] referenceTiles_,
        Integer orderSeed,
        boolean assertContents,
        Object constReferenceMask
    ) {
        this.inspectReferenceTiles(quadTreeMeta, referenceTiles_, orderSeed, assertContents, constReferenceMask, true);
    }

    protected void inspectReferenceTiles(
        QuadTreeMeta quadTreeMeta,
        Object [][] referenceTiles_,
        Integer orderSeed,
        boolean assertContents,
        Object constReferenceMask,
        boolean write
    ) {
        List<Integer> remapping = getRemapping(referenceTiles_.length, orderSeed);

        for (int i = 0; i<referenceTiles_.length; i++) {
            int j = remapping.get(i);
            Object[] referenceTileInfo = referenceTiles_[j];
            System.out.format("(%d of %d) Checking reference tile %d\n", i, referenceTiles_.length, j);
            int zoom = (int)referenceTileInfo[0];
            int tilex = (int)referenceTileInfo[1];
            int tiley = (int)referenceTileInfo[2];
            byte[] maskBytes = getRefMaskBytes(quadTreeMeta, constReferenceMask != null ? constReferenceMask : referenceTileInfo[3]);

            BufferedImage result = quadTreeMeta.quadTreeRoot.getNodeForTile(
                tilex,
                tiley,
                zoom,
                write
            ).getMask(write, write);
            quadTreeMeta.quadTreeRoot.checkIntegrity();

            byte[] resultMaskBytes = ((DataBufferByte) result.getData().getDataBuffer()).getData();

            if (assertContents) {
                try {
                    assertArrayEquals(
                        resultMaskBytes,
                        maskBytes
                    );
                } catch (final AssertionError e) {
                    System.out.format("assertArrayEquals failed on reference tile %d\n", j);
                    System.out.println(javax.xml.bind.DatatypeConverter.printHexBinary(resultMaskBytes));
                    throw e;
                }
            }
        }
    }
}
