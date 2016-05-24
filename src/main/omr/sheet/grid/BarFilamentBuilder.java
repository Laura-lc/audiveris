//------------------------------------------------------------------------------------------------//
//                                                                                                //
//                               B a r F i l a m e n t B u i l d e r                              //
//                                                                                                //
//------------------------------------------------------------------------------------------------//
// <editor-fold defaultstate="collapsed" desc="hdr">
//  Copyright © Hervé Bitteur and others 2000-2016. All rights reserved.
//  This software is released under the GNU General Public License.
//  Goto http://kenai.com/projects/audiveris to report bugs or suggestions.
//------------------------------------------------------------------------------------------------//
// </editor-fold>
package omr.sheet.grid;

import omr.glyph.dynamic.Filament;

import omr.lag.Section;
import static omr.run.Orientation.HORIZONTAL;

import omr.sheet.Sheet;

import omr.util.Navigable;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

/**
 * Class {@code BarFilamentBuilder}
 *
 * @author Hervé Bitteur
 */
public class BarFilamentBuilder
{
    //~ Instance fields ----------------------------------------------------------------------------

    /** Related sheet. */
    @Navigable(false)
    private final Sheet sheet;

    /** Specific factory for peak-based filaments. */
    private final BarFilamentFactory factory;

    //~ Constructors -------------------------------------------------------------------------------
    /**
     * Creates a new {@code BarFilamentBuilder} object.
     *
     * @param sheet the containing sheet
     */
    public BarFilamentBuilder (Sheet sheet)
    {
        this.sheet = sheet;

        factory = new BarFilamentFactory(sheet.getScale());
    }

    //~ Methods ------------------------------------------------------------------------------------
    //---------------//
    // buildFilament //
    //---------------//
    /**
     * Build a bar/bracket/brace filament out of provided sections.
     *
     * @param peak              the peak to process
     * @param verticalExtension additional margin beyond staff to detect bracket/brace ends
     * @param allSections       large pre-filtered collection of candidates (ordered by abscissa)
     * @return the filament built, or null
     */
    public Filament buildFilament (StaffPeak peak,
                                   int verticalExtension,
                                   List<Section> allSections)
    {
        final Rectangle peakBox = peak.getBounds();

        // Increase height slightly beyond staff
        peakBox.grow(0, verticalExtension);

        final int xBreak = peakBox.x + peakBox.width;
        final List<Section> sections = new ArrayList<Section>();
        final int maxSectionWidth = peak.getWidth(); // Width of this particular peak

        for (Section section : allSections) {
            final Rectangle sectionBox = section.getBounds();

            if (sectionBox.intersects(peakBox)) {
                if (section.getLength(HORIZONTAL) <= maxSectionWidth) {
                    sections.add(section);
                }
            } else if (sectionBox.x >= xBreak) {
                break; // Since allSections are sorted by abscissa
            }
        }

        Filament filament = factory.buildBarFilament(sections, peak.getBounds());

        if (filament != null) {
            sheet.getFilamentIndex().register(filament);
        }

        return filament;
    }
}
