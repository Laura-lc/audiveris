//----------------------------------------------------------------------------//
//                                                                            //
//                        S h a p e D e s c r i p t o r                       //
//                                                                            //
//----------------------------------------------------------------------------//
// <editor-fold defaultstate="collapsed" desc="hdr">
//  Copyright © Herve Bitteur and others 2000-2013. All rights reserved.
//  This software is released under the GNU General Public License.
//  Goto http://kenai.com/projects/audiveris to report bugs or suggestions.
//----------------------------------------------------------------------------//
// </editor-fold>
package omr.image;

import omr.constant.Constant;
import omr.constant.ConstantSet;

import omr.glyph.Shape;
import static omr.glyph.Shape.*;
import omr.glyph.ShapeSet;

import omr.image.Anchored.Anchor;
import omr.image.Template.Key;

import omr.ui.symbol.MusicFont;
import static omr.ui.symbol.MusicFont.getFont;
import omr.ui.symbol.TemplateSymbol;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Class {@code ShapeDescriptor} handles all the templates for a shape
 * at a given global interline value.
 * <p>
 * It gathers all relevant template variants (they depend on line config).
 * All these variants share the same physical dimension (width * height).
 * <p>
 * TODO: Support could be added for slightly different widths, if so needed.
 *
 * @author Hervé Bitteur
 */
public class ShapeDescriptor
{
    //~ Static fields/initializers ---------------------------------------------

    private static final Constants constants = new Constants();

    private static final Logger logger = LoggerFactory.getLogger(
            ShapeDescriptor.class);

    /** All shapes with hole(s). */
    private static final EnumSet shapesWithHoles = EnumSet.of(
            NOTEHEAD_VOID,
            NOTEHEAD_VOID_SMALL,
            WHOLE_NOTE,
            WHOLE_NOTE_SMALL);

    /** Color for foreground pixels. */
    private static final int BLACK = Color.BLACK.getRGB();

    /** Color for background pixels. */
    private static final int RED = Color.RED.getRGB();

    /** Color for hole pixels. */
    private static final int GREEN = Color.GREEN.getRGB();

    /** Color for irrelevant pixels (fully transparent). */
    private static final int TRANS = new Color(0, 0, 0, 0).getRGB();

    //~ Instance fields --------------------------------------------------------
    private final Shape shape;

    private final int interline;

    private final Map<Key, Template> variants = new HashMap<Key, Template>();

    private int width = -1; // Template width

    private int height = -1; // Template height

    //~ Constructors -----------------------------------------------------------
    /**
     * Creates a new ShapeDescriptor object.
     *
     * @param shape     the described shape
     * @param interline global scale value
     */
    public ShapeDescriptor (Shape shape,
                            int interline)
    {
        this.shape = shape;
        this.interline = interline;

        buildAllVariants();
    }

    //~ Methods ----------------------------------------------------------------
    //----------//
    // evaluate //
    //----------//
    /**
     * Try all defined templates at specified location and report
     * the best distance found.
     *
     * @param x         location abscissa
     * @param y         location ordinate
     * @param anchor    location WRT template
     * @param distances table of distances
     * @param line      use of middle hasLine or not
     * @return the best distance found
     */
    public double evaluate (int x,
                            int y,
                            Anchor anchor,
                            DistanceTable distances,
                            boolean line)
    {
        double best = Double.MAX_VALUE;

        for (Entry<Key, Template> entry : variants.entrySet()) {
            if (entry.getKey().hasLine == line) {
                best = Math.min(
                        best,
                        entry.getValue().evaluate(x, y, anchor, distances));
            }
        }

        return best;
    }

    //-----------//
    // getHeight //
    //-----------//
    public int getHeight ()
    {
        return height;
    }

    //-----------//
    // getOffset //
    //-----------//
    public Point getOffset (Anchored.Anchor anchor)
    {
        for (Template tpl : variants.values()) {
            return tpl.getOffset(anchor);
        }

        return null;
    }

    //----------//
    // getShape //
    //----------//
    public Shape getShape ()
    {
        return shape;
    }

