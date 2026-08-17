import java.util.ArrayList;

class Wave
{
	protected static ArrayList	s_waveList = null;
	protected static int		s_nextWaveNum = 1;

	protected	HexGrid		m_grid;
	protected	int			m_rad;

	protected 	int			m_num;	// wave number
	protected	int			m_size;	// num pixels presently active in wave

	// dirty when comprised of multiple independent waves
	protected	boolean		m_dirty;	
	protected	boolean		m_init;	

	// storage coordinates of center
	protected	int			m_pixX;
	protected	int			m_pixY;
	// physical center of wave
	protected	double		m_centerX;
	protected	double		m_centerY;
	protected	double		m_velocity;
	protected	int			m_startTime;
	protected	double		m_farPoint;

	Wave(int rad)
	{
		m_grid = new HexGrid(rad);
		m_rad = rad;
		m_num = 0;

		m_size = 0;
		m_centerX = 0;
		m_centerY = 0;
		m_velocity = -1;
		m_startTime = 0;
		m_farPoint = 0;

		m_dirty = false;
		m_init = false;
	}

	void reset()
	{
		m_grid.clear();

		m_size = 0;
		m_centerX = 0;
		m_centerY = 0;
		m_velocity = -1;
		m_farPoint = 0;

		m_dirty = false;
		m_init = false;
	}

	void markDirty()	{ m_dirty = true;	}
	boolean isDirty()	{ return m_dirty;	}
	int pixX()			{ return m_dirty?0:m_pixX;	}
	int pixY()			{ return m_dirty?0:m_pixY;	}
	double centerX()	{ return m_dirty?0:m_centerX;	}
	double centerY()	{ return m_dirty?0:m_centerY;	}
	double velocity()	{ return m_dirty?0:m_velocity;	}
	int size()			{ return m_size;				}
	int num()			{ return m_num;					}

	// subtract pixel from wave
	void subPixel() 	
	{ 
//System.out.println("Wave "+m_num+" is size "+(m_size-1));
		if (--m_size == 0)
		{
			// wave is over - tally size and return wave to pool
			for (int y=-m_rad; y<=m_rad; y++)
			{
				for (int x=-m_rad; x<=m_rad; x++)
				{
					if (!HexGrid.inGrid(x, y, m_rad))
						continue;
					Pixel pix = (Pixel) m_grid.get(x, y);
					if (pix == null)
						continue;
					m_size++;
				}
			}

			StatMaker.recordStatistics(this);
			freeWave(this);
//m_grid = null;
		}
	}

	void merge(Wave wv)
	{
//System.out.println("Wave "+m_num+" size = "+m_size);
//System.out.println("Wave "+wv.m_num+" size = "+wv.m_size);
		// absorb wave, mark self as dirty
		for (int y=-m_rad; y<=m_rad; y++)
		{
			for (int x=-m_rad; x<=m_rad; x++)
			{
				if (!HexGrid.inGrid(x, y, m_rad))
					continue;
				Pixel pix = (Pixel) wv.m_grid.get(x, y);
				if (pix != null)
				{
					m_grid.set(x, y, pix);
					m_size++;
					pix.waveNum = m_num;
					pix.wave = this;
				}
			}
		}
		markDirty();
		freeWave(wv);
//wv.m_grid  = null;
	}

	void addPixels(ArrayList pixelList, boolean init, int when)
	{
		if (init)
		{
			double xpos = 0;
			double ypos = 0;
			double sum = 0;
			for (int i=0; i<pixelList.size(); i++)
			{
				Pixel pix = (Pixel) pixelList.get(i);
//System.out.println("  wave pixel\t"+pix.x+","+pix.y+"\t\t"+pix.calcium);
				sum += pix.calcium;
				xpos += pix.calcium * pix.posX;
				ypos += pix.calcium * pix.posY;

//if (m_num == 40) System.out.println(" adding "+pix.x+","+pix.y);
				m_grid.set(pix.pixX, pix.pixY, pix);
				pix.waveNum = m_num;
				pix.wave = this;
			}

			m_size = pixelList.size();
//System.out.println("Wave "+m_num+" starting at size "+m_size);
			// take weighted average of pixels to find center (round 
			// 	to nearest pixel)
			m_centerX = xpos / sum;
			m_centerY = ypos / sum;
			m_startTime = when;
			// have physical center, now need to work backwards and find
			// 	position on retina
			double pos = m_centerY / (34.0 * Math.sqrt(3.0)/2.0);
			m_pixY = (int) (pos>0?pos+0.5:pos-0.5);
			pos = (m_centerX - m_pixY * 17) / 34.0;
			m_pixX = (int) (pos>0?pos+0.5:pos-0.5);

//System.out.print("new wave ("+m_num+") at "+m_centerX+","+m_centerY);
		}
		else	// register points; update velocity score
		{
			for (int i=0; i<pixelList.size(); i++)
			{
				Pixel pix = (Pixel) pixelList.get(i);
//if (m_grid == null)
//	System.err.println("Error - wave "+num()+" has been freed");
				if (m_grid.get(pix.pixX, pix.pixY) == null)
				{
					m_grid.set(pix.pixX, pix.pixY, pix);
//if (m_num == 40) System.out.println(" adding "+pix.x+","+pix.y);
					m_size++;
					pix.waveNum = m_num;
					pix.wave = this;

					double dx = pix.posX - m_centerX;
					double dy = pix.posY - m_centerY;
					double dist = Math.sqrt(dx*dx + dy*dy);
					if (dist > m_farPoint)
					{
						m_farPoint = dist;
						m_velocity = dist / (0.001 * (when - m_startTime));
//System.out.println("  "+m_num+"  sz="+m_size+"  ("+pix.posX+","+pix.posY+") vel="+m_velocity+"  dist="+dist+" dT="+(when-m_startTime));
//if (m_num == 40) System.out.println("velocity = "+m_velocity);
					}
				}
			}
//System.out.println("Wave "+m_num+" increasing to size "+m_size+" ("+pixelList.size()+")");
		}
	}

	static Wave createWave(int rad)
	{
		if (s_waveList == null)
			s_waveList = new ArrayList();

		int sz = s_waveList.size();
		Wave wv;
		if (sz > 0)
		{
			wv = (Wave) s_waveList.remove(sz-1);
//System.out.println("recycle (old num="+wv.num()+")");
			wv.reset();
		}
		else
			wv = new Wave(rad);
		wv.m_num = s_nextWaveNum++;
		return wv;
	}

	static void freeWave(Wave wv)
	{
//System.out.println("Wave "+wv.num()+" freed");
		s_waveList.add(wv);
	}
}

