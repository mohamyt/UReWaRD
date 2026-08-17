import java.io.*;
import java.awt.*;
import java.awt.event.*;
import java.lang.Thread;
import java.util.ArrayList;
import java.text.DecimalFormat;
import java.util.StringTokenizer;

class XAnalysis extends DisplayFrame
{
	protected WaveMaker		m_wave;
	protected StatMaker		m_stat;
	protected int m_radius;
	protected double m_simLen;

	static final double fellerIWI[] = 
	{ 	0.0010, 0.0100, 0.0490, 0.1367, 0.1218, 
		0.1361, 0.1429, 0.1099, 0.0789, 0.0500,
		0.0439, 0.0439, 0.0160, 0.0139, 0.0051, 
		0.0099, 0.0099, 0.0000, 0.0000, 0.0000  };
	static final double fellerSize[] = 
	{ 	0.1048, 0.1388, 0.1640, 0.0898, 0.1170, 0.0820, 0.0429, 0.0388,
		0.0480, 0.0408, 0.0190, 0.0201, 0.0139, 0.0170, 0.0160, 0.0119,
		0.0061, 0.0010, 0.0088, 0.0041, 0.0000, 0.0068, 0.0041, 0.0068,
		0.0010, 0.0010, 0.0010, 0.0000, 0.0000, 0.0000, 0.0031, 0.0000, 
		0.0031, 0.0000, 0.0000, 0.0000, 0.0000, 0.0000, 0.0000, 0.0000 };
	static final double fellerIWIMax = 0.1429;
	static final double fellerSizeMax = 0.1640;

	static final long serialVersionUID = 1;

	public XAnalysis(String str) throws IOException
	{
		super("XStatMaker - "+str, 10, 40, 1200, 800);	

		m_wave = new WaveMaker(str);
		m_stat = new StatMaker(m_wave);
		m_radius = m_wave.radius();
	}

	// parse file on the fly and display image
	// run at ~10fps during activity and ~100fps between waves
	public void runAnalysis()	throws IOException
	{
		prepImage();
		writeHeader();
		m_stat.analyze(true);

		// show window
		// run analyses, update image w/ each completion
		drawSize();
		drawIWI();
		drawCoverage();
		drawInitPoints();
//		m_stat.calcVelocity(false);
//		drawVelocity();
	}

	public void prepImage()
	{
		Image img = getImage();
		Graphics gc = img.getGraphics();

		// fill screen to background color
		gc.setColor(Color.white);
		//gc.setColor(new Color(64, 64, 64));
		gc.fillRect(0, 0, m_width, m_height);

		setImage(img);
		requestDataUpdate();
	}

	public void writeHeader()
	{
		Image img = getImage();
		Graphics gc = img.getGraphics();

		int xoff = 10;
		int yoff = 580;
		gc.setColor(Color.black);
		StringTokenizer tok = new StringTokenizer(m_wave.header());
		int cnt = 0;
		while (tok.hasMoreTokens() == true)
		{
			String nam = tok.nextToken();
			String val = tok.nextToken();
			if (nam.equals("runtime"))
				m_simLen = Double.parseDouble(val);
			gc.drawString(nam + " = " + val, xoff, yoff+cnt*15);
			cnt++;
		}

		DecimalFormat nf = new DecimalFormat();
		nf.setMaximumFractionDigits(3);
		gc.drawString("Ca thresh = "+nf.format(StatMaker.THRESH)+"/"+
				nf.format(StatMaker.THRESH_LOW),xoff+200,yoff);

		setImage(img);
		requestDataUpdate();
	}

