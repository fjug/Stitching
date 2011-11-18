package plugin;

import static stitching.CommonFunctions.addHyperLinkListener;
import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.MultiLineLabel;
import ij.plugin.PlugIn;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;

import mpicbg.imglib.type.numeric.integer.UnsignedByteType;
import mpicbg.imglib.type.numeric.integer.UnsignedShortType;
import mpicbg.imglib.type.numeric.real.FloatType;
import mpicbg.models.InvertibleBoundable;
import mpicbg.models.TranslationModel2D;
import mpicbg.models.TranslationModel3D;
import mpicbg.stitching.CollectionStitchingImgLib;
import mpicbg.stitching.ImageCollectionElement;
import mpicbg.stitching.ImagePlusTimePoint;
import mpicbg.stitching.StitchingParameters;
import mpicbg.stitching.TextFileAccess;
import mpicbg.stitching.fusion.Fusion;
import stitching.CommonFunctions;

/**
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 */
public class Stitching_Grid implements PlugIn
{
	final private String myURL = "http://fly.mpi-cbg.de/preibisch";
	
	public static int defaultGridChoice1 = 0;
	public static int defaultGridChoice2 = 0;

	public static int defaultGridSizeX = 2, defaultGridSizeY = 3;
	public static double defaultOverlap = 20;
	
	//TODO: change to ""
	public static String defaultDirectory = "/Volumes/Macintosh HD 2/Truman/standard";
	public static boolean defaultConfirmFiles = true;
	
	//TODO: change back to "tile{iii}.tif"
	public static String defaultFileNames = "{ii}.tif";
	public static String defaultTileConfiguration = "TileConfiguration.txt";
	public static boolean defaultComputeOverlap = true;
	public static boolean defaultSubpixelAccuracy = true;
	public static boolean writeOnlyTileConfStatic = false;
	
