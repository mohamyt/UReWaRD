import java.io.*;
import java.awt.*;
import java.util.ArrayList;
import java.awt.image.BufferedImage;
import java.util.concurrent.ThreadLocalRandom;
import java.nio.file.*;
import javax.imageio.ImageIO;

// This class is based on AmaViewer.java.
// changes have been made by Benjamin Cappell, 2023: 
// - removing the visualization part 
// - integrate "rendering" and saving png images - raw and augmented ("parsed")
// - support larger retinas
// - batch processing

class AmaImageParser
{

	protected int m_nr;
	protected WaveMaker		m_wave;
	protected int radius;

	protected boolean	m_pause;
	protected boolean	m_step;
	protected boolean	m_ff;

	protected boolean   output_all = false;
	protected int 		m_frameNum = 0;

	static final long serialVersionUID = 1;

	protected HexGrid	m_hist;
	protected HexGrid	m_hist2;
	protected HexGrid	m_timer;
	protected HexGrid	m_timer2;

	protected BufferedImage rawDataImg;
	protected BufferedImage bwImg;
	protected BufferedImage outImg;

	public AmaImageParser(String str, int nr, boolean out_all) throws IOException
	{
		output_all = out_all;
		//super("Amacrine wave viewer - "+str, 20, 50, 1440, 400);
		m_pause = false;
		//m_pause = true;
		m_step = false;
		m_ff = true; //set to true for quickest calculation
		m_nr = nr;
		m_wave = new WaveMaker(str);
		//System.out.println(m_wave.header());
		radius = m_wave.radius();
		System.out.println("radius in AmaImageParser = " + radius);

		m_hist = new HexGrid(radius);
		m_hist2 = new HexGrid(radius);
		m_timer = new HexGrid(radius);
		m_timer2 = new HexGrid(radius);
		for (int y=-radius; y<=radius; y++)
		{
			for (int x=-radius; x<=radius; x++)
			{
				if (!HexGrid.inGrid(x, y, radius))
					continue;

				m_hist.set(x, y, Integer.valueOf(0));
				m_hist2.set(x, y, Integer.valueOf(0));
				m_timer.set(x, y, Integer.valueOf(-200));
				m_timer2.set(x, y, Integer.valueOf(-200));
			}
		}
	
	}

	public void clear() throws IOException {
		m_hist.clear();
		m_hist2.clear();
		m_timer.clear();
		m_timer2.clear();
		m_wave.close();
		m_wave = null;
		System.gc(); //try tro free up memory
	}

	// parse file on the fly and display image
	public int runVideo(int count, int threshold, int spacing, int start)	throws IOException
	{
		boolean running = true;
		int image_count = 0;
	
		if (spacing < 1) {
			spacing = 1;
		}

		//create directory
		Files.createDirectories(Paths.get("../images/" + m_nr + "/parsed"));
		if (output_all) {
			Files.createDirectories(Paths.get("../images/" + m_nr + "/raw"));
		}
		if (start<=0) {
			//create info m_nr.txt file
			try {
				

				File file = new File("../images/" + m_nr + "/" + m_nr + ".txt");
				file.createNewFile();
				FileWriter fw = new FileWriter("../images/" + m_nr + "/" + m_nr + ".txt");
				fw.write(m_wave.m_header);
				fw.close();
				file = null;
				fw = null;
			} catch (IOException e) {
				System.out.println("An error occurred while saving Sim info to txt.");
      			e.printStackTrace();
			}
		}


		outerloop: 
		while (running && (image_count < count))
		{
			
			for (int i = 0; i < spacing; i++) {
				int res = m_wave.nextFrame();
				if (res < 0) //no data left, end the outer loop
					break outerloop;
			}
			

			m_frameNum+=1;
			if (!(writeImage("../images/" + m_nr + "/", (image_count + start), m_frameNum, 256, threshold, start==0)<0)) {
				image_count+=1;
				if (threshold==-1) {
					count +=1;
				}
			}
		}

		m_wave.close();
		System.out.println("runVideo DONE. Generated " + image_count + " images.");
		return image_count;
	}