	public void drawSize()
	{
		Image img = getImage();
		Graphics gc = img.getGraphics();

		int xoff = 10;
		int yoff = 10;
		int h = 200;
		int w = 450;
		
		int sizeHist[] = m_stat.sizeHist();
		int n = m_stat.numWaves();
		// compute biggest bin
		int max = (int) (n * fellerSizeMax);
		for (int i=0; i<StatMaker.NUM_SIZE_BINS; i++)
		{
			if (sizeHist[i] > max)
				max = sizeHist[i];
		}
		// relative height of tallest bin
		double scale = 1.0 * n / max;

		// draw feller's histogram
		gc.setColor(new Color(255, 192, 192));
		for (int i=0; i<StatMaker.NUM_SIZE_BINS; i++)
		{
			int sz = (int) (scale * h * 1.0 * fellerSize[i]);
			int pos = (int) (w * 1.0 * i / StatMaker.NUM_SIZE_BINS);
			int pos2 = (int) (w * 1.0 * (i+1) / StatMaker.NUM_SIZE_BINS);
			for (int j=pos; j<=pos2; j++)
				gc.drawLine(xoff+j, yoff+h, xoff+j, yoff+h-sz);
		}
		gc.setColor(Color.black);

		// draw grid lines
		gc.drawLine(xoff, yoff, xoff, yoff+h);
		gc.drawLine(xoff, yoff+h, xoff+w, yoff+h);

		// draw vertical tick marks - small for 5%, big for 10%
		double ticWidth = 0.05 * scale * h;
		double ticPos = 0;
		int sz = 6;
		while (ticPos < h)
		{
			int pos = (int) (yoff + h - ticPos);
			gc.drawLine(xoff-sz, pos, xoff, pos);
			sz = (sz==3?6:3);
			ticPos += ticWidth;
		}

		// draw ticks on horizontal axis
		for (int i=0; i<=StatMaker.NUM_SIZE_BINS; i++)
		{
			sz = ((i%8)==0?6:0);
			int pos = (int) (w * 1.0 * i / StatMaker.NUM_SIZE_BINS);
			gc.drawLine(xoff+pos, yoff+h, xoff+pos, yoff+h+sz);
		}

		// draw histogram
		for (int i=0; i<StatMaker.NUM_SIZE_BINS; i++)
		{
			sz = (int) (scale * h * 1.0 * sizeHist[i] / n);
			int pos = (int) (w * 1.0 * i / StatMaker.NUM_SIZE_BINS);
			int pos2 = (int) (w * 1.0 * (i+1) / StatMaker.NUM_SIZE_BINS);
			gc.drawLine(xoff+pos, yoff+h, xoff+pos, yoff+h-sz);
			gc.drawLine(xoff+pos2, yoff+h, xoff+pos2, yoff+h-sz);
			gc.drawLine(xoff+pos, yoff+h-sz, xoff+pos2, yoff+h-sz);
		}
		// draw line from feller's histogram
		gc.setColor(new Color(255, 192, 192));
		for (int i=0; i<StatMaker.NUM_SIZE_BINS; i++)
		{
			sz = (int) (scale * h * 1.0 * fellerSize[i]);
			int pos = (int) (w * 1.0 * i / StatMaker.NUM_SIZE_BINS);
			int pos2 = (int) (w * 1.0 * (i+1) / StatMaker.NUM_SIZE_BINS);
			pos = (pos + pos2) / 2;
			gc.drawLine(xoff+pos, yoff+h-1, xoff+pos, yoff+h-sz);
		}
		gc.setColor(Color.black);

		double mean = m_stat.sizeMean();
		double stddev = m_stat.sizeSD();
		double median = m_stat.sizeMedian();
		DecimalFormat nf = new DecimalFormat();
		nf.setMaximumFractionDigits(3);
		String size = "Wave size: "+nf.format(mean)+" +/- "+
			nf.format(stddev) + " (median: " + nf.format(median) + 
			" n="+n+")";
		gc.drawString(size, xoff, yoff+h+25);

		// print velocity info here
		mean = m_stat.velMean();
		stddev = m_stat.velSD();
		String vel = "Wave velocity: "+nf.format(mean)+" +/- "+
			nf.format(stddev);
		gc.drawString(vel, xoff, yoff+h+45);
		double amaArea = 6 * (0.017 * 0.017 / Math.sqrt(3.0));
		double area = amaArea * m_wave.numAmacrine();
//		double retRad = 0.034 * (m_radius + 0.5);
//		double area = 6 * (retRad*Math.sqrt(3.0)/2.0) * (retRad/2.0);
		double fr = 1.0 * n / m_simLen / area;
		String freq = "Waves/min/mm2: "+nf.format(fr)+"  (area=" +
			nf.format(area)+" mm2)";
			nf.format(stddev);
		gc.drawString(freq, xoff, yoff+h+65);

		setImage(img);
		requestDataUpdate();
	}

