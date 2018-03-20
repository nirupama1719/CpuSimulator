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
    @Override
    public void reset() {
        program_counter = 0;
        register_file = new int[32];
    }

    
    // Is the simulation running?  This is set at startup and cleared by
    // the HALT instruction (in Writeback).
    boolean running;
    
    // Program store whence we fetch instructions
    public InstructionSequence program;
    
    // Register file and invalid flags
    public int[] register_file = new int[32];
    public boolean[] register_invalid = new boolean[32];

    
    // Main memory
    public int[] memory = new int[1024];

    
    // Current instruction to look up
    public int program_counter = 0;
    // Next PC to fetch if Decode not stalled and/or branch resolved not taken
    public int next_program_counter_nobranch = 0;
    // Next PC to fetch if Decode not stalled and branch resolved taken
    public int next_program_counter_takenbranch = 0;
    
    
    // Names of branch states and signals
    public static enum EnumBranchState {
        NULL, WAITING, TAKEN, NOT_TAKEN
    }
    
    // Current branch state used by Fetch to determine stall condition.
    // Valid values are NULL and WAITING.
    public EnumBranchState current_branch_state = EnumBranchState.NULL;
    
    // Next branch state as determined by Fetch.  Valid values are NULL
    // and WAITING.  This is computed by Fetch.compute() and committed to
    // current_branch_state in Fetch.advanceClock() when Decode is able to 
    // accept new work.  (When next_branch_state_fetch is committed to
    // current_branch_state, then next_branch_state_fetch must be cleared.)
    public EnumBranchState next_branch_state_fetch = EnumBranchState.NULL;
    
    // Next branch state as determined by Decode.  Valid values are NULL,
    // TAKEN, and NOT_TAKEN.  This is computed by Decode.compute() and 
    // committed to current_branch_state in Fetch.advanceClock().  Think of 
    // this as a SIGNAL from Decode to Fetch rather than a state, since it 
    // lasts only one cycle.  (When next_branch_state_decode is committed to
    // current_branch_state, then next_branch_state_decode must be cleared.)
    public EnumBranchState next_branch_state_decode = EnumBranchState.NULL;
}
