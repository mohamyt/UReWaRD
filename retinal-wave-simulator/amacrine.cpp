/**********************************************************************
    Amacrine.cpp - code to simulate amacrine cells 
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
#include "retina.h"
#include "amacrine.h"

static double s_maxInput = 0;

Amacrine::Amacrine(int px, int py)
{
	m_nbrInput = 0;
	init();

	// hex grid coordinates of cell
	x = px;
	y = py;

	// calculate physical position of cell
	m_posY = 34.0 * py * (sqrt(3.0)/2.0);
	m_posX = 34.0 * px + py * 17.0;
	m_centerDist = sqrt(m_posY*m_posY + m_posX*m_posX);

	char buf[64];
	snprintf(buf, 63, "ama (%d,%d) nbr list", px, py);
	buf[63] = 0;
	m_nbrList = new Arraylist<Amacrine*>(36, buf);
	snprintf(buf, 63, "ama (%d,%d) str list", px, py);
	buf[63] = 0;
	m_strList = new Arraylist<double>(60, buf);
}

Amacrine::~Amacrine()
{
	delete m_nbrList;
	delete m_strList;
}

void Amacrine::addNeighbor(Amacrine * nbr)
{
	// calculate relative excitatory strength of coupling
	// calc overlap of dend arbors. 
	double dx = nbr->xCoord() - m_posX;
	double dy = nbr->yCoord() - m_posY;
	double dist = sqrt(dx*dx + dy*dy);
	const double R = AMA_DEND_RADIUS;
	const double H = R - dist/2;
	if (H <= 0)
		return;

	// formula for determining area of circular segment from 
	// http://mathworld.wolfram.com/CircularSegment.html
	// A = R2 acos ((R-H)/R) - (R-H) sqrt (2RH - H2)
	// R is radius, H is length (height) of circular segment as
	// 	measured along line passing through center
	double A = R*R * acos((R-H)/R) - (R-H) * sqrt(2*H*R - H*H); 
	// total overlap is twice the circular segment 
	A *= 2;

	// have input equal to normalized overlap area (full dend overlap
	// 	has excitatory strength of 1.0)
	double str = A / (M_PI * R * R);
//printf("%d,%d -> %d,%d  \tdist:%.3f,%.3f = %.3f \t\toverlap=%.3f\n", x, y, nbr->posX(), nbr->posY(), dx, dy, dist, str);

	m_nbrInput += str;
	if (m_nbrInput > s_maxInput)
		s_maxInput = m_nbrInput;
	m_strList->add(str);
	m_nbrList->add(nbr);
}

// initialize cell values 
void Amacrine::init()
{
	m_state = AMA_STATE_OFF;
	m_exc = 0;
	m_thresh = g_rng->rand(0.5, 5.0);
	
	// test initial conditions from the extreme case
	// set all threshold values equal, with exception of 1 or 2
	// 	to break symmetry
//	m_thresh = 5.0;
//	if ((x == 6) && (y == 13))
//	{
//		m_thresh = 10.0;
//		printf("%d,%d\tSetting thresh to %f\n", x, y, m_thresh);
//	}
//	if ((x == -12) && (y == -4))
//	{
//		m_thresh = 16.0;
//		printf("%d,%d\tSetting thresh to %f\n", x, y, m_thresh);
//	}

	//////////////////////////////////////////////
	// chaos test (butterfly effect)
	// pick random X and Y, adjust threshold by fixed amount,
	// 	see what happens
//	if ((x == 10) && (y == 10))
//	if ((x == 5) && (y == -15))
//	if ((x == 6) && (y == 13))
//	if ((x == -12) && (y == -4))
//	if ((x == -15) && (y == 1))
//	if ((x == 0) && (y == -13))
//	if ((x == 1) && (y == 15))
//	if ((x == 9) && (y == -5))
//	if ((x == -8) && (y == 3))
//	if ((x == -6) && (y == -5))
//		m_thresh += 0.1;

	m_dThresh = 0;
	m_achInput = 0;
	m_timer = -2 * WARMUP_PERIOD;

	m_accumInput = 0;
	m_threshDecay = (P_H1 / P_P) * m_nbrInput / s_maxInput;
#if !defined(DETERMINISTIC)
	m_threshDecay *= g_rng->norm(1.0, 0.2);
#endif	// DETERMINISTIC
}

// share excitation with neighbors if depolarized
// normally, this function would be implemented as polling all 
// 	neighboring cells to see what input they offer. an equivalent
// 	and much faster way to do this is to 'push' the data from
// 	a depolarized cell onto its neighbors - that way computational
// 	cycles aren't spent polling cells that are quite 95% of the time
void Amacrine::calcInput()
{
	if (m_state > AMA_STATE_OFF)
	{
		// send excitation to all neighbors
		for (int i=0; i<m_nbrList->size(); i++)
		{
			Amacrine *nbr = m_nbrList->get(i);
			double str = m_strList->get(i);
			nbr->acceptInput(str);
		}
	}
}

// update internal state of cell
void Amacrine::updateState()
{
#if (SIMTYPE == 2)
	double input = m_achInput;
	// move excitation towards input
	m_exc += (input - m_exc) * DELTA_T / P_TK;
	if (m_exc < 0)
		m_exc = 0;

	if (m_state == AMA_STATE_PULSE)
	{
		// cell is in fixed duration depolarization period
		if (NOW > m_timer)
		{
			if (m_exc >= m_thresh)
				m_state = AMA_STATE_BURST;
			else
				m_state = AMA_STATE_OFF;
			shell->logEvent(m_state, x, y);
			m_timer = NOW + 3.0;	
#if !defined(DETERMINISTIC)
			m_threshDecay = (P_H1 / P_P) * (m_nbrInput/s_maxInput);
			m_threshDecay *= g_rng->norm(1.0, 0.2);
#endif	// DETERMINISTIC
		}
	}
	else if (m_state == AMA_STATE_BURST)
	{
		// cell has sufficient input to be depolarized after
		// 	fixed duration has passed ("burst" here is misnomer)
		if (m_exc < m_thresh)
		{
			m_state = AMA_STATE_OFF;
			shell->logEvent(m_state, x, y);
		}
		else if (NOW > m_timer)
		{
			m_state = AMA_STATE_PULSE;
			shell->logEvent(m_state, x, y);
			m_timer = NOW + P_D;
		}
	}
	else	// AMA_STATE_OFF
	{
		// cell is/was below threshold
		
		if (m_exc >= m_thresh)
		{
			// threshold crossed
			if (m_timer < NOW)
			{
				// we're OK for a pulse
				m_state = AMA_STATE_PULSE;
				m_timer = NOW + P_D;
			}
			else
			{
				// cell depolarized and active, but too near
				// 	in to the last pulse instance - just go
				// 	into burst mode
				m_state = AMA_STATE_BURST;
			}
			shell->logEvent(m_state, x, y);
		}
	}

	///////////////////
	// adjust threshold
	double dtr = -m_threshDecay;
	// adjust for timestep
	dtr *= DELTA_T;

	// activity dependent change
	double cache = 0;
	if (m_state == AMA_STATE_PULSE)
			cache += (P_H1 + m_achInput * P_H2) / P_D;
	else if (m_state == AMA_STATE_BURST)
			cache += (m_achInput * P_H2) / P_D;
	m_dThresh += cache * DELTA_T;

	// contribution from AHP onset
	if (m_dThresh > 0)
	{
		// slowly feed pending threshold change to threshold
		if ((DELTA_T * P_H4 / 1.0) > m_dThresh)
		{
			dtr += m_dThresh;
			m_dThresh = 0;
		}
		else
		{
			double dh = DELTA_T * P_H4 / 1.0;
			dtr += dh;
			m_dThresh -= dh;
		}
	}
	
	// finally, apply changes
	m_thresh += dtr;
	if (m_thresh < 0)
		m_thresh = 0;

#else	
///////////////////////////////////////////////////////////////////////
//	base model

	if (m_state > AMA_STATE_OFF)
	{
		// cell depolarized
		if (NOW > m_timer)
		{
			// fixed duration depolarization complete - 
			// 	reset state and become quiet (and notify shell of change)
			m_state = AMA_STATE_OFF;
			shell->logEvent(m_state, x, y);
			// used to enforce a 3 second quiet period to allow
			// wave to pass and prevent subsequent bursts
			// - it's just as effective to zero out excitation
			m_exc = 0;
#if !defined(DETERMINISTIC)
			// if not in deterministic mode, recalculate new value for P
			m_threshDecay = (P_H1 / P_P) * (m_nbrInput/s_maxInput);
			m_threshDecay *= g_rng->norm(1.0, 0.2);
#endif	// DETERMINISTIC
		}
	}
	else //if (NOW > m_timer)
	{
		// soma is hyperpolarized - sum input to see if going to
		// 	depolarize
		m_exc += (m_achInput - m_exc) * DELTA_T / P_TK;
		if (m_exc < 0)
			m_exc = 0;
		if (m_exc >= m_thresh)
		{
			// time to depolarize - change state and inform shell of
			// 	change
			m_state = AMA_STATE_PULSE;
			shell->logEvent(m_state, x, y);
			m_timer = NOW + P_D;
		}
	}
	
	// apply threshold change
	double dtr = -m_threshDecay;
	if (m_state > AMA_STATE_OFF)
			dtr += (P_H1 + m_achInput * P_H2) / P_D;
	m_thresh += dtr * DELTA_T;
	if (m_thresh < 0)
		m_thresh = 0;
#endif		// SIMTYPE
	m_achInput = 0;
}

void Amacrine::acceptInput(double ach)
{
	m_achInput += ach;
}