    //-------------------//
    // getSymbolBoundsAt //
    //-------------------//
    /**
     * Report the symbol bounds, which may be smaller than the template
     * bounds, because of margins.
     *
     * @param x      abscissa for anchor
     * @param y      ordinate for anchor
     * @param anchor reference for (x, y)
     * @return the template bounds
     */
    public Rectangle getSymbolBoundsAt (int x,
                                        int y,
                                        Anchored.Anchor anchor)
    {
        for (Template tpl : variants.values()) {
            return tpl.getSymbolBoundsAt(x, y, anchor);
        }

        return null;
    }

    //-------------//
    // getTemplate //
    //-------------//
    public Template getTemplate (Key key)
    {
        return variants.get(key);
    }

    //---------------------//
    // getTemplateBoundsAt //
    //---------------------//
    /**
     * Report the template bounds, which may be larger than the symbol
     * bounds, because of margins.
     *
     * @param x      abscissa for anchor
     * @param y      ordinate for anchor
     * @param anchor reference for (x, y)
     * @return the template bounds
     */
    public Rectangle getTemplateBoundsAt (int x,
                                          int y,
                                          Anchored.Anchor anchor)
    {
        for (Template tpl : variants.values()) {
            return tpl.getBoundsAt(x, y, anchor);
        }

        return null;
    }

    //----------//
    // getWidth //
    //----------//
    public int getWidth ()
    {
        return width;
    }

    //-------------//
    // putTemplate //
    //-------------//
    public void putTemplate (Key key,
                             Template tpl)
    {
        variants.put(key, tpl);
    }

    //----------//
    // toString //
    //----------//
    @Override
    public String toString ()
    {
        StringBuilder sb = new StringBuilder();
        sb.append(shape);
        sb.append("-");
        sb.append(interline);
        sb.append("(")
                .append(width)
                .append("x")
                .append(height)
                .append(")");

        return sb.toString();
    }

    //------------------//
    // computeDistances //
    //------------------//
    /**
     * Compute all distances to nearest foreground pixel.
     * For this we work as if there was a foreground rectangle right around
     * the image.
     * Similarly, the non-relevant pixels are assumed to be foreground.
     * This is to allow the detection of reliable background key points.
     *
     * @param img the source image
     * @param key the template specs
     * @return the table of distances to foreground
     */
    private static Table computeDistances (BufferedImage img,
                                           Key key)
    {
        // Retrieve foreground pixels
        final int width = img.getWidth();
        final int height = img.getHeight();
        final boolean[][] fore = new boolean[width + 2][height + 2];

        // Fill with img foreground pixels and irrelevant pixels
        for (int y = 1; y < (height + 1); y++) {
            for (int x = 1; x < (width + 1); x++) {
                Color pix = new Color(img.getRGB(x - 1, y - 1), true);

                if (pix.equals(Color.BLACK) || (pix.getAlpha() == 0)) {
                    fore[x][y] = true;
                }
            }
        }

        // Surround with a rectangle of foreground pixels
        for (int y = 0; y < (height + 2); y++) {
            fore[0][y] = true;
            fore[width + 1][y] = true;
        }

        for (int x = 0; x < (width + 2); x++) {
            fore[x][0] = true;
            fore[x][height + 1] = true;
        }

        // Compute template distance transform
        final Table distances = new ChamferDistance.Short().compute(fore);

        if (logger.isDebugEnabled()) {
            distances.dump(key + "  distances");
        }

        // Trim the distance table of its surrounding rectangle?
        return distances;
    }

    //---------//
    // getCode //
    //---------//
    private static int getCode (Key key)
    {
        switch (key.shape) {
        case NOTEHEAD_BLACK:
        case NOTEHEAD_BLACK_SMALL:
            return 207;

        case NOTEHEAD_VOID:
        case NOTEHEAD_VOID_SMALL:
            return 250;

        case WHOLE_NOTE:
        case WHOLE_NOTE_SMALL:
            return 119;
        }

        logger.error(key.shape + " is not supported!");

        return 0;
    }

