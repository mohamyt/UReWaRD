/**********************************************************************
    retina.h - header file and parameter declaration for retinal 
		wave simulator
    Copyright 2007 Keith Godfrey
    Copyright 2023 Benjamin Cappell

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
#if !defined(RETINA_H)
#define RETINA_H

#include "hexgrid.h"
#include "arrays.h"
#include "crow.h"

/*
	Data file format (all data stored big endian)
	// NOTE: this is approximate - see main.cpp (Shell::run) for
	//  actual write code, and WaveMaker.java for reading code
	
	int24	file ident ('a' 'm' 'a')
	int8	version

	// version 3
	int16 				header length
	char[header length]	simulation info (stored as text)

	int16				radius of hex storage grid

	int32				number of amacrine cells  //changed int16->int32
	foreach amacrine cell
		int16		x position
		int16		y position

	int16				number of cells composing boundary
						(these cells have dendrites extending
						 beyond retinal boundary)
	foreach boundary cell
		int16		x position
		int16		y position

	int16				number of cells that are non-active
						(i.e. exist outside of circular retina -
						 data stored in hexagon, so some spaces
						 are empty)
	foreach border "cell"
		int16		x position
		int16		y position


	// simulation data
	// repeat until completed
	// for each new timestep indicating an active cell:
		int8		't'
		int32		timestamp (# of milliseconds since simulation start)
					NOTE: warm-up period not included in simulation 
					(these are stored as negative time values)
		
	// for each event (i.e. an amacrine cell changed state)
		int8		'a'	
		int8		new state of cell
		int16		x position of cell
		int16		y position of cell

	// end of simulation
		int8		'e' (should be last character in file)
*/

// 'traditional' model is type=1; variable burst dur is type=2; custom setting is type 0 (trad) or 3 (var)
#define SIMTYPE		(0)

// constants used by data file to indicate when amacrine cell goes on|off
#define	AMA_STATE_OFF	(0)
#define	AMA_STATE_PULSE	(1)
#define	AMA_STATE_BURST	(2)

#define AMA_DEND_RADIUS 	( 170.0)	// microns was 85.0 in V1!

///////////////////////////////////////////
// parameters for different simulation runs
//
#if (SIMTYPE == 1)
// ferret
#define P_H1_INIT	(4.0)
#define P_H2_INIT	(0.75)
#define P_D_INIT	(1.30)
#define P_P_INIT	( 43.0)
#define P_TK_INIT	(0.250)
// rabbit
//#define P_H1_INIT	(4.0)
//#define P_H2_INIT	(0.60)
//#define P_D_INIT	(1.05)
//#define P_P_INIT	( 44.0)
//#define P_TK_INIT	(0.250)
// mouse
//#define P_H1_INIT	(4.0)
//#define P_H2_INIT	(0.75)
//#define P_D_INIT	(2.30)
//#define P_P_INIT	( 32.0)
//#define P_TK_INIT	(0.350)
// chick E14-15 (2000 Sernagor)
//#define P_H1_INIT	(4.0)
//#define P_H2_INIT	(0.40)
//#define P_D_INIT	(1.05)
//#define P_P_INIT	( 38.0)
//#define P_TK_INIT	(0.025)
// chick E16 (98 Wong)
//#define P_H1_INIT	(3.1)
//#define P_H2_INIT	(0.10)
//#define P_D_INIT	(0.80)
//#define P_P_INIT	( 30.0)
//#define P_TK_INIT	(0.020)
// turtle (vel=226+/-14 sern03; IWI=40-100 burst/sec Wong99)
//#define P_H1_INIT	(4.0)
//#define P_H2_INIT	(0.70)
//#define P_D_INIT	(1.00)
//#define P_P_INIT	( 23.0)
//#define P_TK_INIT	(0.200)
// ferret - det
//#define P_H1_INIT	(5.0)
//#define P_H2_INIT	(0.85)
//#define P_D_INIT	(1.30)
//#define P_P_INIT	( 45.0)
//#define P_TK_INIT	(0.250)
//#define DETERMINISTIC	(1)
#elif (SIMTYPE == 2)
// ferret
#define P_H1_INIT	(5.0)
#define P_H2_INIT	(0.25)
#define P_H4_INIT	(4.0)
#define P_D_INIT	(0.45)
#define P_P_INIT	(36.0)
#define P_TK_INIT	(0.300)