	public int convertImages(String path, int total_count, int dimensions, int threshold)
	{
		//TODO for more efficient image creation
		return 0;
	}

	public int writeImage(String path, int start_number, int current_real_number, int dimensions, int threshold, boolean first_run)
	{
		boolean random_flip = false;
		boolean random_rotate = false;
		boolean test = false;
		int tmpSize = 2*radius+1;
		rawDataImg = new BufferedImage(tmpSize, tmpSize, BufferedImage.TYPE_INT_RGB);
		bwImg = new BufferedImage(tmpSize, tmpSize, BufferedImage.TYPE_BYTE_BINARY);

		//init imgs with black
		for (int x = 0; x < tmpSize; x++) {
            for (int y = 0; y < tmpSize; y++) {
                int value = 0 << 16 | 0 << 8 | 0; //RGB black
                rawDataImg.setRGB(x, y, value);
				bwImg.setRGB(x,y,0);
            }
        }

		//bw = 0 if cell is inactive, = 255 if state of amacrine is > 1
		//red = 0-255, calcium imaging response, 0 = no response, 255 = full
		//green = (amacrine state * 8) - 1
		//blue = 255 if pixel is on retina, else 0

		ArrayList cells = m_wave.amaList();
		int red, green, blue, bw;
		int all_count = 0;
		for (int i=0; i<cells.size(); i++)
		{
			red = 0;
			green = 0;
			blue = 0;
			bw = 0;

			Point pt = (Point) cells.get(i);
			Pixel pix = m_wave.pixel(pt.x, pt.y);
			int x_ = pt.x + radius;
			int y_ = pt.y + radius;

			if (pix != null) {
				blue = 255;
				red = (int) (pix.calcium*255);
				if (pix.amacrine != 0) {
					green = pix.amacrine * 64-1;
					bw = 255;
					all_count++; //just for runtime optimziation, later: count only the pixels in the actual image
					
					bwImg.setRGB(x_, y_, bw << 16 | bw << 8 | bw);
				}
				rawDataImg.setRGB(x_, y_, red << 16 | green << 8 | blue);
			}
		}
	


		//only continue if there are any pixels in the raw data active.
		if (all_count > 0) {
			if (output_all && first_run) {
				//also write raw img to disk
				File rawImgOutFile = new File(path +"raw/"+ current_real_number + ".png");
				try {
					ImageIO.write(rawDataImg, "png", rawImgOutFile);
				} catch (IOException e1) {
					System.out.println("IOException while writing raw data image " + rawImgOutFile);
				}
			}
			//init grey image
			outImg = new BufferedImage(dimensions, dimensions,
			BufferedImage.TYPE_BYTE_BINARY);
			for (int x = 0; x < dimensions; x++) {
				for (int y = 0; y < dimensions; y++) {
					int value = 127 << 16 | 127 << 8 | 127; //grey
					outImg.setRGB(x, y, value);
				}
			}

			int count = 0;
			Graphics2D g = (Graphics2D) outImg.getGraphics();
			final RenderingHints interpol_rh = new RenderingHints(
					RenderingHints.KEY_INTERPOLATION,
					RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
    		g.setRenderingHints(interpol_rh);
			
			
			//go to middle
			g.translate(dimensions / 2, dimensions / 2);
			
			if (random_flip) { //FLIP
				g.scale(ThreadLocalRandom.current().nextInt(2)*2-1, 1);
			}
			if (random_rotate) { //ROTATE
				g.rotate(ThreadLocalRandom.current().nextDouble(360.0));
			}
			if (test) {
				g.scale(.99, .99); // TEST downscale a bit to see corners
			}
			
			//hexgrid -> pixels - 1 "Stretch in X direction"
			g.scale(2.0/Math.sqrt(3), 1);
			//hexgrid -> pixels - 2 "Untilt in x direction"
			g.shear(0.5, 0);

			//double scaling_factor = dimensions * 1.0f / tmpSize * (1 / (Math.sqrt(3)/2.0f));
			//g.scale(scaling_factor, scaling_factor);
			
			//g.scale(Math.sqrt(Math.PI), Math.sqrt(Math.PI));
			//g.scale(2.f/Math.sqrt(Math.PI), 2.f/Math.sqrt(Math.PI));
			g.scale(Math.sqrt(2), Math.sqrt(2));

			g.scale(0.5f*dimensions/radius, 0.5f*dimensions/radius);
			g.translate(-radius, -radius);
			
			g.drawImage(bwImg, 0, 0, null);

			//count active pixels
			for (int x = 0; x < dimensions; x++) {
				for (int y = 0; y < dimensions ; y++) {
					if (!((outImg.getRGB(x, y) & 0x000000FF) <= 0)) {
						count++;
					}
				}
			}

			if (count > threshold || test) { //TEST: write image directly ignoring pixel count
				File parsedOutFile = new File(path + "parsed/"+ start_number + ".png");
				try {
					ImageIO.write(outImg, "png", parsedOutFile);
				} catch (IOException e1) {
					System.out.println("IOException while writing parsed image " + parsedOutFile);
				}
				g.dispose();
				cells = null;
				return count;
			}
			g.dispose();
			cells = null;
			
		}
		return -1;
	}

	///////////////////////////////////////////////////////////////////
	///////////////////////////////////////////////////////////////////
	public static void main(String[] args)
	{
		// ./view DATAFILE_STARTNUM [THRESHOLD] [DATAFILE COUNT] [IMAGE COUNT per DF] [IMAGE SPACING] [OUTPUT_RAW]
		// if only 1 Argument, all images of this datafile will be written
		try
		{
			int num = -1;
			String name = "../amacrine_1.dat";
			int threshold = 300;
			int file_count = 1;
			int image_count = 1000;
			int spacing = 1;
			boolean out_all = true;
			if (args.length >= 1)
			{
				num = Integer.parseInt(args[0]);
				name = "../amacrine_" + num + ".dat";


				if (args.length >= 2) {
					threshold = Integer.parseInt(args[1]);
					System.out.println("threshold: " + threshold);
					if (args.length >= 3) {
						file_count = Integer.parseInt(args[2]);
						System.out.println("file count: " + file_count);
						if (args.length >= 5) {
							image_count = Integer.parseInt(args[3]);
							System.out.println("image count: " + image_count);

							spacing = Integer.parseInt(args[4]);
							System.out.println("spacing: " + spacing);
							if (args.length >=6) {
								out_all = (Integer.parseInt(args[5]) > 0);
								System.out.println("out_all: " + out_all);
							}
						}
					}
				}
			}

			AmaImageParser ama_parser;
			for (int i = 0; i<file_count; i++) {
				name = "../amacrine_" + (num + i) + ".dat";	
				System.out.println("Data file: " + name);
				System.out.println("threshold: " + threshold + " | # in batch: " + (i+1) + "/" + file_count + " | image count: " + image_count + " | spacing: " + spacing);
				ama_parser = new AmaImageParser(name, num+i, out_all);
				int left_count = image_count;
				int count_imgs = ama_parser.runVideo(left_count, threshold, spacing, 0);
				if (threshold > 0) {
					int tmp_threshold = threshold;
					int tmp_spacing = spacing;
					while (count_imgs < image_count) {
						tmp_threshold = (int) (tmp_threshold* 0.75f);
						tmp_spacing -= 2;
						if (tmp_spacing < 1) {
							tmp_spacing = 1;
						}
						ama_parser.clear();
						ama_parser = new AmaImageParser(name, num+i, out_all);
						left_count = image_count - count_imgs;
						System.out.println("Reducing tmp_threshold & tmp_spacing to: " + tmp_threshold + " & " + tmp_spacing + ". remaining count: " + left_count);
						count_imgs += ama_parser.runVideo(left_count, tmp_threshold, tmp_spacing, image_count-left_count);
					}
				}
				ama_parser.clear();
			}

		}
		catch (IOException ioe)
		{
				ioe.toString();
				ioe.printStackTrace();
		}
	}
}

