

# windows cygwin host config
#
# comment out the lines below. Converting the path on the 
# windows host is necessary when mounting, otherwise the 
# mounted-in directory will not be found:
#
# make: Entering directory '/workspace'
# make: Leaving directory '/workspace'
# make: *** No rule to make target 'useradd-su'.  Stop.
#
# pathcmd := cygpath -a -m
# docker := docker.exe


# on windows, all mounted in dirs need to be specified with C:/../
repomount := $(shell cygpath -a -m $$PWD)

include Makefile
