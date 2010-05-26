#ifndef VM_H
#define VM_H

#include "kondo_lisp.h"

typedef struct vminst_rec{
    word opcode;
    word operand;
} vminst_t;

typedef struct vm_rec {
    word *stack;
    
    // registers
    lispval_t  val;   // value register
    vminst_t  *pc;    // program counter
    word      *fp;    // frame pointer
    word      *sp;    // stack pointer
} vm_t;

#include "vminst.h"

#endif
