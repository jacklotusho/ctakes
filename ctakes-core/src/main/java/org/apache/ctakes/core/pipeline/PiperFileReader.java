package org.apache.ctakes.core.pipeline;


import org.apache.ctakes.core.cc.XmiWriterCasConsumerCtakes;
import org.apache.ctakes.core.resource.FileLocator;
import org.apache.log4j.Logger;
import org.apache.uima.UIMAException;
import org.apache.uima.analysis_component.AnalysisComponent;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.collection.CollectionReader;
import org.apache.uima.resource.ResourceInitializationException;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Creates a pipeline (PipelineBuilder) from specifications in a flat plaintext file.
 *
 * <p>There are several basic commands:
 * package <i>user_package_name</i>
 * load <i>path_to_another_pipeline_file</i>
 * set <i>ae_parameter_name=ae_parameter_value e_parameter_name=ae_parameter_value</i> ...
 * cli <i>ae_parameter_name=cli_parameter_char e_parameter_name=cli_parameter_char</i> ...
 * reader <i>collection_reader_class_name</i>
 * readFiles <i>input_directory</i>
 *    <i>input_directory</i> can be empty if
 *    {@link org.apache.ctakes.core.config.ConfigParameterConstants#PARAM_INPUTDIR} ("InputDirectory") was specified
 * add <i>ae_or_cc_class_name ae_parameter_name=ae_parameter_value e_parameter_name<=ae_parameter_value</i> ...
 * addLogged <i>ae_or_cc_class_name ae_parameter_name=ae_parameter_value e_parameter_name=ae_parameter_value</i> ...
 * addDescription <i>ae_or_cc_class_name</i>
 * addLast <i>ae_or_cc_class_name</i>
 * collectCuis
 * collectEntities
 * writeXmis <i>output_directory</i>
 *    <i>output_directory</i> can be empty if
 *    {@link XmiWriterCasConsumerCtakes#PARAM_OUTPUTDIR} ("OutputDirectory") was specified
 * // and # and ! may be used to mark line comments
 * </p>
 * class names must be fully-specified with package unless they are in standard ctakes cr ae or cc packages,
 * or in a package specified by an earlier package command.
 *
 * @author SPF , chip-nlp
 * @version %I%
 * @since 10/10/2016
 */
@SuppressWarnings( "unchecked" )
final public class PiperFileReader {

   static private final Logger LOGGER = Logger.getLogger( "PiperFileReader" );

   static public final String AE_VIEW_NAMES = "AeViews";

   static private final String[] CTAKES_PACKAGES
         = { "core",
         "contexttokenizer",
         "postagger",
         "chunker",
         "dictionary.lookup.fast",
         "assertion",
         "dictionary.lookup2",
             "clinicalpipeline",
         "clinical.pipeline",
         "constituency.parser",
         "lvg",
         "relationextractor",
             "coreference",
             "dependency.parser",
             "temporal",
             "drug-ner",
             "necontexts",
         "preprocessor",
             "sideeffect",
             "smokingstatus",
         "dictionary.lookup",
             "template.filler" };

   static private final Object[] EMPTY_OBJECT_ARRAY = new Object[ 0 ];

   static private final Pattern SPACE_PATTERN = Pattern.compile( "\\s+" );
   static private final Pattern KEY_VALUE_PATTERN = Pattern.compile( "=" );
   static private final Pattern COMMA_ARRAY_PATTERN = Pattern.compile( "," );
   static private final Pattern QUOTE_PATTERN = Pattern.compile( "\"" );
   static private final Pattern QUOTE_VALUE_PATTERN = Pattern.compile( "(?:[^\"=\\s]+)|(?:\"[^\"=\\r\\n]+\")" );
   static private final Pattern NAME_VALUE_PATTERN = Pattern
         .compile( "[^\"\\s=]+=(?:(?:[^\"=\\s]+)|(?:\"[^\"=\\r\\n]+\"))" );
   static private final Pattern VIEWS_PATTERN = Pattern.compile( AE_VIEW_NAMES + "=[^\\s]+" );

   private PipelineBuilder _builder;

   private final Collection<String> _userPackages;
   private CliOptionals _cliOptionals;

   /**
    * Create and empty PipelineReader
    */
   public PiperFileReader() {
      _builder = new PipelineBuilder();
      _userPackages = new ArrayList<>();
   }

   /**
    * Create a PipelineReader and load a file with command parameter pairs for building a pipeline
    *
    * @param filePath path to the pipeline command file
    * @throws UIMAException if the pipeline cannot be loaded
    */
   public PiperFileReader( final String filePath ) throws UIMAException {
      _builder = new PipelineBuilder();
      _userPackages = new ArrayList<>();
      loadPipelineFile( filePath );
   }

   /**
    * Create a PipelineReader and load a file with command parameter pairs for building a pipeline
    *
    * @param filePath     path to the pipeline command file
    * @param cliOptionals command line options pre-defined
    * @throws UIMAException if the pipeline cannot be loaded
    */
   public PiperFileReader( final String filePath, final CliOptionals cliOptionals ) throws UIMAException {
      _builder = new PipelineBuilder();
      _userPackages = new ArrayList<>();
      setCliOptionals( cliOptionals );
      loadPipelineFile( filePath );
   }

   public void setCliOptionals( final CliOptionals cliOptionals ) {
      _cliOptionals = cliOptionals;
   }

   /**
    * Add some user package or directory to the known path
    * @param packagePath user package or directory
    */
   public void addUserPackage( final String packagePath ) {
      _userPackages.add( packagePath );
   }

   /**
    * Load a file with command parameter pairs for building a pipeline
    *
    * @param filePath path to the pipeline command file
    */
   public boolean loadPipelineFile( final String filePath ) throws UIMAException {
      try ( final BufferedReader reader = getPiperReader( filePath ) ) {
         String line = reader.readLine();
         while ( line != null ) {
            parsePipelineLine( line.trim() );
            line = reader.readLine();
         }
      } catch ( IOException ioE ) {
         LOGGER.error( "Could not read piper file: " + filePath );
//         return false;
         throw new UIMAException( ioE );
      }
      return true;
   }

   public boolean parsePipelineLine( final String line ) throws UIMAException {
      if ( line.isEmpty() || line.startsWith( "//" ) || line.startsWith( "#" ) || line.startsWith( "!" ) ) {
         return true;
      }
      final int spaceIndex = line.indexOf( ' ' );
      if ( spaceIndex < 0 ) {
         return addToPipeline( line, "" );
      } else {
         return addToPipeline( line.substring( 0, spaceIndex ), line.substring( spaceIndex + 1 ).trim() );
      }
   }

   /**
    * @return the PipelineBuilder with its current state set by this PipelineReader
    */
   public PipelineBuilder getBuilder() {
      return _builder;
   }

   /**
    * @param command   specified by first word in the file line
    * @param parameter specified by second word in the file line
    * @return true if the command and parameters are valid
    * @throws UIMAException if the command could not be executed
    */
   private boolean addToPipeline( final String command, final String parameter ) throws UIMAException {
      final Collection<String> viewSpecs = getViewSpecs( parameter );
      final Collection<String> views = getViews( viewSpecs );
      final String info = removeViewSpecs( parameter, viewSpecs );
      switch ( command ) {
         case "load":
            return loadPipelineFile( info );
         case "package":
            addUserPackage( info );
            return true;
         case "set":
            _builder.set( splitParameters( info ) );
            return true;
         case "cli":
            _builder.setIfEmpty( getCliParameters( info ) );
            return true;
         case "reader":
            if ( hasParameters( info ) ) {
               final String[] component_parameters = splitFromParameters( info );
               final String component = component_parameters[ 0 ];
               final Object[] parameters = splitParameters( component_parameters[ 1 ] );
               _builder.reader( getReaderClass( component ), parameters );
            } else {
               _builder.reader( getReaderClass( info ) );
            }
            return true;
         case "readFiles":
            if ( info.isEmpty() ) {
               _builder.readFiles();
            } else {
               _builder.readFiles( info );
            }
            return true;
         case "add":
            if ( hasParameters( info ) ) {
               final String[] component_parameters = splitFromParameters( info );
               final String component = component_parameters[ 0 ];
               final Object[] parameters = splitParameters( component_parameters[ 1 ] );
               _builder.add( getComponentClass( component ), views, parameters );
            } else {
               _builder.add( getComponentClass( info ), views );
            }
            return true;
         case "addLogged":
            if ( hasParameters( info ) ) {
               final String[] component_parameters = splitFromParameters( info );
               final String component = component_parameters[ 0 ];
               final Object[] parameters = splitParameters( component_parameters[ 1 ] );
               _builder.addLogged( getComponentClass( component ), views, parameters );
            } else {
               _builder.addLogged( getComponentClass( info ), views );
            }
            return true;
         case "addDescription":
            if ( hasParameters( info ) ) {
               final String[] descriptor_parameters = splitFromParameters( info );
               final String component = descriptor_parameters[ 0 ];
               final Object[] values = splitDescriptorValues( descriptor_parameters[ 1 ] );
               final AnalysisEngineDescription description = createDescription( component, values );
               _builder.addDescription( description, views );
            } else {
               final AnalysisEngineDescription description = createDescription( info );
               _builder.addDescription( description, views );
            }
            return true;
         case "addLast":
            if ( hasParameters( info ) ) {
               final String[] component_parameters = splitFromParameters( info );
               final String component = component_parameters[ 0 ];
               final Object[] parameters = splitParameters( component_parameters[ 1 ] );
               _builder.addLast( getComponentClass( component ), views, parameters );
            } else {
               _builder.addLast( getComponentClass( info ), views );
            }
            return true;
         case "collectCuis":
            _builder.collectCuis();
            return true;
         case "collectEntities":
            _builder.collectEntities();
            return true;
         case "writeXmis":
            if ( info.isEmpty() ) {
               _builder.writeXMIs();
            } else {
               _builder.writeXMIs( info );
            }
            return true;
         default:
            LOGGER.error( "Unknown Piper Command: " + command );
            return false;
      }
   }

   /**
    * @param className fully-specified or simple name of an ae or cc component class
    * @return discovered class for ae or cc
    * @throws ResourceInitializationException if the class could not be found
    */
   private Class<? extends AnalysisComponent> getComponentClass( final String className ) throws
                                                                                          ResourceInitializationException {
      Class componentClass;
      try {
         componentClass = Class.forName( className );
      } catch ( ClassNotFoundException cnfE ) {
         componentClass = getPackagedComponent( className );
      }
      if ( componentClass == null ) {
         throw new ResourceInitializationException(
               "No Analysis Component found for " + className, EMPTY_OBJECT_ARRAY );
      }
      assertClassType( componentClass, AnalysisComponent.class );
      return componentClass;
   }

   /**
    * @param className fully-specified or simple name of an ae or cc component class
    * @return discovered class for ae or cc
    */
   private Class<? extends AnalysisComponent> getPackagedComponent( final String className ) {
      Class componentClass;
      for ( String packageName : _userPackages ) {
         componentClass = getPackagedClass( packageName, className, AnalysisComponent.class );
         if ( componentClass != null ) {
            return componentClass;
         }
      }
      for ( String packageName : CTAKES_PACKAGES ) {
         componentClass = getPackagedClass(
               "org.apache.ctakes." + packageName + ".ae", className, AnalysisComponent.class );
         if ( componentClass != null ) {
            return componentClass;
         }
         componentClass = getPackagedClass(
               "org.apache.ctakes." + packageName + ".cc", className, AnalysisComponent.class );
         if ( componentClass != null ) {
            return componentClass;
         }
         componentClass = getPackagedClass(
               "org.apache.ctakes." + packageName, className, AnalysisComponent.class );
         if ( componentClass != null ) {
            return componentClass;
         }
      }
      return null;
   }

   public BufferedReader getPiperReader( final String filePath ) throws FileNotFoundException {
      final InputStream stream = getPiperStream( filePath );
      if ( stream == null ) {
         throw new FileNotFoundException( "No piper file found for " + filePath );
      }
      return new BufferedReader( new InputStreamReader( stream ) );
   }

   /**
    * @param filePath fully-specified or simple path of a piper file
    * @return discovered path for the piper file
    */
   public InputStream getPiperStream( final String filePath ) {
      final File piperFile = new File( filePath );
      String parentPath = null;
      if ( piperFile.isAbsolute() ) {
         parentPath = piperFile.getParent();
      } else {
//         try {
         final File located = FileLocator.getFileQuiet( filePath );
         if ( located != null ) {
            parentPath = located.getParent();
         }
//         } catch ( FileNotFoundException fnfE ) {
            // do nothing
//         }
      }
      if ( parentPath != null && !parentPath.isEmpty() && !_userPackages.contains( parentPath ) ) {
         _userPackages.add( parentPath );
      }
      InputStream stream = FileLocator.getStreamQuiet( filePath );
      if ( stream != null ) {
         return stream;
      }
      // Check user packages
      for ( String packageName : _userPackages ) {
         stream = FileLocator.getStreamQuiet( packageName.replace( '.', '/' ) + '/' + filePath );
         if ( stream != null ) {
            return stream;
         }
         stream = FileLocator.getStreamQuiet( packageName.replace( '.', '/' ) + "/pipeline/" + filePath );
         if ( stream != null ) {
            return stream;
         }
      }
      // Check ctakes packages
      for ( String packageName : CTAKES_PACKAGES ) {
         stream = FileLocator.getStreamQuiet( "org/apache/ctakes/" + packageName.replace( '.', '/' ) + '/' + filePath );
         if ( stream != null ) {
            return stream;
         }
         stream = FileLocator.getStreamQuiet( "org/apache/ctakes/" + packageName.replace( '.', '/' ) + "/pipeline/" + filePath );
         if ( stream != null ) {
            return stream;
         }
      }
      if ( !filePath.toLowerCase().endsWith( ".piper" ) ) {
         return getPiperStream( filePath + ".piper" );
      }
      LOGGER.error( "No piper file found for " + filePath );
      return null;
   }


   /**
    * This requires that the component class has a static createAnnotatorDescription method with no parameters
    * @param className component class for which a descriptor should be created
    * @param values optional parameter values for the descriptor creator
    * @return a description generated for the component
    * @throws ResourceInitializationException if anything went wrong with finding the class or the method,
    * or invoking the method to get an AnalysisEngineDescription
    */
   private AnalysisEngineDescription createDescription( final String className, final Object... values )
         throws ResourceInitializationException {
      final Class<? extends AnalysisComponent> componentClass = getComponentClass( className );
      Method method;
      try {
         if ( values.length == 0 ) {
            method = componentClass.getMethod( "createAnnotatorDescription" );
         } else {
            method = componentClass.getMethod( "createAnnotatorDescription", getValueTypes( values ) );
         }
      } catch ( NoSuchMethodException nsmE ) {
         LOGGER.error( "No createAnnotatorDescription method in " + className );
         throw new ResourceInitializationException( nsmE );
      }
      try {
         final Object invocation = method.invoke( null, values );
         if ( !AnalysisEngineDescription.class.isInstance( invocation ) ) {
            LOGGER.error( "createAnnotatorDescription in " + className + " returned an "
                          + invocation.getClass().getName() + " not an AnalysisEngineDescription" );
            throw new ResourceInitializationException();
         }
         return (AnalysisEngineDescription)invocation;
      } catch ( IllegalAccessException | InvocationTargetException multE ) {
         LOGGER.error( "Could not invoke createAnnotatorDescription on " + className );
         throw new ResourceInitializationException( multE );
      }
   }

   /**
    * The java reflection getMethod does not handle autoboxing/unboxing.
    * So, we assume that Integer and Boolean parameter values will actually be primitives.
    *
    * @param values parameter value objects
    * @return parameter value class types, unboxing to primitives where needed
    */
   static private Class<?>[] getValueTypes( final Object... values ) {
      final Class<?>[] classArray = new Class[ values.length ];
      for ( int i = 0; i < values.length; i++ ) {
         final Class<?> type = values[ i ].getClass();
         if ( type.equals( Integer.class ) ) {
            classArray[ i ] = int.class;
         } else if ( type.equals( Boolean.class ) ) {
            classArray[ i ] = boolean.class;
         } else {
            classArray[ i ] = type;
         }
      }
      return classArray;
   }

   /**
    * @param className fully-specified or simple name of a cr Collection Reader class
    * @return a class for the reader
    * @throws ResourceInitializationException if the class could not be found or instantiated
    */
   private Class<? extends CollectionReader> getReaderClass( final String className )
         throws ResourceInitializationException {
      Class readerClass;
      try {
         readerClass = Class.forName( className );
      } catch ( ClassNotFoundException cnfE ) {
         readerClass = getPackagedReader( className );
      }
      if ( readerClass == null ) {
         throw new ResourceInitializationException( "No Collection Reader found for " + className, EMPTY_OBJECT_ARRAY );
      }
      assertClassType( readerClass, CollectionReader.class );
      return readerClass;
   }

   /**
    * @param className simple name of a cr Collection Reader class
    * @return discovered class for a cr
    */
   private Class<? extends CollectionReader> getPackagedReader( final String className ) {
      Class readerClass;
      for ( String packageName : _userPackages ) {
         readerClass = getPackagedClass( packageName, className, CollectionReader.class );
         if ( readerClass != null ) {
            return readerClass;
         }
      }
      for ( String packageName : CTAKES_PACKAGES ) {
         readerClass = getPackagedClass(
               "org.apache.ctakes." + packageName + ".cr", className, CollectionReader.class );
         if ( readerClass != null ) {
            return readerClass;
         }
         readerClass = getPackagedClass(
               "org.apache.ctakes." + packageName, className, CollectionReader.class );
         if ( readerClass != null ) {
            return readerClass;
         }
      }
      return null;
   }

   /**
    * @param packageName     possible package for class
    * @param className       simple name for class
    * @param wantedClassType desired superclass type
    * @return discovered class or null if no proper class was discovered
    */
   static private Class<?> getPackagedClass( final String packageName, final String className,
                                             final Class<?> wantedClassType ) {
      try {
         Class<?> classType = Class.forName( packageName + "." + className );
         if ( isClassType( classType, wantedClassType ) ) {
            return classType;
         }
      } catch ( ClassNotFoundException cnfE ) {
         // do nothing
      }
      return null;
   }

   /**
    * @param classType       class type to test
    * @param wantedClassType wanted class type
    * @throws ResourceInitializationException if the class type does not extend the wanted class type
    */
   static private void assertClassType( final Class<?> classType, final Class<?> wantedClassType )
         throws ResourceInitializationException {
      if ( !isClassType( classType, wantedClassType ) ) {
         throw new ResourceInitializationException(
               "Not " + wantedClassType.getSimpleName() + " " + classType.getName(), EMPTY_OBJECT_ARRAY );
      }
   }

   /**
    * @param classType       class type to test
    * @param wantedClassType wanted class type
    * @return true if the class type extends the wanted class type
    */
   static private boolean isClassType( final Class<?> classType, final Class<?> wantedClassType ) {
      return wantedClassType.isAssignableFrom( classType );
   }

   /**
    *
    * @param text -
    * @return true if there is more than one word in the text
    */
   static private boolean hasParameters( final String text ) {
      return SPACE_PATTERN.split( text ).length > 1;
   }

   /**
    * @param text text with more than one word
    * @return an array of two strings, [0]= the first word, [1]= the remaining words separated by spaces
    */
   static private String[] splitFromParameters( final String text ) {
      final String[] allSplits = SPACE_PATTERN.split( text );
      final String[] returnSplits = new String[ 2 ];
      returnSplits[ 0 ] = allSplits[ 0 ];
      String parameters = allSplits[ 1 ];
      for ( int i = 2; i < allSplits.length; i++ ) {
         parameters += " " + allSplits[ i ];
      }
      returnSplits[ 1 ] = parameters;
      return returnSplits;
   }

   /**
    * @param text -
    * @return array created by splitting text ' ' and then at '=' characters
    */
   static private Object[] splitParameters( final String text ) {
      if ( text == null || text.trim().isEmpty() ) {
         return EMPTY_OBJECT_ARRAY;
      }
      final Matcher matcher = NAME_VALUE_PATTERN.matcher( text );
      final List<String> pairList = new ArrayList<>();
      while ( matcher.find() ) {
         pairList.add( text.substring( matcher.start(), matcher.end() ) );
      }
      final String[] pairs = pairList.toArray( new String[ pairList.size() ] );
      final Object[] keysAndValues = new Object[ pairs.length * 2 ];
      int i = 0;
      for ( String pair : pairs ) {
         final String[] keyAndValue = KEY_VALUE_PATTERN.split( pair );
         keysAndValues[ i ] = keyAndValue[ 0 ];
         if ( keyAndValue.length == 1 ) {
            keysAndValues[ i + 1 ] = "";
            i += 2;
            continue;
         } else if ( keyAndValue.length > 2 ) {
            LOGGER.warn( "Multiple parameter values, using first of " + pair );
         }
         keysAndValues[ i + 1 ] = getValueObject( keyAndValue[ 1 ] );
         i += 2;
      }
      return keysAndValues;
   }

   /**
    * @param text -
    * @return array created by splitting text ' ' and then at '=' characters
    */
   private Object[] getCliParameters( final String text ) {
      if ( _cliOptionals == null ) {
         LOGGER.warn( "Attempted to set Parameter by Command-line options.  Command-line options are not specified." );
         return EMPTY_OBJECT_ARRAY;
      }
      if ( text == null || text.trim().isEmpty() ) {
         return EMPTY_OBJECT_ARRAY;
      }
      final Matcher matcher = NAME_VALUE_PATTERN.matcher( text );
      final List<String> pairList = new ArrayList<>();
      while ( matcher.find() ) {
         pairList.add( text.substring( matcher.start(), matcher.end() ) );
      }
      final String[] pairs = pairList.toArray( new String[ pairList.size() ] );
      final Object[] keysAndValues = new Object[ pairs.length * 2 ];
      int i = 0;
      for ( String pair : pairs ) {
         final String[] keyAndValue = KEY_VALUE_PATTERN.split( pair );
         keysAndValues[ i ] = keyAndValue[ 0 ];
         if ( keyAndValue.length == 1 ) {
            keysAndValues[ i + 1 ] = "";
            i += 2;
            continue;
         } else if ( keyAndValue.length > 2 ) {
            LOGGER.warn( "Multiple parameter values, using first of " + pair );
         }
         keysAndValues[ i + 1 ] = getValueObject( CliOptionalsHandler
               .getCliOptionalValue( _cliOptionals, keyAndValue[ 1 ] ) );
         i += 2;
      }
      return keysAndValues;
   }

   /**
    * @param text -
    * @return any specifications of views
    */
   static private Collection<String> getViewSpecs( final String text ) {
      final Matcher matcher = VIEWS_PATTERN.matcher( text );
      final Collection<String> viewSpecs = new ArrayList<>();
      while ( matcher.find() ) {
         viewSpecs.add( text.substring( matcher.start(), matcher.end() ) );
      }
      return viewSpecs;
   }

   /**
    * @param text      -
    * @param viewSpecs -
    * @return the text with all viewSpec texts removed
    */
   static private String removeViewSpecs( final String text, final Collection<String> viewSpecs ) {
      if ( viewSpecs.isEmpty() ) {
         return text;
      }
      String viewless = text;
      for ( String viewSpec : viewSpecs ) {
         viewless = viewless.replace( viewSpec, " " );
      }
      return viewless;
   }

   /**
    * @param viewSpecs -
    * @return views listed in view specs
    */
   static private Collection<String> getViews( final Collection<String> viewSpecs ) {
      if ( viewSpecs.isEmpty() ) {
         return Collections.emptyList();
      }
      final Collection<String> views = new HashSet<>();
      for ( String viewSpec : viewSpecs ) {
         String viewText = viewSpec.substring( AE_VIEW_NAMES.length() + 1 );
         views.addAll( Arrays.asList( viewText.split( "," ) ) );
      }
      return views;
   }

   static private Object[] splitDescriptorValues( final String text ) {
      final Matcher matcher = QUOTE_VALUE_PATTERN.matcher( text );
      final List<String> valueList = new ArrayList<>();
      while ( matcher.find() ) {
         valueList.add( text.substring( matcher.start(), matcher.end() ) );
      }
      final String[] values = valueList.toArray( new String[ valueList.size() ] );
      final Object[] valueObjects = new Object[ values.length ];
      for ( int i = 0; i < values.length; i++ ) {
         valueObjects[ i ] = getValueObject( values[ i ] );
      }
      return valueObjects;
   }

   static private Object getValueObject( final String value ) {
      if ( value.indexOf( '\"' ) >= 0 ) {
         // Quoted values should be returned outright - no array splitting, no integer conversion, etc.
         return QUOTE_PATTERN.matcher( value ).replaceAll( "" );
      }
      if ( isCommaArray( value ) ) {
         return attemptParseArray( value );
      }
      final Object returner = attemptParseBoolean( value );
      if ( !value.equals( returner ) ) {
         return returner;
      }
      return attemptParseInt( value );
   }

   /**
    * Since uimafit parameter values can be integers, check for an integer value
    *
    * @param value String value parsed from file
    * @return the value as an Integer, or the original String if an Integer could not be resolved
    */
   static private Object attemptParseInt( final String value ) {
      try {
         return Integer.valueOf( value );
      } catch ( NumberFormatException nfE ) {
         return value;
      }
   }

   /**
    * Since uimafit parameter values can be boolean, check for a boolean value
    *
    * @param value String value parsed from file
    * @return the value as a Boolean, or the original String if it is not "true" or "false", case insensitive
    */
   static private Object attemptParseBoolean( final String value ) {
      if ( value.equalsIgnoreCase( "true" ) ) {
         return Boolean.TRUE;
      } else if ( value.equalsIgnoreCase( "false" ) ) {
         return Boolean.FALSE;
      }
      return value;
   }

   /**
    * @param value String value parsed from file
    * @return true if there are any comma characters in the value, denoting an array
    */
   static private boolean isCommaArray( final String value ) {
      return value.indexOf( ',' ) > 0;
   }

   /**
    * @param value String value parsed from file
    * @return an array of String
    */
   static private Object attemptParseArray( final String value ) {
      return COMMA_ARRAY_PATTERN.split( value );
   }

}
