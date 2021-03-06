package org.apache.ctakes.examples.ae;

import org.apache.ctakes.core.pipeline.PipeBitInfo;
import org.apache.ctakes.core.util.Pair;
import org.apache.ctakes.core.util.regex.RegexSpanFinder;
import org.apache.ctakes.typesystem.type.textspan.Segment;
import org.apache.log4j.Logger;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;

import java.util.Arrays;
import java.util.Collection;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author SPF , chip-nlp
 * @version %I%
 * @since 8/27/2017
 */
@PipeBitInfo(
      name = "RegexBpFinder",
      description = "Detect Blood Pressure values in Vital Signs Section", role = PipeBitInfo.Role.ANNOTATOR
)
final public class RegexBpFinder extends JCasAnnotator_ImplBase {

   static private final Logger LOGGER = Logger.getLogger( "RegexBpFinder" );

   static private final Collection<String> BP_SECTIONS = Arrays.asList( "Vital Signs", "General Exam", "Objective", "SIMPLE_SEGMENT" );
   static private final String BP_TRIGGER = "\\bB\\/?P(?:\\s*:)?\\s+";
   static private final String VIT_BP_TRIGGER = "^VITS?:\\s+";

   static private final String BP_VALUES = "\\d{2,3} ?\\/ ?\\d{2,3}\\b";

   static private final RegexSpanFinder BP_SPAN_FINDER
         = new RegexSpanFinder( BP_TRIGGER + BP_VALUES );

   static private final RegexSpanFinder VIT_SPAN_FINDER
         = new RegexSpanFinder( VIT_BP_TRIGGER, Pattern.CASE_INSENSITIVE | Pattern.MULTILINE, 1000 );

   /**
    * {@inheritDoc}
    */
   @Override
   public void process( final JCas jCas ) throws AnalysisEngineProcessException {
      LOGGER.info( "Finding Blood Pressure values in Vital Signs section ..." );

      // Get the sections
      JCasUtil.select( jCas, Segment.class ).stream()
            // filter by sections with the id "Vital Signs"
            .filter( s -> BP_SECTIONS.contains( s.getId() ) )
            // find blood pressure values
            .forEach( RegexBpFinder::logBloodPressure );

      LOGGER.info( "Finished." );
   }


   static private void logBloodPressure( final Segment section ) {
      final String sectionText = section.getCoveredText();
      if ( !section.getId().equals( "SIMPLE_SEGMENT" ) ) {
         logBloodPressure( sectionText );
         return;
      }
      final Collection<Pair<Integer>> spans = VIT_SPAN_FINDER.findSpans( sectionText );
      for ( Pair<Integer> span : spans ) {
         final int eol = sectionText.indexOf( '\n', span.getValue2() );
         if ( eol < 0 ) {
            break;
         }
         final String text = sectionText.substring( span.getValue2(), eol );
         logBloodPressure( text );
      }
   }

   static private void logBloodPressure( final String text ) {
      final Collection<String> values = BP_SPAN_FINDER.findSpans( text ).stream()
            // switch from spans to text
            .map( p -> text.substring( p.getValue1(), p.getValue2() ) )
            // get rid of the bp trigger word
            .map( t -> t.replaceAll( BP_TRIGGER, "" ) )
            // get rid of whitespace on ends
            .map( String::trim )
            .collect( Collectors.toList() );
      if ( !values.isEmpty() ) {
         LOGGER.info( "Found " + values.size() + " Blood Pressure value(s)" );
         values.forEach( LOGGER::info );
      }
   }

   /**
    * Close the RegexSpanFinder when the run is complete, otherwise a thread will wait forever
    */
   @Override
   public void collectionProcessComplete() throws org.apache.uima.analysis_engine.AnalysisEngineProcessException {
      BP_SPAN_FINDER.close();
      VIT_SPAN_FINDER.close();
      super.collectionProcessComplete();
   }


}
