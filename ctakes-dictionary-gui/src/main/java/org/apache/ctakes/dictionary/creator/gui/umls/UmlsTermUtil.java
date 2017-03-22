package org.apache.ctakes.dictionary.creator.gui.umls;

import org.apache.ctakes.dictionary.creator.util.FileUtil;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.function.Consumer;
import java.util.stream.Collectors;


/**
 * Contains all the methods used to parse individual text definitions of umls terms
 * <p/>
 * Author: SPF
 * Affiliation: CHIP-NLP
 * Date: 1/16/14
 */
final public class UmlsTermUtil {


   private enum DATA_FILE {
      REMOVAL_PREFIX_TRIGGERS( "RemovalPrefixTriggers.txt" ),
      REMOVAL_SUFFIX_TRIGGERS( "RemovalSuffixTriggers.txt" ),
      REMOVAL_FUNCTION_TRIGGERS( "RemovalFunctionTriggers.txt" ),
      REMOVAL_COLON_TRIGGERS( "RemovalColonTriggers.txt" ),
      UNWANTED_PREFIXES( "UnwantedPrefixes.txt" ),
      UNWANTED_SUFFIXES( "UnwantedSuffixes.txt" ),
      MODIFIER_SUFFIXES( "ModifierSuffixes.txt" ),
      RIGHT_ABBREVIATIONS( "RightAbbreviations.txt" );
      final private String __name;

      DATA_FILE( final String name ) {
         __name = name;
      }
   }

   static private String getDataPath( final String dataDir, final DATA_FILE dataFile ) {
      return dataDir + '/' + dataFile.__name;
   }

   final private Collection<String> _removalPrefixTriggers;
   final private Collection<String> _removalSuffixTriggers;
   final private Collection<String> _removalColonTriggers;
   final private Collection<String> _removalFunctionTriggers;
   final private Collection<String> _unwantedPrefixes;
   final private Collection<String> _unwantedSuffixes;
   final private Collection<String> _modifierSuffixes;
   final private Collection<String> _abbreviations;

   public UmlsTermUtil( final String dataDir ) {
      this( getDataPath( dataDir, DATA_FILE.REMOVAL_PREFIX_TRIGGERS ),
            getDataPath( dataDir, DATA_FILE.REMOVAL_SUFFIX_TRIGGERS ),
            getDataPath( dataDir, DATA_FILE.REMOVAL_COLON_TRIGGERS ),
            getDataPath( dataDir, DATA_FILE.REMOVAL_FUNCTION_TRIGGERS ),
            getDataPath( dataDir, DATA_FILE.UNWANTED_PREFIXES ),
            getDataPath( dataDir, DATA_FILE.UNWANTED_SUFFIXES ),
            getDataPath( dataDir, DATA_FILE.MODIFIER_SUFFIXES ),
            getDataPath( dataDir, DATA_FILE.RIGHT_ABBREVIATIONS ) );
   }

   public UmlsTermUtil( final String removalPrefixTriggersPath, final String removalSuffixTriggersPath,
                        final String removalColonTriggersPath, final String removalFunctionTriggersPath,
                        final String unwantedPrefixesPath, final String unwantedSuffixesPath,
                        final String modifierSuffixesPath, final String abbreviationsPath ) {
      _removalPrefixTriggers = FileUtil.readOneColumn( removalPrefixTriggersPath, "term removal Prefix Triggers" );
      _removalSuffixTriggers = FileUtil.readOneColumn( removalSuffixTriggersPath, "term removal Suffix Triggers" );
      _removalColonTriggers = FileUtil.readOneColumn( removalColonTriggersPath, "term removal Colon Triggers" );
      _removalFunctionTriggers = FileUtil.readOneColumn( removalFunctionTriggersPath, "term removal Function Triggers" );
      _unwantedPrefixes = FileUtil.readOneColumn( unwantedPrefixesPath, "unwanted Prefixes" );
      _unwantedSuffixes = FileUtil.readOneColumn( unwantedSuffixesPath, "unwanted Suffixes" );
      _modifierSuffixes = FileUtil.readOneColumn( modifierSuffixesPath, "modifier Suffixes" );
      _abbreviations = FileUtil.readOneColumn( abbreviationsPath, "Abbreviations to expand" );
   }