	//TODO: change to 1
	public static int defaultStartI = 73;
	public static int defaultStartX = 1;
	public static int defaultStartY = 1;
	public static int defaultFusionMethod = 0;
	public static double defaultR = 0.3;
	public static double defaultRegressionThreshold = 0.3;
	public static double defaultDisplacementThresholdRelative = 2.5;		
	public static double defaultDisplacementThresholdAbsolute = 3.5;		
	public static boolean defaultOnlyPreview = false;
	public static int defaultMemorySpeedChoice = 0;
	
	
	@Override
	public void run( String arg0 ) 
	{
		final GridType grid = new GridType();
		
		final int gridType = grid.getType();
		final int gridOrder = grid.getOrder();
		
		if ( gridType == -1 || gridOrder == -1 )
			return;

		final GenericDialogPlus gd = new GenericDialogPlus( "Grid stitching: " + GridType.choose1[ gridType ] + ", " + GridType.choose2[ gridType ][ gridOrder ] );

		if ( gridType < 5 )
		{
			gd.addNumericField( "Grid_size_x", defaultGridSizeX, 0 );
			gd.addNumericField( "Grid_size_y", defaultGridSizeY, 0 );
			
			gd.addSlider( "Tile_overlap [%]", 0, 100, defaultOverlap );
			
			// row-by-row, column-by-column or snake
			// needs the same questions
			if ( grid.getType() < 4 )
			{
				gd.addNumericField( "First_file_index_i", defaultStartI, 0 );
			}
			else
			{
				gd.addNumericField( "First_file_index_x", defaultStartX, 0 );
				gd.addNumericField( "First_file_index_y", defaultStartY, 0 );
			}
		}
		gd.addDirectoryField( "Directory", defaultDirectory, 50 );
		
		if ( gridType == 5 )
			gd.addCheckbox( "Confirm_files", defaultConfirmFiles );
		
		if ( gridType < 5 )			
			gd.addStringField( "File_names for tiles", defaultFileNames, 50 );
		
		if ( gridType == 6 )
			gd.addStringField( "Layout_file", defaultTileConfiguration, 50 );
		else
			gd.addStringField( "Output_textfile_name", defaultTileConfiguration, 50 );
				
		gd.addChoice( "Fusion_method", CommonFunctions.fusionMethodListGrid, CommonFunctions.fusionMethodListGrid[ defaultFusionMethod ] );
		gd.addNumericField( "Regression_threshold", defaultRegressionThreshold, 2 );
		gd.addNumericField( "Max/avg_displacement_threshold", defaultDisplacementThresholdRelative, 2 );		
		gd.addNumericField( "Absolute_displacement_threshold", defaultDisplacementThresholdAbsolute, 2 );
		
		if ( gridType < 5 )
			gd.addCheckbox( "Compute_overlap (otherwise use approximate grid coordinates)", defaultComputeOverlap );
		else if ( gridType == 6 )
			gd.addCheckbox( "Compute_overlap (otherwise apply coordinates from layout file)", defaultComputeOverlap );
		
		gd.addCheckbox( "Subpixel_accuracy", defaultSubpixelAccuracy );
		gd.addChoice( "Computation_parameters", CommonFunctions.cpuMemSelect, CommonFunctions.cpuMemSelect[ defaultMemorySpeedChoice ] );
		gd.addMessage("");
		gd.addMessage( "This Plugin is developed by Stephan Preibisch\n" + myURL);

		MultiLineLabel text = (MultiLineLabel) gd.getMessage();
		addHyperLinkListener(text, myURL);

		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return;
		
		// the general stitching parameters
		final StitchingParameters params = new StitchingParameters();
		
		final int gridSizeX, gridSizeY;
		double overlap;
		int startI = 0, startX = 0, startY = 0;
		
		if ( gridType < 5 )
		{
			gridSizeX = defaultGridSizeX = (int)Math.round(gd.getNextNumber());
			gridSizeY = defaultGridSizeY = (int)Math.round(gd.getNextNumber());
			overlap = defaultOverlap = gd.getNextNumber();
			overlap /= 100.0;
	
			// row-by-row, column-by-column or snake
			// needs the same questions
			if ( grid.getType() < 4 )
			{
				startI = defaultStartI = (int)Math.round(gd.getNextNumber());
			}
			else // position
			{
				startX = defaultStartI = (int)Math.round(gd.getNextNumber());
				startY = defaultStartI = (int)Math.round(gd.getNextNumber());			
			}
		}
		else
		{
			gridSizeX = gridSizeY = 0;
			overlap = 0;
		}
		
		String directory = defaultDirectory = gd.getNextString();
		
		final boolean confirmFiles;
		
		if ( gridType == 5 )
			confirmFiles = defaultConfirmFiles = gd.getNextBoolean();
		else
			confirmFiles = false;
		
		final String filenames;
		if ( gridType < 5 )
			filenames = defaultFileNames = gd.getNextString();
		else
			filenames = "";

		String outputFile = defaultTileConfiguration = gd.getNextString();
		params.fusionMethod = defaultFusionMethod = gd.getNextChoiceIndex();
		params.regThreshold = defaultRegressionThreshold = gd.getNextNumber();
		params.relativeThreshold = defaultDisplacementThresholdRelative = gd.getNextNumber();		
		params.absoluteThreshold = defaultDisplacementThresholdAbsolute = gd.getNextNumber();
		
		if ( gridType < 5 )
			params.computeOverlap = defaultComputeOverlap = gd.getNextBoolean();
		else if ( gridType == 5 )
			params.computeOverlap = true;
		else if ( gridType == 6 )
			params.computeOverlap = defaultComputeOverlap = gd.getNextBoolean();
		
		params.subpixelAccuracy = defaultSubpixelAccuracy = gd.getNextBoolean();
		params.cpuMemChoice = defaultMemorySpeedChoice = gd.getNextChoiceIndex();
		
		// we need to set this
		params.channel1 = 0;
		params.channel2 = 0;
		params.timeSelect = 0;
		params.checkPeaks = 5;
				
		// for reading in writing the tileconfiguration file
		directory = directory.replace('\\', '/');
		directory = directory.trim();
		if (directory.length() > 0 && !directory.endsWith("/"))
			directory = directory + "/";
		
		// get all imagecollectionelements
		final ArrayList< ImageCollectionElement > elements;
		
		if ( gridType < 5 )
			elements = getGridLayout( grid, gridSizeX, gridSizeY, overlap, directory, filenames, startI, startX, startY );
		else if ( gridType == 5 )
			elements = getAllFilesInDirectory( directory, confirmFiles );
		else if ( gridType == 6 )
			elements = getLayoutFromFile( directory, outputFile );
		else
			elements = null;
		
		if ( elements == null )
		{
			IJ.log( "Could not initialise stitching." );
			return;
		}
		
		// open all images (if not done already by grid parsing) and test them, collect information
		int numChannels = -1;
		int numTimePoints = -1;
		
		boolean is2d = false;
		boolean is3d = false;
		
		for ( final ImageCollectionElement element : elements )
		{
			if ( gridType >=5 )
				IJ.log( "Loading: " + element.getFile().getAbsolutePath() + " ... " );
			
			long time = System.currentTimeMillis();
			final ImagePlus imp = element.open();
			time = System.currentTimeMillis() - time;
			
			if ( imp == null )
				return;
			
			int lastNumChannels = numChannels;
			int lastNumTimePoints = numTimePoints;
			numChannels = imp.getNChannels();
			numTimePoints = imp.getNFrames();
			
			if ( imp.getNSlices() > 1 )
			{
				if ( gridType >=5 )
					IJ.log( "" + imp.getWidth() + "x" + imp.getHeight() + "x" + imp.getNSlices() + "px, channels=" + numChannels + ", timepoints=" + numTimePoints + " (" + time + " ms)" );
				is3d = true;					
			}
			else
			{
				if ( gridType >=5 )
					IJ.log( "" + imp.getWidth() + "x" + imp.getHeight() + "px, channels=" + numChannels + ", timepoints=" + numTimePoints + " (" + time + " ms)" );
				is2d = true;
			}
			
			// test validity of images
			if ( is2d && is3d )
			{
				IJ.log( "Some images are 2d, some are 3d ... cannot proceed" );
				return;
			}
			
			if ( ( lastNumChannels != numChannels ) && lastNumChannels != -1 )
			{
				IJ.log( "Number of channels per image changes ... cannot proceed" );
				return;					
			}

			if ( ( lastNumTimePoints != numTimePoints ) && lastNumTimePoints != -1 )
			{
				IJ.log( "Number of timepoints per image changes ... cannot proceed" );
				return;					
			}
			
			if ( gridType == 5 )
			{
				if ( is2d )
				{
					element.setDimensionality( 2 );
            		element.setModel( new TranslationModel2D() );
            		element.setOffset( new float[]{ 0, 0 } );
				}
				else
				{
					element.setDimensionality( 3 );
            		element.setModel( new TranslationModel3D() );
            		element.setOffset( new float[]{ 0, 0, 0 } );
				}
				
			}
		}
		
		// the dimensionality of each image that will be correlated (might still have more channels or timepoints)
		final int dimensionality;
		
		if ( is2d )
			dimensionality = 2;
		else
			dimensionality = 3;
		
		params.dimensionality = dimensionality;
    	
    	// write the initial tileconfiguration
    	if ( gridType != 6 )
    		writeTileConfiguration( new File( directory, outputFile ), elements );
    	    	
    	// call the final stitiching
    	final ArrayList<ImagePlusTimePoint> optimized = CollectionStitchingImgLib.stitchCollection( elements, params );

    	// output the result
		for ( final ImagePlusTimePoint imt : optimized )
			IJ.log( imt.getImagePlus().getTitle() + ": " + imt.getModel() );
		
    	// write the file tileconfiguration
		if ( params.computeOverlap )
		{
			if ( outputFile.endsWith( ".txt" ) )
				outputFile = outputFile.substring( 0, outputFile.length() - 4 ) + ".registered.txt";
			else
				outputFile = outputFile + ".registered.txt";
				
			writeRegisteredTileConfiguration( new File( directory, outputFile ), elements );
		}
		
		// fuse		
		if ( params.fusionMethod != CommonFunctions.fusionMethodListGrid.length - 1 )
		{
			long time = System.currentTimeMillis();
			IJ.log( "Fusing ..." );
			
			// first prepare the models and get the targettype
			final ArrayList<InvertibleBoundable> models = new ArrayList< InvertibleBoundable >();
			final ArrayList<ImagePlus> images = new ArrayList<ImagePlus>();
			
			boolean is32bit = false;
			boolean is16bit = false;
			boolean is8bit = false;
			
			for ( final ImagePlusTimePoint imt : optimized )
			{
				final ImagePlus imp = imt.getImagePlus();
				
				if ( imp.getType() == ImagePlus.GRAY32 )
					is32bit = true;
				else if ( imp.getType() == ImagePlus.GRAY16 )
					is16bit = true;
				else if ( imp.getType() == ImagePlus.GRAY8 )
					is8bit = true;
				
				images.add( imp );
			}
			
			for ( int f = 1; f <= numTimePoints; ++f )
				for ( final ImagePlusTimePoint imt : optimized )
					models.add( (InvertibleBoundable)imt.getModel() );
	
			ImagePlus imp = null;
			
			if ( is32bit )
				imp = Fusion.fuse( new FloatType(), images, models, params.dimensionality, params.subpixelAccuracy, params.fusionMethod );
			else if ( is16bit )
				imp = Fusion.fuse( new UnsignedShortType(), images, models, params.dimensionality, params.subpixelAccuracy, params.fusionMethod );
			else if ( is8bit )
				imp = Fusion.fuse( new UnsignedByteType(), images, models, params.dimensionality, params.subpixelAccuracy, params.fusionMethod );
			else
				IJ.log( "Unknown image type for fusion." );
			
			IJ.log( "Finished ... (" + (System.currentTimeMillis() - time) + " ms)");
			
			if ( imp != null )
				imp.show();
		}
		
    	// close all images
    	for ( final ImageCollectionElement element : elements )
    		element.close();
	}
	
