/**********************************************************************
    Arrays - 1D and 2D array class (w/ bounds checking), a class for 
		Queues, and a cheap clone of the java ArrayList
    Copyright 2006-7 Keith Godfrey

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
#if !defined(ARRAYS_H)
#define ARRAYS_H

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#define BOUNDS_CHECK	(1)

#define MAX_LABEL_LEN	(32)

#if defined(MAX_ARRAYS)
// if we've created more than X arrays, odds are that something is wrong
#define memCheck() { if (Arraylist<T>::newInstance(false) > MAX_ARRAYS) { printf("Error - exceeded maximum array count (%d) for %s\n", MAX_ARRAYS, label); exit(1); } }
#else
#define memCheck()		{ }
#endif	// MAX_ARRAYS

#if defined(MAX_ARRAY_SIZE)
// if we've created more than X arrays, odds are that something is wrong
#define sizeCheck() { if (m_length > MAX_ARRAY_SIZE) { printf("Error - exceeded maximum array size in %s\n", m_label); exit(1); } }
#else
#define sizeCheck()		{ }
#endif	// MAX_ARRAYS

#if defined(BOUNDS_CHECK)
#include <assert.h>
#endif	// BOUNDS_CHECK

template <class T> class Arraylist 
{
//protected:
public:
	int		m_length;
	int		m_totLength;
	T *		m_data;
	char	m_label[MAX_LABEL_LEN];

public:
	Arraylist(char* label)
	{
		m_length = 0;
		m_totLength = 32;
		m_data = (T *) malloc(m_totLength * sizeof(T));
		memset(m_data, 0, m_totLength * sizeof(T));
		snprintf(m_label, MAX_LABEL_LEN-1, label);
		m_label[MAX_LABEL_LEN-1] = 0;
		newInstance(true);
		memCheck();
	}

	Arraylist(int len, char* label)
	{
		if (len < 4)
			len = 4;
		m_length = 0;
		m_totLength = len;
		m_data = (T *) malloc(len * sizeof(T));
		snprintf(m_label, MAX_LABEL_LEN-1, label);
		m_label[MAX_LABEL_LEN-1] = 0;
		newInstance(true);
		memCheck();
	}

	~Arraylist()
	{
		free(m_data);
	}

	static int newInstance(bool created)
	{
		static int s_count;
		if (created)
			s_count++;
		return s_count;
	}

	const char * name()	{ return m_label;	}

//	void deleteContents()
//	{
//		for (int i=0; i<m_length; i++)
//			if (m_data[i])
//				delete m_data[i];
//	}

	// shorten a list without changing the memory allocation
	void truncate(int len)
	{
		if ((len <= m_length) && (len >= 0))
			m_length = len;
	}
	
	void resize(int len)
	{
		if (len < m_totLength)
			truncate(len);
		else
		{
			m_data = (T *) realloc(m_data, len * sizeof(T));
			m_totLength = len;
		}
	}

	// removes specified element.  if not last element in list, 
	//	last element moved to replace this one (i.e. order of list
	//	is not static)
	// returns removed object
	T remove(int pos)
	{
		if ((pos < 0) || (pos >= m_length))
			return 0;

		// if specified position is not at end of list, replace
		//	it with the end of the list.  in either case, decrement
		//	list size
		T val = 0;
		if (pos < (m_length - 1))
		{
			val = m_data[pos];
			m_data[pos] = m_data[m_length-1];	
		}
		else
			val = m_data[m_length-1];
		m_length--;

		return val;
	}

	T removeLast()		
	{ 
		if (m_length > 0)
		{
			return m_data[--m_length];
		}
		return NULL;
	}

	///////////////////////////////////////////////////////
	// stack interface
	T pop()		
	{ 
		if (m_length > 0)
			return m_data[--m_length];
		else
			return NULL;
	}
	void push(T t)		{ add(t);		}

	/////////////////////////////////////////////////////////
	void clear()
	{
		m_length = 0;
	}

	T get(int n)
	{
#if defined(BOUNDS_CHECK)
//		assert((n >= 0) && (n < m_length));
		if ((n < 0) || (n >= m_length))
		{
			fprintf(stderr, "Tried to get #%d in %d element array '%s'\n",
					n, m_length, m_label);
			exit(1);
		}
#endif		// BOUNDS_CHECK
		return m_data[n];
	}

	void set(int n, T t)
	{
#if defined(BOUNDS_CHECK)
		if ((n < 0) || (n >= m_length))
		{
			fprintf(stderr, "Tried to get #%d in %d element array '%s'\n",
					n, m_length, m_label);
			exit(1);
		}
#endif		// BOUNDS_CHECK
		m_data[n] = t;
	}

	void add(T v)
	{
		sizeCheck();

		if (m_length >= m_totLength)
		{
			resize(2*m_totLength);
		}
		m_data[m_length++] = v;
	}

	int size()	{ return m_length;		}

private:
	Arraylist(Arraylist& ar)	{ ; }
};

template <class T> class Queue
{
protected:	
	int		m_length;
	T *		m_data;
	T 		m_defaultVal;
	int		m_pos;
	char	m_label[MAX_LABEL_LEN];

public:
	Queue(int len, char* label, T defaultVal)
	{
		m_length = len;
		m_defaultVal = defaultVal;
		m_pos = 0;
		m_data = (T *) malloc(len * sizeof(T));
		snprintf(m_label, MAX_LABEL_LEN-1, label);
		m_label[MAX_LABEL_LEN-1] = 0;
		clear();
	}

	~Queue()
	{
		free(m_data);
	}

	int length()		{ return m_length;	}

	void clear()
	{
		for (int i=0; i<m_length; i++)
			m_data[i] = m_defaultVal;
	}

	T getNext()
	{
		T val = m_data[m_pos];
		m_data[m_pos++] = m_defaultVal;
		if (m_pos >= m_length)
			m_pos = 0;
		return val;
	}

	void advanceNoClear(int n)
	{
		if (n < 0)
		{
			fprintf(stderr, "Invalid advance position (%d) in Queue '%s'\n", 
					n, m_label);
			exit(1);
		}
		m_pos = (m_pos + n) % m_length;
	}

	T peek()
	{
		return m_data[m_pos];
	}

	T peek(int pos)
	{
		return m_data[(m_pos+pos)%m_length];
	}

	void set(T val)
	{
		m_data[m_pos++] = val;
		if (m_pos >= m_length)
			m_pos = 0;
	}

	void append(T val)
	{
		m_data[m_pos++] += val;
		if (m_pos >= m_length)
			m_pos = 0;
	}

	int position()		{ return m_pos;		}
	void setPosition(int pos)
	{
		if ((pos < 0) && (pos >= m_length))
		{
			fprintf(stderr, "Invalid position set (%d) in Queue '%s'\n", 
					pos, m_label);
			exit(1);
		}
		m_pos = pos;
	}
};

template <class T> class Array1D 
{
protected:
	int		m_length;
	T *		m_data;
	char	m_label[MAX_LABEL_LEN];

public:
	Array1D(int len, char* label, T init)
	{
		m_length = len;
		m_data = (T *) malloc(len * sizeof(T));
		snprintf(m_label, MAX_LABEL_LEN-1, label);
		m_label[MAX_LABEL_LEN-1] = 0;
		clear(init);
	}

	~Array1D()
	{
		free(m_data);
	}

	void clear(T init)
	{
		for (int i=0; i<m_length; i++)
			m_data[i] = init;
	}

	T get(int n)
	{
#if defined(BOUNDS_CHECK)
		if ((n < 0) || (n >= m_length))
		{
			fprintf(stderr, "Out of bound Get (%d) in %d element array '%s'\n",
					n, m_length, m_label);
			exit(1);
		}
#endif		// BOUNDS_CHECK
		return m_data[n];
	}

	void set(int n, T v)
	{
#if defined(BOUNDS_CHECK)
		if ((n < 0) || (n >= m_length))
		{
			fprintf(stderr, "Out of bound Set (%d) in %d element array '%s'\n",
					n, m_length, m_label);
			exit(1);
		}
#endif		// BOUNDS_CHECK
		m_data[n] = v;
	}

private:
	Array1D(Array1D& ar)	{ ; }
};

template <class T> class Array2D 
{
protected:
	int		m_width, m_height;
	T *		m_data;
	char	m_label[MAX_LABEL_LEN];

public:
	Array2D(int w, int h, char* label, T init)
	{
		m_width = w;
		m_height = h;
		m_data = (T *) malloc(w * h * sizeof(T));
		snprintf(m_label, MAX_LABEL_LEN-1, label);
		m_label[MAX_LABEL_LEN-1] = 0;
		clear(init);
	}

	~Array2D()
	{
		free(m_data);
	}

	void clear(T init)
	{
		for (int y=0; y<m_height; y++)
			for (int x=0; x<m_width; x++)
				m_data[y*m_width+x] = init;
	}

	void resize(int w, int h, T init)
	{
		m_data = (T *) realloc(m_data, w * h * sizeof(T));
		m_width = w;
		m_height = h;
		clear(init);
	}


	T get(int x, int y)
	{
#if defined(BOUNDS_CHECK)
		if ((x < 0) || (x >= m_width) || (y < 0) || (y >= m_height))
		{
			fprintf(stderr, "Out of bound Get (%d,%d) in %dx%d array '%s'\n",
				   x, y, m_width, m_height, m_label);
			exit(1);
		}
#endif		// BOUNDS_CHECK
		return m_data[y*m_width + x];
	}

	void set(int x, int y, T v)
	{
#if defined(BOUNDS_CHECK)
		if ((x < 0) || (x >= m_width) || (y < 0) || (y >= m_height))
		{
			fprintf(stderr, "Out of bound Set (%d,%d) in %dx%d array '%s'\n",
				   x, y, m_width, m_height, m_label);
			exit(1);
		}
#endif		// BOUNDS_CHECK
		m_data[y*m_width + x] = v;
	}

private:
	Array2D(Array2D& ar)	{ ; }
};

#undef memCheck
#undef sizeCheck

#endif		// ARRAYS_H
