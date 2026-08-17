/**********************************************************************
    Crow - a random number generator based on arcfour algorithm
    Copyright 2006-2007 Keith Godfrey

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
#if !defined(CROW_H)
#define CROW_H

#include <time.h>
#include <string.h>
#include <math.h>
#include "arrays.h"

/**********************************************************************
  Crow is based on Raven, being a smaller and simpler cousin.  It 
  focuses on producing high quality random numbers using a small
  memory footprint.  3 arcfour rounds are used to produce a double
  having 42 bits of "randomness".  Estimated memory footprint is 17K.
  Many features have been copied from Raven.
**********************************************************************/

///////////////////////////////////////////////////////////////////
// Crow API
//
// Random number access
//  rand()        returns a 32 bit random integer
//  rand(X,Y)     returns a uniformly distributed number (double
//                precision) on the interval [X,Y)
//  norm(M,D)     returns a normally distributed number (double 
//                precision) with mean M and standard deviation D
//	randBool()	  returns a random bit
//
// Initialization and seeding
//  bootstrap()   initializes system to its starting point
//  seedRand(S,L) seed the system with a string S of length L
//

// these tables of constants are used to initialize the LCGs
// last several primes before 2^19
// tables contain 256 pairs
const int CROW_M[] = {
	521009,	521021,	521023,	521039,	521041,	521047,	521051,	521063,
	521107,	521119,	521137,	521153,	521161,	521167,	521173,	521177,
	521179,	521201,	521231,	521243,	521251,	521267,	521281,	521299,
	521309,	521317,	521329,	521357,	521359,	521363,	521369,	521377,
	521393,	521399,	521401,	521429,	521447,	521471,	521483,	521491,
	521497,	521503,	521519,	521527,	521533,	521537,	521539,	521551,
	521557,	521567,	521581,	521603,	521641,	521657,	521659,	521669,
	521671,	521693,	521707,	521723,	521743,	521749,	521753,	521767,
	521777,	521789,	521791,	521809,	521813,	521819,	521831,	521861,
	521869,	521879,	521881,	521887,	521897,	521903,	521923,	521929,
	521981,	521993,	521999,	522017,	522037,	522047,	522059,	522061,
	522073,	522079,	522083,	522113,	522127,	522157,	522161,	522167,
	522191,	522199,	522211,	522227,	522229,	522233,	522239,	522251,
	522259,	522281,	522283,	522289,	522317,	522323,	522337,	522371,
	522373,	522383,	522391,	522409,	522413,	522439,	522449,	522469,
	522479,	522497,	522517,	522521,	522523,	522541,	522553,	522569,
	522601,	522623,	522637,	522659,	522661,	522673,	522677,	522679,
	522689,	522703,	522707,	522719,	522737,	522749,	522757,	522761,
	522763,	522787,	522811,	522827,	522829,	522839,	522853,	522857,
	522871,	522881,	522883,	522887,	522919,	522943,	522947,	522959,
	522961,	522989,	523007,	523021,	523031,	523049,	523093,	523097,
	523109,	523129,	523169,	523177,	523207,	523213,	523219,	523261,
	523297,	523307,	523333,	523349,	523351,	523357,	523387,	523403,
	523417,	523427,	523433,	523459,	523463,	523487,	523489,	523493,
	523511,	523519,	523541,	523543,	523553,	523571,	523573,	523577,
	523597,	523603,	523631,	523637,	523639,	523657,	523667,	523669,
	523673,	523681,	523717,	523729,	523741,	523759,	523763,	523771,
	523777,	523793,	523801,	523829,	523847,	523867,	523877,	523903,
	523907,	523927,	523937,	523949,	523969,	523987,	523997,	524047,
	524053,	524057,	524063,	524071,	524081,	524087,	524099,	524113,
	524119,	524123,	524149,	524171,	524189,	524197,	524201,	524203,
	524219,	524221,	524231,	524243,	524257,	524261,	524269,	524287
};

