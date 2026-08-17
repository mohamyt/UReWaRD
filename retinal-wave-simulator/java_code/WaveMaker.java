import java.io.*;
import java.util.ArrayList;
import java.awt.Point;

// changes have been made by Benjamin Cappell, 2023: 
// - support larger retinas

class WaveMaker
{
	protected DataInputStream m_in;
	protected HexGrid m_grid;

	public static final int TIME_STEP = 100;

	protected String	m_header;
	protected int 	m_radius;
	protected int 	m_nextStart;

	protected int	m_nextFrameTime;

	protected ArrayList	m_boundaryList;
	protected ArrayList	m_borderList;
	protected ArrayList	m_amaList;
	protected ArrayList	m_amaCenList;
	int numAmacrine()		{ return m_amaList.size();	}
	ArrayList amaList()			{ return m_amaList;			}
	int numCenterAmacrine()		{ return m_amaCenList.size();	}
	ArrayList amaCenterList()	{ return m_amaCenList;			}
	ArrayList boundaryList()	{ return m_boundaryList;		}
	ArrayList borderList()		{ return m_borderList;		}

	//static final long serialVersionUID = 1;

	public WaveMaker(String str) throws IOException
	{
//System.out.println("wave maker");
		m_in = new DataInputStream(new FileInputStream(str));
		int i1 = m_in.readByte();
		int i2 = m_in.readByte();
		int i3 = m_in.readByte();
		int v = m_in.readByte();
		if ((i1 != 'a') || (i2 != 'm') || (i3 != 'a'))
			throw new IOException("Error - unsupported file type");
		if (v != 3)
			throw new IOException("Error - unsupported version");

		int dataLen = m_in.readShort();
		byte buf[] = new byte[dataLen+1];
		m_in.read(buf, 0, dataLen);
		m_header = new String(buf, 0, dataLen);
System.out.print("" + m_header);

		m_radius = m_in.readShort();
//System.out.println("radius of hex storage grid = "+m_radius);

		// read grid cells
		//int numAmacrine = m_in.readShort();
		int numAmacrine = m_in.readInt(); //changed 2023 to support larger retinas
		
//System.out.println("num amacrine grid cells = "+numAmacrine);
		m_amaList = new ArrayList();
		m_amaCenList = new ArrayList();
		for (int i=0; i<numAmacrine; i++)
		{
			int x = m_in.readShort();
			int y = m_in.readShort();
			m_amaList.add(new Point(x,y));
			m_amaCenList.add(new Point(x,y));
//System.out.println("grid cell at "+x+","+y);
		}

		// read boundary cells
		int numBoundary = m_in.readShort();
//System.out.println("num boundary cells = "+numBoundary);
		m_boundaryList = new ArrayList();
		for (int i=0; i<numBoundary; i++)
		{
			int x = m_in.readShort();
			int y = m_in.readShort();
			m_boundaryList.add(new Point(x,y));
//System.out.println("boundary cell at "+x+","+y);
		}

		// read border cells
		int numBorder = m_in.readShort();
//System.out.println("num boundary cells = "+numBoundary);
		m_borderList = new ArrayList();
		for (int i=0; i<numBorder; i++)
		{
			int x = m_in.readShort();
			int y = m_in.readShort();
			m_borderList.add(new Point(x,y));
			m_amaList.add(new Point(x, y));
//System.out.println("boundary cell at "+x+","+y);
		}

		// initialize grid
		m_grid = new HexGrid(m_radius);
		for (int y=-m_radius; y<=m_radius; y++)
		{
			for (int x=-m_radius; x<=m_radius; x++)
			{
				if (!HexGrid.inGrid(x, y, m_radius))
					continue;
				m_grid.set(x, y, new Pixel(x, y));
			}
		}
		// assign neighbors
		for (int y=-m_radius; y<=m_radius; y++)
		{
			for (int x=-m_radius; x<=m_radius; x++)
			{
				if (!HexGrid.inGrid(x, y, m_radius))
					continue;

				int nbrCnt = 0;
				Pixel pix = (Pixel) m_grid.get(x, y);
				for (int dy=-1; dy<=1; dy++)
				{
					for (int dx=-1; dx<=1; dx++)
					{
						if (!HexGrid.inGrid(dx, dy, 1))
							continue;
						if (!HexGrid.inGrid(x+dx, y+dy, m_radius))
							continue;
						Pixel nbr = (Pixel) m_grid.get(x+dx, y+dy);
						if (nbr != pix)
							pix.neighbors[nbrCnt++] = nbr;
					}
				}
			}
		}

		m_nextStart = 0;
		m_nextFrameTime = 0;
	}

