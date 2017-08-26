package org.openstreetmap.josm.plugins.markseen;

import java.io.IOException;
import java.util.List;

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

    protected void markRects(QuadTreeMeta quadTreeMeta, Object[][] seenRects_, Integer orderSeed) {
        List<Integer> remapping = getRemapping(seenRects_.length, orderSeed);

        for (int i = 0; i<seenRects_.length; i++) {
            int j = remapping.get(i);
            Object[] seenRectInfo = seenRects_[j];
            System.out.format("(%d of %d) Marking seen rect %d\n", i, seenRects_.length, j);
            Bounds bounds = (Bounds)seenRectInfo[0];
            double minTilesAcross = (double)seenRectInfo[1];

            quadTreeMeta.quadTreeRoot.markBoundsSeen(bounds, minTilesAcross);
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

//             System.out.println(javax.xml.bind.DatatypeConverter.printHexBinary(
//                 ((DataBufferByte) result.getData().getDataBuffer()).getData())
//             );

            if (assertContents) {
                assertArrayEquals(
                    ((DataBufferByte) result.getData().getDataBuffer()).getData(),
                    maskBytes
                );
            }
        }
    }
}
