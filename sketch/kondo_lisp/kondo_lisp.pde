
#include <avr/pgmspace.h>
#include <EEPROM.h>

extern "C"
{
#include "kondo_lisp.h"

#define assert(cond)                                    \
    {                                                   \
        if (!(cond)){                                   \
            Serial.print("Assertion failed at ");       \
            Serial.print(__FILE__);                     \
            Serial.print(':', BYTE);                    \
            Serial.println(__LINE__, DEC);              \
            HALT("");                                   \
        }                                               \
    }

#define CELLBLOCK_ELEM_NUM  16
#define CELLBLOCK_SIZE  (CELLBLOCK_ELEM_NUM * sizeof(cell_t))
#define CELLBLOCK_NUM	16

#define CELLSPACE_SIZE  (CELLBLOCK_NUM * CELLBLOCK_SIZE) // 1024
#define CELLSPACE_ALIGN 32

#define STACK_SIZE (128)
#define VARTABLE_SIZE	(128+32)

#define CODE_SIZE (15 * 1024)

#define HALT(msg)                               \
    {                                           \
        Serial.print("HALT by error: ");        \
        Serial.println(msg);                    \
        Serial.print(0, BYTE);                  \
        while(1){};                             \
    }

    PROGMEM prog_uchar code[CODE_SIZE];

    void
    setup()
    {
        Serial.begin(9600);
        init_vars();
        // init global constants
        Vnil.data = 0;
        Vt.data = (1<<13 | 1);
        
        Serial.println("*** Welcome to KondoLisp ***");
        Serial.print(0, BYTE);
    }

    void
    loop()
    {
        byte c1, c2, c3, c4;
        word addr = 0;
        lispval_t ret;
        ret = Vnil;
        c1 = 0; c2 = 0; c3 = 0; c4 = 0;
#define READ_NEXT()                             \
        {                                       \
            c1 = c2;                            \
            c2 = c3;                            \
            c3 = c4;                            \
            while (Serial.available() == 0){    \
                continue;                       \
            }                                   \
            c4 = Serial.read();                 \
        }

        while(true){
            int i;
            addr = 0;
            READ_NEXT(); READ_NEXT(); READ_NEXT(); READ_NEXT();
            while(c1 | c2 | c3 | c4){
                for(i = 0;i < 4;i++){
                    EEPROM.write(addr++, c1);
                    if (debug_mode){
                        Serial.print(addr, DEC);
                        Serial.print(':', BYTE);
                        Serial.print(c1, DEC);
                        Serial.print(',', BYTE);
                        Serial.print(c2, DEC);
                        Serial.print(',', BYTE);
                        Serial.print(c3, DEC);
                        Serial.print(',', BYTE);
                        Serial.print(c4, DEC);
                        Serial.println("");
                    }
                    READ_NEXT();
                }
            }
            if (debug_mode){
                Serial.println("");
            }
            // terminator
            for(i = 0;i < 4;i++){
                EEPROM.write(addr++, 0);
            }
            ret = vm_eval();
            writeln(ret);
            Serial.print(0, BYTE);
        }
    }

    void
    init_vars()
    {
        debug_mode = false;
        cellspace = (cell_t *) malloc(VARTABLE_SIZE
                                      + CELLSPACE_SIZE
                                      + STACK_SIZE
                                      + CELLSPACE_ALIGN - 1);
        if (cellspace == NULL) {
            HALT("Cannot allocate enough memory for cell space and var table.");
        }
        int rem = ((int)cellspace) % CELLSPACE_ALIGN;
        if (rem != 0) {
            cellspace = (cell_t *)(((int)cellspace) + rem);
        }
        vartable = (var_entry_t *)(((word)cellspace) + CELLSPACE_SIZE);
        cur_var = vartable;
        stack = (word *)(((word)cellspace) + CELLSPACE_SIZE + STACK_SIZE);
        memset(cellspace, 0, CELLSPACE_SIZE + VARTABLE_SIZE + STACK_SIZE);
    }

    cell_t *
    alloc_cell()
    {
        int i;
        cell_t *cell;
        cell_t *blk;
        cell_t *ptr;
        cell_t *upper_bound;

        cell = NULL;
        blk = cellspace;
        upper_bound = cellspace + (CELLSPACE_SIZE / sizeof(cell_t));
        while(blk < upper_bound){
            for(i = 1;i < 7;i++){
                ptr = blk + i;
                if (ptr->car.data == 0 && ptr->cdr.data == 0){
                    return ptr;
                }
            }
            blk += CELLBLOCK_ELEM_NUM;
        }
        return NULL;
    }

    void
    free_cell(cell_t *cell)
    {
        int i, j;
        cell->car.cell = NULL;
        cell->cdr.cell = NULL;
    }

    void
    write(lispval_t val){
        lispval_t buf;
        if(IS_T(val)){
            Serial.print('T');
        } else if (IS_NIL(val)) {
            Serial.print("NIL");
        } else if (IS_NUM(val)){
            Serial.print(EN2N(val), DEC);
        } else if (IS_SYM(val)){
            Serial.print(EC2C(SYM2C1(val)), BYTE);
            Serial.print(EC2C(SYM2C2(val)), BYTE);
        } else if (IS_CELL(val)){
            Serial.print('(', BYTE);
            write(CAR(val));
            while (IS_CELL(CDR(val))){
                Serial.print(' ', BYTE);
                val = CDR(val);
                write(CAR(val));
            }
            if (!IS_NIL(CDR(val))){
                if (!IS_CELL(CDR(val))){
                    Serial.print(" . ");
                } else {
                    Serial.print(' ', BYTE);
                }
                write(CDR(val));
            }
            Serial.print(')', BYTE);
        } else {
            HALT("Invalid lisp data");
        }
    }
    void
    writeln(lispval_t val){
        write(val);
        Serial.println("");
    }

    vm_t vm;
    
    word
    vm_push(word x)
    {
        if (vm.sp >= (vm.stack + STACK_SIZE)) {
            HALT("Stack overflow");
        }
        *vm.sp = (word) x;
        vm.sp++;
        return x;
    }
    word
    vm_pop(void)
    {
        return (vm.val.data = *(--vm.sp));
    }
    word
    vm_operand(void)
    {
        return eeprom_readword((word)vm.pc+2);
    }
    word
    vm_opecode(void)
    {
        return eeprom_readword((word)vm.pc);
    }
    word
    vm_ival()
    {
        return vm.val.data = vm_operand();
    }
    word
    vm_vref()
    {
        var_entry_t *ptr = cur_var - 1;
        lispval_t sym;
        sym.data = vm_operand();
        while(ptr >= vartable){
            if (sym.data == ptr->sym.data){
                break;
            }
            ptr --;
        }
        if (sym.data == ptr->sym.data){
            vm.val = ptr->val;
        } else {
            Serial.print("Cannot find symbol: ");
            Serial.print(SYM2C1(sym), BYTE);
            Serial.print(SYM2C2(sym), BYTE);
            HALT("");
        }
        return vm.val.data;
    }
    word
    vm_vset()
    {
        var_entry_t *ptr = cur_var - 1;
        lispval_t sym;
        sym.data = vm_operand();
        while(ptr >= vartable){
            if (sym.data == ptr->sym.data){
                break;
            }
            ptr --;
        }
        if (sym.data == ptr->sym.data){
            ptr->val = vm.val;
        } else {
            Serial.print("Cannot find symbol: ");
            Serial.print(SYM2C1(sym), BYTE);
            Serial.print(SYM2C2(sym), BYTE);
            HALT("");
        }
    }
    lispval_t
    vm_binop(word op)
    {
        lispval_t x, y;
        x = vm.val;
        y.data = vm_pop();
        boolean pred;
        switch(op){
        case VM_LT:
            pred = EN2N(x) < EN2N(y);
            break;
        case VM_GT:
            pred = EN2N(x) > EN2N(y);
            break;
        case VM_LE:
            pred = EN2N(x) <= EN2N(y);
            break;
        case VM_GE:
            pred = EN2N(x) >= EN2N(y);
            break;
        case VM_EQ:
            pred = EN2N(x) == EN2N(y);
            break;
        case VM_PLUS:
            pred = EN2N(x) + EN2N(y);
            break;
        case VM_MINUS:
            pred = EN2N(x) - EN2N(y);
            break;
        }
        if (pred){
            vm.val = Vt;
        } else {
            vm.val = Vnil;
        }
        return vm.val;
    }

    lispval_t
    vm_eval()
    {
        // initialize VM
        vm.val.data = 0;
        vm.stack = stack;
        vm.pc = 0;
        vm.fp = NULL;
        vm.sp = vm.stack;

    dispatch:
        if (debug_mode) {
            Serial.print("PC: ");
            Serial.print((word)vm.pc / sizeof(vminst_t), DEC);
            Serial.print(", opecode: ");
            Serial.print(vm_opecode(), DEC);
            Serial.print(", operand: ");
            Serial.print(vm_operand(), DEC);
            Serial.print(", VAL: ");
            write(vm.val);
            Serial.print(", SP: ");
            Serial.print((word)vm.sp, DEC);
            Serial.print(", FP: ");
            Serial.print((word)vm.fp, DEC);
            Serial.println("");
        }
        switch(vm_opecode()){
        case VM_IVAL:
            vm_ival();
            break;
        case VM_IVAL_PUSH:
            vm_push(vm_ival());
            break;
        case VM_PUSH:
            vm_push(vm.val.data);
            break;
        case VM_POP:
            vm_pop();
            break;
        case VM_JMP:
            vm.pc = (vminst_t *) (vm_operand() * sizeof(vminst_t));
            goto dispatch;
        case VM_BIF:
            if (!IS_NIL(vm.val)){
                vm.pc = (vminst_t *) (vm_operand() * sizeof(vminst_t));
            } else {
                vm.pc++;
            }
            goto dispatch;
        case VM_BIFN:
            if (IS_NIL(vm.val)){
                vm.pc = (vminst_t *) (vm_operand() * sizeof(vminst_t));
            } else {
                vm.pc++;
            }
            goto dispatch;
        case VM_PUSH_FRAME:
            vm_push((word) vm.fp);
            vm.fp = vm.sp - 1;
            break;
        case VM_FUNCALL:
        {
            word nargs = ((word)vm.sp - (word)vm.fp) / sizeof(word) - 1;
            word n;
            lispval_t a1 = Vnil;
            lispval_t a2 = Vnil;
            lispval_t a3 = Vnil;
            lispval_t rest = Vnil;
            lispval_t tmp;
            lispval_t fname; fname.data = vm_operand();
            if (nargs >= 1) a1.data = (*--vm.sp);
            if (nargs >= 2) a2.data = (*--vm.sp);
            if (nargs >= 3) a3.data = (*--vm.sp);
            if (nargs > 3){
                tmp = rest = make_cell(Vnil, Vnil);
                rest.cell->car.data = (*--vm.sp);
                n = nargs - 1;
                while(n > 3){
                    tmp.cell->cdr = make_cell(Vnil, Vnil);
                    tmp = tmp.cell->cdr;
                    tmp.cell->car.data = (*--vm.sp);
                    n--;
                }
            } else {
                rest = Vnil;
            }

            // dispatch
            if (IS_NUM(fname)) {
                vm.val = builtin_fun(EN2N(fname), nargs, a1, a2, a3, rest);
            } else {
                HALT("FUNCALL by symbol not implemented");
            }
            tmp = vm.val;
            vm.fp = (word *) vm_pop(); // restore stack frame
            vm.val = tmp;
        }
            break;
        case VM_BIND:
            if (((word)cur_var - (word)vartable) >= VARTABLE_SIZE){
                HALT("Vartable overflow");
            }
            cur_var->sym.data = vm_operand();
            cur_var->val = vm.val;
            cur_var++;
            break;
        case VM_UNBIND:
        {
            int nvar = vm_operand();
            while(nvar > 0){
                cur_var->sym.data = 0;
                cur_var->val.data = 0;
                cur_var--;
                nvar--;
            }
        }
            break;
        case VM_VREF:
            vm_vref();
            break;
        case VM_VREF_PUSH:
            vm_push(vm_vref());
            break;
        case VM_VSET:
            vm_vset();
            break;
        case VM_VINC:
        {
            var_entry_t *ptr = cur_var - 1;
            lispval_t sym;
            sym.data = vm_operand();
            while(ptr >= vartable){
                if (sym.data == ptr->sym.data){
                    break;
                }
                ptr --;
            }
            if (sym.data == ptr->sym.data){
                if (IS_NUM(ptr->val)){
                    vm.val = ptr->val = make_num(EN2N(ptr->val) + 1);
                } else {
                    Serial.print("VM_VINC: Type error: ");
                    Serial.print(SYM2C1(sym), BYTE);
                    Serial.print(SYM2C2(sym), BYTE);
                    Serial.println(" is not a number");
                    HALT("");
                }
            } else {
                Serial.print("Cannot find symbol: ");
                Serial.print(SYM2C1(sym), BYTE);
                Serial.print(SYM2C2(sym), BYTE);
                HALT("");
            }
        }
        break;
        case VM_LT:
        case VM_GT:
        case VM_LE:
        case VM_GE:
        case VM_EQ:
        case VM_PLUS:
        case VM_MINUS:
            vm_binop(vm_opecode());
            break;
        case VM_IVAL_LT:
        case VM_IVAL_GT:
        case VM_IVAL_LE:
        case VM_IVAL_GE:
        case VM_IVAL_EQ:
        case VM_IVAL_PLUS:
        case VM_IVAL_MINUS:
            vm_ival();
            vm_binop(vm_opecode());
            break;
        case VM_IVAL_CONS:
            vm_ival();
        case VM_CONS:
        {
            lispval_t car, cdr;
            cdr = vm.val;
            car.data = vm_pop();
            vm.val = make_cell(car, cdr);
            break;
        }
        case VM_EXIT:
            goto end_eval;
        default:
            Serial.print("Not implemented. addr: ");
            Serial.print((word)vm.pc, DEC);
            Serial.print(", opecode: ");
            Serial.print(vm_opecode(), DEC);
            Serial.print(", operand: ");
            Serial.println(vm_operand(), DEC);
        }

        vm.pc++;
        goto dispatch;
    end_eval:
        free(vm.stack);
        return vm.val;
    }

    lispval_t
    builtin_fun(word fname, word nargs,
                lispval_t a1, lispval_t a2, lispval_t a3,
                lispval_t rest)
    {
        lispval_t ret = Vnil;
        switch(fname){
        case Fprint_char:
            assert(nargs > 0);
#define PRINT_CHAR(c) { if (EN2N(c) != 0) {Serial.print(EN2N(c), BYTE);}}
            PRINT_CHAR(a1);
            if (nargs >= 2) PRINT_CHAR(a2);
            if (nargs >= 3) PRINT_CHAR(a3);
            break;
        case Fwrite:
            assert(nargs == 1);
            writeln(a1);
            break;
        case Fplus:
        {
            int tmp = EN2N(a1);
            assert(nargs > 0);
            if (nargs > 1) tmp += EN2N(a2);
            if (nargs > 2) tmp += EN2N(a3);
            if (nargs > 3){
                Serial.print("plus: ");
                writeln(rest);
                while(!IS_NIL(rest)){
                    tmp += EN2N(CAR(rest));
                    rest = CDR(rest);
                }
            }
            ret = make_num(tmp);
        }
        break;
        case Fminus:
            int tmp;
            assert(nargs > 0);
            if (nargs == 1){
                tmp = EN2N(a1) * -1;
            } else {
                tmp = EN2N(a1);
                if (nargs > 1) tmp -= EN2N(a2);
                if (nargs > 2) tmp -= EN2N(a3);
                if (nargs > 3){
                    while(!IS_NIL(rest)){
                        tmp -= EN2N(CAR(rest));
                        rest = CDR(rest);
                    }
                }
            }
            ret = make_num(tmp);
            break;
        case Ftimes:
        {
            int tmp = EN2N(a1);
            assert(nargs > 0);
            if (nargs > 1) tmp *= EN2N(a2);
            if (nargs > 2) tmp *= EN2N(a3);
            if (nargs > 3){
                while(!IS_NIL(rest)){
                    tmp *= EN2N(CAR(rest));
                    rest = CDR(rest);
                }
            }
            ret = make_num(tmp);
        }
            break;
        case Fdivide:
        {
            int tmp = EN2N(a1);
            assert(nargs > 0);
            if (nargs > 1) tmp /= EN2N(a2);
            if (nargs > 2) tmp /= EN2N(a3);
            if (nargs > 3){
                while(!IS_NIL(rest)){
                    tmp /= EN2N(CAR(rest));
                    rest = CDR(rest);
                }
            }
            ret = make_num(tmp);
        }
        break;
        case Fremainder:
            assert(nargs == 2);
            ret = make_num(EN2N(a1) % EN2N(a2));
            break;
        case Feq:
            assert(nargs == 2);
            if (a1.data == a2.data) {
                ret = Vt;
            } else {
                ret = Vnil;
            }
            break;
        case Fequal:
            assert(nargs == 2);
            HALT("EQUAL not implemented");
            break;
        case Fdebug_mode:
            if (nargs > 0) {
                if (!IS_NIL(a1)){
                    debug_mode = true;
                } else {
                    debug_mode = false;
                }
            } else {
                debug_mode = !debug_mode;
            }
            break;
        case Fpin_mode:
            assert(nargs == 2);
            Serial.print("pinmode: ");
            Serial.print(EN2N(a1), DEC);
            if (EN2N(a2) == 0){
                pinMode(EN2N(a1), INPUT);
                Serial.println(" input");
            } else {
                pinMode(EN2N(a1), OUTPUT);
                Serial.println(" output");
            }
            break;
        case Fdelay:
            assert(nargs == 1);
            delay(EN2N(a1));
            break;
        case Fdigital_write:
            assert(nargs == 2);
            digitalWrite(EN2N(a1), (EN2N(a2) == 0 ? LOW : HIGH));
            break;
        case Fdigital_read:
            assert(nargs == 1);
            ret = make_num(digitalRead(EN2N(a1)) == HIGH ? 1 : 0);
            break;
        case Fanalog_reference:
            assert(nargs == 1);
            switch(EN2N(a1)){
            case 0:
                analogReference(DEFAULT);
                break;
            case 1:
                analogReference(INTERNAL);
                break;
            case 2:
                analogReference(EXTERNAL);
                break;
            }
            break;
        case Fanalog_read:
            assert(nargs == 1);
            ret = make_num(analogRead(EN2N(a1)));
            break;
        case Fanalog_write:
            assert(nargs == 2);
            analogWrite(EN2N(a1), EN2N(a2));
            break;
        }
        return ret;
    }
}
