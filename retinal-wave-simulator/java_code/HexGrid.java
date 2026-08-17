
import java.awt.Point;

// image data is typically represented such that the first row of data 
//	is the top row in the image.  However, hex grids, which are designed
//	to be circular and have the center point as 0,0, necessarily have
//	negative numbers.  It is too counter-intuitive to have the first
//	row in the hex grid representing a negative Y value 
//	(i.e. height/2 - rad), so the hex grid is inverted relative to
//	the image it represents
public class HexGrid
{
	protected int	m_rad;
	protected int	m_len;
	protected int	m_count;

	protected int	m_iterX;
	protected int	m_iterY;
	protected int	m_iterCtr;

	protected Object[][]	m_grid;

	public int	getRadius()		{ return m_rad;		}
	
	public HexGrid(int rad)
	{
		m_rad = rad;
		m_len = 2 * rad + 1;
		final int r1 = rad + 1;
		m_count = r1 * r1 - 1 + rad * (rad-1);

		m_grid = new Object[m_len][m_len];

		m_iterX = -rad;
		m_iterY = rad;
		m_iterCtr = 0;
	}

	public void clear()
	{
		for (int x=0; x<m_len; x++)
			for (int y=0; y<m_len; y++)
				m_grid[x][y] = null;
	}

	public void clear(HexGrid master)
	{
		if (m_rad != master.m_rad)
		{
			// bitch
			throw new java.lang.RuntimeException("Attempted to " +
					"initialize a grid using a different sized template.\n" +
					"Radius=" + m_rad + "  Template radius=" + 
					master.m_rad);
		}
		for (int x=0; x<m_len; x++)
			for (int y=0; y<m_len; y++)
				m_grid[x][y] = master.m_grid[x][y];
	}

	public boolean inGrid(int x, int y)
	{
		return inGrid(x, y, m_rad);
	}
//	public boolean inGrid(int x, int y)
//	{
//		// if y positive and w/in range, make sure x not in top right
//		if ((y >= 0) && (y <= m_rad) && (x >= (y-m_rad)))
//			return true;
//		// if y negative and w/in range, make sure x not in bottom left
//		if ((y < 0) && (y >= -m_rad) && ((x+m_rad) < (m_len+y)))
//			return true;
//		return false;
//	}

//	public static boolean inGrid(int x, int y, int rad)
//	{
//		final int len = 2 * rad + 1;
//		// if y positive and w/in range, make sure x not in top right
//		if ((y >= 0) && (y <= rad) && (x >= (y-rad)))
//			return true;
//		// if y negative and w/in range, make sure x not in bottom left
//		if ((y < 0) && (y >= -rad) && ((x+rad) < (len+y)))
//			return true;
//		return false;
//	}
//
	public static boolean inGrid(int x, int y, int rad)
	{
		if (calcRingFromHexSpace(x, y) <= rad)
			return true;
		else
			return false;
//		if (x*y <= 0)
//		{
//			// quadrant 2 or 4 - these are full quadrants so as long
//			//	as absolute valoe of x and y are <=rad, we're cool
//			if ((x >= -rad) && (x <= rad) && (y >= -rad) && (y <= rad))
//				return true;
//			else
//				return false;
//		}
//		else if (x > 0)
//		{
//			// Q1 - make sure not to right of line y = -x + rad
//			if ((x + y) > rad)
//				return false;
//			else 
//				return true;
//		}
//		else
//		{
//			// Q3 - make sure not to left of y = -x - rad
//			if ((x+y) < -rad)
//				return false;
//			else
//				return true;
//		}
	}

