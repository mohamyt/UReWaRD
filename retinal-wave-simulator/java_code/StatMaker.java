import java.util.ArrayList;
import java.io.IOException;
import java.text.DecimalFormat;
import java.awt.Point;

class StatMaker {
	protected WaveMaker	m_source;
	protected int		m_rad;
	static protected ArrayList	s_statList = null;

	protected ArrayList	m_nbrWaveList = null;
	protected ArrayList	m_searchList = null;
	protected ArrayList	m_pixelList = null;
	protected ArrayList	m_stateList = null;

	protected HexGrid m_superPos;
	public HexGrid	getSuperPos()	{ return m_superPos;	}

	protected HexGrid m_coverage;
	protected double	m_peakCoverage = 0;
	protected double	m_covMean = 0;
	protected double	m_covSD = 0;
	protected double	m_covCenMean = 0;
	protected double	m_covCenSD = 0;
	double	peakCoverage()	{ return m_peakCoverage;	}
	double	coverMean()		{ return m_covMean;	}
	double	coverSD()		{ return m_covSD;	}
	double	getActivity(int x, int y)
		{ return ((Double) m_coverage.get(x,y)).doubleValue();	}
	double	coverCenterMean()	{ return m_covCenMean;	}
	double	coverCenterSD()		{ return m_covCenSD;	}

	protected int m_peakInitCount = 0;
	protected HexGrid m_initPoints;
	int m_cleanWaves = 0;
	int m_totalWaves = 0;
	int peakInit()		{ return m_peakInitCount;		}
	int getInit(int x, int y)
		{ return ((Integer) m_initPoints.get(x,y)).intValue();	}
	int totalWaves()	{ return m_totalWaves;	}
	int cleanWaves()	{ return m_cleanWaves;	}

	protected int[] m_sizeHist;
	int[] 	sizeHist()		{ return m_sizeHist;	}
	protected int	m_numWaves;
	protected double	m_sizeMean;
	protected double	m_sizeSD;
	protected double	m_sizeMedian;
	int		numWaves()		{ return m_numWaves;	}
	double	sizeMean()		{ return m_sizeMean;	}
	double	sizeSD()		{ return m_sizeSD;		}
	double	sizeMedian()	{ return m_sizeMedian;	}

	protected int[] m_iwiHist;
	int[] 	iwiHist()		{ return m_iwiHist;	}
	protected int		m_numIWI;
	protected double	m_iwiMean;
	protected double	m_iwiSD;
	protected double	m_iwiMedian;
	int		numIWI()		{ return m_numIWI;	}
	double	iwiMean()		{ return m_iwiMean;	}
	double	iwiSD()			{ return m_iwiSD;	}
	double	iwiMedian()		{ return m_iwiMedian;	}

	protected double	m_velMean;
	protected double	m_velSD;
	double	velMean()		{ return m_velMean;	}
	double	velSD()			{ return m_velSD;	}

	public static final double	THRESH		= 0.30;
	public static final double	THRESH_LOW	= 0.25;

	public static final int		NUM_SIZE_BINS	= 40;
	public static final int		SIZE_BIN_WIDTH	= 25;

	public static final int		NUM_IWI_BINS	= 20;
	public static final int		IWI_BIN_WIDTH	= 20;

	protected DecimalFormat	m_df;
	
	StatMaker(WaveMaker source) throws IOException
	{
		if (s_statList == null)
			s_statList = new ArrayList();
		else
			s_statList.clear();
		m_rad = source.radius();

		m_df = new DecimalFormat();
		m_df.setMaximumFractionDigits(3);

		m_source = source;

		m_nbrWaveList = new ArrayList();
		m_searchList = new ArrayList();
		m_stateList = new ArrayList();
		m_pixelList = new ArrayList();

		m_iwiHist = new int[NUM_IWI_BINS];
		for (int i=0; i<NUM_IWI_BINS; i++)
			m_iwiHist[i] = 0;
		m_sizeHist = new int[NUM_SIZE_BINS];
		for (int i=0; i<NUM_SIZE_BINS; i++)
			m_sizeHist[i] = 0;

		m_coverage = new HexGrid(m_rad);
		m_initPoints = new HexGrid(m_rad);
		m_superPos = new HexGrid(m_rad);
		for (int y=-m_rad; y<=m_rad; y++)
		{
			for (int x=-m_rad; x<=m_rad; x++)
			{
				if (!HexGrid.inGrid(x, y, m_rad))
					continue;
				m_coverage.set(x, y, Double.valueOf(0));
				m_initPoints.set(x, y, Integer.valueOf(0));
				m_superPos.set(x, y, Double.valueOf(0));
			}
		}
	}

