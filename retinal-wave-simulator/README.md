# Retinal Wave Simulator

**Source:** https://github.com/BennyCa/Retinal-Wave-Simulator  
**Author:** Benjamin Cappell (2023), based on the model by Keith Godfrey (Godfrey & Swindale)

---

Retinal wave simulation code used for the retinal wave model created
by Keith Godfrey and that is referenced in the paper by Godfrey
and Swindale.

This code has been developed and tested under linux (Redhat, Debian 
and Unbuntu flavors). While the C++ should be POSIX compliant, it will
need updating to run under Windows (it may work under Mac as-is). The 
Java code should work universally (that's the point of Java, isn't it?), 
but this hasn't been tested.

Sparse documentation can be found throughout the code. 

This code has been updated by Benjamin Cappell in 2023.
The following changes were made:
- slightly changed .dat storage format to support bigger retinas
- improve/implement batch processing of both c++ and Java code
- Java visualization part has been changed to a "renderer" of png images.
- some documentation has been updated.
- The Java code has been tested under Windows.

## RUNNING THE PROGRAM
To build the executible, type
	
	make all

### To RUN THE SIMULATION, type 
	
	./gen [start_num] [relative_start] [relative_end_exclusive]

where all args are integers and relative_end_exclusive>relative_start.

The produced data files will be 
'amacrine_<start_num+relative_start>.dat' - 
'amacrine_<start_num+relative_end_exclusive-1>.dat'.

#### EXAMPLE
./gen 1000 100 200 will produce amacrine files from 1100 to 1199.

This can be useful for BATCH processing large datasets on multiple
threads, e.g. on Linux:

	cd .. & ./gen 0 0 62 & ./gen 0 62 124 & ...  & ./gen 0 1302 1364 &
will produce 1364 amacrine_<num>.dat files (0>=num>1364).

this is not perfect, as some batches might finish faster -
future improvement would be directly implementing parallelism.



### To GENERATE IMAGES FROM THE SIMULATION, type
	
	./view [start_num_ama] [threshold] [count_ama] [num_imgs] [spacing] [output_all]

where all args are integers and
 - count_ama > 0
 - threshold >= 0
 - num_imgs > 0
 - spacing > 0
 - output_all 0 or 1

start_num_ama: the amacrine start file to be parsed to images.
threshold: how many pixels should be active per img. if 0, all images.
count_ama: total number of amacrine files to parse.
num_img: how many images per amacrine file should be created.
spacing: temporal spacing of images per amacrine file. 1=no skip.
output_all: if 1, output also raw images (without augmentations).

#### EXAMPLE & Batch processing e.g. on Linux:
	cd .. & ./view 706 1000 3 2000 1 1 & ./view 709 1000 3 2000 1 1 &
will produce 6 folders, 706..711, each containing a "parsed" folder
with 2000 augmented images and a "raw" folder with all
the raw simulation frames visited in the parsed image creation process.
No frames will be skipped (spacing=1), all images in the parsed folder 
have at least 1000 active pixels. If after one iteration through the 
raw data less than 2000 images were created, threshold is reduced and 
another iteration is done (until 2000 images are created in total).

### To analyze the program, type in one of the following

	./stat [num]
	./xview [num]

'stat' will print a list of wave statistics to the console, while
'xview' will produce a window with the same statistics, the simulation
parameters, and a plot of several wave characteristics (IWI histogram,
size histogram, retinal coverage plot, and wave init points plot).

### CUSTOMIZED SIMULATIONS
The simulation parameters are stored in the file 'retina.h'. There is a
list of parameters there used to produce waves similar to those seen
in several species (ferret, rabbit, mouse, chick and turtle). These can
be modified to produce waves with different spatio-temporal 
characteristics.

The model will also produce waves with variable duration depolarizations.
Technically, this should be the 'core' model, as this better describes
amacrine cell activity, but in order to keep the model core as simple
as possible, amacrine cells were given fixed depolarization durations, 
and this was provided as an alternate form. Change the definition of 
SIMTYPE to "2" to convert the model to allow amacrine cells to undergo
short duration depolarizations when in isolation, and to remain 
depolarized for as long as they have sufficient input to do so.