	protected ArrayList< ImageCollectionElement > getLayoutFromFile( final String directory, final String layoutFile )
	{
		ArrayList< ImageCollectionElement > elements = new ArrayList< ImageCollectionElement >();
		int dim = -1;
		int index = 0;
		
		try
		{
			final BufferedReader in = TextFileAccess.openFileRead( new File( directory, layoutFile ) );
			int lineNo = 0;
			
			while ( in.ready() )
			{
				String line = in.readLine().trim();
				lineNo++;
				if ( !line.startsWith( "#" ) && line.length() > 3 )
				{
					if ( line.startsWith( "dim" ) )
					{
						String entries[] = line.split( "=" );
						if ( entries.length != 2 )
						{
							System.out.println( "Stitching_Grid.getLayoutFromFile: Line " + lineNo + " does not look like [ dim = n ]: " + line);
							IJ.log( "Stitching_Grid.getLayoutFromFile: Line " + lineNo + " does not look like [ dim = n ]: " + line);
							return null;						
						}
						
						try
						{
							dim = Integer.parseInt( entries[1].trim() );
						}
						catch ( NumberFormatException e )
						{
							System.out.println( "Stitching_Grid.getLayoutFromFile: Line " + lineNo + ": Cannot parse dimensionality: " + entries[1].trim());
							IJ.log( "Stitching_Grid.getLayoutFromFile: Line " + lineNo + ": Cannot parse dimensionality: " + entries[1].trim());
							return null;														
						}
					}
					else
					{
						if ( dim < 0 )
						{
							System.out.println( "Stitching_Grid.getLayoutFromFile: Line " + lineNo + ": Header missing, should look like [dim = n], but first line is: " + line);
							IJ.log( "Stitching_Grid.getLayoutFromFile: Line " + lineNo + ": Header missing, should look like [dim = n], but first line is: " + line);
							return null;							
						}
						
						if ( dim < 2 || dim > 3 )
						{
							System.out.println( "Stitching_Grid.getLayoutFromFile: Line " + lineNo + ": only dimensions of 2 and 3 are supported: " + line);
							IJ.log( "Stitching_Grid.getLayoutFromFile: Line " + lineNo + ": only dimensions of 2 and 3 are supported: " + line);
							return null;							
						}
						
						// read image tiles
						String entries[] = line.split(";");
						if (entries.length != 3)
						{
							System.out.println( "Stitching_Grid.getLayoutFromFile: Line " + lineNo + " does not have 3 entries! [fileName; ImagePlus; (x,y,...)]");
							IJ.log( "Stitching_Grid.getLayoutFromFile: Line " + lineNo + " does not have 3 entries! [fileName; ImagePlus; (x,y,...)]");
							return null;						
						}
						String imageName = entries[0].trim();
						String imp = entries[1].trim();
						
						if (imageName.length() == 0 && imp.length() == 0)
						{
							System.out.println( "Stitching_Grid.getLayoutFromFile: Line " + lineNo + ": You have to give a filename or a ImagePlus [fileName; ImagePlus; (x,y,...)]: " + line);
							IJ.log( "Stitching_Grid.getLayoutFromFile: Line " + lineNo + ": You have to give a filename or a ImagePlus [fileName; ImagePlus; (x,y,...)]: " + line);
							return null;						
						}
						
						String point = entries[2].trim();
						if (!point.startsWith("(") || !point.endsWith(")"))
						{
							System.out.println( "Stitching_Grid.getLayoutFromFile: Line " + lineNo + ": Wrong format of coordinates: (x,y,...): " + point);
							IJ.log( "Stitching_Grid.getLayoutFromFile: Line " + lineNo + ": Wrong format of coordinates: (x,y,...): " + point);
							return null;
						}
						
						point = point.substring(1, point.length() - 1);
						String points[] = point.split(",");
						if (points.length != dim)
						{
							System.out.println( "Stitching_Grid.getLayoutFromFile: Line " + lineNo + ": Wrong format of coordinates: (x,y,z,..), dim = " + dim + ": " + point);
							IJ.log( "Stitching_Grid.getLayoutFromFile: Line " + lineNo + ": Wrong format of coordinates: (x,y,z,...), dim = " + dim + ": " + point);
							return null;
						}
						
						ImageCollectionElement element = new ImageCollectionElement( new File( directory, imageName ), index++ );
						element.setDimensionality( dim );
								
						if ( dim == 3 )
							element.setModel( new TranslationModel3D() );
						else
							element.setModel( new TranslationModel2D() );

						final float[] offset = new float[ dim ];
						for ( int i = 0; i < dim; i++ )
						{
							try
							{
								offset[ i ] = Float.parseFloat( points[i].trim() ); 
							}
							catch (NumberFormatException e)
							{
								System.out.println( "Stitching_Grid.getLayoutFromFile: Line " + lineNo + ": Cannot parse number: " + points[i].trim());
								IJ.log( "Stitching_Grid.getLayoutFromFile: Line " + lineNo + ": Cannot parse number: " + points[i].trim());
								return null;							
							}
						}
						
						element.setOffset( offset );
						elements.add( element );
					}
				}
			}
		}
		catch ( IOException e )
		{
			System.out.println( "Stitch_Grid.getLayoutFromFile: " + e );
			IJ.log( "Stitching_Grid.getLayoutFromFile: " + e );
			return null;
		};
		
		return elements;
	}
	
