 /* To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package implementation;

import implementation.AllMyLatches.*;
import utilitytypes.EnumOpcode;
import baseclasses.InstructionBase;
import baseclasses.PipelineRegister;
import baseclasses.PipelineStageBase;
import voidtypes.VoidInstruction;
import voidtypes.VoidLatch;
import baseclasses.CpuCore;

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
    static class Fetch extends PipelineStageBase<VoidLatch,FetchToDecode> {
        public Fetch(CpuCore core, PipelineRegister input, PipelineRegister output) {
            super(core, input, output);
        }
                
        @Override
        public String getStatus() {
            // Generate a string that helps you debug.
            return null;
        }
        
        /* (non-Javadoc)
         * @see baseclasses.PipelineStageBase#compute(baseclasses.LatchBase, baseclasses.LatchBase)
         */
        @Override
        public void compute(VoidLatch input, FetchToDecode output) {
            
        	GlobalData globals = (GlobalData)core.getGlobalResources();
            int pc = globals.program_counter;
            // Fetch the instruction
            //System.out.println(globals.cycle);
            InstructionBase ins = globals.program.getInstructionAt(pc);
            if (ins.isNull()) return;
                       
            // Do something idempotent to compute the next program counter.
            
            // Don't forget branches, which MUST be resolved in the Decode
            // stage.  You will make use of global resources to communicate
            // between stages.
            
            // Your code goes here...         
            output.setInstruction(ins);
        }
        
        @Override
        public boolean stageWaitingOnResource() {
            // Hint:  You will need to implement this for when branches
            // are being resolved.
        	GlobalData globals = (GlobalData)core.getGlobalResources();
        	if(globals.bra_stalled)
        		return true;
        	else
        		return false;
        }
        /**
         * This function is to advance state to the next clock cycle and
         * can be applied to any data that must be updated but which is
         * not stored in a pipeline register.
         */
        @Override
        public void advanceClock() {
        	
            // Hint:  You will need to implement this help with waiting
            // for branch resolution and updating the program counter.
            // Don't forget to check for stall conditions, such as when
            // nextStageCanAcceptWork() returns false.
        	GlobalData globals = (GlobalData)core.getGlobalResources();
        	
        	if(!globals.decode_stalled){
        		globals.program_counter++;
        	}
        }
    }

    
    /*** Decode Stage ***/
    static class Decode extends PipelineStageBase<FetchToDecode,DecodeToExecute> {
        public Decode(CpuCore core, PipelineRegister input, PipelineRegister output) {
            super(core, input, output);
        }
        
        @Override
        public boolean stageWaitingOnResource() {
            // Hint:  You will need to implement this to deal with 
            // dependencies.
        	
        	GlobalData globals = (GlobalData)core.getGlobalResources();
        	if(globals.stall){
        		globals.decode_stalled=true;
        		return true;
        	}
        	else
            return false;
        }
        

        @Override
        public void compute(FetchToDecode input, DecodeToExecute output) {
            InstructionBase ins = input.getInstruction();
            GlobalData globals = (GlobalData)core.getGlobalResources();
            int[] regfile = globals.register_file;
            boolean[] regStatus= globals.register_invalid;
            //System.out.println(ins);
            // These null instruction checks are mostly just to speed up
            // the simulation.  The Void types were created so that null
            // checks can be almost completely avoided.
            if (ins.isNull()) return;
            
            // Do what the decode stage does:
            // - Look up source operands
            // - Decode instruction
            // - Resolve branches  
            
            if(globals.taken){
            	ins=VoidInstruction.getVoidInstruction();
            	globals.taken=false;
            }
            else{
            if(ins.getSrc1().isRegister()){
                	if(regStatus[ins.getSrc1().getRegisterNumber()]){
                	globals.stall=true;
                	return;
                	}
                	else
                		globals.stall=false;
                }  
            if(ins.getSrc2().isRegister()){	
                	if(regStatus[ins.getSrc2().getRegisterNumber()]){
                    	globals.stall=true;
                    	return;
                }else
            		globals.stall=false;
            }
            if(ins.getOper0().isRegister()){    	
            if(regStatus[ins.getOper0().getRegisterNumber()]){
            	globals.stall=true;
            	return;
            }
            else{
                regStatus[ins.getOper0().getRegisterNumber()]=true;
                globals.decode_stalled=false;
                globals.stall=false;
                }
            }           
            if(ins.getOpcode().toString()=="BRA"){
            	globals.bra_stalled=true;
            	int j=regfile[ins.getOper0().getRegisterNumber()];
            	if(ins.getComparison().name()=="LT"){
            		if(globals.lt){
            			j=ins.getLabelTarget().getAddress();
            			globals.program_counter=j-1;
            			globals.taken=true;
            		}
            	
            	else{
            			globals.program_counter--;
            			globals.program_counter++;
            	}
            	}
            	if(ins.getComparison().name()=="GE"){
            		if(globals.ge){
            			j=ins.getLabelTarget().getAddress();
            			globals.program_counter=j-1;
            			globals.taken=true;
            		}
            	
            		else{
            			globals.program_counter--;
            			globals.program_counter++;
            		}
            	}
            	
            	if(ins.getComparison().name()=="EQ"){
            		if(globals.eq){
            			j=ins.getLabelTarget().getAddress();
            			globals.program_counter=j-1;
            			globals.taken=true;
            		}
            	
            		else{
            			globals.program_counter--;
            			globals.program_counter++;    			
            		}
            	}
            	if(ins.getComparison().name()=="GT"){
            		if(globals.gt){
            			j=ins.getLabelTarget().getAddress();
            			globals.program_counter=j-1;
            			globals.taken=true;
            		}
            	
            		else{
            			globals.program_counter--;
            			globals.program_counter++;    			
            		}
            	}
            	if(ins.getComparison().name()=="NE"){
            		if(globals.ne){
            			j=ins.getLabelTarget().getAddress();
            			globals.program_counter=j-1;
            			globals.taken=true;
            		}
            	
            		else{
            			globals.program_counter--;
            			globals.program_counter++;    			
            		}
            	}
            	if(ins.getComparison().name()=="LE"){
            		if(globals.le){
            			j=ins.getLabelTarget().getAddress();
            			globals.program_counter=j-1;
            			globals.taken=true;
            		}
            	
            		else{
            			globals.program_counter--;
            			globals.program_counter++;    			
            		}
            	}
            	globals.bra_stalled=false;
            	globals.eq=false;
            	globals.gt=false;
            	globals.ne=false;
            	globals.ge=false;
            	globals.lt=false;
            	globals.le=false;
            	regStatus[ins.getOper0().getRegisterNumber()]=false;
            	globals.decode_stalled=false;
            }             
            else if(ins.getOpcode().toString()=="JMP"){
            	globals.program_counter=ins.getLabelTarget().getAddress()-1;
            	globals.taken=true;
            }
           output.setInstruction(ins);
            // Set other data that's passed to the next stage.
            }
        }
    }
    

    /*** Execute Stage ***/
    static class Execute extends PipelineStageBase<DecodeToExecute,ExecuteToMemory> {
        public Execute(CpuCore core, PipelineRegister input, PipelineRegister output) {
            super(core, input, output);
        }

        @Override
        public void compute(DecodeToExecute input, ExecuteToMemory output) {
            InstructionBase ins = input.getInstruction();
            if (ins.isNull()) return;
            GlobalData globals = (GlobalData)core.getGlobalResources();
            int[] regfile = globals.register_file;
            boolean[] regStatus=globals.register_invalid;
            int source1=0, source2=0, oper0=0;
            if(ins.getOpcode().toString()=="MOVC"){
            	source1 = ins.getSrc1().getValue();
                source2 = ins.getSrc2().getValue();
                oper0 =   ins.getOper0().getRegisterNumber();
            }
            else if(ins.getOpcode().toString()=="ADD"){
            	source1 = regfile[ins.getSrc1().getRegisterNumber()];
            	if(ins.getSrc2().getRegisterNumber()!=0&&ins.getSrc2().getRegisterNumber()!=-1){
                source2 = regfile[ins.getSrc2().getRegisterNumber()];
            	}
            	else{
            		source2 = ins.getSrc2().getValue();
            	}
                oper0 = ins.getOper0().getRegisterNumber();
            }
            else if(ins.getOpcode().toString()=="CMP"){
            	source1 = regfile[ins.getSrc1().getRegisterNumber()];
            	if(ins.getSrc2().isRegister()){
                source2 = regfile[ins.getSrc2().getRegisterNumber()];
            	}else{
            		source2=ins.getSrc2().getValue();
            	}
                oper0 = ins.getOper0().getRegisterNumber();
            }
            else if(ins.getOpcode().toString()=="OUT"){
            	int prime=regfile[ins.getOper0().getRegisterNumber()];
            	System.out.printf("@@output: %d\n",prime);
            	regStatus[ins.getOper0().getRegisterNumber()]=false;
            }
            else {
            source1 = ins.getSrc1().getValue();
            source2 = ins.getSrc2().getValue();
            oper0 =   ins.getOper0().getValue();
            }
            int result = MyALU.execute(ins.getOpcode(), source1, source2, oper0);
            
            // Fill output with what passes to Memory stage...
            ins.setoperationResult(result);
            output.setInstruction(ins);
            // Set other data that's passed to the next stage.
        }
    }
    

    /*** Memory Stage ***/
    static class Memory extends PipelineStageBase<ExecuteToMemory,MemoryToWriteback> {
        public Memory(CpuCore core, PipelineRegister input, PipelineRegister output) {
            super(core, input, output);
        }

        @Override
        public void compute(ExecuteToMemory input, MemoryToWriteback output) {
            InstructionBase ins = input.getInstruction();
            if (ins.isNull()) return;
            GlobalData globals = (GlobalData)core.getGlobalResources();
            int regfile[]=globals.register_file;
            boolean[] regStatus=globals.register_invalid;
            int memory[]=globals.memory;
            // Access memory...
            if(ins.getOpcode().toString()=="STORE"){
            	int data=regfile[ins.getSrc1().getRegisterNumber()]+ins.getSrc2().getValue();
            	memory[data]=regfile[ins.getOper0().getRegisterNumber()];
            	regStatus[ins.getOper0().getRegisterNumber()]=false;
            }
            else if(ins.getOpcode().toString()=="LOAD"){
            	int data=regfile[ins.getSrc1().getRegisterNumber()];
               	regfile[ins.getOper0().getRegisterNumber()]=memory[data];
            	regStatus[ins.getOper0().getRegisterNumber()]=false;
            	
             }

            output.setInstruction(ins);
            // Set other data that's passed to the next stage.
        }
    }
    

    /*** Writeback Stage ***/
    static class Writeback extends PipelineStageBase<MemoryToWriteback,VoidLatch> {
        public Writeback(CpuCore core, PipelineRegister input, PipelineRegister output) {
            super(core, input, output);
        }

        @Override
        public void compute(MemoryToWriteback input, VoidLatch output) {
            InstructionBase ins = input.getInstruction();
            if (ins.isNull()) return;
            
            // Write back result to register file
            GlobalData globals = (GlobalData)core.getGlobalResources();
            int regfile[]=globals.register_file;
            boolean[] regStatus=globals.register_invalid;
            if(ins.getOpcode().toString()=="MOVC"){
            regfile[ins.getOper0().getRegisterNumber()]=ins.getoperationResult();
            regStatus[ins.getOper0().getRegisterNumber()]=false;
            }
            if(ins.getOpcode().toString()=="ADD"){
                regfile[ins.getOper0().getRegisterNumber()]=ins.getoperationResult();
                regStatus[ins.getOper0().getRegisterNumber()]=false;
            }
            if(ins.getOpcode().toString()=="CMP"){
                    if(ins.getoperationResult()==1){
                    	regfile[ins.getOper0().getRegisterNumber()]=1;
                    	regStatus[ins.getOper0().getRegisterNumber()]=false;
                    	globals.gt = true;
                    	globals.ne = true;
                    	globals.ge = true;
                    	
                    }
                    else if(ins.getoperationResult()==0){
                    	regfile[ins.getOper0().getRegisterNumber()]=0;
                    	regStatus[ins.getOper0().getRegisterNumber()]=false;
                    	globals.eq=true;
                    	globals.le=true;
                    	globals.ge = true;
                    }
                    else{
                    	regfile[ins.getOper0().getRegisterNumber()]=2;
                    	regStatus[ins.getOper0().getRegisterNumber()]=false;
                    	globals.lt = true;
                    	globals.ne = true;
                    	globals.le = true;
                    }
            }
            
            if (input.getInstruction().getOpcode() == EnumOpcode.HALT) {
            	System.exit(1);
            }
        }
    }
}
