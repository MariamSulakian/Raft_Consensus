#include <stdio.h>  // needed for size_t
#include <unistd.h> // needed for sbrk
#include <assert.h> // needed for asserts
#include "dmm.h"

/* You can improve the below metadata structure using the concepts from Bryant
 * and OHallaron book (chapter 9).
 */

typedef struct metadata {
  /* size_t is the return type of the sizeof operator. Since the size of an
   * object depends on the architecture and its implementation, size_t is used
   * to represent the maximum size of any object in the particular
   * implementation. size contains the size of the data object or the number of
   * free bytes
   */
  size_t size;
  struct metadata* next;
  struct metadata* prev; 
} metadata_t;

/* freelist maintains all the blocks which are not in use; freelist is kept
 * sorted to improve coalescing efficiency 
 */

static metadata_t* freelist = NULL;

void* dmalloc(size_t numbytes) {
  /* malloc requests storage from OS as needed */
  
  /* initialize through sbrk call first time */
  if(freelist == NULL) { 			
    if(!dmalloc_init())
      return NULL;
  }

  assert(numbytes > 0);

  /* your code here */
  metadata_t *allocated, *prevOriginal, *nextOriginal;
  metadata_t *current = freelist;
  
  // find big enough block in freelist
  // first fit algorithm
LOOP1:
	;
  	// METADATA_T_ALIGNED is header
  	size_t target_size = ALIGN(numbytes);
  	size_t target_size_total = (target_size + METADATA_T_ALIGNED);
  	
  	/* check if we have sufficient space to allocate necessary blocks
  	 size should be less than # of needed blocks + # header bytes */
  	if(current->size > target_size_total) {
		
		// initialize allocated block
		// takes current block and allocates # of needed blocks to it
		size_t allocated_block_size = current->size;
		allocated = current;
		allocated->size = target_size; // this block will be returned to caller

		// save original previous and next nodes so we can relink
		prevOriginal = current->prev;
		nextOriginal = current->next;
		
		void* freeblock = current;
		freeblock += target_size_total;
		current = (metadata_t *) freeblock;
		
		/* SPLIT: new current block is portion of block left over after part of it
		has been allocated. This portion remains in freelist.
		new size is allocated size - # needed blocks - header */
		current->size = allocated_block_size - target_size_total;
		current->next = nextOriginal;
		
		
		// now we must fix linked list so that updates are reflected
		// in the freelist
		
		// case 1: current is at head
		// current becomes head of freelist
		if(prevOriginal == NULL){
			freelist = current;
			if (nextOriginal != NULL) freelist->next = nextOriginal;
			else freelist->next = NULL;
			freelist->prev = NULL;
			
			if (freelist->next != NULL){
				freelist->next->prev = freelist;
				//nextOriginal->prev = freelist;
			}
		}
		
		// case 2
		// make original prev and next point to new current
		else {
			prevOriginal->next = current;
			// current at end
			if (nextOriginal == NULL) {
				current->prev = prevOriginal; 
				current->next = NULL;
				prevOriginal->next = current;
				//freelist = current->next;
				//current->prev=freelist;
			}
			// current in middle
			else {
				current->next = nextOriginal;
				nextOriginal->prev = current;
				current->prev = prevOriginal;
			}

		}
		// current->next=NULL;
  		// returns pointer to allocated block
  		return ((char*) allocated + METADATA_T_ALIGNED);
  	}
  	// if equal, remove current block from freelist
  	if (current->size == target_size_total) {
  		allocated = current;
  		allocated->size = target_size;
  		
  		if (current->prev == NULL) {
  			freelist=current->next;
  			//freelist->prev = NULL;
  		}
  		else {
  			if (current->next == NULL) current->prev->next = NULL;
  			else {
  				current->prev->next = current->next;
  				current->next->prev = current->prev;
  			}
  		}
  		return ((char*) allocated + METADATA_T_ALIGNED);
  	}
  	current = current->next;
  if (current!=NULL) goto LOOP1;
  return NULL;
}
  void dfree(void* ptr) {
  /* your code here */
  /* free does nothing to the pointer. it only deallocates the memory that
  ptr pointed to, telling the OS that it is available for reuse. */

  metadata_t *current, *toBeFreed, *loop;
  
  // current initialized to start of freelist
  current = freelist;
  
  // the ptr includes the header currently
  // toBeFreed moves address of ptr to correct spot
  toBeFreed = ptr - METADATA_T_ALIGNED;
  
  // we want to insert the block back into freelist
  // need to loop through freelist and find position of toBeFreed
  
  /*
  printf("%p\n",toBeFreed);
  printf("%p\n", current);
  printf("%p\n", ptr);
  */
  /*
  if(current==NULL) {
  	freelist=current;
  	goto COALESCE;
  }
  */
  // check if toBeFreed is equal to freelist (head)
  if (toBeFreed < freelist) {
	freelist->prev = toBeFreed;
	toBeFreed->prev = NULL;
	toBeFreed->next = freelist;
	freelist = toBeFreed;
	goto COALESCE;
  }

LOOP2:	
	;
	bool currGreater = current > toBeFreed;
  	
  	// toBeFreed in middle of freelist
  	 if((current->prev < toBeFreed) && currGreater){
  		toBeFreed->prev = current->prev;
  		current->prev->next = toBeFreed;
  		toBeFreed->next = current;
  		current->prev = toBeFreed;
  		goto COALESCE;
  	}
  	
  	// if toBeFreed at end of freelist
  	else if ((current->next == NULL) && !currGreater){
  		toBeFreed->prev = current;
  		toBeFreed->next = NULL;
  		current->next = toBeFreed;
  		goto COALESCE;
  	}
  current = current->next;
  if (current != NULL) goto LOOP2;
  
  
COALESCE:
  /* coalescing conjoins adjacent free blocks into
  one larger block to allow for allocation of larger
  block sizes. Prevents heap fragmentation.*/
  
	// loop initialized to updated freelist
	loop = freelist;
	//printf("trying coalesce");
while(loop!=NULL){
	//print_freelist();
	size_t loopAndHeaderSize = loop->size + METADATA_T_ALIGNED;
	if(((void *) loop + loopAndHeaderSize) == loop->next){
		// new block is original block plus next block
		loop->size = loop->next->size + loopAndHeaderSize;
		// new conjoined block points to 2 blocks after now
		loop->next = loop->next->next;
		if(loop->next != NULL) loop->next->prev = loop;
		//loop = freelist;
	} else loop = loop->next;
	//print_freelist()
	}
return;
}