	protected ArrayList< ImageCollectionElement > getAllFilesInDirectory( final String directory, final boolean confirmFiles )
	{
		// get all files from the directory
		final File dir = new File( directory );
		if ( !dir.isDirectory() )
		{
			IJ.log( "'" + directory + "' is not a directory. stop.");
			return null;
		}
		
		final String[] imageFiles = dir.list();
		final ArrayList<String> files = new ArrayList<String>();
		for ( final String fileName : imageFiles )
		{
			File file = new File( dir, fileName );
			
			if ( file.isFile() && !file.isHidden() && !fileName.endsWith( ".txt" ) && !fileName.endsWith( ".TXT" ) )
			{
				IJ.log( file.getPath() );
				files.add( fileName );
			}
		}
		
		IJ.log( "Found " + files.size() + " files (we ignore hidden and .txt files)." );
		
		if ( files.size() < 2 )
		{
			IJ.log( "Only " + files.size() + " files found in '" + dir.getPath() + "', you need at least 2 - stop." );
			return null ;
		}
		
		final boolean[] useFile = new boolean[ files.size() ];
		for ( int i = 0; i < files.size(); ++i )
			useFile[ i ] = true;
		
		if ( confirmFiles )
		{
			final GenericDialogPlus gd = new GenericDialogPlus( "Confirm files" );
			
			for ( final String name : files )
				gd.addCheckbox( name, true );
			
			gd.showDialog();
			
			if ( gd.wasCanceled() )
				return null;
			
			for ( int i = 0; i < files.size(); ++i )
				useFile[ i ] = gd.getNextBoolean();
		}
	
		final ArrayList< ImageCollectionElement > elements = new ArrayList< ImageCollectionElement >();

		for ( int i = 0; i < files.size(); ++i )
			if ( useFile [ i ] )
				elements.add( new ImageCollectionElement( new File( directory, files.get( i ) ), i ) );
		
		if ( elements.size() < 2 )
		{
			IJ.log( "Only " + elements.size() + " files selected, you need at least 2 - stop." );
			return null ;			
		}
		
		return elements;
	}
	
