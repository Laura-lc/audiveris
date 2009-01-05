//----------------------------------------------------------------------------//
//                                                                            //
//                             C l a s s U t i l                              //
//                                                                            //
//  Copyright (C) Herve Bitteur 2000-2007. All rights reserved.               //
//  This software is released under the GNU General Public License.           //
//  Contact author at herve.bitteur@laposte.net to report bugs & suggestions. //
//----------------------------------------------------------------------------//
//
package omr.util;


/**
 * Class <code>ClassUtil</code> provides utilities related to Class handling.
 *
 * @author Herv&eacute Bitteur
 * @version $Id$
 */
public class ClassUtil
{
    //~ Methods ----------------------------------------------------------------

    //-----------------//
    // getCallingFrame //
    //-----------------//
    /**
     * Infer the calling frame, skipping the given classes if so provided.
     * Code was derived from a private method found in the JDK Logger class
     *
     * @param skippedClasses the classes to skip
     * @return the frame found, just before the skipped classes (or just before
     * the caller of this method)
     */
    public static StackTraceElement getCallingFrame (Class... skippedClasses)
    {
        // Get the current stack trace.
        StackTraceElement[] stack = (new Throwable()).getStackTrace();

        // Simple case, no classes to skip, just return the caller of the caller
        if (skippedClasses.length == 0) {
            return stack[2];
        }

        // More complex case, return the caller, just before the skipped classes
        // First, search back to a method in the skipped classes, if any
        int ix;
        searchingForSkipped: 
        for (ix = 0; ix < stack.length; ix++) {
            StackTraceElement frame = stack[ix];
            String            cname = frame.getClassName();

            for (Class skipped : skippedClasses) {
                if (cname.equals(skipped.getName())) {
                    break searchingForSkipped;
                }
            }
        }

        // Now search for the first frame before the skipped classes
        searchingForNonSkipped: 
        for (; ix < stack.length; ix++) {
            StackTraceElement frame = stack[ix];
            String            cname = frame.getClassName();

            for (Class skipped : skippedClasses) {
                if (cname.equals(skipped.getName())) {
                    continue searchingForNonSkipped;
                }
            }

            // We've found the relevant frame.
            return frame;
        }

        // We haven't found a suitable frame
        return null;
    }

    //--------//
    // nameOf //
    //--------//
    /**
     * Report the full name of the object class, without the package information
     *
     * @param obj the object to name
     * @return the concatenation of (enclosing) simple names
     */
    public static String nameOf (Object obj)
    {
        StringBuilder sb = new StringBuilder();

        for (Class cl = obj.getClass(); cl != null;
             cl = cl.getEnclosingClass()) {
            if (sb.length() > 0) {
                sb.insert(0, "-");
            }

            sb.insert(0, cl.getSimpleName());
        }

        return sb.toString();
    }
}