	void analyze(boolean print) throws IOException
	{
		double dt = WaveMaker.TIME_STEP * 0.001;
//		double superMax = 0;
//		int superCnt = 0;
//		int blackout = 0;
		while (m_source.nextFrame() >= 0)
		{
		 	for (int y=-m_rad; y<=m_rad; y++)
			{
				for (int x=-m_rad; x<=m_rad; x++)
				{
					if (!HexGrid.inGrid(x, y, m_rad))
						continue;
					Pixel pix = m_source.pixel(x,y);
//if ((pix.waveNum > 0) && (pix.waveNum != pix.wave.num()))
//{
//	System.err.println("Error at "+m_source.frameTime()+" pix "+pix.posX+","+pix.posY+" is corrupted. num="+pix.waveNum+"  assigned wave="+pix.wave.num());
//	return;
//}
					if ((pix.calcium > THRESH) && (pix.trackIWI == 0))
					{
						pix.trackIWI = 1;
						// just turned on - calculate IWI
						int t = m_source.frameTime();
						if ((pix.lastOff > 0) && (pix.lastOff < (t-3000)))
						{
							double dur = (t - pix.lastOff) * 0.001;
//System.out.println(t+"\t"+pix.pixX+","+pix.pixY+" \t"+dur+" \t("+pix.lastOff+")");
							pix.iwiList.add(Double.valueOf(dur));
						}
					}
					if ((pix.calcium > THRESH) && (pix.waveNum == -1))
					{
						trackWave(pix);
					}
					else if ((pix.waveNum > 0) && (pix.calcium < THRESH_LOW))
					{
						pix.wave.subPixel();
//System.out.println("Wave "+pix.waveNum+" is size "+pix.wave.size());
						pix.waveNum = -1;
						pix.wave = null;
						pix.lastOff = m_source.frameTime();
						pix.trackIWI = 0;
					}
					if (pix.calcium >= THRESH)
					{
						double db;
						db = ((Double) m_coverage.get(x, y)).doubleValue();
						db += dt;
						if (db > m_peakCoverage)
							m_peakCoverage = db;
						m_coverage.set(x, y, db);
					}
				}
			}

//			// superposition snapshots
//			if ((superCnt < 11) && (blackout < m_source.frameTime()))
//			{
//				// haven't taken 11 snapshots yet, nor are we in a blackout
//				// 	period - check to see if a new snapshot is appropriate
//				//Pixel pix = m_source.pixel(-15, 11);
//				//Pixel pix = m_source.pixel(5,9);
//				Pixel pix = m_source.pixel(6, -16);
//				//Pixel pix = m_source.pixel(15, 0);
//				//Pixel pix = m_source.pixel(0, 0);
//				//if ((pix != null) && (pix.amacrine > 0))
//				if ((pix != null) && (pix.calcium > THRESH))
//				{
//					// time for another picture
//					for (int y=-m_rad; y<=m_rad; y++)
//					{
//						for (int x=-m_rad; x<=m_rad; x++)
//						{
//							if (!HexGrid.inGrid(x, y, m_rad))
//								continue;
//							pix = m_source.pixel(x,y);
//							double sup = ((Double) 
//									m_superPos.get(x,y)).doubleValue();
////							if (pix.amacrine > 0)
////								sup += 1.0;
//							sup += pix.calcium;
//							m_superPos.set(x, y, new Double(sup));
//							if (sup > superMax)
//								superMax = sup;
//						}
//					}
//					superCnt++;
//					// 10 second blackout
//					blackout = m_source.frameTime() + 10000;	
//				}
//			}
		}

//		if ((superCnt > 0) && (superMax > 0))
//		{
//			// normalize superposition plot - set most active pixel 
//			// 	value to 1.0
//			for (int y=-m_rad; y<=m_rad; y++)
//			{
//				for (int x=-m_rad; x<=m_rad; x++)
//				{
//					if (!HexGrid.inGrid(x, y, m_rad))
//						continue;
//					Pixel pix = m_source.pixel(x,y);
//					double sup = ((Double) 
//							m_superPos.get(x,y)).doubleValue();
//					sup /= superMax;
//					m_superPos.set(x, y, Double.valueOf(sup));
//				}
//			}
//		}
//System.out.println("done with analysis - print stats " + s_statList.size());
//System.out.println("\n");

		// now analyze statistics
//		for (int i=0; i<s_statList.size(); i++)
//		{
//			WaveStat ws = (WaveStat) s_statList.get(i);
//
//			// for now, just print everything out
//			System.out.println("Wave "+ws.num+" ("+
//				ws.pixX+","+ws.pixY+")\t" + 
//				"size="+ws.size+"\tvel="+ws.vel);
//		}
		calcSize(print);
		calcIWI(print);
		calcVelocity(print);
		calcCoverage(print);
		calcCenterCoverage(print);
		calcInitial();
	}