    //--------------//
    // getKeyPoints //
    //--------------//
    /**
     * Build the collection of key points to be used for matching
     * tests.
     * These are the locations where the image distance value will be checked
     * against the recorded template distance value.
     * TODO: We could carefully select a subset of these locations?
     *
     * @param img       the template source image
     * @param distances the template distances (extended on each direction)
     * @return the collection of key locations, with their corresponding
     *         distance value
     */
    private static List<PixelDistance> getKeyPoints (BufferedImage img,
                                                     Table distances)
    {
        // Generate key points
        List<PixelDistance> keyPoints = new ArrayList<PixelDistance>();

        for (int y = 0, h = img.getHeight(); y < h; y++) {
            for (int x = 0, w = img.getWidth(); x < w; x++) {
                Color pix = new Color(img.getRGB(x, y), true);

                // Select only relevant pixels
                if (pix.getAlpha() == 255) {
                    if (pix.getGreen() != 0) {
                        // Green = hole, use dist to nearest foreground
                        keyPoints.add(
                                new PixelDistance(
                                        x,
                                        y,
                                        distances.getValue(x + 1, y + 1)));
                    } else if (pix.getRed() != 0) {
                        // Red = background, use dist to nearest foreground
                        keyPoints.add(
                                new PixelDistance(
                                        x,
                                        y,
                                        distances.getValue(x + 1, y + 1)));
                    } else {
                        // Black = foreground,  dist to nearest foreground is 0
                        keyPoints.add(new PixelDistance(x, y, 0));
                    }
                }
            }
        }

        return keyPoints;
    }

    //------------//
    // addAnchors //
    //------------//
    /**
     * Add specific anchors to the template.
     * All templates get basic anchors at construction time, but some may need
     * additional anchors.
     *
     * @param template the template to populate
     */
    private void addAnchors (Template template)
    {
        // Symbol bounds (relative to template origin)
        Rectangle sym = template.getSymbolBounds();

        // Define common basic anchors
        template.addAnchor(
                Anchor.CENTER,
                (sym.x + (0.5 * (sym.width - 1))) / width,
                (sym.y + (0.5 * (sym.height - 1))) / height);
        template.addAnchor(
                Anchor.MIDDLE_LEFT,
                (double) sym.x / width,
                (sym.y + (0.5 * (sym.height - 1))) / height);

        // WHOLE_NOTE & WHOLE_NOTE_SMALL are not concerned further
        if (ShapeSet.Notes.contains(template.getKey().shape)) {
            return;
        }

        // Add anchors for potential stems on left and right sides
        final double dx = constants.stemDx.getValue();
        final double left = (sym.x + (dx * (sym.width - 1))) / width;
        final double right = (sym.x + ((1 - dx) * (sym.width - 1))) / width;

        final double dy = constants.stemDy.getValue();
        final double top = (sym.y + (dy * (sym.height - 1))) / height;
        final double bottom = (sym.y + ((1 - dy) * (sym.height - 1))) / height;

        template.addAnchor(Anchor.TOP_LEFT_STEM, left, top);
        template.addAnchor(Anchor.LEFT_STEM, left, 0.5);
        template.addAnchor(Anchor.BOTTOM_LEFT_STEM, left, bottom);

        template.addAnchor(Anchor.TOP_RIGHT_STEM, right, top);
        template.addAnchor(Anchor.RIGHT_STEM, right, 0.5);
        template.addAnchor(Anchor.BOTTOM_RIGHT_STEM, right, bottom);
    }

    //----------//
    // addHoles //
    //----------//
    /**
     * Background pixels inside a given shape must be recognized as
     * such.
     * <p>
     * Such pixels are marked with a specific color (green foreground) so that
     * the template can measure their distance to (black) foreground.
     *
     * @param img   the source image
     * @param lines the hasLines configuration
     */
    private void addHoles (BufferedImage img,
                           boolean line)
    {
        if (shapesWithHoles.contains(shape)) {
            // Identify holes
            final List<Point> holeSeeds = new ArrayList<Point>();

            if (line) {
                // We have a ledger in the middle, with holes above and below
                if ((shape == WHOLE_NOTE) || (shape == WHOLE_NOTE_SMALL)) {
                    holeSeeds.add(
                            new Point(
                                    (int) Math.rint(img.getWidth() * 0.5),
                                    (int) Math.rint(img.getHeight() * 0.33)));
                    holeSeeds.add(
                            new Point(
                                    (int) Math.rint(img.getWidth() * 0.5),
                                    (int) Math.rint(img.getHeight() * 0.67)));
                } else {
                    holeSeeds.add(
                            new Point(
                                    (int) Math.rint(img.getWidth() * 0.7),
                                    (int) Math.rint(img.getHeight() * 0.33)));
                    holeSeeds.add(
                            new Point(
                                    (int) Math.rint(img.getWidth() * 0.2),
                                    (int) Math.rint(img.getHeight() * 0.6)));
                }
            } else {
                // We have no ledger, just a big hole in the center
                holeSeeds.add(
                        new Point(img.getWidth() / 2, img.getHeight() / 2));
            }

            // Fill the holes if any with green color
            FloodFiller floodFiller = new FloodFiller(img);

            for (Point seed : holeSeeds) {
                // Background (red) -> background (green)
                floodFiller.fill(seed.x, seed.y, RED, GREEN);
            }
        }
    }