	public void drawIWI()
	{
		Image img = getImage();
		Graphics gc = img.getGraphics();

		int xoff = 10;
		int yoff = 310;
		int h = 200;
		int w = 450;

		int iwiHist[] = m_stat.iwiHist();
		int n = m_stat.numIWI();
		// compute biggest bin
		int max = (int) (n * fellerIWIMax);
		for (int i=0; i<StatMaker.NUM_IWI_BINS; i++)
		{
			if (iwiHist[i] > max)
				max = iwiHist[i];
		}
		// relative height of tallest bin
		double scale = 1.0 * n / max;
		
		// draw feller's histogram
		gc.setColor(new Color(255, 192, 192));
		for (int i=0; i<StatMaker.NUM_IWI_BINS; i++)
		{
			int sz = (int) (scale * h * 1.0 * fellerIWI[i]);
			int pos = (int) (w * 1.0 * i / StatMaker.NUM_IWI_BINS);
			int pos2 = (int) (w * 1.0 * (i+1) / StatMaker.NUM_IWI_BINS);
			for (int j=pos; j<=pos2; j++)
				gc.drawLine(xoff+j, yoff+h, xoff+j, yoff+h-sz);
		}

		gc.setColor(Color.black);

		// draw grid lines
		gc.drawLine(xoff, yoff, xoff, yoff+h);
		gc.drawLine(xoff, yoff+h, xoff+w, yoff+h);

		// draw vertical tick marks - small for 5%, big for 10%
		double ticWidth = 0.05 * scale * h;
		double ticPos = 0;
		int sz = 6;
		while (ticPos < h)
		{
			int pos = (int) (yoff + h - ticPos);
			gc.drawLine(xoff-sz, pos, xoff, pos);
			sz = (sz==3?6:3);
			ticPos += ticWidth;
		}

		// draw ticks on horizontal axis
		for (int i=0; i<=StatMaker.NUM_IWI_BINS; i++)
		{
			sz = ((i%5)==0?6:0);
			int pos = (int) (w * 1.0 * i / StatMaker.NUM_IWI_BINS);
			gc.drawLine(xoff+pos, yoff+h, xoff+pos, yoff+h+sz);
		}

		// draw histogram
		for (int i=0; i<StatMaker.NUM_IWI_BINS; i++)
		{
			sz = (int) (scale * h * 1.0 * iwiHist[i] / n);
			int pos = (int) (w * 1.0 * i / StatMaker.NUM_IWI_BINS);
			int pos2 = (int) (w * 1.0 * (i+1) / StatMaker.NUM_IWI_BINS);
			gc.drawLine(xoff+pos, yoff+h, xoff+pos, yoff+h-sz);
			gc.drawLine(xoff+pos2, yoff+h, xoff+pos2, yoff+h-sz);
			gc.drawLine(xoff+pos, yoff+h-sz, xoff+pos2, yoff+h-sz);
		}
		// draw line from feller's histogram
		gc.setColor(new Color(255, 192, 192));
		for (int i=0; i<StatMaker.NUM_IWI_BINS; i++)
		{
			sz = (int) (scale * h * 1.0 * fellerIWI[i]);
			int pos = (int) (w * 1.0 * i / StatMaker.NUM_IWI_BINS);
			int pos2 = (int) (w * 1.0 * (i+1) / StatMaker.NUM_IWI_BINS);
			pos = (pos + pos2) / 2;
			gc.drawLine(xoff+pos, yoff+h-1, xoff+pos, yoff+h-sz);
		}
		gc.setColor(Color.black);

		double mean = m_stat.iwiMean();
		double stddev = m_stat.iwiSD();
		double median = m_stat.iwiMedian();
		DecimalFormat nf = new DecimalFormat();
		nf.setMaximumFractionDigits(3);
		String size = "IWI: "+nf.format(mean)+" +/- "+
			nf.format(stddev)+" (median="+nf.format(median)+" n="+n+")";
		gc.drawString(size, xoff, yoff+h+25);

		setImage(img);
		requestDataUpdate();
	}