	void calcInitial()
	{
		int max = 0;
		int wavesClean = 0;
		int wavesTotal = 0;
		for (int i=0; i<s_statList.size(); i++)
		{
			WaveStat ws = (WaveStat) s_statList.get(i);
			wavesTotal++;
//System.out.println("Wave "+ws.num+" at "+ws.pixX+","+ws.pixY+"   "+(ws.dirty?"dirty":"clean"));
			if (ws.dirty == false)
			{
				wavesClean++;
				int x = ws.pixX;
				int y = ws.pixY;
				int cnt = ((Integer) m_initPoints.get(x, y)).intValue();
				cnt++;
				if (cnt > max)
					max = cnt;
				m_initPoints.set(x, y, Integer.valueOf(cnt));
			}
		}
		m_cleanWaves = wavesClean;
		m_totalWaves = wavesTotal;
		m_peakInitCount = max;
	}

	void calcCoverage(boolean print)
	{
		double mean = 0;
		double var = 0;
		int cnt = 0;
		ArrayList amaList = m_source.amaList();
		for (int i=0; i<amaList.size(); i++)
		{
			Point ama = (Point) amaList.get(i);
			double dur = getActivity(ama.x, ama.y);
			cnt++;
			mean += dur;
		}
		mean /= 1.0 * cnt;
		for (int i=0; i<amaList.size(); i++)
		{
			Point ama = (Point) amaList.get(i);
			double dur = getActivity(ama.x, ama.y);
			double v = mean - dur;
			var += v * v;
		}
		var /= 1.0 * cnt;
		double sd = Math.sqrt(var);

		m_covMean = mean;
		m_covSD = sd;
		if (print)
			System.out.println("Total activity: "+m_df.format(mean)+
				" +/- "+m_df.format(sd) + " seconds");
	}

	void calcCenterCoverage(boolean print)
	{
		double mean = 0;
		double var = 0;
		int cnt = 0;
		ArrayList amaList = m_source.amaCenterList();
		for (int i=0; i<amaList.size(); i++)
		{
			Point ama = (Point) amaList.get(i);
			double dur = getActivity(ama.x, ama.y);
			cnt++;
			mean += dur;
		}
		mean /= 1.0 * cnt;
		for (int i=0; i<amaList.size(); i++)
		{
			Point ama = (Point) amaList.get(i);
			double dur = getActivity(ama.x, ama.y);
			double v = mean - dur;
			var += v * v;
		}
		var /= 1.0 * cnt;
		double sd = Math.sqrt(var);

		m_covCenMean = mean;
		m_covCenSD = sd;
		if (print)
			System.out.println("Center activity: "+m_df.format(mean)+
				" +/- "+m_df.format(sd) + " seconds");
	}

