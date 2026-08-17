/**********************************************************************
    HexGrid - a hexagonal, 2D array
    Copyright 2005-7 Keith Godfrey

    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program; if not, write to the 
	    Free Software Foundation, Inc.
		59 Temple Place, Suite 330
		Boston, MA  02111-1307  USA

**********************************************************************/
#if !defined(HEXGRID_H)
#define HEXGRID_H

#include <stdio.h>
#include <stdlib.h>
#include <math.h>
#include <string.h>
#include <assert.h>

#if defined(MAX_GRIDS)
// if we've created more than X arrays, odds are that something is wrong
#define memCheck() { if (HexGrid<T>::newInstance(false) > MAX_GRIDS) { printf("Error - exceeded maximum array count for %s\n", name); exit(1); } }
#else
#define memCheck()		{ }
#endif	// MAX_GRIDS

class Point
{
public:
	Point() { x = 0; y = 0; }
	int	x;
	int	y;
};

#define MAX_NAME_LEN	(32)

// a hex grid is defined as compact array of hexagons where each hex
//	unit has two edges parallel to the X axis
// the hex grid itself is restricted to being circular in form (i.e.
//	a hexagon).  
//
// in C++, it is possible to define a class that uses an arbitrary data
//	form.  such classes are called templates.  in this template, the 
//	value 'T' represents the data form to be stored, whether that be
//	an integer, a floating point value, or a pointer of another object
//
// NOTE - when using a hex grid to store image data:
// image data is typically represented such that the first row of data 
//	is the top row in the image.  However, hex grids, which are designed
//	to be circular and have the center point as 0,0, necessarily have
//	negative numbers.  It is too counter-intuitive to have the first
//	row in the hex grid representing a negative Y value 
//	(i.e. height/2 - rad), so the hex grid is inverted relative to
//	the image it represents
//
template <class T>
class HexGrid
{
protected:
	int		m_rad;		// radius of this grid
	int		m_len;		// width of the grid (2*rad+1)
	int		m_count;	// number of units in grid

	T*		m_grid;

	char	m_name[MAX_NAME_LEN];

public:
	HexGrid(int rad, const char * name)
	{
		m_rad = rad;
		m_len = 2 * rad + 1;
		const int r1 = rad + 1;
		m_count = 2 * r1 * r1 - 1 + rad * (rad-1);

		int sz = m_len * m_len * sizeof(T);
		m_grid = (T*) malloc(sz);
		memset(m_grid, 0, sz);

		int len = strlen(name);
		if (len >= (MAX_NAME_LEN-1))
			len = MAX_NAME_LEN-1;
		strncpy(m_name, name, len);
		m_name[len] = 0;
		newInstance(true);
		memCheck();
	}

	static int newInstance(bool created)
	{
		static int s_count;
		if (created)
			s_count++;
		return s_count;
	}

	~HexGrid()
	{
		free(m_grid);
	}

	///////////////////////////////////////////////////////////////////

	int	getRadius()		{ return m_rad;		}
	
	// reset all elements in grid to zero (or NULL if it's a pointer)
	void clear()
	{
		int sz = m_len * m_len * sizeof(T);
		memset(m_grid, 0, sz);
	}

	bool inGrid(int x, int y)
	{
		return inGrid(x, y, m_rad);
	}

	// general function for determining if a point is within a grid
	//	having the specified radius
	static bool inGrid(int x, int y, int rad)
	{
		if (x*y <= 0)
		{
			// quadrant 2 or 4 - these are full quadrants so as long
			//	as absolute valoe of x and y are <=rad, we're cool
			if ((x >= -rad) && (x <= rad) && (y >= -rad) && (y <= rad))
				return true;
			else
				return false;
		}
		else if (x > 0)
		{
			// Q1 - make sure not to right of line y = -x + rad
			if ((x + y) > rad)
				return false;
			else 
				return true;
		}
		else
		{
			// Q3 - make sure not to left of y = -x - rad
			if ((x+y) < -rad)
				return false;
			else
				return true;
		}
	}

	///////////////////////////////////////////////////////////////////
	// data access functions
	// get/set access a point by its coordinates, where 
	//	x is on [-rad,rad]
	//	y is on [-rad,rad]
	// certain combinations of x,y will fall into the rectangular 
	//	storage grid but not into the represented hexagon (accessing
	//	such points will cause an error).  to determinie if a point
	//	is within the hexagonal grid, call inGrid(x,y)

	void set(int x, int y, T obj)
	{
		if (!inGrid(x, y))
		{
			fprintf(stderr, "Error - attempted to set nonexistent "
					"grid point in hex array '%s' (%d, %d)\n", m_name,
					x, y);
			int *xyz = 0;
			*xyz = 5;
			exit(1);
		}
		m_grid[(y+m_rad)*m_len + (x+m_rad)] = obj;
	}