	// debug function for inGrid
	public static boolean gg(int x, int y, int rad)
	{
		final int len = 2 * rad + 1;
System.out.print("len="+len+"  x="+x+"  y="+y+"  rad="+rad+"   ");
		if (y >= 0)
		{
			// if y positive 
			// if w/in range, make sure x not in bottom left
			if (y <= rad)
			{
				if (x >= (y-rad))
				//if ((x+rad) < (len-y))
				{
System.out.println("pass 1  ");
					return true;
				}
//else System.out.println("x+rad >= len-y");
			}
//else System.out.println("y>rad");
		}
		else
		{
			// y negative
			// if w/in range, make sure x not in top right
			if (y >= -rad)
			{
				//if (x >= -(rad+y))
				if ((x+rad) < (len+y))
				{
System.out.println("pass 2  ");
					return true;
				}
//else System.out.println("x < -(rad+y)");
			}
//else System.out.println("y < -rad");
		}
System.out.println("no pass");
		return false;
	}

	// calculate the ring a given unit is in
	public static int calcRingFromHexSpace(int x, int y)
	{
		if (x*y < 0)
		{
			if (x < 0)
				x = -x;
			if (y < 0)
				y = -y;
			return (x>y?x:y);
		}
		else
		{
			if (x < 0)
				x = -x;
			if (y < 0)
				y = -y;
			return x + y;
		}
	}

	public Object getRadially(int ring, int hex)
	{
		Point pt = HexPosition.getXY(ring, hex);
		return get(pt.x, pt.y);
	}
	
	public void setRadially(int ring, int hex, Object obj)
	{
		Point pt = HexPosition.getXY(ring, hex);
		set(pt.x, pt.y, obj);
	}
	
	public void set(int x, int y, Object obj)
	{
		if (!inGrid(x, y))
		{
			throw new java.lang.RuntimeException("Attempted to set " +
					"nonexistent grid point in hex array  x="+x+"  y="+y);
		}
		m_grid[x+m_rad][y+m_rad] = obj;
	}

	public Object get(int x, int y)
	{
		if (!inGrid(x, y))
		{
			throw new java.lang.RuntimeException("Attempted to get " +
					"nonexistent grid point in hex array  x="+x+"  y="+y);
		}
		return m_grid[x+m_rad][y+m_rad];
	}

	public int count()	{ return m_count;		}

	public void resetIterator()
	{
		m_iterX = -m_rad;
		m_iterY = m_rad;
		m_iterCtr = 0;
	}

	public Object next()
	{
		if (m_iterCtr++ >= m_count)
			return null;

		// get object from current position
		Object obj = get(m_iterX, m_iterY);

		// now advance place holders
		if (!inGrid(++m_iterX, m_iterY))
		{
			m_iterX = -m_rad;
			m_iterY--;
			if (!inGrid(m_iterX, m_iterY))
			{
				System.out.println("Internal error - iteration exceeded " +
						"bounds (" + m_iterX + ", " + m_iterY + ", " +
						m_iterCtr + ")");
				System.exit(1);
			}
		}
		return obj;
	}
	
	// return a grid object of specified radius centered about (x, y)
	// parts of the neighborhood that are outside of the current grid 
	//	will be null
	public void getNeighborhood(int x, int y, HexGrid hg)
	{
		hg.clear();
		int rad = hg.getRadius();

		// copy block from current grid.  if it's outside of true grid 
		//	boundaries, the values will be NULL, if we try to set a 
		//	value in new grid outside it's boundaries (i.e. top right 
		//	or lower left), it will be ignored
		for (int i=-rad; i<=rad; i++)
		{
			if ((x+i) < -m_rad)
				continue;
			else if ((x+i) > m_rad)
				break;

			for (int j=-rad; j<=rad; j++)
			{
				if ((y+j) < -m_rad)
					continue;
				else if ((y+j) > m_rad)
					break;

				hg.set(i, j, get(x+i, y+j));
			}
		}
	}
}

/***
  C++ style header interface

class HexGrid
{
	public:
		HexGrid(int rad);
		void		clear();
		boolean		inGrid(int x, int y);
		static boolean inGrid(int x, int y, int rad);

		void	set(int x, int y, Object obj);
		Object	get(int x, int y);
		void	resetIterator();
		Object	next();

		void	getNeighborhood(int x, int y, HexGrid hg);
		
		// returns number of hex units in grid
		int		count()				{ return m_count;		}
		int		getRadius()			{ return m_rad;			}

	protected: 
		int	m_rad;
		int	m_len;
		int	m_count;

		int	m_iterX;
		int	m_iterY;
		int	m_iterCtr;
		Object[][]	m_grid;
}
***/