	protected ArrayList< ImageCollectionElement > getGridLayout( final GridType grid, final int gridSizeX, final int gridSizeY, final double overlap, final String directory, final String filenames, final int startI, final int startX, final int startY )
	{
		final int gridType = grid.getType();
		final int gridOrder = grid.getOrder();

		// define the parsing of filenames
		// find how to parse
		String replaceX = "{", replaceY = "{", replaceI = "{";
		int numXValues = 0, numYValues = 0, numIValues = 0;

		if ( grid.getType() < 4 )
		{
			int i1 = filenames.indexOf("{i");
			int i2 = filenames.indexOf("i}");
			if (i1 >= 0 && i2 > 0)
			{
				numIValues = i2 - i1;
				for (int i = 0; i < numIValues; i++)
					replaceI += "i";
				replaceI += "}";
			}
			else
			{
				replaceI = "\\\\\\\\";
			}			
		}
		else
		{
			int x1 = filenames.indexOf("{x");
			int x2 = filenames.indexOf("x}");
			if (x1 >= 0 && x2 > 0)
			{
				numXValues = x2 - x1;
				for (int i = 0; i < numXValues; i++)
					replaceX += "x";
				replaceX += "}";
			}
			else
			{
				replaceX = "\\\\\\\\";
			}

			int y1 = filenames.indexOf("{y");
			int y2 = filenames.indexOf("y}");
			if (y1 >= 0 && y2 > 0)
			{
				numYValues = y2 - y1;
				for (int i = 0; i < numYValues; i++)
					replaceY += "y";
				replaceY += "}";
			}
			else
			{
				replaceY = "\\\\\\\\";
			}			
		}
		
		// determine the layout
		final ImageCollectionElement[][] gridLayout = new ImageCollectionElement[ gridSizeX ][ gridSizeY ];
		
		// all snakes, row, columns, whatever
		if ( grid.getType() < 4 )
		{
			// the current position[x, y] 
			final int[] position = new int[ 2 ];
			
			// we have gridSizeX * gridSizeY tiles
			for ( int i = 0; i < gridSizeX * gridSizeY; ++i )
			{
				// get the vector where to move
				getPosition( position, i, gridType, gridOrder, gridSizeX, gridSizeY );

				// get the filename
            	final String file = filenames.replace( replaceI, getLeadingZeros( numIValues, i + startI ) );
            	gridLayout[ position[ 0 ] ][ position [ 1 ] ] = new ImageCollectionElement( new File( directory, file ), i ); 
			}
		}
		else // fixed positions
		{
			// an index for the element
			int i = 0;
			
			for ( int y = 0; y < gridSizeY; ++y )
				for ( int x = 0; x < gridSizeX; ++x )
				{
					final String file = filenames.replace( replaceX, getLeadingZeros( numXValues, x + startX ) ).replace( replaceY, getLeadingZeros( numYValues, y + startY ) );
	            	gridLayout[ x ][ y ] = new ImageCollectionElement( new File( directory, file ), i++ );
				}
		}
		
		// based on the minimum size we will compute the initial arrangement
		int minWidth = Integer.MAX_VALUE;
		int minHeight = Integer.MAX_VALUE;
		int minDepth = Integer.MAX_VALUE;
		
		boolean is2d = false;
		boolean is3d = false;
		
		// open all images and test them, collect information
		for ( int y = 0; y < gridSizeY; ++y )
			for ( int x = 0; x < gridSizeX; ++x )
			{
				IJ.log( "Loading (" + x + ", " + y + "): " + gridLayout[ x ][ y ].getFile().getAbsolutePath() + " ... " );
				
				long time = System.currentTimeMillis();
				final ImagePlus imp = gridLayout[ x ][ y ].open();
				time = System.currentTimeMillis() - time;
				
				if ( imp == null )
					return null;
				
				if ( imp.getNSlices() > 1 )
				{
					IJ.log( "" + imp.getWidth() + "x" + imp.getHeight() + "x" + imp.getNSlices() + "px, channels=" + imp.getNChannels() + ", timepoints=" + imp.getNFrames() + " (" + time + " ms)" );
					is3d = true;					
				}
				else
				{
					IJ.log( "" + imp.getWidth() + "x" + imp.getHeight() + "px, channels=" + imp.getNChannels() + ", timepoints=" + imp.getNFrames() + " (" + time + " ms)" );
					is2d = true;
				}
				
				// test validity of images
				if ( is2d && is3d )
				{
					IJ.log( "Some images are 2d, some are 3d ... cannot proceed" );
					return null;
				}

				if ( imp.getWidth() < minWidth )
					minWidth = imp.getWidth();

				if ( imp.getHeight() < minHeight )
					minHeight = imp.getHeight();
				
				if ( imp.getNSlices() < minDepth )
					minDepth = imp.getNSlices();
			}
		
		final int dimensionality;
		
		if ( is3d )
			dimensionality = 3;
		else
			dimensionality = 2;
			
		// now get the approximate coordinates for each element
		// that is easiest done incrementally
		int xoffset = 0, yoffset = 0, zoffset = 0;
    	
		// an ArrayList containing all the ImageCollectionElements
		final ArrayList< ImageCollectionElement > elements = new ArrayList< ImageCollectionElement >();
		
    	for ( int y = 0; y < gridSizeY; y++ )
    	{
        	if ( y == 0 )
        		yoffset = 0;
        	else 
        		yoffset += (int)( minWidth * ( 1 - overlap ) );

        	for ( int x = 0; x < gridSizeX; x++ )
            {
        		final ImageCollectionElement element = gridLayout[ x ][ y ];
        		
            	if ( x == 0 && y == 0 )
            		xoffset = yoffset = zoffset = 0;
            	
            	if ( x == 0 )
            		xoffset = 0;
            	else 
            		xoffset += (int)( minWidth * ( 1 - overlap ) );
            	            	
            	element.setDimensionality( dimensionality );
            	
            	if ( dimensionality == 3 )
            	{
            		element.setModel( new TranslationModel3D() );
            		element.setOffset( new float[]{ xoffset, yoffset, zoffset } );
            	}
            	else
            	{
            		element.setModel( new TranslationModel2D() );
            		element.setOffset( new float[]{ xoffset, yoffset } );
            	}
            	
            	elements.add( element );
            }
    	}
    	
    	return elements;
	}
	