	public void close() throws IOException
	{
		m_in.close();
		//System.out.println("done");
	}

	public int nextFrame()	throws IOException
	{
		// load next frame data
		int res = loadData();
		if (res != 0)
			return res;

		// update wave image
		updateImage();

		return 0;
	}

	public String header()	{ return m_header;			}
	public int	radius()	{ return m_radius;			}
	public int	frameTime()	{ return m_nextFrameTime;	}
	public Pixel pixel(int x, int y)
	{
		if (HexGrid.inGrid(x, y, m_radius))
			return (Pixel) m_grid.get(x, y);
		else
			return null;
	}

	///////////////////////////////////////////////////////////////////

	protected int loadData() throws IOException
	{
		// load next chunk of data
		while (m_nextStart < m_nextFrameTime)
		{
			int tag = m_in.readByte();
			if (tag == 't')
				m_nextStart = m_in.readInt();
			else if (tag == 'e')
			{
				// end of stream - 
				m_nextStart = 0x0fffffff;
				// break out 
				return -1;
			}
			else if (tag == 'a')
			{
				int state = m_in.readByte();
				int px = m_in.readShort() - 4096;
				int py = m_in.readShort() - 4096;
				//int px = m_in.readShort() - 8192;
				//int py = m_in.readShort() - 8192;
				Pixel pix = (Pixel) m_grid.get(px, py);
				pix.amacrine = state;
				if ((state < 0) || (state > 3))
				{
					// parse error
					throw new IOException("Parse error in data file (state)");
				}
			}
			else 
			{
				// parse error
				throw new IOException("Parse error in data file (tag)");
			}
		}
		m_nextFrameTime += TIME_STEP;

		return 0;
	}

	protected void updateImage()
	{
		// fade calcium
		Pixel pix;
		for (int y=-m_radius; y<=m_radius; y++)
		{
			for (int x=-m_radius; x<=m_radius; x++)
			{
				if (!HexGrid.inGrid(x, y, m_radius))
					continue;

				pix = (Pixel) m_grid.get(x, y);
				pix.calcium *= 0.85;
			}
		}
				
		// for each active amacrine cell, increase calcium signal in
		// 	dendritic tree
		for (int y=-m_radius; y<=m_radius; y++)
		{
			for (int x=-m_radius; x<=m_radius; x++)
			{
				if (!HexGrid.inGrid(x, y, m_radius))
					continue;
				
				pix = (Pixel) m_grid.get(x, y);
				if (pix.amacrine > 0)
				{
					// if state not set, amacrine cell in this location
					// 	is quiet
					for (int dx=-3; dx<=3; dx++)
					{
						for (int dy=-3; dy<=3; dy++)
						{
							if (!HexGrid.inGrid(dx, dy, 3))
								continue;
							if (!HexGrid.inGrid(x+dx, y+dy, m_radius))
								continue;

							pix = (Pixel) m_grid.get(x+dx, y+dy);
							if ((dx==0) && (dy==0))
								pix.calcium += 0.01;
							else
								pix.calcium += 0.005;
							if (pix.calcium > 1.0)
								pix.calcium = 1.0;
						}
					}
				}
			}
		}
	}
}