#else //custom 		v2			v1

#define P_H1_INIT	(5.0)	// 	same	control the increase in threshold after an amacrine cell depolarizes
#define P_H2_INIT	(0.4)	// 	0.25	control the increase in threshold after an amacrine cell depolarizes

#define P_D_INIT	(1.25)	// 	2		[seconds] D depolarization interval, during which an amacrine cell actively excites its neighbors
#define P_P_INIT	(30.0)	// 	36.0	[seconds] P regulates the interval between spontaneous amacrine cell depolarizations
#define P_TK_INIT	(0.200)	// 	0.100	[seconds] K time constant (T)K, which regulates how fast cells react to excitation from neighboring cells



#if (SIMTYPE > 1)
#define P_H4_INIT	(4.0)  // unclear - maybe same as h1 and h2
#endif // SIMTYPE > 1
#endif // SIMTYPE

#if defined(MAIN_CPP)
// keep many values as variables (instead of defines) so they can
// 	be changed at runtime

int MS_PER_ROUND	=    25; //100 ms = 10 FPS? 25 ms-> 10 FPS (yes, not 40??) 1000 ms = 4FPS???
double DELTA_T		= MS_PER_ROUND * 0.001;

// SIMULATION RUNTIME
#define MINUTES		(60)
int WARMUP_PERIOD	=   15 * MINUTES;
double RUNTIME	=  15 * MINUTES; // -> 9k images?
// int WARMUP_PERIOD  	=	0 * MINUTES;
// double RUNTIME		=   1 * MINUTES;

//int	RADIUS	= 36;
int	RADIUS		= 256; //radius of the retina
//int	CON_RAD		= 4; 
double	CON_RAD		= 5.0; 


double NOW		= 0.0;
class Shell * shell = NULL;
class Crow * g_rng = NULL;
double P_H1 = P_H1_INIT;
double P_H2 = P_H2_INIT;
#if (SIMTYPE > 1)
double P_H4 = P_H4_INIT;
#endif	// SIMTYPE > 1
double P_D = P_D_INIT;
double P_TK = P_TK_INIT;
double P_P = P_P_INIT;
#else
extern int	RADIUS;
extern double	CON_RAD;
extern int MS_PER_ROUND;
extern double DELTA_T;
extern int WARMUP_PERIOD;
extern double RUNTIME;
extern double NOW;
extern class Shell * shell;
extern class Crow * g_rng;
extern double P_H1;
extern double P_H2;
#if (SIMTYPE > 1)
extern double P_H4;
#endif	// SIMTYPE > 1
extern double P_D;
extern double P_TK;
extern double P_P;
#endif		// MAIN_CPP

// wrapper for simulation - handles general input and output
class Shell {
public:
	Shell();
	~Shell();

	// run simulation using current parameters
	void run();

	// amacrine cell logs change of state (e.g. quiet to depolarized)
	void logEvent(int state, int x, int y);

	void init();
	void reset();

	void appendHeaderInfo(char * str);

protected:
	double	lastUpdate;
	int		round;
	FILE *	logfile;

	Arraylist<class Amacrine *> * amaList;
	Arraylist<class Amacrine *> * amaCenterList;
	Arraylist<int> * boundaryListX;
	Arraylist<int> * boundaryListY;
	Arraylist<int> * borderListX;
	Arraylist<int> * borderListY;
	HexGrid<class Amacrine *> * amaGrid;

	char *	m_headerStr;
	int		m_headerLen;
	int		m_headerBufLen;
};

#endif	// RETINA_H

