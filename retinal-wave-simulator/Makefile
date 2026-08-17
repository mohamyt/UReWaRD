
SHELL = /bin/sh

# ------------------ Compilation options ------------------------

# load math and realtime libraries
LIBS = -lm -lrt -ldl

INC = -Iinclude/

# Flags for the C compiler:
#   -Wall for strict gcc warnings (requires prototypes for all functions).
#   -g to produce debug data for gdb
#   -O3 for optimization
#   -pg for gprof
#CFLAGS = -Wall -g $(INC)
#CFLAGS = -Wall -O3 $(INC)
CFLAGS = -O3 $(INC)
#CPPFLAGS = -Wall -g  $(INC) 
#CPPFLAGS = -Wall -O3 $(INC) 
CPPFLAGS = -O3 $(INC) -Wno-unused-parameter

CPP = g++
CC = gcc

HEADERS = retina.h amacrine.h
OBJS = main.o amacrine.o

# ------------------------ Rules --------------------------------

all_c: $(HEADERS) $(OBJS) 
	${CPP} $(OBJS) ${CPPFLAGS} $(LIBS) -o gen

all: $(HEADERS) $(OBJS) java 
	${CPP} $(OBJS) ${CPPFLAGS} $(LIBS) -o gen

refresh: clean all

java:
	cd java_code; javac *.java

#---------------- wave
main.o: main.cpp $(HEADERS)
	${CPP} main.cpp ${CPPFLAGS} -c

amacrine.o: amacrine.cpp $(HEADERS)
	${CPP} amacrine.cpp ${CPPFLAGS} -c

clean: 
	rm -f gen *.o
	rm -f java_code/*.class

