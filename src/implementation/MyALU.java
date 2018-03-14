/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package implementation;

import utilitytypes.EnumOpcode;

/**
 * The code that implements the ALU has been separates out into a static
 * method in its own class.  However, this is just a design choice, and you
 * are not required to do this.
 * 
 * @author 
 */
public class MyALU {
    static int execute(EnumOpcode opcode, int input1, int input2, int oper0) {
        int result = 0;
        
        // Implement code here that performs appropriate computations for
        // any instruction that requires an ALU operation.  See
        // EnumOpcode.
        
        switch(opcode){
        case ADD:
        	result=input1+input2;
        	break;
        case SUB:
        	result=input1-input2;
        	break;
        case AND:
        	result=input1&input2;
        	break;
        case OR:
        	result=input1|input2;
        	break;
        case MOVC:
        	result=input1;
        	break;
        case CMP:
        	if(input1==input2){
        		result=0;
        	}
        	else if(input1>input2){
        		result=1;
        	}
        	else{
        		result=2;
        	}
        	break;
        default:
        	result=0; 
        }
        return result;
    }    
}
