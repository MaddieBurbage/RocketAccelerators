// Tests for the general binary combinations accelerator
// (c) Maddie Burbage, 2020

#include "rocc.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

/* A function to help generate all binary strings of a certain length.
 * The generation is computed using the cool-er pattern from "The Coolest
 * Way to Generate Binary Strings"
 */
int nextGeneralCombination(int n, int last, int *out) {
    unsigned int next, mask, lastTemporary, lastLimit, lastPosition, first, shifted, rotated, result;

    next = last ^ (last + 1);
    mask = (next > 3)? next : ~(0);

    lastTemporary = (mask >> 1) + 1;
    lastLimit = 1 << (n-1);
    lastPosition = (lastTemporary > lastLimit)? lastLimit : lastTemporary;
    first = (next > 3)? 1 & last : 1 & ~(last);
    shifted = (last & mask) >> 1;
    rotated = (first == 1)? shifted | lastPosition : shifted;
    result = rotated | (~mask & last);

    if(result == (1 << n) - 1) {
        return -1;
    }

    *out = result;
    return 1;
}


static inline int testAccelerator(int inputString, int length) {
    int outputString, answer, mismatches;

    mismatches = 0;

    //For each string in the sequence, compare the c output to the accelerator's
    while(nextGeneralCombination(length, inputString, &answer) != -1) {
        ROCC_INSTRUCTION_DSS(0, outputString, length, inputString, 2);
	if(outputString == answer) {
	  printf("Next string: %d, accelerator found %d\n", answer, outputString);
	} else {
	  printf("ERROR: next string: %d, accelerator found %d\n", answer, outputString);
	  mismatches++;
	}
	inputString = answer;
    }
    return mismatches; //Mismatches is 0 for success, otherwise it's positive
}

int main(void) {
    int inputString = 0b0;
    int length = 4;


    int testResult = testAccelerator(inputString, length);
    return testResult;
}