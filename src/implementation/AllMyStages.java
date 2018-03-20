/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package implementation;

import implementation.AllMyLatches.*;
import utilitytypes.EnumOpcode;
import baseclasses.InstructionBase;
import baseclasses.PipelineRegister;
import baseclasses.PipelineStageBase;
import voidtypes.VoidLatch;
import baseclasses.CpuCore;
import cpusimulator.CpuSimulator;
import static utilitytypes.EnumOpcode.BRA;
import static utilitytypes.EnumOpcode.CALL;
import static utilitytypes.EnumOpcode.JMP;
import utilitytypes.Operand;
import voidtypes.VoidInstruction;

/**
 * The AllMyStages class merely collects together all of the pipeline stage 
 * classes into one place.  You are free to split them out into top-level
 * classes.
 * 
 * Each inner class here implements the logic for a pipeline stage.
 * 
 * It is recommended that the compute methods be idempotent.  This means
 * that if compute is called multiple times in a clock cycle, it should
 * compute the same output for the same input.
 * 
 * How might we make updating the program counter idempotent?
 * 
 * @author
 */
public class AllMyStages {
    /*** Fetch Stage ***/
    public static class Fetch extends PipelineStageBase<VoidLatch,FetchToDecode> {
        public Fetch(CpuCore core, PipelineRegister input, PipelineRegister output) {
            super(core, input, output);
        }
        
        // Does this state have an instruction it wants to send to the next
        // stage?  Note that this is computed only for display and debugging
        // purposes.
        boolean has_work;
                
        /** 
         * For Fetch, this method only has diagnostic value.  However, 
         * stageHasWorkToDo is very important for other stages.
         * 
         * @return Status of Fetch, indicating that it has fetched an 
         *         instruction that needs to be sent to Decode.
         */
        @Override
        public boolean stageHasWorkToDo() {
            return has_work;
        }
        
