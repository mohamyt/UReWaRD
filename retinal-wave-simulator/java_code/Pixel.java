import java.io.IOException;
import java.util.ArrayList;

class Pixel {
	Pixel(int x, int y)
	{
		this.pixX = x;
		this.pixY = y;

		// hexagonal grid is hexagon shaped with a flat top
		// the grid is stored in memory as 
		//           -2,2       -1,2       0,2   
		//     -2,1        -1,1       0,1       1,1 
		// -2,0      -1,0        0,0       1,0       2,0
		//     -1,-1       0,-1       1,-1      2,-1 
		//            0,-2       1,-2      2,-2   
		// or, more clearly
		// -2,2		-1,2	0,2	
		// -2,1		-1,1	0,1		1,1
		// -2,0		-1,0	0,0		1,0		2,0
		//			-1,-1	0,-1	1,-1	2,-1
		//					0,-2	1,-2	2,-2
		// with blank locations on the grid corresponding to NULLs
		//	in the storage array (i.e. unused space)
		// use this layout to translate storage coordinates into physical ones
		posX = x * 34 + y * 17;
		posY = y * (34 * Math.sqrt(3.0)/2.0);

		waveNum = -1;
		wave = null;

		searchState = -1;
		neighbors = new Pixel[6];

		amacrine = 0;	// 0 if off, >=1 if on
		calcium = 0;

		iwiList = new ArrayList();
		lastOff = -1;
	}

	public int		amacrine;
	public double	calcium;

	public int		waveNum;
	public Wave		wave;

	public int		searchState;
	public Pixel 	neighbors[];

	// storage coordinates
	public int		pixX;
	public int		pixY;

	// physical coordinates
	public double	posX;
	public double	posY;

	public ArrayList	iwiList;
	public int		lastOff;
	public int		trackIWI;
}

