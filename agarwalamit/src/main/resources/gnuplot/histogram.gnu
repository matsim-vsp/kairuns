#!/usr/bin/gnuplot

reset

print "script name        : ", ARG0
print "input file     : ", ARG1
print "output file     : ", ARG2
print "output file     : ", ARG3

set term postscript eps size 6in,6in enhanced color font "Helvetica,18"
set output ARG2
#set output '|ps2pdf -dEPSCrop - output.pdf' 

set style fill solid
set xlabel ""
set ylabel "size" font "Helvetica,22"

set key font "Helvetica,22"

unset xtics

plot ARG1 using 1: xtic (2) with histogram title ARG3