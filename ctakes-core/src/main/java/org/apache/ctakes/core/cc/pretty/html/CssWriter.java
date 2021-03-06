package org.apache.ctakes.core.cc.pretty.html;


import org.apache.ctakes.core.cc.pretty.SemanticGroup;
import org.apache.log4j.Logger;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import static org.apache.ctakes.core.cc.pretty.html.HtmlTextWriter.*;

/**
 * @author SPF , chip-nlp
 * @version %I%
 * @since 10/15/2016
 */
final class CssWriter {

   static private final Logger LOGGER = Logger.getLogger( "CssWriter" );


   private CssWriter() {
   }

   /**
    * @param filePath path to css file
    */
   static void writeCssFile( final String filePath ) {
      final File outputFile = new File( filePath );
      outputFile.getParentFile().mkdirs();
      try ( final BufferedWriter writer = new BufferedWriter( new FileWriter( outputFile ) ) ) {
//         writer.write( setBody() );
         writer.write( setUnderline( GENERIC, "gray", "solid", "0.10" ) );
         writer.write( setUnderline( AFFIRMED, "green", "solid", "0.15" ) );
         writer.write( setUnderline( UNCERTAIN, "gold", "dotted", "0.16" ) );
         writer.write( setUnderline( NEGATED, "red", "dashed", "0.16" ) );
         writer.write( setUnderline( UNCERTAIN_NEGATED, "orange", "dashed", "0.16" ) );

         writer.write( setSuperColor( SemanticGroup.FINDING.getCode(), "magenta" ) );
         writer.write( setSuperColor( SemanticGroup.DISORDER.getCode(), "black" ) );
         writer.write( setSuperColor( SemanticGroup.MEDICATION.getCode(), "red" ) );
         writer.write( setSuperColor( SemanticGroup.PROCEDURE.getCode(), "blue" ) );
         writer.write( setSuperColor( SemanticGroup.ANATOMICAL_SITE.getCode(), "gray" ) );
         writer.write( setSuperColor( SemanticGroup.UNKNOWN_SEMANTIC_CODE, "gray" ) );
         writer.write( getToolTipCss() );
         writer.write( getRightDivCss() );
      } catch ( IOException ioE ) {
         LOGGER.error( "Could not not write css file " + outputFile.getPath() );
         LOGGER.error( ioE.getMessage() );
      }
   }


   static private String setBody() {
      return "\nbody {\n" +
             "  margin: 20px;\n" +
            "}\n";
   }

   // dashType is solid or dashed or double or dotted     size is relative: 0.1 or 0.2 for 10%, 20%
   // See https://css-tricks.com/styling-underlines-web/ shadow for another possibility
   static private String setUnderline( final String className, final String color, final String dashType,
                                       final String size ) {
      return "\n." + className + " {\n" +
             "  position: relative;\n" +
             "  display: inline-block " + color + ";\n" +
             "  border-bottom: " + size + "em " + dashType + " " + color + ";\n" +
             "}\n";
   }

   static private String setSuperColor( final String className, final String color ) {
      return "\n." + className + " {\n" +
            "  color: " + color + ";\n" +
            "}\n";
   }


   static private String setHighlight( final String idName, final String color ) {
      // PowderBlue
      return "#" + idName + "{\n  background-color: " + color + ";\n}\n";
   }

   static private String getToolTipCss() {
      return
            // position z
            "\n[" + TOOL_TIP + "] {\n" +
                  "  position: relative;\n" +
            "  z-index: 2;\n" +
            "  cursor: pointer;\n" +
            "}\n" +
            // invisible
                  "[" + TOOL_TIP + "]::before,\n" +
                  "[" + TOOL_TIP + "]::after {\n" +
                  "  visibility: hidden;\n" +
            "  -ms-filter: \"progid:DXImageTransform.Microsoft.Alpha(Opacity=0)\";\n" +
            "  filter: progid: DXImageTransform.Microsoft.Alpha(Opacity=0);\n" +
            "  opacity: 0;\n" +
            "  pointer-events: none;\n" +
            "}\n" +
            // position & sketch
                  "[" + TOOL_TIP + "]::before {\n" +
                  "  position: absolute;\n" +
                  "  bottom: 0%;\n" +
                  "  left: 100%;\n" +
                  "  margin-bottom: 5px;\n" +
            "  padding: 7px;\n" +
            "  -webkit-border-radius: 3px;\n" +
            "  -moz-border-radius: 3px;\n" +
            "  border-radius: 3px;\n" +
            "  background-color: #000;\n" +
            "  background-color: hsla(0, 0%, 20%, 0.9);\n" +
            "  color: #fff;\n" +
                  "  content: attr(" + TOOL_TIP + ");\n" +
                  "  text-align: center;\n" +
            "  font-size: 14px;\n" +
            "  line-height: 1.2;\n" +
            "}\n" +
            // hover show
                  "[" + TOOL_TIP + "]:hover::before,\n" +
                  "[" + TOOL_TIP + "]:hover::after {\n" +
                  "  visibility: visible;\n" +
            "  -ms-filter: \"progid:DXImageTransform.Microsoft.Alpha(Opacity=100)\";\n" +
            "  filter: progid: DXImageTransform.Microsoft.Alpha(Opacity=100);\n" +
            "  opacity: 1;\n" +
            "}\n";
   }


   static private String getRightDivCss() {
      return "\ndiv#ia {\n" +
            "  position: fixed;\n" +
            "  top: 0;\n" +
            "  right: 0;\n" +
            "  width: 20%;\n" +
            "  height: 100%;\n" +
            "  padding: 10px;\n" +
            "  overflow: auto;\n" +
            "  background-color: lightgray;\n" +
            "}\n" +
            "\ndiv#content {\n" +
            "  width: 79%;\n" +
            "  height: 100%;\n" +
            "  padding: 10px;\n" +
            "  overflow: auto;\n" +
            "}\n";
   }


}