    //----------//
    // binarize //
    //----------//
    /**
     * Use only fully black or fully transparent pixels
     *
     * @param img       the source image
     * @param threshold alpha level to separate relevant from irrelevant pixels
     */
    private void binarize (BufferedImage img,
                           int threshold)
    {
        for (int y = 0, h = img.getHeight(); y < h; y++) {
            for (int x = 0, w = img.getWidth(); x < w; x++) {
                Color pix = new Color(img.getRGB(x, y), true);

                if (pix.getAlpha() >= threshold) {
                    Color color = new Color(pix.getRGB(), false);

                    if (color.getRed() >= threshold) {
                        img.setRGB(x, y, RED);
                    } else {
                        img.setRGB(x, y, BLACK);
                    }
                } else {
                    img.setRGB(x, y, TRANS);
                }
            }
        }
    }

    //------------------//
    // buildAllVariants //
    //------------------//
    private void buildAllVariants ()
    {
        final MusicFont font = getFont(interline);

        for (boolean line : new boolean[]{false, true}) {
            Key key = new Key(shape, line);
            Template tpl = createTemplate(key, font);
            putTemplate(key, tpl);
        }
    }

    //----------------//
    // createTemplate //
    //----------------//
    /**
     * Build a template for desired shape and size, based on
     * MusicFont.
     * TODO: Implement a better way to select representative key points
     * perhaps using a skeleton for foreground and another skeleton for
     * holes?
     *
     * @param key  full identification of the template
     * @param font the underlying music font properly scaled
     * @return the brand new template
     */
    private Template createTemplate (Key key,
                                     MusicFont font)
    {
        // Get symbol image painted on template rectangle
        final TemplateSymbol symbol = new TemplateSymbol(key, getCode(key));
        final BufferedImage img = symbol.buildImage(font);
        width = img.getWidth();
        height = img.getHeight();

        binarize(img, 127);

        // Distances to foreground
        final Table distances = computeDistances(img, key);

        // Add holes if any
        addHoles(img, key.hasLine);

        // Flag non-relevant pixels
        flagIrrelevantPixels(img, distances);

        // Store a copy on disk for visual check?
        if (constants.keepTemplates.isSet()) {
            ImageUtil.saveOnDisk(img, key + ".tpl" + interline);
        }

        // Generate key points for relevant pixels (fore, holes or back)
        final List<PixelDistance> keyPoints = getKeyPoints(img, distances);

        // Generate the template instance
        Template template = new Template(
                key,
                width,
                height,
                symbol.getSymbolBounds(font),
                keyPoints);

        // Add specific anchor points, if any
        addAnchors(template);

        if (logger.isDebugEnabled()) {
            logger.info("Created {}", template);
            template.dump();
        }

        return template;
    }

    //----------------------//
    // flagIrrelevantPixels //
    //----------------------//
    /**
     * Some pixels in (non-hole) background regions must be set as non
     * relevant.
     * They are roughly the "exterior" half of these regions, where the distance
     * to foreground would be impacted by the presence of nearby stem or staff /
     * ledger line.
     * Only the "interior" half of such region is relevant, for its distance to
     * foreground is not dependent upon the presence of stem or line.
     *
     * @param img the image to modify
     */
    private void flagIrrelevantPixels (BufferedImage img,
                                       Table distances)
    {
        // First browse from each foreground pixel in the 4 directions to first
        // non-foreground pixel. If this pixel is relevant and non-hole, flag it
        // as a border pixel.
        Set<Point> borders = getBorders(img);

        // Then, extend each border pixel in the 4 directions as long as the
        // distance read for the pixel increases.
        Table extensions = getExtensions(borders, img, distances);

        // The border pixels and extensions compose the relevant part of
        // (non-hole background) regions.
        // Flag the other (non-hole) background pixels as irrelevant
        for (int y = 0, h = img.getHeight(); y < h; y++) {
            for (int x = 0, w = img.getWidth(); x < w; x++) {
                // Check (non-hole) background pixel
                if (img.getRGB(x, y) == RED) {
                    if (extensions.getValue(x, y) != 1) {
                        img.setRGB(x, y, TRANS);
                    }
                }
            }
        }
    }