// among last primes before 8192 (i.e. 2^13) so that A*M < 2^32
// these are selected to match M values above to create maximal 
//	period (i.e. A^N mod M has a period of M-1 for each pair)
const short CROW_A[] = {
	8179,	8191,	8171,	8167,	8123,	8161,	8101,	8117,
	8147,	8111,	8093,	8081,	8089,	8069,	8053,	8039,
	8087,	8011,	8059,	8009,	7993,	8017,	7937,	7963,
	7907,	7933,	7951,	7927,	7919,	7879,	7949,	7877,
	7883,	7901,	7873,	7867,	7823,	7817,	7829,	7853,
	7841,	7793,	7759,	7789,	7727,	7757,	7741,	7723,
	7717,	7753,	7703,	7691,	7687,	7669,	7681,	7699,
	7673,	7621,	7583,	7643,	7639,	7649,	7607,	7603,
	7591,	7589,	7549,	7559,	7577,	7561,	7573,	7547,
	7541,	7537,	7517,	7481,	7523,	7499,	7529,	7507,
	7411,	7487,	7489,	7459,	7457,	7369,	7477,	7451,
	7333,	7283,	7309,	7417,	7351,	7297,	7433,	7331,
	7393,	7307,	7321,	7349,	7247,	7243,	7253,	7237,
	7219,	7229,	7213,	7151,	7211,	7193,	7187,	7207,
	7159,	7177,	7121,	7129,	7109,	7127,	7043,	7103,
	7057,	7079,	7069,	7039,	7019,	6983,	6997,	7027,
	7013,	6961,	7001,	6971,	6977,	6967,	6959,	6991,
	6863,	6917,	6947,	6907,	6871,	6949,	6883,	6911,
	6857,	6899,	6833,	6827,	6869,	6709,	6841,	6823,
	6829,	6779,	6803,	6763,	6781,	6761,	6793,	6791,
	6737,	6701,	6719,	6733,	6679,	6689,	6661,	6619,
	6673,	6703,	6637,	6581,	6653,	6599,	6577,	6691,
	6551,	6607,	6563,	6571,	6553,	6569,	6659,	6547,
	6449,	6521,	6451,	6491,	6529,	6481,	6469,	6373,
	6473,	6361,	6427,	6389,	6337,	6421,	6397,	6323,
	6379,	6353,	6343,	6367,	6317,	6359,	6329,	6301,
	6311,	6299,	6269,	6287,	6247,	6277,	6271,	6263,
	6217,	6229,	6199,	6203,	6173,	6257,	6131,	6133,
	6211,	6197,	6221,	6151,	6101,	6121,	6163,	6091,
	6089,	6079,	6143,	6113,	6073,	6011,	6053,	6067,
	6047,	6043,	6037,	6007,	5953,	5987,	6029,	5939,
	5927,	5981,	5857,	5903,	5881,	5827,	5879,	5867
};

static const int MASK = 0x3fff;
static const int BOX_SIZE = 14;
static const double	quanta = 1.0 / (1 << BOX_SIZE);

class Crow
{
public:
	Crow(const char * init)
	{
		seedInit(init);
	}

	///////////////////////////////////////////////////////////////////
	// random number generation

	// normally distributed random number, based on center and 
	//	standard deviation from there
	double norm(double center, double sdev)
	{
		if (m_normFlag == true)
		{
			m_normFlag = false;
			return center + sdev * m_normSpare;
		}

		double x, y, z;
		do {
			x = -1.0 + rand28() / (1.0 * (1 << 27));
			y = -1.0 + rand28() / (1.0 * (1 << 27));
			z = x*x + y*y;
		} while ((z >= 1.0) || (z == 0.0));
		z = sqrt(-2.0 * log(z) / z);
		m_normSpare = z * y;
		m_normFlag = true;
		const double val = center + sdev * z * x;
		return val;
	}