bool dmalloc_init() {

  /* Two choices: 
   * 1. Append prologue and epilogue blocks to the start and the
   * end of the freelist 
   *
   * 2. Initialize freelist pointers to NULL
   *
   * Note: We provide the code for 2. Using 1 will help you to tackle the 
   * corner cases succinctly.
   */

  size_t max_bytes = ALIGN(MAX_HEAP_SIZE);
  /* returns heap_region, which is initialized to freelist */
  freelist = (metadata_t*) sbrk(max_bytes); 
  /* Q: Why casting is used? i.e., why (void*)-1? */
  if (freelist == (void *)-1){
    perror("sbrk failed:");
    return false;
    }
  freelist->next = NULL;
  freelist->prev = NULL;
  freelist->size = max_bytes-METADATA_T_ALIGNED;
  return true;
}

/* for debugging; can be turned off through -NDEBUG flag*/
void print_freelist() {
  metadata_t *freelist_head = freelist;
  while(freelist_head != NULL) {
    printf("\tFreelist Size:%zd, Head:%p, Prev:%p, Next:%p\t",
	  freelist_head->size,
	  freelist_head,
	  freelist_head->prev,
	  freelist_head->next);
    freelist_head = freelist_head->next;
  }
  printf("\n");
} 
/*
int main (int argc, char *argv[]) {
	void* ptr = dmalloc(1000);
	void* ptr1 = dmalloc(300);
	void* ptr2 = dmalloc(300);
	void* ptr3 = dmalloc(16);
	printf("malloc done");
	dfree(ptr);
	dfree(ptr3);
	dfree(ptr2);
	printf("here");
}
	*/
	 