	void calcIWI(boolean print)
	{
		double mean = 0;
		double var = 0;
		m_numIWI = 0;
		ArrayList amaList = m_source.amaList();
		for (int i=0; i<amaList.size(); i++)
		{
			Point ama = (Point) amaList.get(i);
			Pixel pix = m_source.pixel(ama.x,ama.y);

			if (pix.iwiList.size() == 0)
				continue;
			for (int j=0; j<pix.iwiList.size(); j++)
			{
				double dur = ((Double) pix.iwiList.get(j)).doubleValue();
				mean += dur;
				m_numIWI++;

				// put intervals into bins
				// bins in Feller97 are undefined - ~=20 seconds each
				int bin = (int) (dur / IWI_BIN_WIDTH);
				if (bin >= NUM_IWI_BINS)
					bin = NUM_IWI_BINS-1;
				m_iwiHist[bin]++;
			}
		}
		mean /= 1.0 * m_numIWI;
		double data[] = new double[m_numIWI];
		int ctr=0;
		for (int i=0; i<amaList.size(); i++)
		{
			Point ama = (Point) amaList.get(i);
			Pixel pix = m_source.pixel(ama.x,ama.y);

			if (pix.iwiList.size() == 0)
				continue;

			for (int j=0; j<pix.iwiList.size(); j++)
			{
				double val = ((Double) pix.iwiList.get(j)).doubleValue();
				data[ctr++] = val;
				double v = mean - val;
				var += v * v;
			}
		}
		var /= 1.0 * m_numIWI;
		double sd = Math.sqrt(var);

		m_iwiMean = mean;
		m_iwiSD = sd;
		
		// calculate median value
		// sort array, select middle value
		Sorter.sortDoubleAsc(data, m_numIWI);
		m_iwiMedian = data[m_numIWI/2];

		if (print)
			System.out.println("IWI:      "+m_df.format(m_iwiMean)+
				" +/- "+m_df.format(m_iwiSD) + 
				" (median="+m_df.format(m_iwiMedian)+")");
	}

	void calcSize(boolean print)
	{
		double mean = 0;
		m_numWaves = 0;
		// area of each grid square (approx)
		double area = 0.034 * 0.034 * Math.PI * 0.25;
		for (int i=0; i<s_statList.size(); i++)
		{
			WaveStat ws = (WaveStat) s_statList.get(i);
			double sz = ws.size * area;
			mean += sz;
			m_numWaves++;

			// put waves into bins
			// bins in Feller97 .025mm2 wide - recreate here
			int bin = (int) (sz / (0.001 * SIZE_BIN_WIDTH));
			if (bin >= NUM_SIZE_BINS)
				bin = NUM_SIZE_BINS-1;
			m_sizeHist[bin]++;
		}
		mean /= m_numWaves;
		double data[] = new double[s_statList.size()];
		double var = 0;
		for (int i=0; i<s_statList.size(); i++)
		{
			WaveStat ws = (WaveStat) s_statList.get(i);
			double val = ws.size * area;
			data[i] = val;
			double v = mean - val;
			var += v * v;
		}
		var /= m_numWaves;
		m_sizeMean = mean;
		m_sizeSD = Math.sqrt(var);
		
		// calculate median value
		// sort data array, select middle value
		Sorter.sortDoubleAsc(data, data.length);
		m_sizeMedian = data[s_statList.size()/2];

		if (print)
			System.out.println("Size:     "+m_df.format(m_sizeMean)+
				" +/- "+m_df.format(m_sizeSD)+
				" (median="+m_df.format(m_sizeMedian)+")");
	}

	void calcVelocity(boolean print)
	{
		double mean = 0;
		int ctr = 0;
		for (int i=0; i<s_statList.size(); i++)
		{
			WaveStat ws = (WaveStat) s_statList.get(i);
			if (ws.dirty)
				continue;
			if (ws.size < 100)
				continue;
			if (ws.vel <= 0)
				continue;
			mean += ws.vel;
			ctr++;
		}
		mean /= ctr;
		double var = 0;
		for (int i=0; i<s_statList.size(); i++)
		{
			WaveStat ws = (WaveStat) s_statList.get(i);
			if (ws.dirty)
				continue;
			if (ws.size < 100)
				continue;
			if (ws.vel <= 0)
				continue;
			double v = mean - ws.vel;
			var += v * v;
		}
		var /= ctr;
		m_velMean = mean;
		m_velSD = Math.sqrt(var);
		if (print)
			System.out.println("Velocity: "+m_df.format(m_velMean)+
				" +/- "+m_df.format(m_velSD));
			//System.out.println("Velocity: "+mean+" +/- "+Math.sqrt(var));
	}

