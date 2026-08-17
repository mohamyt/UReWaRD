/**********************************************************************
	Retinal wave simulator code - below is the main() function and
		a Shell class that manages the simulation
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
#include <stdio.h>
#include <arpa/inet.h>

#define MAIN_CPP

#include "retina.h"
#include "amacrine.h"

static void spread();
static void spread2();

static int s_num = 1;
static int local_min = 0;
static int local_max = 10000;
// put together simulation parameter information - send to shell
static void writeSimHeader()
{
	char buf[128];
#if defined(DETERMINISTIC)
	sprintf(buf, "DETERMINISTIC\t1\n");
	shell->appendHeaderInfo(buf);
#endif // DETERMINISTIC
	sprintf(buf, "warmup\t%d\n", WARMUP_PERIOD / 60);
	shell->appendHeaderInfo(buf);
	sprintf(buf, "runtime\t%.2f\n", RUNTIME / 60);
	shell->appendHeaderInfo(buf);
	sprintf(buf, "dT\t%f\n", DELTA_T);
	shell->appendHeaderInfo(buf);
	sprintf(buf, "rad\t%d\n", RADIUS);
	shell->appendHeaderInfo(buf);
	sprintf(buf, "CON_RAD\t%f\n", CON_RAD);
	shell->appendHeaderInfo(buf);
	Amacrine::writeHeader();
}

int main(int argc, char **argv)
{
	// args: start number, relative start, relative end

	// collect (start) data file number if specified
	if (argc >= 2)
	{
		int start = atol(argv[1]);
		if ((start >= 0) && (start < 10000000))
			s_num = start;

		// collect min and max data file to create if specified
		if (argc >= 4)
			{
				int min = atol(argv[2]);
				int max = atol(argv[3]);
				if ((min >= 0) && (min < 10000000) && (max >= 0) && (max < 10000000))
					{
						local_min = min + s_num;
						local_max = max + s_num;
					}
			}
	} 

	g_rng = new Crow("retinal wave generator - 3rd variant");
	new Shell();

	// 'spread' calls are to run several simulation iteratively, to
	// 	check range of parameter values
	// spread();
	spread2();

	// run single simulation
	// printf("[INFO] run single sim.\n");
	// shell->reset();
	// shell->run();
	return 0;

	// pseudo-call these to supress compiler warnings
	if (false)
	{
		spread();
		spread2();
	}
}

void resetConsts()
{
	P_H1 = P_H1_INIT;
	P_H2 = P_H2_INIT;
#if (SIMTYPE > 1)
	P_H4 = P_H4_INIT;
#endif // SIMTYPE > 1
	P_D = P_D_INIT;
	P_TK = P_TK_INIT;
	P_P = P_P_INIT;
}

void singleParam(double *param, double val)
{
	int i;
	double v = *param * 0.4;
	double step = *param * 0.1;
	resetConsts();
	for (i = 0; i < 13; i++, v += step)
	{
		shell->reset();
		*param = v;
		shell->run();
	}
}

void singleParam2(double *param, double start, double step, int n)
{
	double v = start;
	resetConsts();
	for (int i = 0; i < n; i++, v += step)
	{
		shell->reset();
		*param = v;
		shell->run();
	}
}

//n = step count, step = step size, d = param count
void multiParam(double start, double step, int n, int d, double *params[], int *i, int min, int max)
{

	// printf("[INFO] multiParam.\n");
	// printf("start: %f step: %f d: %i, param[0]: %f, param[4]: %f\n", start, step, d, *params[0], *params[4]);
	if (d <= 0)
	{
		if (*i >= min && *i < max)
		{
			
			printf("[PARAMSET s_num=%i]", s_num);
			for (int j = 0; j < 5; j++)
			{
				printf(" %i: %f,", j, *params[j]);
			}
			printf(" i=%i, min=%i, max=%i", *i, min, max);
			printf("\n");
			shell->reset();
			shell->run();
		} else {
			s_num++;
		}
		(*i)++;
		
	}
	else
	{
		double tmp = (*(params[d - 1]));
		double v = (*(params[d - 1])) * start;
		double step_local = (*(params[d - 1])) * step;
		for (int j = 0; j < n; j++)
		{
			*(params[d - 1]) = v;
			multiParam(start, step, n, d - 1, params, i, min, max);
			v += step_local;
		}
		*(params[d - 1]) = tmp;
	}
}
void spread()
{
	singleParam(&P_H1, P_H1_INIT);
	singleParam(&P_H2, P_H2_INIT);
#if (SIMTYPE > 1)
	singleParam(&P_H4, P_H4_INIT);
#endif // SIMTYPE > 1
	singleParam(&P_P, P_P_INIT);
	singleParam(&P_TK, P_TK_INIT);
	singleParam(&P_D, P_D_INIT);
}

void spread2()
{
	resetConsts();
	printf("[INFO] Spread2.\n\n");
	//double *params[] = {&P_H1, &P_H2, &P_P, &P_TK, &P_D};
	double *params[] = {&CON_RAD, &P_H1, &P_H2, &P_P, &P_TK, &P_D};
	int start = s_num;
	//multiParam(.25, .5, 4, 5, params, &start, local_min, local_max);
	multiParam(.25, .5, 4, 6, params, &start, local_min, local_max);
}

///////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////

Shell::Shell()
{
	// open log file
	shell = this;

	m_headerBufLen = 256;
	m_headerStr = (char *)malloc(m_headerBufLen * sizeof(char));

	reset();
	init();
}

Shell::~Shell()
{
}

void Shell::reset()
{
	round = 0;
	NOW = round * DELTA_T;
	lastUpdate = -1;
	m_headerLen = 0;
}

void Shell::init()
{
	amaList = new Arraylist<Amacrine *>(HexGrid<Amacrine *>::size(RADIUS), (char*) "ama list");
	amaCenterList = new Arraylist<Amacrine *>(HexGrid<Amacrine *>::size(RADIUS), (char*) "ama center list");
	amaGrid = new HexGrid<Amacrine *>(RADIUS, (char*) "ama grid");
	boundaryListX = new Arraylist<int>((char*) "boundary list x");
	boundaryListY = new Arraylist<int>((char*) "boundary list Y");

	borderListX = new Arraylist<int>((char*) "border list x");
	borderListY = new Arraylist<int>((char*) "border list Y");

	////////////////////////////
	// initialize amacrine cells
	// create them
	for (int y = -RADIUS; y <= RADIUS; y++)
	{
		for (int x = -RADIUS; x <= RADIUS; x++)
		{
			if (!amaGrid->inGrid(x, y))
				continue;

			// use circular retina
			double px = 34.0 * x + 17.0 * y;
			double py = 34.0 * y * (sqrt(3.0) / 2.0);
			double dist = sqrt(px * px + py * py);
			// calculate nearest edge of hex grid
			double near = ((RADIUS + 0.5) * 34.0) * (sqrt(3.0) / 2.0);
			double border = near - AMA_DEND_RADIUS;

			// identify boundary and border pixels
			if (dist >= near)
			{
				boundaryListX->add(x);
				boundaryListY->add(y);
				continue;
			}

			Amacrine *ama = new Amacrine(x, y);
			amaGrid->set(x, y, ama);
			amaList->add(ama);
			if (dist >= border)
			{
				borderListX->add(x);
				borderListY->add(y);
			}
			else
			{
				amaCenterList->add(ama);
			}
		}
	}

	// connect them together
	// cross connect all amacrine cells within a radius CON_RAD-1 ring
	for (int y = -RADIUS; y <= RADIUS; y++)
	{
		for (int x = -RADIUS; x <= RADIUS; x++)
		{
			if (!amaGrid->inGrid(x, y))
				continue;
			Amacrine *ama = amaGrid->get(x, y);
			if (ama == NULL)
				continue;

			for (int py = (int) -CON_RAD; py <= (int) CON_RAD; py++)
			{
				for (int px = (int) -CON_RAD; px <= (int) CON_RAD; px++)
				{
					// see if neighbor is in radius CON_RAD-1 ring
					if (!HexGrid<Amacrine *>::inGrid(px, py, (int) CON_RAD))
						continue;
					int xx = px + x;
					int yy = py + y;
					// see if neighbor is on grid
					if (!HexGrid<Amacrine *>::inGrid(xx, yy, RADIUS))
						continue;

					Amacrine *nbr = amaGrid->get(xx, yy);
					if (nbr == NULL)
						continue;
					// not neighbor of self
					if (nbr && (nbr != ama))
						ama->addNeighbor(nbr);
				}
			}
		}
	}
}

void Shell::appendHeaderInfo(char *str)
{
	int len = strlen(str);
	int start = m_headerLen;
	m_headerLen += len;
	bool reset = false;
	while (m_headerLen >= m_headerBufLen)
	{
		reset = true;
		m_headerBufLen *= 2;
	}
	if (reset)
		m_headerStr = (char *)realloc(m_headerStr, m_headerBufLen * sizeof(char));
	strncpy(&m_headerStr[start], str, len);
}

void Shell::run()
{
	writeSimHeader();

	// set up global variables (parameters may have been changed through
	// 	command line so need to recompute these)
	round = -WARMUP_PERIOD * 1000;

	for (int i = 0; i < amaList->size(); i++)
	{
		Amacrine *ama = amaList->get(i);
		ama->init();
	}
	////////////////////////////

	char buf[64];
	snprintf(buf, 64, "amacrine_%d.dat", s_num++);
	buf[63] = 0;
	logfile = fopen(buf, "wb");
	if (!logfile)
	{
		fprintf(stderr, "Error opening log file '%s'\n", buf);
		exit(1);
	}
	printf("datafile: %s\n", buf);

	// write header
	buf[0] = 'a';
	buf[1] = 'm';
	buf[2] = 'a';
	fwrite(buf, 3, 1, logfile);
	buf[0] = 3; // version
	fwrite(buf, 1, 1, logfile);

	short headerSize = htons(m_headerLen);
	fwrite(&headerSize, sizeof(short), 1, logfile);
	fwrite(m_headerStr, 1, m_headerLen, logfile);
	printf("%s", m_headerStr);

	// size of hex storage grid
	short rad = htons(RADIUS);
	fwrite(&rad, sizeof(short), 1, logfile);

	// number of amacrine cells
	int cnt = amaCenterList->size();

	// short numCells = htons(cnt);
	// printf("htons out: %d", numCells);
	// fwrite(&numCells, sizeof(short), 1, logfile);

	int numCellsInt = htonl(cnt);
	// printf("htonl out: %d", numCellsInt);
	fwrite(&numCellsInt, 4, 1, logfile);

	for (int i = 0; i < cnt; i++)
	{
		Amacrine *ama = amaCenterList->get(i);
		short px = ama->posX();
		short val = htons(px);
		fwrite(&val, sizeof(short), 1, logfile);
		short py = ama->posY();
		val = htons(py);
		fwrite(&val, sizeof(short), 1, logfile);
		// printf("ama writing %d,%d\n", px, py);
	}

	// list of boundary 'cells'
	cnt = boundaryListX->size();
	short numBoundaries = htons(cnt);
	fwrite(&numBoundaries, sizeof(short), 1, logfile);
	for (int i = 0; i < cnt; i++)
	{
		short px = boundaryListX->get(i);
		short val = htons(px);
		fwrite(&val, sizeof(short), 1, logfile);
		short py = boundaryListY->get(i);
		val = htons(py);
		fwrite(&val, sizeof(short), 1, logfile);
		// printf("border writing %d,%d\n", px, py);
	}

	// list of border region cells
	cnt = borderListX->size();
	short numBorders = htons(cnt);
	fwrite(&numBorders, sizeof(short), 1, logfile);
	for (int i = 0; i < cnt; i++)
	{
		short px = borderListX->get(i);
		short val = htons(px);
		fwrite(&val, sizeof(short), 1, logfile);
		short py = borderListY->get(i);
		val = htons(py);
		fwrite(&val, sizeof(short), 1, logfile);
		// printf("border writing %d,%d\n", px, py);
	}

	// printf("runtime = %.1f minutes\n", RUNTIME/60);
	// printf("warmup = %d minutes\n", WARMUP_PERIOD/60);
	// printf("list size=%d\n", amaList->size());
	//  run simulation
	while (NOW < RUNTIME)
	{
		round += MS_PER_ROUND;
		NOW = round * 0.001;
		// printf("%.3f\n", NOW);

		for (int i = 0; i < amaList->size(); i++)
		{
			Amacrine *ama = amaList->get(i);
			ama->calcInput();
		}

		for (int i = 0; i < amaList->size(); i++)
		{
			Amacrine *ama = amaList->get(i);
			ama->updateState();
		}
	}

	// close log file
	buf[0] = 'e';
	fwrite(buf, 1, 1, logfile);
	fclose(logfile);
}

// 'x' and 'y' are coordinates of amacrine on retina, offset by 32768 to
// 	make sure value is positive
void Shell::logEvent(int state, int x, int y)
{
	if (NOW < 0)
		return;

	char flag;
	if (lastUpdate < NOW)
	{
		lastUpdate = NOW;

		flag = 't';
		fwrite(&flag, 1, 1, logfile);
		// printf("timestamp: %c \t%d\n", flag, round);
		int when = htonl(round);
		fwrite(&when, 4, 1, logfile);
	}

	flag = 'a';
	fwrite(&flag, 1, 1, logfile);
	char st = (char)state;
	fwrite(&st, 1, 1, logfile);
	short px = x + 4096;
	short py = y + 4096;
	// short px = x + 8192;
	// short py = y + 8192;
	// printf("writing: %c \t%d,%d (%d,%d)\n", flag, px, py, x, y);
	px = htons(px);
	py = htons(py);
	fwrite(&px, 2, 1, logfile);
	fwrite(&py, 2, 1, logfile);
}
