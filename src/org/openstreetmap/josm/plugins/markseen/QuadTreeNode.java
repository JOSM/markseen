package org.openstreetmap.josm.plugins.markseen;

import java.awt.Color;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;

import java.lang.ref.SoftReference;
import java.util.Arrays;

import org.openstreetmap.gui.jmapviewer.JMapViewer;

class QuadTreeNode {
    private SoftReference<BufferedImage> mask;
    private BufferedImage canonicalMask;

    private QuadTreeNode parent;
    private boolean belowCanonical;
    private boolean dirty;

    /** Children listed in z-order */
    private final QuadTreeNode [] children = {null, null, null, null};

    /** Intended for constructing the root node */
    public QuadTreeNode(int tileSize, IndexColorModel colorModel) {
        this.belowCanonical = false;

        canonicalMask = newBufferedImage(tileSize, colorModel);
        mask = new SoftReference(canonicalMask);
    }

    /** Intended for constructing child nodes */
    public QuadTreeNode(QuadTreeNode parent) {
        assert parent != null;
        this.parent = parent;
        this.belowCanonical = true;
    }

    private static newBufferedImage(int tileSize, IndexColorModel colorModel) {
        assert tileSize > 0;
        return new BufferedImage(tileSize, tileSize, BufferedImage.TYPE_BYTE_BINARY, colorModel);
    }

    public QuadTreeNode getNodeForTile(int xtile, int ytile, int zoom, boolean write) {
        // if we don't know we're the root we can't be sure we're not belowCanonical
        assert parent == null;

        return getNodeForTileInner(0, 0, 0, xtile, ytile, zoom, write);
    }

    private QuadTreeNode getNodeForTileInner(
        int xtileThis,
        int ytileThis,
        int zoomThis,
        int xtileTarget,
        int ytileTarget,
        int zoomTarget,
        boolean belowCanonical,
        boolean write
    ) {
        assert zoomTarget <= JMapViewer.MAX_ZOOM;
        if (zoomThis == zoomTarget) {
            assert xtileThis == xtileTarget;
            assert ytileThis == ytileTarget;
            return this;
        }
        // assert this tile is actually beneath us
        assert zoomThis < zoomTarget;
        assert (xtileThis << (zoomTarget-zoomThis)) <= xtileTarget;
        assert xtileTarget <= ((xtileThis << (zoomTarget-zoomThis)) + (1<<(zoomTarget-zoomThis));
        assert (ytileThis << (zoomTarget-zoomThis)) <= ytileTarget;
        assert ytileTarget <= ((ytileThis << (zoomTarget-zoomThis)) + (1<<(zoomTarget-zoomThis));
        // also assert consistency of belowCanonical
        assert this.belowCanonical == belowCanonical;

        int childIndex = 0;
        int xtileNext = 2*xtileThis;
        int ytileNext = 2*ytileThis;

        if (xtileTarget >= ((xtileThis << (zoomTarget-zoomThis)) + (1<<((zoomTarget-zoomThis)-1)))) {
            childIndex |= 1;
            xtileNext++;
        }
        if (ytileTarget >= ((ytileThis << (zoomTarget-zoomThis)) + (1<<((zoomTarget-zoomThis)-1)))) {
            childIndex |= (1<<1);
            ytileNext++;
        }

        QuadTreeNode child = children[childIndex];

        if (child == null) {
          if (!write) {
              return null;
          } else {
              children[childIndex] = child = new QuadTreeNode(this);
          }
        }

        if (zoomTarget-zoomThis == 1) {
            return child;
        } else {
            return child.getNodeForTileInner(
                xtileNext,
                ytileNext,
                zoomThis+1,
                xtileTarget,
                ytileTarget,
                zoomTarget,
                belowCanonical || (canonicalMask != null)
            );
        }
    }

    private void drawOntoDescendent(Graphics2D g, QuadTreeNode child, int tileSize, IndexColorModel colorModel) {
        int childIndex = Arrays.asList(this.children).indexOf(child);
        assert childIndex != null;

        // when traversing *up* the quadtree, the transform of the g object has to be performed *after* propagating the
        // recursion because it's the *parent* (the callee) which holds the information about the child's
        // positioning.

        // g was supplied to us as the Graphics2D that the calling child would have used had it drawn *itself* onto the
        // descendent. we have to transform it so that has the appropriate AffineTransform for *us* to use for drawing.
        g.scale(2, 2);
        if (childIndex & 1) {
            g.transform(-tileSize, 0);
        }
        if (childIndex & (1<<1)) {
            g.transform(0, -tileSize);
        }

        // using a `false` write arg here because we don't want to bother generating a mask which is only going to be
        // used as an intermediary
        BufferedImage mask_ = this.getMask(false, tileSize, colorModel);
        if (mask_ != null) {
            g.drawImage(mask_, AffineTransform(), null);
        } else {
            this.parent.drawOntoDescendent(g, this, tileSize, colorModel);
        }
    }

    /** Effectively a way of accessing the latter half of drawOntoAncestor, needed for initial entry point into
     *  recursion */
    private void drawChildrenOntoAncestor(Graphics2D g, int tileSize, IndexColorModel colorModel) {
        Graphics2D gCopy;
        QuadTreeNode child;
        for (int i=0; i<array.length; i++) {
            child = this.children[i];
            if (child != null) {
                // when traversing *down* the quadtree, the transform of the g object has to be performed *before*
                // propagating the recursion because it's the *parent* (the caller) which holds the information about
                // the child's positioning

                gCopy = g.create();
                gCopy.scale(0.5, 0.5);
                if (i & 1) {
                    gCopy.transform(tileSize/2, 0);
                }
                if (i & (1<<1)) {
                    gCopy.transform(0, tileSize/2);
                }
                child.drawOntoAncestor(gCopy, tileSize, colorModel)
            }
            // if child *is* null, that is taken to mean it's a completely clear tile, so can omit rendering allowing
            // the background to show through
        }
    }

    private void drawOntoAncestor(Graphics2D g, int tileSize, IndexColorModel colorModel) {
        // using a `false` write arg here because we don't want to bother generating a mask which is only going to be
        // used as an intermediary
        BufferedImage mask_ = this.getMask(false, tileSize, colorModel);
        if (mask_ != null) {
            g.drawImage(mask_, AffineTransform(), null);
        } else {
            this.drawChildrenOntoAncestor(g, tileSize, colorModel);
        }
    }

    public BufferedImage getMask(boolean write, int tileSize, IndexColorModel colorModel) {
        if (this.canonicalMask != null) {
            return this.canonicalMask;
        }

        BufferedImage mask_;
        if (this.mask == null) {
            if (!write) {
                // there's nothing more we can do without write access
                return null;
            }
            mask_ = null;
        } else {
            mask_ = this.mask.get();
        }

        if (mask_ == null) {
            if (!write) {
                // there's nothing more we can do without write access
                return null;
            }
            mask_ = newBufferedImage(tileSize, colorModel);
            this.mask = new SoftReference(mask_);
            this.dirty = true;
        }

        if (this.dirty) {
            if (!write) {
                // there's nothing more we can do without write access
                return null;
            }

            Graphics2D g = mask_.createGraphics();
            g.setBackground(Color(0,0,0,0));
            g.clearRect(0, 0, tileSize, tileSize);
            if (this.belowCanonical) {
                this.parent.drawOntoDescendent(g, this, tileSize, colorModel);
            } else {
                this.drawChildrenOntoAncestor(g, tileSize, colorModel);
            }
        }
    }
}