        /**
         * For other stages, status can be automatically computed, but 
         * for Fetch, we have to compute it.  This method has only diagnostic
         * value.
         * 
         * @return Status string
         */
        @Override
        public String getStatus() {
            GlobalData globals = (GlobalData)core.getGlobalResources();
            String s = super.getStatus();
            if (globals.current_branch_state == GlobalData.EnumBranchState.WAITING) {
                if (s.length() > 0) s += ", ";
                s += "ResolveWait";
            }
            return s;
        }

        
        @Override
        public void compute(VoidLatch input, FetchToDecode output) {
            GlobalData globals = (GlobalData)core.getGlobalResources();
            
            // Get the PC and fetch the instruction
            int pc = globals.program_counter;
            InstructionBase ins = globals.program.getInstructionAt(pc);
            
            // Initialize this status flag to assume a stall or bubble condition
            // by default.
            has_work = false;
            
            // If the instruction is NULL (like we ran off the end of the
            // program), just return.  However, for diagnostic purposes,
            // we make sure something meaningful appears when 
            // CpuSimulator.printStagesEveryCycle is set to true.
            if (ins.isNull()) {
                // Fetch is working on no instruction at no address
                setActivity("----: NULL");
                // Nothing more to do.
                return;
            }
            
            // Also don't do anything if we're stalled waiting on branch
            // resolution.
            if (globals.current_branch_state != GlobalData.EnumBranchState.NULL) {
                // Fetch is waiting on branch resolution
                setActivity("----: BRANCH-BUBBLE");
                // Since we're stalled, nothing more to do.
                return;
            }
            
            // Compute the value of the next program counter, to be committed
            // in advanceClock depending on stall states.  This makes 
            // computing the next PC idempotent.  
            globals.next_program_counter_nobranch = pc + 1;
            
            // Since there is no input pipeline register, we have to inform
            // the diagnostic helper code explicitly what instruction Fetch
            // is working on.
            has_work = true;
            setActivity(ins.toString());
            
            // If the instruction is a branch, request that the branch wait
            // state be set.  This will be committed in Fetch.advanceClock
            // if Decode isn't stalled.  This too is idempotent.
            if (ins.getOpcode().isBranch()) {
                globals.next_branch_state_fetch = GlobalData.EnumBranchState.WAITING;
            }
            
            // Send the fetched instruction to the output pipeline register.
            // PipelineRegister.advanceClock will ignore this if 
            // Decode is stalled, and Fetch.compute will keep setting the
            // output instruction to the same thing over and over again.
            // In the stall case Fetch.advanceClock will not change the program 
            // counter, nor will it commit globals.next_branch_state_fetch to 
            // globals.branch_state_fetch.
            output.setInstruction(ins);
        }
        
                
        /**
         * This function is to advance state to the next clock cycle and
         * can be applied to any data that must be updated but which is
         * not stored in a pipeline register.
         */
        @Override
        public void advanceClock() {
            // Only take take action if Decode is able to accept a new 
            // instruction.
            if (nextStageCanAcceptWork()) {
                GlobalData globals = (GlobalData)core.getGlobalResources();
                
                if (globals.current_branch_state == GlobalData.EnumBranchState.WAITING) {
                    // If we're currently waiting for a branch resolution...
                    
                    // See if the Decode stage has provided a resolution
                    if (globals.next_branch_state_decode != GlobalData.EnumBranchState.NULL) {
                        
                        // Take action based on the resolution.
                        switch (globals.next_branch_state_decode) {
                            
                            // If Decode resolves that the branch is to be taken...
                            case TAKEN:     
                                // Set the PC to the branch target
                                globals.program_counter = globals.next_program_counter_takenbranch;
                                break;

                            // If Decode resolves that the branch is no to be taken...
                            case NOT_TAKEN:
                                // Set the PC to the address immediately after the branch
                                globals.program_counter = globals.next_program_counter_nobranch;
                                break;
                                
                        }
                        
                        // Clear the signal from Decode
                        globals.next_branch_state_decode = GlobalData.EnumBranchState.NULL;

                        // Clear the stall state for Fetch
                        globals.current_branch_state = GlobalData.EnumBranchState.NULL;
                    }                    
                } else {
                    // If we've not been waiting on a branch resolution...
                    
                    if (globals.next_branch_state_fetch != GlobalData.EnumBranchState.NULL) {
                        // If Fetch wants to change its stall state...
                        
                        // Commit the new state
                        globals.current_branch_state = globals.next_branch_state_fetch;
                        
                        // Clear the signal to change the state
                        globals.next_branch_state_fetch = GlobalData.EnumBranchState.NULL;
                    } else {
                        // If Fetch is not switching to a stall state, 
                        // increment the program counter.                        
                        globals.program_counter = globals.next_program_counter_nobranch;
                    }
                }
            }
        }
    }

    
    /*** Decode Stage ***/
    public static class Decode extends PipelineStageBase<FetchToDecode,DecodeToExecute> {
        public Decode(CpuCore core, PipelineRegister input, PipelineRegister output) {
            super(core, input, output);
        }
        
        
        // This flag is set if Decode has to stall because at least one input
        // operand is a register that is marked invalid.
        boolean invalid_register_stall = false;
                
        @Override
        public boolean stageWaitingOnResource() {
            return invalid_register_stall;
        }


        // Destination register to mark invalid if the next stage is able
        // to accept new work.  A value of -1 means "none."
        int register_to_invalidate = -1;
        

