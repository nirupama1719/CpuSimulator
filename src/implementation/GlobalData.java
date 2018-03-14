/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package implementation;

import utilitytypes.IGlobals;
import tools.InstructionSequence;

/**
 * As a design choice, some data elements that are accessed by multiple
 * pipeline stages are stored in a common object.
 * 
 * TODO:  Add to this any additional global or shared state that is missing.
 * 
 * @author 
 */
public class GlobalData implements IGlobals {
    public InstructionSequence program;
    public int program_counter = 0;
    public int getProgram_counter() {
		return program_counter;
	}
    
    public boolean decode_stalled=false;
    public boolean exec_stalled=false;
    public boolean mem_stalled=false;
    public boolean write_stalled=false;
    public boolean bra_stalled=false;
    public boolean taken=false;
    public boolean stall=false;
    public boolean eq = false;
    public boolean ne = false;
    public boolean gt = false;
    public boolean ge = false;
    public boolean lt = false;
    public boolean le = false;
    
	public void setProgram_counter(int program_counter) {
		this.program_counter = program_counter;
	}

	public int[] register_file = new int[32];
    public boolean[] register_invalid = new boolean[32];
    public int[] memory=new int[4000];
    @Override
    public void reset() {
        program_counter = 0;
        register_file = new int[32];
    }
    
    
    // Other global and shared variables here....

}