// hex grid is defined as compact array of hexagons where each hex
//	unit has two edges parallel to the X axis
class HexPosition
{
	// returns relative angular position within unit on [0,1]
	public static double getPositionInUnit(int ring, double theta)
	{
		// calculate radian width of each hex
		final double width = 2.0 * Math.PI / count(ring);
		// because we're going with flat sides, the Y axis will
		//	alternately cut through band between grid units on different
		//	rings (between on odd rings, through on even)

		if ((ring%2) == 0)	// even
		{
			// need to adjust
			theta += width / 2.0;
		}

		// ensure theta is on [0, 2pi)
		while (theta < 0)
			theta += 2 * Math.PI;
		while (theta >= 2 * Math.PI)
			theta -= 2 * Math.PI;

		// now figure out which hex unit theta goes through
		final int unit = (int) (theta / width);
		final double start = 1.0 * unit * width;
		return (theta - start) / width;
	}

	public static int getRingUnit(int ring, double theta)
	{
		// calculate radian width of each hex
		final double width = 2.0 * Math.PI / count(ring);
		// because we're going with flat sides, the Y axis will
		//	alternately cut through band between grid units on different
		//	rings (between on odd rings, through on even)

		if ((ring%2) == 0)	// even
		{
			// need to adjust
			theta += width / 2.0;
		}

		// ensure theta is on [0, 2pi)
		while (theta < 0)
			theta += 2 * Math.PI;
		while (theta >= 2 * Math.PI)
			theta -= 2 * Math.PI;

		// now figure out which hex unit theta goes through
		final int unit = (int) (theta / width);
//final double lineLeft = 180.0 * (unit * width + width/2.0) / Math.PI;
//final double lineRight = 180.0 *((unit-1)*width+width/2.0) / Math.PI;
//final double w = 180.0 * width / Math.PI;
//System.out.println("\nLow=" + lineRight);
//System.out.println("theta="+(theta*180/Math.PI)+"\t unit="+unit+"\t width="+w);
//System.out.println("High="+lineLeft);
		return unit;
	}

	// returns number of hex units in the specified ring
	public static int count(int ring)
	{
		if (ring == 0)
			return 1;
		else if (ring < 0)
			ring = -ring;
		return ring * 6;
	}

	// returns position of hex unit in rectangular storage array
	// ring is the radial ring from grid center
	// hex is the point in that ring, oriented from the positive Y axis
	public static Point getXY(int ring, int hex)
	{
		final int cnt = count(ring);
		final int leg = cnt / 6;
		// hex position is flat top
		// zero position should correspond to middle of top
		hex -= leg/2;

		while (hex < 0)
			hex += cnt;
		while (hex >= cnt)
			hex -= cnt;

		int x, y;
		if (hex <= leg)
		{
			x = hex;
			y = ring - hex;
		}
		else if (hex <= 2*leg)
		{
			x = ring;
			y = -(hex - leg);
		}
		else if (hex <= 3*leg)
		{
			x = ring - (hex - 2*leg);
			y = -leg;
		}
		else if (hex <= 4*leg)
		{
			final int i = hex - 3*leg;
			x = -i;
			y = -leg + i;
		}
		else if (hex <= 5*leg)
		{
			final int i = hex - 4*leg;
			x = -leg;
			y = i;
		}
		else
		{
			final int i = hex - 5*leg;
			x = -leg + i;
			y = leg;
		}
		return new Point(x, y);
	}

//	public static boolean inGrid(int x, int y, int rad)
//	{
//		final int len = 2 * rad + 1;
//		// if y positive and w/in range, make sure x not in top right
//		if ((y >= 0) && (y <= rad) && ((x + rad) < (len - y)))
//			return true;
//		// if y negative and w/in range, make sure x not in bottom left
//		if ((y < 0) && (y >= -rad) && (x >= -y))
//			return true;
//		return false;
//	}

}