   public boolean isTextValid( final String text ) {
      // Check for illegal characters
      for ( int i = 0; i < text.length(); i++ ) {
         if ( text.charAt( i ) < ' ' || text.charAt( i ) > '~' ) {
            return false;
         }
      }
      // Check for auto-created note form
      if ( text.split( "@" ).length > 2 ) {
         return false;
      }
      if ( text.length() == 3 && text.charAt( 0 ) == '(' ) {
         return false;
      }
      if ( _removalPrefixTriggers.stream().anyMatch( text::startsWith ) ) {
         return false;
      }
      if ( _removalSuffixTriggers.stream().anyMatch( text::endsWith ) ) {
         return false;
      }
      if ( _removalColonTriggers.stream().anyMatch( text::contains ) ) {
         return false;
      }
      if ( _removalFunctionTriggers.stream().anyMatch( text::contains ) ) {
         return false;
      }
      return true;
   }

   static public boolean isTextTooShort( final String text, final int minCharLength ) {
      return text.length() < minCharLength;
   }


   static public boolean isTextTooLong( final String text, final int maxCharLength,
                                 final int maxWordCount, final int maxSymCount ) {
      final String[] splits = text.split( "\\s+" );
      int wordCount = 0;
      int symCount = 0;
      for ( String split : splits ) {
         if ( split.length() > maxCharLength ) {
            return true;
         }
         if ( split.length() > 2 ) {
            wordCount++;
         } else {
            symCount++;
         }
      }
      return wordCount > maxWordCount || symCount > maxSymCount;
   }


   public Collection<String> getFormattedTexts( final String strippedText, final boolean extractAbbreviations,
                                                final int minCharLength, final int maxCharLength,
                                                final int maxWordCount, final int maxSymCount ) {
      Collection<String> extractedTerms = Collections.emptySet();
      if ( extractAbbreviations ) {
         // add embedded abbreviations
         extractedTerms = extractAbbreviations( strippedText );
      }
      if ( extractedTerms.isEmpty() ) {
         extractedTerms = extractModifiers( strippedText );
      }
      if ( !extractedTerms.isEmpty() ) {
         extractedTerms.add( strippedText );
         return getFormattedTexts( getPluralTerms( getStrippedTexts( extractedTerms ) ), minCharLength, maxCharLength, maxWordCount, maxSymCount );
      }
      Collection<String> texts = new HashSet<>( 1 );
      texts.add( strippedText );
      return getFormattedTexts( getPluralTerms( getStrippedTexts( texts ) ), minCharLength, maxCharLength, maxWordCount, maxSymCount );
   }


   static private Collection<String> getFormattedTexts( final Collection<String> extractedTerms,
                                                final int minCharLength, final int maxCharLength,
                                                final int maxWordCount, final int maxSymCount ) {
      return extractedTerms.stream()
            .filter( t -> !isTextTooShort( t, minCharLength ) )
            .filter( t -> !isTextTooLong( t, maxCharLength, maxWordCount, maxSymCount ) )
            .collect( Collectors.toList() );
   }

   static private Collection<String> getPluralTerms( final Collection<String> texts ) {
      final Collection<String> plurals = texts.stream()
            .filter( t -> t.endsWith( "( s )" ) )
            .collect( Collectors.toList() );
      if ( plurals.isEmpty() ) {
         return texts;
      }
      texts.removeAll( plurals );
      final Consumer<String> addPlural = t -> {
         texts.add( t );
         texts.add( t + "s" );
      };
      plurals.stream()
            .map( t -> t.substring( 0, t.length() - 5 ) )
            .forEach( addPlural );
      return texts;
   }

   private Collection<String> getStrippedTexts( final Collection<String> texts ) {
      return texts.stream()
            .map( this::getStrippedText )
            .filter( t -> !t.isEmpty() )
            .collect( Collectors.toSet() );
   }