        @Override
        public void compute(FetchToDecode input, DecodeToExecute output) {
            InstructionBase ins = input.getInstruction();
            
            // SECTION 1:  DEFAULT STATE
            
            // Default to no stall.  Code below will set this flag to true
            // if a stall condition is found.  The flag must *remain* true
            // as long as ANY stall condition is true.  Note that
            // always starting with the default condition makes the
            // stall checking idempotent.  If a later stage were stalled, then
            // any changes made to the instruction are made to a duplicate,
            // which gets thrown away.
            invalid_register_stall = false;
            
            // No register to invalidate.
            register_to_invalidate = -1;
            
            // If the instruction is null, return; nothing to do.
            if (ins.isNull()) return;
            
            
            // ** SECTION 2:  GET COMMON VARIABLES
            // Duplicate instruction and get references
            // to variables that will be used further down.
            
            // Duplicate the instruction so that we can modify it without
            // affecting what's in the input latch.
            ins = ins.duplicate();
            
            // Get the opcode and determine if oper0 is a source
            EnumOpcode opcode = ins.getOpcode();
            boolean oper0src = opcode.oper0IsSource();

            // Get the register file and valid flags
            GlobalData globals = (GlobalData)core.getGlobalResources();
            int[] regfile = globals.register_file;
            boolean[] reginval = globals.register_invalid;
            
            Operand oper0 = ins.getOper0();
            Operand src1  = ins.getSrc1();
            Operand src2  = ins.getSrc2();

            
            // ** SECTION 1:  EVALUATE STALL CONDITIONS
            // Work out if there are any source registers that we cannot
            // get because they are invalid, waiting on writeback.
            
            int value0 = 0;
            int regnum0 = oper0.getRegisterNumber();
            if (oper0src) {
                // If oper0 is a source, check if it's register is invalid.
                if (oper0.isRegister()) {
                    if (reginval[regnum0]) {
                        invalid_register_stall = true;
                        // If there's a stall, no point in doing anything else,
                        // do might as well just bail out.
                        return;
                    } else {
                        // If the register is valid, look up its value.
                        oper0.lookUpFromRegisterFile(regfile);
                        // This is idempotent because setting of operand
                        // values is being done to the duplicate of the
                        // instruction.
                    }
                }
                // Get the value in case we need it
                value0 = oper0.getValue();
            }
            
            // Check src1 for stall; look up if valid.
            if (src1.isRegister()) {
                if (reginval[src1.getRegisterNumber()]) {
                    invalid_register_stall = true;
                    // Nothing further to do if stalled.
                    return;
                } else {
                    src1.lookUpFromRegisterFile(regfile);
                }
            }
            int value1 = src1.getValue();
            
            // Check src2 for stall; look up if valid.
            if (src2.isRegister()) {
                if (reginval[src2.getRegisterNumber()]) {
                    invalid_register_stall = true;
                    // Nothing further to do if stalled.
                    return;
                } else {
                    src2.lookUpFromRegisterFile(regfile);
                }
            }
            int value2 = src2.getValue();
            
            
            // ** SECTION 3:  EVALUATE BRANCH CONDITIONS
            // Process branch instructions and send signals to
            // Fetch about branch resolution.  
            
            // This code is idempotent because it is setting signals 
            // (next_program_counter_takenbranch, next_branch_state_decode) 
            // that don't take effect except in some pipeline stage's 
            // advanceClock method, which is able to check 
            // nextStageCanAcceptWork before committing anything.  In this 
            // case, it's Fetch.nextStageCanAcceptWork, but that's okay.
            // The point is that if for any reason this stage couldn't 
            // move forward (hand off its work to the next stage),
            // then there would be no side-effects, and this code would
            // produce exactly the same effects on the next cycle.
            
            boolean take_branch = false;
            InstructionBase null_ins = VoidInstruction.getVoidInstruction();
            
            switch (opcode) {
                case BRA:
                    // The CMP instruction just sets its destination to
                    // (src1-src2).  The result of that is in oper0 for the
                    // BRA instruction.  See comment in MyALU.java.
                    switch (ins.getComparison()) {
                        case EQ:
                            take_branch = (value0 == 0);
                            break;
                        case NE:
                            take_branch = (value0 != 0);
                            break;
                        case GT:
                            take_branch = (value0 > 0);
                            break;
                        case GE:
                            take_branch = (value0 >= 0);
                            break;
                        case LT:
                            take_branch = (value0 < 0);
                            break;
                        case LE:
                            take_branch = (value0 <= 0);
                            break;
                    }
                    
                    if (take_branch) {
                        // If the branch is taken, send a signal to Fetch
                        // that specifies the branch target address, via
                        // "globals.next_program_counter_takenbranch".  
                        // If the label is valid, then use its address.  
                        // Otherwise, the target address will be found in 
                        // src1.
                        if (ins.getLabelTarget().isNull()) {
                            globals.next_program_counter_takenbranch = value1;
                        } else {
                            globals.next_program_counter_takenbranch = 
                                    ins.getLabelTarget().getAddress();
                        }
                        
                        // Send a signal to Fetch, indicating that the branch
                        // is resolved taken.  This will be picked up by
                        // Fetch.advanceClock on the same clock cycle.
                        globals.next_branch_state_decode = GlobalData.EnumBranchState.TAKEN;
                    } else {
                        // Send a signal to Fetch, indicating that the branch
                        // is resolved not taken.
                        globals.next_branch_state_decode = GlobalData.EnumBranchState.NOT_TAKEN;
                    }
                    
                    // Later stages should just ignore branch instructions,
                    // letting the pass through.  But it's easier to just not
                    // have to deal with it, and the diagnostic output is
                    // cleaner.  So once we've processed a branch instruction,
                    // we're done with it, so we can pass a bubble to the next
                    // stage.
                    output.setInstruction(null_ins);
                    // All done; return.
                    return;
                    
                case JMP:
                    // JMP is an inconditionally taken branch.  If the
                    // label is valid, then take its address.  Otherwise
                    // its operand0 contains the target address.
                    if (ins.getLabelTarget().isNull()) {
                        globals.next_program_counter_takenbranch = value0;
                    } else {
                        globals.next_program_counter_takenbranch = 
                                ins.getLabelTarget().getAddress();
                    }
                    
                    // Send a signal to Fetch, indicating that the branch is
                    // taken.
                    globals.next_branch_state_decode = GlobalData.EnumBranchState.TAKEN;
                    
                    // Replace the JMP with a bubble for later stages.
                    output.setInstruction(null_ins);
                    // All done; return.
                    return;
                    
                case CALL:
                    // Not implemented yet
                    return;
            }


            // ** SECTION 4:  POST RESULTS FOR NEXT STAGE/NEXT CYCLE
            
            // If the instruction needs to do a writeback, mark its operand0
            // register as invalid.  The register isn't actually marked
            // invalid until advanceClock is called so that nothing
            // will happen if a later stage is stalled.  
            if (opcode.needsWriteback()) {
                register_to_invalidate = regnum0;
            }
            
            // For all other instructions, set instruction in the output
            // pipeline register.
            output.setInstruction(ins);
        }
        
        
        @Override
        public void advanceClock() {
            // Only take take action if the next stage is able to accept a new 
            // instruction.
            if (nextStageCanAcceptWork() && register_to_invalidate>=0) {
                GlobalData globals = (GlobalData)core.getGlobalResources();
                boolean[] reginval = globals.register_invalid;
                
                // Invalidate the requested register
                reginval[register_to_invalidate] = true;
                // Clear the signal
                register_to_invalidate = -1;
                
                // Here's why we invalidate the destination register here:
                // Hypothetically, if any subsequent stage were able to stall, 
                // then invalidating the register in compute() would result in:
                // (a) potential race condition against Writeback clearning
                // invalid flags, and
                // (b) deadlock for any instruction that used the same
                // register as a source and destination.
            }
        }
    }
    

