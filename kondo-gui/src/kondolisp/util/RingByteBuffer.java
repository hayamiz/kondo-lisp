/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package kondolisp.util;

import java.util.concurrent.LinkedBlockingDeque;

/**
 *
 * @author haya
 */
public class RingByteBuffer {
    int size;
    int get_pos;
    int put_pos;
    byte[] buf;

    public RingByteBuffer() {
        this.clear();
    }

    public synchronized void clear(){
        size = 1024;
        buf = new byte[size];
        get_pos = 0;
        put_pos = 0;
    }

    public synchronized byte get() throws UnderflowException{
        if (this.get_pos == this.put_pos){
            throw new UnderflowException();
        }

        byte ret = this.buf[this.get_pos];
        this.get_pos = (this.get_pos+1) % this.size;
        return ret;
    }

    public synchronized void put(byte x){
        if (this.get_pos == (this.put_pos + 1) % this.size){
            // about to overflow. extend buffer
            byte[] newbuf = new byte[this.size * 2];
            if(this.get_pos < this.put_pos){
                for(int i = this.get_pos;i < this.put_pos;i++){
                    newbuf[i] = this.buf[i];
                }
            } else {
                for(int i = this.get_pos;i < this.size+this.put_pos;i++){
                    newbuf[i] = this.buf[i % this.size];
                }
                this.put_pos += this.size;
            }
            this.size *= 2;
            this.buf = newbuf;
        }

        this.buf[this.put_pos] = x;
        this.put_pos = (this.put_pos+1) % this.size;
    }

    public synchronized int available(){
        if (this.get_pos <= this.put_pos){
            return this.put_pos - this.get_pos;
        } else {
            return this.put_pos + this.size - this.get_pos;
        }
    }

    public class UnderflowException extends Exception {
        
    }
}