	void trackWave(Pixel pix) 
	{
//System.out.println("Starting wave track at T="+m_source.frameTime());
//System.out.println("Pix ("+pix.posX+","+pix.posY+")  ca="+pix.calcium);
		m_searchList.add(pix);
		m_stateList.add(pix);
		m_pixelList.add(pix);
		m_nbrWaveList.clear();
		pix.searchState = 0;
		// search all contiguous pixels - identify all adjacent existing
		// 	waves and put all non-assigned and active pixels into list;
		// 	create new wave with list or merge into existing wave(s)
		// recursively trace through connected pixels
		while (m_searchList.size() > 0)
		{
//System.out.println("  "+pix.posX+","+pix.posY+"  stacksize="+m_searchList.size());
			if (pix.searchState >= 6)
			{
//System.out.println("    search state exhausted");
				// all neighbors of this pixel have been searched - 
				// 	remove pixel from search list
				m_searchList.remove(m_searchList.size() - 1);
				if (m_searchList.size() > 0)
					pix = (Pixel) m_searchList.get(m_searchList.size() - 1);
				continue;
			}
			Pixel newPix = pix.neighbors[pix.searchState++];
			if (newPix == null)
				continue;
//System.out.println("    checking "+newPix.x+","+newPix.y);
			if (newPix.waveNum > 0)
			{
//System.out.println("      part of existing wave ("+newPix.waveNum+")");
//System.out.println("      part of existing wave ("+newPix.wave.num()+")");
				boolean hit = false;
				for (int i=0; i<m_nbrWaveList.size(); i++)
				{
					Wave wv = (Wave) m_nbrWaveList.get(i);
					if (newPix.wave == wv)
					{
						// this wave has already been identified
						hit = true;
						break;
					}
				}
				if (hit == false)
					m_nbrWaveList.add(newPix.wave);
			}
			// have lower thresh for including neighbor pixels into wave
			else if ((newPix.calcium > THRESH_LOW) && (newPix.searchState==-1))
			{
//System.out.println("      new pixel");
				pix = newPix;
				newPix.searchState = 0;
				m_searchList.add(newPix);
				m_stateList.add(newPix);
//System.out.println("Adding "+pix.posX+","+pix.posY+" to pixel list");
				m_pixelList.add(pix);
			}
			else
			{
//System.out.println("      ca="+newPix.calcium+" \tsearch="+newPix.searchState);
			}
		}
		if (m_nbrWaveList.size() == 0)
		{
			// new wave
			Wave wv = Wave.createWave(m_rad);
			wv.addPixels(m_pixelList, true, m_source.frameTime());
//System.out.println("Created "+wv.num()+" at time="+m_source.frameTime());
		}
		else if (m_nbrWaveList.size() == 1)
		{
			// merge into existing wave
			Wave wv = (Wave) m_nbrWaveList.get(0);
//System.out.println("Merging into existing wave ("+wv.num()+")");
			wv.addPixels(m_pixelList, false, m_source.frameTime());
		}
		else
		{
			// collision of multiple waves
//System.out.println("  collision at "+m_source.frameTime());
			Wave wave0 = (Wave) m_nbrWaveList.get(0);
//System.out.println("  " + wave0.num());
			wave0.addPixels(m_pixelList, false, m_source.frameTime());
			for (int i=1; i<m_nbrWaveList.size(); i++)
			{
				Wave waveN = (Wave) m_nbrWaveList.get(i);
				wave0.merge(waveN);
//System.out.println("  " + waveN.num());
			}
			wave0.markDirty();
		}

		// reset all search states to zero
		for (int i=0; i<m_stateList.size(); i++)
		{
			Pixel spix = (Pixel) m_stateList.get(i);
			spix.searchState = -1;
		}
		m_stateList.clear();
		m_nbrWaveList.clear();
		m_pixelList.clear();
	}

	static void recordStatistics(Wave wv)
	{
//System.out.println("Recording stats of "+wv.num()+"   "+(wv.isDirty()?"dirty":"clean"));
		WaveStat ws = new WaveStat(wv);
		s_statList.add(ws);
	}

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
			WaveMaker source = new WaveMaker(name);
			StatMaker sm = new StatMaker(source);
			sm.analyze(true);
		}
		catch (IOException ioe)
		{
			ioe.toString();
			ioe.printStackTrace();
		}
	}
}

class WaveStat
{
	WaveStat(Wave wv)
	{
		pixX = wv.pixX();
		pixY = wv.pixY();
		centerX = wv.centerX();
		centerY = wv.centerY();
		vel = wv.velocity();
		size = wv.size();
		num = wv.num();
		dirty = wv.isDirty();
	}

	boolean dirty;
	int num;
	int	size;
	int	pixX;
	int	pixY;
	double	centerX;
	double	centerY;
	double vel;
}