    /*** Execute Stage ***/
    public static class Execute extends PipelineStageBase<DecodeToExecute,ExecuteToMemory> {
        public Execute(CpuCore core, PipelineRegister input, PipelineRegister output) {
            super(core, input, output);
        }

        @Override
        public void compute(DecodeToExecute input, ExecuteToMemory output) {
            InstructionBase ins = input.getInstruction();
            if (ins.isNull()) return;

            // Get all source values (getting the value of oper0 is harmless
            // if it's a destination).
            int source1 = ins.getSrc1().getValue();
            int source2 = ins.getSrc2().getValue();
            int oper0 =   ins.getOper0().getValue();

            // Pass opcode and operands to the ALU.
            int result = MyALU.execute(ins.getOpcode(), source1, source2, oper0);
            
            // I store the ALU result in its own field in the pipeline register.
            output.result = result;
            
            // A viable alternative would be to replace one of the source
            // operands.  At this point, src1 and src2 will no longer be
            // used by anything.  So you could do something like this instead:
            //      ins = ins.duplicate();
            //      ins.getSrc1().setValue(result);
            
            output.setInstruction(ins);
        }
    }
    

    /*** Memory Stage ***/
    public static class Memory extends PipelineStageBase<ExecuteToMemory,MemoryToWriteback> {
        public Memory(CpuCore core, PipelineRegister input, PipelineRegister output) {
            super(core, input, output);
        }