    //------------//
    // getBorders //
    //------------//
    private Set<Point> getBorders (BufferedImage img)
    {
        final Set<Point> borders = new HashSet<Point>();

        for (int y = 0, h = img.getHeight(); y < h; y++) {
            for (int x = 0, w = img.getWidth(); x < w; x++) {
                // Check foreground pixel
                if (img.getRGB(x, y) == BLACK) {
                    // North
                    for (int ny = y - 1; ny >= 0; ny--) {
                        int pix = img.getRGB(x, ny);

                        if (pix != BLACK) {
                            if (pix == RED) {
                                borders.add(new Point(x, ny));
                            }

                            break;
                        }
                    }

                    // South
                    for (int ny = y + 1; ny < h; ny++) {
                        int pix = img.getRGB(x, ny);

                        if (pix != BLACK) {
                            if (pix == RED) {
                                borders.add(new Point(x, ny));
                            }

                            break;
                        }
                    }

                    // West
                    for (int nx = x - 1; nx >= 0; nx--) {
                        int pix = img.getRGB(nx, y);

                        if (pix != BLACK) {
                            if (pix == RED) {
                                borders.add(new Point(nx, y));
                            }

                            break;
                        }
                    }

                    // East
                    for (int nx = x + 1; nx < w; nx++) {
                        int pix = img.getRGB(nx, y);

                        if (pix != BLACK) {
                            if (pix == RED) {
                                borders.add(new Point(nx, y));
                            }

                            break;
                        }
                    }
                }
            }
        }

        return borders;
    }

    //---------------//
    // getExtensions //
    //---------------//
    private Table getExtensions (Set<Point> borders,
                                 BufferedImage img,
                                 Table distances)
    {
        Table ext = new Table.UnsignedByte(img.getWidth(), img.getHeight());
        ext.fill(0);

        final int w = img.getWidth();
        final int h = img.getHeight();

        for (Point p : borders) {
            ext.setValue(p.x, p.y, 1);

            int dist = distances.getValue(p.x + 1, p.y + 1);

            // North
            for (int ny = p.y - 1; ny >= 0; ny--) {
                int d = distances.getValue(p.x + 1, ny + 1);

                if (d > dist) {
                    dist = d;
                    ext.setValue(p.x, ny, 1);
                } else {
                    break;
                }
            }

            // South
            dist = distances.getValue(p.x + 1, p.y + 1);

            for (int ny = p.y + 1; ny < h; ny++) {
                int d = distances.getValue(p.x + 1, ny + 1);

                if (d > dist) {
                    dist = d;
                    ext.setValue(p.x, ny, 1);
                } else {
                    break;
                }
            }

            // West
            dist = distances.getValue(p.x + 1, p.y + 1);

            for (int nx = p.x - 1; nx >= 0; nx--) {
                int d = distances.getValue(nx + 1, p.y + 1);

                if (d > dist) {
                    dist = d;
                    ext.setValue(nx, p.y, 1);
                } else {
                    break;
                }
            }

            // East
            dist = distances.getValue(p.x + 1, p.y + 1);

            for (int nx = p.x + 1; nx < w; nx++) {
                int d = distances.getValue(nx + 1, p.y + 1);

                if (d > dist) {
                    dist = d;
                    ext.setValue(nx, p.y, 1);
                } else {
                    break;
                }
            }
        }

        return ext;
    }

    //~ Inner Classes ----------------------------------------------------------
    //-----------//
    // Constants //
    //-----------//
    private static final class Constants
            extends ConstantSet
    {
        //~ Instance fields ----------------------------------------------------

        final Constant.Boolean keepTemplates = new Constant.Boolean(
                false,
                "Should we keep the templates images?");

        final Constant.Ratio stemDx = new Constant.Ratio(
                0.05,
                "(Ratio) abscissa of stem anchor WRT symbol width");

        final Constant.Ratio stemDy = new Constant.Ratio(
                0.15,
                "(Ratio) ordinate of stem anchor WRT symbol height");
    }
}