   public String getStrippedText( final String text ) {
      // remove form underlines
//      if ( text.contains( "_ _ _" ) ) {
//         final int lastParen = text.lastIndexOf( '(' );
//         final int lastDash = text.indexOf( "_ _ _" );
//         final int deleteIndex = Math.max( 0, Math.min( lastParen, lastDash ) );
//         if ( deleteIndex > 0 ) {
//            return getStrippedText( text.substring( 0, deleteIndex - 1 ).trim() );
//         }
//      }
      // remove unmatched parentheses, brackets, etc.
      //      if ( text.startsWith( "(" ) && !text.contains( ")" ) ) {
      //         return getStrippedText( text.substring( 1 ).trim() );
      //      }
      //      if ( text.startsWith( "[" ) && !text.contains( "]" ) ) {
      //         return getStrippedText( text.substring( 1 ).trim() );
      //      }
      //      if ( text.startsWith( "(" ) && text.endsWith( ") or" ) ) {
      //         return getStrippedText( text.substring( 1, text.length() - 4 ).trim() );
      //      }
      //      if ( text.startsWith( "or (" ) ) {
      //         return getStrippedText( text.substring( 2 ).trim() );
      //      }
      //      if ( text.startsWith( "\"" ) && text.endsWith( "\"" ) ) {
      //         return getStrippedText( text.substring( 1 ).trim() );
      //      }
      //      if ( text.startsWith( "(" ) && text.endsWith( ")" ) ) {
      //         return getStrippedText( text.substring( 1, text.length() - 2 ).trim() );
      //      }
      //      if ( text.startsWith( "[" ) && text.endsWith( "]" ) ) {
      //         return getStrippedText( text.substring( 1, text.length() - 2 ).trim() );
      //      }
      //      if ( text.startsWith( "&" ) ) {
      //         return getStrippedText( text.substring( 1 ).trim() );
      //      }
      //      if ( text.endsWith( "]" ) && !text.contains( "[" ) ) {
      //         return getStrippedText( text.substring( 0, text.length() - 2 ).trim() );
      //      }
      //      if ( text.endsWith( ")" ) && !text.contains( "(" ) ) {
      //         return getStrippedText( text.substring( 0, text.length() - 2 ).trim() );
      //      }
      String strippedText = text.trim();
      // Text in umls can have multiple suffixes and/or prefixes.  Stripping just once doesn't do the trick
      int lastLength = Integer.MAX_VALUE;
      while ( lastLength != strippedText.length() ) {
         lastLength = strippedText.length();
         for ( String prefix : _unwantedPrefixes ) {
            if ( strippedText.startsWith( prefix ) ) {
               strippedText = strippedText.substring( prefix.length() ).trim();
            }
         }
         for ( String suffix : _unwantedSuffixes ) {
            if ( strippedText.endsWith( suffix ) ) {
               strippedText = strippedText.substring( 0, strippedText.length() - suffix.length() ).trim();
            }
         }
         if ( !isTextValid( strippedText ) ) {
            return "";
         }
      }
      if ( strippedText.contains( "(" ) && strippedText.contains( "[" ) ) {
         return "";
      }
      return strippedText;
   }


   private Collection<String> extractAbbreviations( final String tokenizedText ) {
      for ( String abbreviation : _abbreviations ) {
         if ( tokenizedText.endsWith( abbreviation )
               && !tokenizedText.contains( ":" ) && !tokenizedText.contains( " of " )
               && !tokenizedText.contains( " for " ) ) {
            final String noAbbrTerm
                  = tokenizedText.substring( 0, tokenizedText.length() - abbreviation.length() ).trim();
            final String abbrTerm
                  = abbreviation.replace( ":", "" ).replace( "(", "" ).replace( ")", "" ).replace( "-", "" )
                  .replace( "[", "" ).replace( "]", "" ).replace( "&", "" ).trim();
            final Collection<String> extractedAbbreviations = new HashSet<>( 2 );
            extractedAbbreviations.add( noAbbrTerm );
            extractedAbbreviations.add( abbrTerm );
            return extractedAbbreviations;
         }
      }
      return Collections.emptyList();
   }

   private Collection<String> extractModifiers( final String tokenizedText ) {
      for ( String modifier : _modifierSuffixes ) {
         if ( tokenizedText.endsWith( modifier ) ) {
            final String mainText = tokenizedText.substring( 0, tokenizedText.length() - modifier.length() ).trim();
            final String modifierText
                  = modifier.replace( "(", "" ).replace( ")", "" ).replace( "-", "" ).replace( ",", "" ).trim();
            final Collection<String> modifiedTexts = new HashSet<>( 2 );
            modifiedTexts.add( tokenizedText );
            modifiedTexts.add( modifierText + " " + mainText );
            return modifiedTexts;
         }
      }
      return Collections.emptyList();
   }


}