        @Override
        public void compute(ExecuteToMemory input, MemoryToWriteback output) {
            InstructionBase ins = input.getInstruction();
            if (ins.isNull()) return;

            // Get the globals, where main memory is held
            GlobalData globals = (GlobalData)core.getGlobalResources();
            
            // The address for LOAD and STORE is the ALU result.
            int addr = input.result;
            
            // A viable alternative would be to store the ALU result in
            // one of the no-longer-used source operands.  Here, we would get
            // that value in some way like this:
            //      int addr = ins.getSrc1().getValue();
            
            int value;
            switch (ins.getOpcode()) {
                case LOAD:
                    // Fetch the value from main memory at the address
                    // retrieved above.
                    value = globals.memory[addr];
                    
                    // Use that value as the result passed down the pipeline.
                    output.result = value;
                    
                    // Alternative:
                    //      ins = ins.duplicate();
                    //      ins.getSrc1().setvalue(value);
                    break;
                
                case STORE:
                    // For store, the value to be stored in main memory is
                    // in oper0, which was fetched in Decode.
                    value = ins.getOper0().getValue();
                    globals.memory[addr] = value;
                    break;
                    
                default:
                    output.result = input.result;
                    break;
            }

            output.setInstruction(ins);
        }
    }
    

    /*** Writeback Stage ***/
    public static class Writeback extends PipelineStageBase<MemoryToWriteback,VoidLatch> {
        public Writeback(CpuCore core, PipelineRegister input, PipelineRegister output) {
            super(core, input, output);
        }

        @Override
        public void compute(MemoryToWriteback input, VoidLatch output) {
            InstructionBase ins = input.getInstruction();
            if (ins.isNull()) return;
            GlobalData globals = (GlobalData)core.getGlobalResources();
            
            // NOTE:  This code is not idempotent.  However, since it can't
            // stall, and there are no subsequent stages that could stall,
            // then there can be no harmful side-effects of committing
            // register writes here.
            // If we REALLY wanted to be pedantic, we could do all of this in
            // Writeback.advanceClock, but it would add unnecessary complexity
            // to the code.
            
            // If this instruction needs a writeback...
            if (ins.getOpcode().needsWriteback()) {
                // By definition, oper0 is a register and the destination.
                // Get its register number;
                int regnum = ins.getOper0().getRegisterNumber();
                
                // Store the result in the register.
                globals.register_file[regnum] = input.result;
                
                // Alternative:
                //      int result = ins.getSrc1().getValue();
                //      globals.register_file[regnum] = result;
                
                // Mark the register as now valid.
                globals.register_invalid[regnum] = false;
            }
            
            // If the instruction is HALT, clear the running flag in the
            // globals.  It is very important that we process HALT in this
            // stage; otherwise we'd shut down when there are other
            // still in the pipeline that are not finished.
            if (ins.getOpcode() == EnumOpcode.HALT) {
                globals.running = false;
                System.exit(1);
            }
        }
    }
}
