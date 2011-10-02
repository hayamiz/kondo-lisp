#ifndef SCHEME_H
#define SCHEME_H


static inline word combine_bytes(byte lower, byte higher){
    return (lower | (higher << 8));
}

static inline word eeprom_readword(word addr){
    word lower, higher;
    lower = EEPROM.read(addr);
    higher = EEPROM.read(addr+1);
    return combine_bytes(lower, higher);
}

static inline word eeprom_writeword(word addr, word value){
    word lower, higher;
    lower = value & 0xFF;
    higher = value >> 8;
    EEPROM.write(addr, lower);
    EEPROM.write(addr+1, higher);
    return value;
}


typedef word celladdr_t;

struct cell_rec;
enum {
    NUM_T,
    SYM_T,
    CEL_T,
};

typedef union {
    struct cell_rec *cell;
    word     num;
    word     sym;
    word    data;
} lispval_t;

typedef struct cell_rec {
    lispval_t cdr;
    lispval_t car;
} cell_t;

typedef struct var_entry_rec {
    lispval_t sym;
    lispval_t val;
} var_entry_t;

lispval_t Vnil;
lispval_t Vt;
cell_t *cellspace;
var_entry_t *vartable;
var_entry_t *cur_var;
word   *stack;
boolean debug_mode;


void init_vars(void);
cell_t *alloc_cell(void);
void free_cell(cell_t *);
lispval_t vm_eval();

lispval_t builtin_fun(word fname, word nargs,
                      lispval_t a1, lispval_t a2, lispval_t a3,
                      lispval_t rest);

void write(lispval_t);
void writeln(lispval_t);

/*
  NIL: 0b0000000000000000
  T  : 0b0010000000000001

  SYMBOL:
     : 0b01xxxxxxxxxxxxx1
            ******       ... bits for first char
                  ****** ... bits for second char
  NUMBER:
     : 0b1xxxxxxxxxxxxxx1
          *               ... sign
           *************  ... number
 */

#define C2EC(c)		((c) - '!') // char to encoded char
#define EC2C(c)		((c) + '!') // encoded char to char
#define C2SYM(c1, c2)	((((C2EC(c1) << 6) | C2EC(c2)) << 1) | 0x4001)     // mask: 0b0100000000000001
static inline lispval_t
make_sym(int c1, int c2){
    lispval_t buf;
    buf.data = C2SYM(c1, c2);
    return buf;
}
#define SYM2C1(s)	(((s).data & 0x3ffe) >> 7) // first char.  mask: 0b0011111111111110
#define SYM2C2(s)	(((s).data & 0x7e) >> 1) // second char.   mask: 0b0000000001111110
#define IS_SYM(c)	(((c).data & 0xc001) == 0x4001)
        
#define N2EN(n)                                                 \
    ((n >= 0                                                    \
      ? ((n & ((1<<13) - 1)) << 1)                              \
      : ((n & ((1<<14) - 1)) << 1)) | 0x8001)
// mask: 0b1000000000000001
#define EN2N(n)                                                         \
    ((int)((((n).data & 0x7ffe) >> 1) | /* number part, mask: 0b0011111111111110 */ \
           (((n).data & (1 << 14)) ? (7 << 13) : 0))) /* sign part,   mask: 0b1110000000000000 */
#define IS_NUM(n)	(((n).data & 0x8001) == 0x8001)
static inline lispval_t
make_num(int x){
    lispval_t buf;
    buf.data = N2EN(x);
    return buf;
}

#define CAR(x)		((x).cell->car)
#define CDR(x)		((x).cell->cdr)
static inline lispval_t cell2v(cell_t *x){lispval_t buf; buf.cell = x; return buf;}
static inline lispval_t make_cell(lispval_t x, lispval_t y){
    lispval_t buf;
    buf.cell = alloc_cell();
    buf.cell->car = x;
    buf.cell->cdr = y;
    return buf;
}
#define IS_CELL(x)	((x).data && (((x).data & 1) == 0))

#define IS_NIL(x)	((x).data == 0)
#define IS_T(x)		((x).data == Vt.data)

static inline lispval_t word2v(word x){lispval_t buf; buf.data = x; return buf;}


#include "vm.h"
#include "builtin.h"

#endif