	// current snake directions ( if necessary )
	// they need a global state
	int snakeDirectionX = 0; 
	int snakeDirectionY = 0; 
	
	protected void writeTileConfiguration( final File file, final ArrayList< ImageCollectionElement > elements )
	{
    	// write the initial tileconfiguration
		final PrintWriter out = TextFileAccess.openFileWrite( file );
		final int dimensionality = elements.get( 0 ).getDimensionality();
		
		out.println( "# Define the number of dimensions we are working on" );
        out.println( "dim = " + dimensionality );
        out.println( "" );
        out.println( "# Define the image coordinates" );
        
        for ( final ImageCollectionElement element : elements )
        {
    		if ( dimensionality == 3 )
    			out.println( element.getFile().getName() + "; ; (" + element.getOffset( 0 ) + ", " + element.getOffset( 1 ) + ", " + element.getOffset( 2 ) + ")");
    		else
    			out.println( element.getFile().getName() + "; ; (" + element.getOffset( 0 ) + ", " + element.getOffset( 1 ) + ")");        	
        }

    	out.close();		
	}

	protected void writeRegisteredTileConfiguration( final File file, final ArrayList< ImageCollectionElement > elements )
	{
    	// write the initial tileconfiguration
		final PrintWriter out = TextFileAccess.openFileWrite( file );
		final int dimensionality = elements.get( 0 ).getDimensionality();
		
		out.println( "# Define the number of dimensions we are working on" );
        out.println( "dim = " + dimensionality );
        out.println( "" );
        out.println( "# Define the image coordinates" );
        
        for ( final ImageCollectionElement element : elements )
        {
    		if ( dimensionality == 3 )
    		{
    			final TranslationModel3D m = (TranslationModel3D)element.getModel();
    			out.println( element.getFile().getName() + "; ; (" + m.getTranslation()[ 0 ] + ", " + m.getTranslation()[ 1 ] + ", " + m.getTranslation()[ 2 ] + ")");
    		}
    		else
    		{
    			final TranslationModel2D m = (TranslationModel2D)element.getModel();
    			final float[] tmp = new float[ 2 ];
    			m.applyInPlace( tmp );
    			
    			out.println( element.getFile().getName() + "; ; (" + tmp[ 0 ] + ", " + tmp[ 1 ] + ")");
    		}
        }

    	out.close();		
	}