	public void drawInitPoints()
	{
		Image img = getImage();
		Graphics gc = img.getGraphics();

		gc.setColor(Color.black);

		int xoff = 510;
		int yoff = 375;
		int xsize = 300;
		int pixSz = (int) (1.0 * xsize / (2*m_radius+3));

		int peak = m_stat.peakInit();
//System.out.println("peak init " + peak);

		// draw boundary
		gc.setColor(Color.blue);
		// draw hard boundary
		for (int y=-m_radius-1; y<=m_radius+1; y++)
		{
			// adjust indent
			double xInset = m_radius + y/2.0;

			for (int x=-m_radius-1; x<=m_radius+1; x++)
			{
				int ring = HexGrid.calcRingFromHexSpace(x, y);
				if (ring == m_radius+1)
				{
					int xx = (int) (xoff + pixSz * (xInset + x));
					int yy = (int) (yoff + pixSz * (m_radius + y));
					gc.fillRect((int) xx, yy, pixSz, pixSz);
				}
			}
		}
		// draw 'soft' boundary
		ArrayList boundary = m_wave.boundaryList();
		for (int i=0; i<boundary.size(); i++)
		{
			Point pt = (Point) boundary.get(i);
			int x = pt.x;
			int y = pt.y;
			double xInset = m_radius + y/2.0;
			gc.fillRect((int) (
					xoff+pixSz*(xInset+x)), 
					yoff+pixSz*(m_radius+y), pixSz, pixSz);
		}
		// draw center
		gc.setColor(Color.black);
		if (peak > 0)
		{
			ArrayList cells = m_wave.amaList();
			for (int i=0; i<cells.size(); i++)
			{
				Point pt = (Point) cells.get(i);
				int x = pt.x;
				int y = pt.y;
				double xInset = m_radius + y/2.0;

				double val = m_stat.getInit(x, y);
				val /= 1.0 * peak;
				int shade = 255 - ((int) (255 * val));
				gc.setColor(new Color(shade, shade, shade));
				gc.fillRect((int) (
						xoff+pixSz*(xInset+x)), 
						yoff+pixSz*(m_radius+y), pixSz, pixSz);
				if ((y==0) && (x>=0))
				{
					gc.setColor(Color.red);
					gc.fillRect((int) (
							xoff+pixSz*(xInset+x)), 
							yoff+pixSz*(m_radius+y), 1, 1);
				}
			}
		}
		gc.setColor(Color.black);
		String str = "Total waves: "+m_stat.totalWaves()+
				"   'Clean' waves "+m_stat.cleanWaves();
		gc.drawString(str, xoff, yoff+xsize+30);
		
		// draw 'cut' through plot, from origin to edge in quadrant 1
		xoff += 320;
		yoff +=  50;
		int w = 300;
		int h = 200;
		int step = w / m_radius;
		w = m_radius * step;
		// draw grid lines
		gc.setColor(Color.black);
		gc.drawLine(xoff, yoff, xoff, yoff+h);
		gc.drawLine(xoff, yoff+h, xoff+w, yoff+h);
		gc.setColor(new Color(192, 192, 192));
		for (int i=1; i<=10; i++)
		{
			gc.drawLine(xoff, yoff+h-i*h/10, xoff+w, yoff+h-i*h/10);
		}
		gc.setColor(Color.black);
		if (peak > 0)
		{
			int last = (int) (h * m_stat.getInit(0, 0) / peak);
			for (int i=1; i<=m_radius; i++)
			{
				int next = (int) (h * m_stat.getInit(i, 0) / peak);
				gc.drawLine(xoff+(i-1)*step, yoff+h-last,
						xoff+i*step, yoff+h-next);
				last = next;
			}
		}

		setImage(img);
		requestDataUpdate();
	}


