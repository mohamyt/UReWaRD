/**********************************************************************
    Amacrine.h - header file for amacrine cell class
    Copyright 2007 Keith Godfrey

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
#if !defined(AMACRINE_H)
#define AMACRINE_H

class Amacrine {
public:
	// constructor - x,y is hex coordinates
	Amacrine(int x, int y);
	~Amacrine();

	void init();

	// distance of cell from retina center
	double dist()	{ return m_centerDist;	}

	// x,y position of cell in hex grid
	short posX()	{ return x;	}
	short posY()	{ return y;	}
	// physical x,y coordinates of cell in retina
	double xCoord()	{ return m_posX;	}
	double yCoord()	{ return m_posY;	}

	// list neighbor cells is cached so they don't have to be
	// 	recomputed on each simulation round
	void addNeighbor(Amacrine * nbr);

	// another cell is depolarized - accept input from that cell
	void acceptInput(double ach);

	// update round where all information is exchanged between cells
	// 	(foreign update)
	void calcInput();	
	// update round to update internal state given the total input
	// 	received this round (domestic update)
	void updateState();

	// in case someone wants to know our state, give them a way
	// 	to find out
	int state()	{ return m_state;	}

	// interface for butterfly effect tests
	void alterThresh(double d)	{ m_thresh += d;	}

	// write parameter values 
	static void writeHeader()
	{
		char buf[128];
		sprintf(buf, "%s\t%f\n", "P_H1", P_H1);
		shell->appendHeaderInfo(buf);
		sprintf(buf, "%s\t%f\n", "P_H2", P_H2);
		shell->appendHeaderInfo(buf);
#if (SIMTYPE > 1)
		sprintf(buf, "%s\t%f\n", "P_H4", P_H4);
		shell->appendHeaderInfo(buf);
#endif		// SIMTYPE > 1
		sprintf(buf, "%s\t%f\n", "P_D", P_D);
		shell->appendHeaderInfo(buf);
		sprintf(buf, "%s\t%f\n", "P_P", P_P);
		shell->appendHeaderInfo(buf);
		sprintf(buf, "%s\t%f\n", "P_K", P_TK);
		shell->appendHeaderInfo(buf);
	}

protected:
	double m_achInput;

	double m_accumInput;

	double m_exc;
	// current activation threshold
	double m_thresh;
	// pending change of threshold, needed for variable depolarization
	// 	duration (this value slowly is applied to m_thresh - right now
	// 	at 4.0 units per second, but works for other values)
	double m_dThresh;

	// used to indicate termination of current state
	double m_timer;

	// rate at which threshold decays each round
	double m_threshDecay;

	// physical coordinates of cell
	double m_centerDist;
	double m_posX;
	double m_posY;

	// cell state - quiet (o), during fixed duration depol (1), or 
	// 	after fixed duration and still depolarized (2)
	int m_state;

	// sum of max neighbor input (scales input in border region)
	double m_nbrInput;	

	// hex coordinates of cell
	short x;
	short y;

	// list of neighboring cells, and relative area of overlap of
	// 	dend arbors with these cells
	Arraylist<Amacrine *>	*m_nbrList;
	Arraylist<double>		*m_strList;
};

#endif 		// AMACRINE_H