	// produces an element of a Poisson distribution based
	//	on given mean
	int poisson(double mean)
	{
		double target = exp(-mean);
		if (target <= 0)
		{
			// mean rate too low - exponential rounds to zero
			fprintf(stderr, "Internal error - Poisson distribution failed\n");
			exit(1);
		}
		double value = 1.0;
		int ctr = 0;
		while (value >= target)
		{
			value *= rand(0, 1);
			ctr++;
		}
		return ctr-1;
	}

	// returns a 12 bit random value
	unsigned short rand14()
	{
		int si, sj;
		ii = (ii + 1) & MASK;
		si = s[ii];
		jj = (jj + si) & MASK;
		sj = s[jj];
		s[ii] = sj;
		s[jj] = si;
		return s[(s[si] + s[sj]) & MASK];
	}

	unsigned long rand28()
	{
		return ((rand14() << BOX_SIZE) ^ rand14());
	}

	unsigned long long rand42()
	{
		return (rand14() << BOX_SIZE) ^ rand14();
	}

	// uses 3 outputs of rand14, shifted and XORed
	unsigned long rand32()
	{
		return (rand14() << 18) ^ (rand14() << 9) ^ rand14();
	}

	// use 3 successive outputs of the arcfour box to produce a double
	//	precision number uniformly distributed between low and high
	double rand(double low, double high)
	{
		double z = rand14() * quanta;
		z = (z + rand14()) * quanta;
		z = (z + rand14()) * quanta;

		return low + z * (high - low);
	}

	// returns a random boolean value
	// this is when a simple binary up or down will suffice - allows
	//	32 calls to be made between each rand() call
	bool randBool()
	{
		if (--m_boolAccum < 0)
		{
			m_boolRand = rand14();
			m_boolAccum = 11;
		}
		const int bit = m_boolRand & 0x0001;
		m_boolRand >>= 1;
		return (bit == 0);
	}

	///////////////////////////////////////////////////////////////////
	// Initialization and seeding
	//
	//  bootstrap()   initializes system to its starting point
	//  seedRand(S,L) seed the system with a string S of length L

	// initialize to starting position
	void bootstrap()
	{
		lcg_a = CROW_A[0];
		lcg_m = CROW_M[0];

		// initialize SBoxes
		// the arcfour system calls for initializing the s-box using
		//	s[i] = i
		// this seems a bit artificial and prone to output bias. use 
		//	the output of an LCG to artifically scramble the initial
		//	state, as this will produce a better pseudo-random stream
		// start with 0 and 1 in the first two positions.  then pick
		//	a random location within the initialized sections of
		//	the s-box, insert the new value there and append the 
		//	previous value at the end of the s-box
		ii = 0;
		jj = 0;
		s[0] = 0;
		s[1] = 1;
		for (int i=2; i<=MASK; i++)
		{
			int x = lcg();
			int pos = x % i;
			int val = s[pos];
			s[pos] = i;
			s[i] = val;
		}

		m_normFlag = false;
		m_normSpare = 0.0;
		m_boolRand = 0;
		m_boolAccum = 0;
	}

	// void seedRand(const char * data)
	//		data	a string that is used to seed the generator
	// this provides a simple wrapper for append and finalize calls
	//	for use in simple random number generation
	void seedRand(const char * data)
	{
		append(data, strlen(data));
		finalize();
	}

	// use the system clock to generate a unique random number stream
	// uses two different time streams to generate "uniqueness"
	void seedByClock()
	{
		// base one stream on system time
		time_t tt = time(NULL);
		//printf("ctime: %s\n", ctime(&tt));
		const char * tstr = ctime(&tt);
		int len = strlen(tstr);
		append(tstr, len);

		// base another on high-res CPU time 
		// if CLOCK_MONOTONIC doesn't work, try CLOCK_REALTIME
		// that makes the above ctime call redundant, but the 
		//	realtime clock is supposed to be universal
		// NOTE: need to link with realtime library for this
		//	(i.e. link flag -lrt)
		timespec ts;
		clock_gettime(CLOCK_MONOTONIC, &ts);
		char * cts = (char *) &ts;
		append(cts, sizeof(ts));
		
		finalize();
	}
	