	public void drawCoverage()
	{
		Image img = getImage();
		Graphics gc = img.getGraphics();

		gc.setColor(Color.black);

		int xoff = 510;
		int yoff =  10;
		int xsize = 300;
		int pixSz = (int) (1.0 * xsize / (2*m_radius+3));

		double peak = m_stat.peakCoverage();

		// draw boundary
		gc.setColor(Color.blue);
		// draw hard boundary
		for (int y=-m_radius-1; y<=m_radius+1; y++)
		{
			// adjust indent
			double xInset = m_radius + y/2.0;

			for (int x=-m_radius-1; x<=m_radius+1; x++)
			{
				int ring = HexGrid.calcRingFromHexSpace(x, y);
				if (ring > m_radius+1)
					continue;
				
				if (ring == m_radius+1)
				{
					int xx = (int) (xoff + pixSz * (xInset + x));
					int yy = (int) (yoff + pixSz * (m_radius + y));
					gc.fillRect((int) xx, yy, pixSz, pixSz);
					continue;
				}
			}
		}
		// draw 'soft' boundary
		ArrayList boundary = m_wave.boundaryList();
		for (int i=0; i<boundary.size(); i++)
		{
			Point pt = (Point) boundary.get(i);
			int x = pt.x;
			int y = pt.y;
			double xInset = m_radius + y/2.0;
			gc.fillRect((int) (
					xoff+pixSz*(xInset+x)), 
					yoff+pixSz*(m_radius+y), pixSz, pixSz);
		}
		// draw center
		gc.setColor(Color.black);
		if (peak > 0)
		{
			ArrayList cells = m_wave.amaList();
			for (int i=0; i<cells.size(); i++)
			{
				Point pt = (Point) cells.get(i);
				int x = pt.x;
				int y = pt.y;
				double xInset = m_radius + y/2.0;

				double val = m_stat.getActivity(x, y);
				val /= peak;
				int shade = (int) (255 * val);
				gc.setColor(new Color(shade, shade, shade));

				gc.fillRect((int) (
						xoff+pixSz*(xInset+x)), 
						yoff+pixSz*(m_radius+y), pixSz, pixSz);
				if ((y==0) && (x>=0))
				{
					gc.setColor(Color.red);
					gc.fillRect((int) (
							xoff+pixSz*(xInset+x)), 
							yoff+pixSz*(m_radius+y), 1, 1);
				}
			}
		}

		gc.setColor(Color.black);
		DecimalFormat nf = new DecimalFormat();
		nf.setMaximumFractionDigits(2);
		double mn = m_stat.coverMean();
		double sd = m_stat.coverSD();
		String str = "Total Activity: "+nf.format(mn)+" +/- "+nf.format(sd)+ " seconds" +
			"  Peak: "+nf.format(peak);
		gc.drawString(str, xoff, yoff+xsize+20);
		mn = m_stat.coverCenterMean();
		sd = m_stat.coverCenterSD();
		str = "Center Activity: "+nf.format(mn)+" +/- "+nf.format(sd)+ " seconds" +
			"  Peak: "+nf.format(peak);
		gc.drawString(str, xoff, yoff+xsize+35);
		sd /= 0.01 * mn;
		nf.setMaximumFractionDigits(4);
		str = "Normalized center activity: 100.0 +/- "+nf.format(sd);
		gc.drawString(str, xoff, yoff+xsize+50);

		// draw 'cut' through plot, from origin to edge in quadrant 1
		xoff += 320;
		yoff +=  50;
		int w = 300;
		int h = 200;
		int step = w / m_radius;
		w = m_radius * step;
		// draw grid lines
		gc.setColor(Color.black);
		gc.drawLine(xoff, yoff, xoff, yoff+h);
		gc.drawLine(xoff, yoff+h, xoff+w, yoff+h);
		gc.setColor(new Color(192, 192, 192));
		for (int i=1; i<=10; i++)
		{
			gc.drawLine(xoff, yoff+h-i*h/10, xoff+w, yoff+h-i*h/10);
		}
		gc.setColor(Color.black);
		if (peak > 0)
		{
			int last = (int) (h * m_stat.getActivity(0, 0) / peak);
			for (int i=1; i<=m_radius; i++)
			{
				int next = (int) (h * m_stat.getActivity(i, 0) / peak);
				gc.drawLine(xoff+(i-1)*step, yoff+h-last,
						xoff+i*step, yoff+h-next);
				last = next;
			}
		}

		setImage(img);
		requestDataUpdate();
	}

//	public void drawImage()
//	{
//		Image img = getImage();
//		Graphics gc = img.getGraphics();
//
//		for (int y=-radius-1; y<=radius+1; y++)
//		{
//			// adjust indent
//			int d = (y<0?-y:y);
//			double xInset = d/2.0;
//
//			int px=0;
//			for (int x=-radius-1; x<=radius+1; x++)
//			{
//				int ring = HexGrid.calcRingFromHexSpace(x, y);
//				if (ring > radius+1)
//					continue;
//
//				px++;
//				
//				if (ring == radius+1)
//				{
//					gc.setColor(Color.black);
//					int xx = (int) ( 5 + 3 * (xInset + px));
//					int yy = (int) (50 + 3 * (radius + y));
//					gc.fillRect((int) xx, yy, 3, 3);
//
//					xx = (int) (365 + 3 * (xInset + px));
//					yy = (int) (50 + 3 * (radius + y));
//					gc.fillRect((int) xx, yy, 3, 3);
//					continue;
//				}
//
//				Pixel pix = m_wave.pixel(x, y);
//				if (pix.amacrine == 0)
//					gc.setColor(Color.white);
//				else
//					gc.setColor(new Color(255, 0, 0));
//				gc.fillRect((int) (5+3*(xInset+px)), 50+3*(radius+y), 3, 3);
//
//				int i = (int) ((1.0 - pix.calcium) * 255);
//				if (i < 128)
//					gc.setColor(new Color(255, 0, 0));
//				else
//					gc.setColor(new Color(i, i, i));
//				gc.fillRect((int) (365+3*(xInset+px)), 50+3*(radius+y), 3, 3);
//			}
//		}
//
//		int frameTime = m_wave.frameTime();
//		String time = ((int) frameTime / 1000) + "." + (frameTime % 1000);
//		gc.drawString(time, BUTTON_TOP + 6*BUTTON_WIDTH, 
//					BUTTON_TOP + BUTTON_HEIGHT);
//
//		setImage(img);
//		requestDataUpdate();
//	}

	///////////////////////////////////////////////////////////////////
	///////////////////////////////////////////////////////////////////
	public static void main(String[] args)
	{
		try
		{
			int num = -1;
			String name = "../amacrine_1.dat";
			if (args.length >= 1)
			{
				num = Integer.parseInt(args[0]);
				name = "../amacrine_" + num + ".dat";
			}
				
			System.out.println("Data file: " + name);
			XAnalysis view1 = new XAnalysis(name);
			view1.runAnalysis();
		}
		catch (IOException ioe)
		{
				ioe.toString();
				ioe.printStackTrace();
		}
	}
}