	protected void getPosition( final int[] currentPosition, final int i, final int gridType, final int gridOrder, final int sizeX, final int sizeY )
	{
		// gridType: "Row-by-row", "Column-by-column", "Snake by rows", "Snake by columns", "Fixed position"
		// gridOrder:
		//		choose2[ 0 ] = new String[]{ "Right & Down", "Left & Down", "Right & Up", "Left & Up" };
		//		choose2[ 1 ] = new String[]{ "Down & Right", "Down & Left", "Up & Right", "Up & Left" };
		//		choose2[ 2 ] = new String[]{ "Right & Down", "Left & Down", "Right & Up", "Left & Up" };
		//		choose2[ 3 ] = new String[]{ "Down & Right", "Down & Left", "Up & Right", "Up & Left" };
			
		// init the position
		if ( i == 0 )
		{
			if ( gridOrder == 0 || gridOrder == 2 )
				currentPosition[ 0 ] = 0;
			else
				currentPosition[ 0 ] = sizeX - 1;
			
			if ( gridOrder == 0 || gridOrder == 1 )
				currentPosition[ 1 ] = 0;
			else
				currentPosition[ 1 ] = sizeY - 1;
			
			// it is a snake
			if ( gridType == 2 || gridType == 3 )
			{
				// starting with right
				if ( gridOrder == 0 || gridOrder == 2 )
					snakeDirectionX = 1;
				else // starting with left
					snakeDirectionX = -1;
				
				// starting with down
				if ( gridOrder == 0 || gridOrder == 1 )
					snakeDirectionY = 1;
				else // starting with up
					snakeDirectionY = -1;
			}
		}
		else // a move is required
		{
			// row-by-row, "Right & Down", "Left & Down", "Right & Up", "Left & Up"
			if ( gridType == 0 )
			{
				// 0="Right & Down", 2="Right & Up"
				if ( gridOrder == 0 || gridOrder == 2 )
				{
					if ( currentPosition[ 0 ] < sizeX - 1 )
					{
						// just move one more right
						++currentPosition[ 0 ];
					}
					else
					{
						// we have to change rows
						if ( gridOrder == 0 )
							++currentPosition[ 1 ];
						else
							--currentPosition[ 1 ];
						
						// row-by-row going right, so only set position to 0
						currentPosition[ 0 ] = 0;
					}
				}
				else // 1="Left & Down", 3="Left & Up"
				{
					if ( currentPosition[ 0 ] > 0 )
					{
						// just move one more left
						--currentPosition[ 0 ];
					}
					else
					{
						// we have to change rows
						if ( gridOrder == 1 )
							++currentPosition[ 1 ];
						else
							--currentPosition[ 1 ];
						
						// row-by-row going left, so only set position to 0
						currentPosition[ 0 ] = sizeX - 1;
					}					
				}
			}
			else if ( gridType == 1 ) // col-by-col, "Down & Right", "Down & Left", "Up & Right", "Up & Left"
			{
				// 0="Down & Right", 1="Down & Left"
				if ( gridOrder == 0 || gridOrder == 1 )
				{
					if ( currentPosition[ 1 ] < sizeY - 1 )
					{
						// just move one down
						++currentPosition[ 1 ];
					}
					else
					{
						// we have to change columns
						if ( gridOrder == 0 )
							++currentPosition[ 0 ];
						else
							--currentPosition[ 0 ];
						
						// column-by-column going down, so position = 0
						currentPosition[ 1 ] = 0;
					}
				}
				else // 2="Up & Right", 3="Up & Left"
				{
					if ( currentPosition[ 1 ] > 0 )
					{
						// just move one up
						--currentPosition[ 1 ];
					}
					else
					{
						// we have to change columns
						if ( gridOrder == 2 )
							++currentPosition[ 0 ];
						else
							--currentPosition[ 0 ];
						
						// column-by-column going up, so position = sizeY - 1
						currentPosition[ 1 ] = sizeY - 1;
					}
				}
			}
			else if ( gridType == 2 ) // "Snake by rows"
			{
				// currently going right
				if ( snakeDirectionX > 0 )
				{
					if ( currentPosition[ 0 ] < sizeX - 1 )
					{
						// just move one more right
						++currentPosition[ 0 ];
					}
					else
					{
						// just we have to change rows
						currentPosition[ 1 ] += snakeDirectionY;
						
						// and change the direction of the snake in x
						snakeDirectionX *= -1;
					}
				}
				else
				{
					// currently going left
					if ( currentPosition[ 0 ] > 0 )
					{
						// just move one more left
						--currentPosition[ 0 ];
						return;
					}
					else
					{
						// just we have to change rows
						currentPosition[ 1 ] += snakeDirectionY;
						
						// and change the direction of the snake in x
						snakeDirectionX *= -1;
					}
				}
			}
			else if ( gridType == 3 ) // "Snake by columns" 
			{
				// currently going down
				if ( snakeDirectionY > 0 )
				{
					if ( currentPosition[ 1 ] < sizeY - 1 )
					{
						// just move one more down
						++currentPosition[ 1 ];
					}
					else
					{
						// we have to change columns
						currentPosition[ 0 ] += snakeDirectionX;
						
						// and change the direction of the snake in y
						snakeDirectionY *= -1;
					}
				}
				else
				{
					// currently going up
					if ( currentPosition[ 1 ] > 0 )
					{
						// just move one more up
						--currentPosition[ 1 ];
					}
					else
					{
						// we have to change columns
						currentPosition[ 0 ] += snakeDirectionX;
						
						// and change the direction of the snake in y
						snakeDirectionY *= -1;
					}
				}
			}
		}
	}
	
	public static String getLeadingZeros( final int zeros, final int number )
	{
		String output = "" + number;
		
		while (output.length() < zeros)
			output = "0" + output;
		
		return output;
	}
}
 