	// void append(const char * data, const int len)
	//		data	the input stream
	//		len		the number of bytes in the stream
	//	
	// this applies a stream of data to increase the entropy of the 
	//	system.  'finalize(...)' needs to be called after the last call 
	//	to 'append(...)' to make the stream ready to use
	void append(const char * data, const int len)
	{
		if (data == NULL)
			return;

		int si;
		// apply stream to LCG
		for (int i=0; i<len; i++)
		{
			int c = (unsigned char) data[i];
			lcg_x = (lcg_a * (lcg_x + c)) % lcg_m;
			if (lcg_x == 0)
				lcg_x = rand14();
		}

		// apply stream to s-box
		for (int i=0; i<len; i++)
		{
			int c = (unsigned char) data[i];
			
			ii = (ii + 1) & MASK;
			si = s[ii];
			jj = (jj + si + c) & MASK;
			s[ii] = s[jj];
			s[jj] = si;
		}
	}
	void append(const unsigned char * data, const int len)
			{ append((const char*) data, len);		}


	// void finalize()
	//
	// after data from the seed/key/hash document has been provided 
	//	to the system, the system needs to be mixed up to reflect
	//	these changes.  that is done here.  finalize only needs to
	//	be called if 'append(...)' is called explicitly.
	void finalize()
	{
		// apply LCG output to s-box - this helps to magnify the
		//	difference from a one bit change in the seed value
		int si, sj, val;
		for (int i=0; i<MASK; i++)
		{
			val = lcg();
			si = s[i];
			jj = (jj + si + val) & MASK;
			s[i] = s[jj];
			s[jj] = si;
		}

		// advance s-box state
		// iterate through entire s-box 4 times.  this is recommended
		//	for cryptographic applications but is probably not needed
		//	here.  but, it's a one time operation and doesn't hurt
		for (int i=0; i<4*MASK; i++)
		{
			int pos = i % MASK;
			si = s[pos];
			jj = (jj + si) & MASK;
			sj = s[jj];
			s[pos] = sj;
			s[jj] = si;
		}

		// select a new key dependent set of LCG constants 
		int pos = rand14() & 255;
		lcg_a = CROW_A[pos];
		lcg_m = CROW_M[pos];
	}

protected:
	unsigned long lcg() { return ( lcg_x = (lcg_a * lcg_x) % lcg_m); }

	void seedInit(const unsigned char * init, int len)
	{
		bootstrap();

		// scramble (i.e. set to initial state for supplied key)
		append(init, len);

		// for certain applications, such as hashing or encryption, 
		//	it's not necessary to scramble the streams here, as this 
		//	will be done later.  for random numbers, however, a 
		//	finalization is necessary.  it doesn't hurt anything 
		//	to do this now and it simplifies the API to do so, so
		//	go ahead and mix things up.  
		finalize();
	}

	void seedInit(const char * init)
	{
		if (init != NULL)
		{
			int len = strlen(init);
			seedInit((const unsigned char*) init, len);
		}
	}

	////////////////////////////////////////////////////////////
	// data members

	unsigned long	lcg_x, lcg_a, lcg_m;

	bool	m_normFlag;
	double	m_normSpare;

	unsigned long m_boolRand;
	int m_boolAccum;

	// arcfour box
	long ii;	// 'i' from arcfour
	long jj; 	// 'j' from arcfour
	unsigned short s[MASK+1];

	///////////////////////////////////////////////////////////////////
	// debugging and display code
public: 
	static void print(int val)
	{
		for (int i=31; i>=0; i--)
		{
			printf("%s ", (val&(1<<i))==0?"0":"1");
			if ((i%4)==0)
				printf("  ");
		}
		printf("\n");
	}
};

#endif		// CROW_H

