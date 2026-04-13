@echo off
qemu-system-i386 -monitor vc -m 2048 -smp 2 -cdrom ./all/build/cdroms/jnode-x86.iso -boot d