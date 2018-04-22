Group Members: Mariam Sulakian, Alexa Alcalay, Emily Mi<br />
Group Number: 165
# Server Smashing: Hack into a Server
[Project Spec](https://lpc.cs.duke.edu/compsci310/projects/p2.php)<br />
[Remote Server](http://310test.cs.duke.edu:9478)

## OBJECTIVE: 
This project will helps to understand call-stack vulnerabilities and debuggers like the GNU Debugger (GDB).

The objective of this project is to compromise a Ubuntu Linux machine running a vulnerable process. We will use a shellcode to compromise the server, which is described in the links below. Once we have compromised our server, we modify the files hosted by the server as proof. As we start trying to compromise our server, it will seg fault.

We will use our shellcode to remotely send commands to the server we take over. The most straightforward way to do this is to choose a shellcode that opens a random port on the server over which it can accept shell commands. 

There are five stages to completing the project

1. Find the vulnerability in webserver.c.

2. Use gdb to identify where the relevant variables are stored in memory.

3. Construct a request string that will exploit the vulnerability by overwriting variables stored on the stack.

4. Pwn a local webserver process.

5. Pwn a remote webserver process and send it shell commands to create a new file or modify index.html. This may take many attempts (but not prohibitively many) since you cannot be exactly sure where the remote server's stack is located.

## VULNERABILITY:
We first found the vulnerability in the code. This occurred at the check in line 107 in the method 'handle' in the Webserver.c file. 
This function calls the helper function check_filename_length(byte len) in line 150 of the original code to check if len's lower 8 bits are less than 100. 
The issue here is that the function takes in the string's length as a byte. A byte is a 8-bit signed two's compliment integer whereas an int is a 32-bit signed two's compliment integer. 
What this means is that, because the function takes in the length as a byte, it looks at the last 8 bits of the length of the given string. 
So, the check in line 107 will only pass if the last 8-bits are less than 100. Otherwise, if a larger string is passed, the buffer will overflow.


## METHOD OF ATTACK:

### REMOTE SERVER:
To reiterate the vulnerability section above, a request with an arbitrarily large size could pass the check and copy data into memory. This would overflow the stack frame and cause the handle function's return address to be overwritten. In other words, our main point of attack is to overflow the buffer.

Things to consider:
- The server can take up to 1024 bits of input.
- The check function checks with bytes not int therefore the buffer looks at the last 8 bits of the given input.
- We can pass in any message of size 1024 or less as long as the lowest 8 bits are less than 100, i.e. length of input MOD 256 < 100. 

Using the guidelines above, we calculated the max length of our input string to be 867. We first used the return address of 0xbfffffff (address of top of stack on a 32 bit server). Using the length of this return address (we repeated the return address 40 times) and the length of our shellcode, we calculated the number of no-ops that would give us a string length as close to 867 as possible. The number of no-ops we came up with is 624.

Starting at the top of the stack at address 0xbfffffff, we then decremented the return address until we found one that worked. We knew we could decrement the address by the size of our no-ops each time. So, working from the top of the stack, we decremented the return address by 512 at a time. We only had to do this once.

In this assignment, we are telling the server to open up a shell on a new port. Our shellcode, adopted from [shell-storm.org](http://shell-storm.org/shellcode/files/shellcode-836.php), is binded to port 1978. We then access the port and overflow the buffer. The return address is what we've passed in (the attack string) instead of the original string. The return address we provide redirects the server into a no-op that we created. A longer block of no-ops increases the probability of hitting the target size. 

We ran the following code in terminal to pipe our shell code to the remote server on our group port.
```
python shellcode.py | nc 310test.cs.duke.edu 9478
```
We then opened a new terminal window and ran 
```
nc310test.cs.duke.edu 9478
```
We then used cd and ls to go into the directory and find the index.html document we needed to edit. We used the 'cat' command to edit the file, and as a result, complete the project.
```
cat > index.html
```

### LOCAL SERVER:
For the local server, we used gdb to find correct address space. We used this return address in shellcode.py. The rest of the steps were similar to those we performed on the remote server.

To compile webserver.c:
```
gcc -m32 -execstack -fno-stack-protector webserver.c -o webserver
```

Then:
```
./webserver 5000
```

In a new terminal window, we ran the following code to pipe our shell code to the remote server on our group port (port 9478).
```
python shellcode.py | nc localhost 5000
```

Our shellcode.py file creates the attack string that we pass into webserver.c. This string contains the new return address, no-op lines, and the shellcode. Note that the length of the string MOD 256 must be less than 100.

## REVIEW OF PROJECT:
We thought this was a fun project to help familiarize us with servers, GDB, and networks. The code write up was a bit vague and it took a while to understand the different parts of the project and how to complete them.
The shell portion was especially confusing. It would be useful to get more instruction on how a shell works before this project and how a port binds to the shell.
For future classes, this would probably be the key aspect to improve not only for completing the project, but understanding the concepts more generally. 
Overall, the project was more interesting than the previous ones and a fun way to practice our hacking skills!