	T get(int x, int y)
	{
		if (!inGrid(x, y))
		{
			fprintf(stderr, "Error - attempted to get nonexistent grid "
					"point in hex array %s (%d,%d)\n", m_name, x, y);
			exit(1);
		}
		return m_grid[(y+m_rad)*m_len + (x+m_rad)];
	}

	// get/set radially - the hex unit is the numerical position of
	//	a unit within a specified ring, counting from the positive
	//	Y axis clockwise.  
	// this function wraps around, so accessing the 7th position in
	//	a ring with 6 units will actually be accessing the unit in
	//	position 1
	// use the function 'unitsInRing' to determine how many units
	//	are in a specified ring
	T getRadially(int ring, int hex)
	{
		if (ring < 0)
		{
			// if using a negative ring number, make it positive
			//	but rotate ring by 180 degrees
			ring = -ring;
			hex += unitsInRing(ring) / 2;
		}
		
		if (ring > m_rad)
		{
			fprintf(stderr, "Error - attempted to get grid point in "
					"non-existent ring %d on grid '%s' (grid rad=%d)\n", 
					ring, m_name, m_rad);
			exit(1);
		}
		Point pt;
		getXY(ring, hex, &pt);
		return get(pt.x, pt.y);
	}
	
	void setRadially(int ring, int hex, T obj)
	{
		if (ring < 0)
		{
			// if using a negative ring number, make it positive
			//	but rotate ring by 180 degrees
			ring = -ring;
			hex += unitsInRing(ring) / 2;
		}
		
		if (ring > m_rad)
		{
			fprintf(stderr, "Error - attempted to set grid point in "
					"non-existent ring %d on grid '%s' (grid rad=%d)\n", 
					ring, m_name, m_rad);
			exit(1);
		}
		Point pt;
		getXY(ring, hex, &pt);
		set(pt.x, pt.y, obj);
	}
	
	///////////////////////////////////////////////////////////////////
	
	// size() returns the number of hexagonal units within a hexagonal
	//	circle of a given radius.  if no radius is specified, then the
	//	number of units in the current grid is returned
	int size()	{ return m_count;		}
	static int size(int rad)	
	{ 
		const int r1 = rad + 1;
		return 2 * r1 * r1 - 1 + rad * (rad-1);	
	}

	// returns number of hexagonal units in a ring of a certain radius
	//	this is only the # of units in a specific ring; for the total 
	//	number of units in a circle of a certain radius, use size()
	static int unitsInRing(int rad)
	{
		if (rad == 0)
			return 1;
		else if (rad < 0)
			rad = -rad;
		return rad * 6;
	}

	///////////////////////////////////////////////////////////////////

	// fill the provided hexagonal grid with data from the current 
	//	grid, centering the data transfer at the specified coordinates
	// in other words
	//		getNeighborhood(5, 5, hg)
	// would overlay grid 'hg' on the current grid (this object) 
	//	putting the origin of 'hg' at (5,5).  the intersection of these
	//	two grids is what will be copied into 'hg'.  if part of 'hg'
	//	extends beyond the borders of this object, the values copied
	//	in will be null.
	void getNeighborhood(int x, int y, HexGrid *hg)
	{
		hg->clear();
		int rad = hg->getRadius();

		// copy block from current grid.  if it's outside of true grid 
		//	boundaries, the values will be NULL, if we try to set a 
		//	value in new grid outside it's boundaries (i.e. top right 
		//	or lower left), it will be ignored
		for (int j=-rad; j<=rad; j++)
		{
			for (int i=-rad; i<=rad; i++)
			{
				// make sure point lies within source grid
				if (!inGrid(x+i, y+j))
					continue;

				// make sure specified point lies within target grid
				if (!hg->inGrid(i, j))
					continue;

				// point lies within intersection of both grids 
				hg->set(i, j, get(x+i, y+j));
			}
		}
	}

	///////////////////////////////////////////////////////////////////
	// function(s) with limited usage (but were written and seem to 
	//	work, so don't delete them)

	// calculate the ring a given unit is in
	// in other words, given a position x,y, in a hexagonal grid, 
	//	calculate the ring in which that position lies
	static int calcRingFromHexSpace(int x, int y)
	{
		if (x*y < 0)
		{
			// quadrant 2 or 4 in storage grid
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

protected:
	///////////////////////////////////////////////////////////////////
	// internal utility function(s) ///////////////////////////////////

	// returns position of hex unit in rectangular storage array
	// ring is the radial ring from grid center
	// hex is the point in that ring, oriented from the positive Y axis
	static void getXY(int ring, int hex, Point *pt)
	{
		const int cnt = unitsInRing(ring);
		const int leg = cnt / 6;
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
			const int i = hex - 3*leg;
			x = -i;
			y = -leg + i;
		}
		else if (hex <= 5*leg)
		{
			const int i = hex - 4*leg;
			x = -leg;
			y = i;
		}
		else
		{
			const int i = hex - 5*leg;
			x = -leg + i;
			y = leg;
		}
		pt->x = x;
		pt->y = y;
	}
};

#undef memCheck

#endif		// HEXGRID_H
