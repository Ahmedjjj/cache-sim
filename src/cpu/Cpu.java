package cpu;

import cache.Cache;
import cache.instruction.CacheInstruction;
import common.Clocked;
import instruction.Instruction;
import instruction.InstructionType;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;

public final class Cpu implements Clocked {

    private final Cache cache;

    private Queue<Instruction> instructions;
    private CpuState state;
    private long cycleCount;
    private int instructionCount;
    private int executingCyclesLeft;

    private int totalComputingCycles;
    private int totalIdleCycles;
    private int numLoadStore;

    public Cpu(Cache cache) {

        this.cache = cache;
        this.cycleCount = 0;
        this.instructionCount = 0;
        this.state = CpuState.IDLE;
        instructions = new LinkedList<>();
        executingCyclesLeft = 0;

        totalComputingCycles = 0;
        totalIdleCycles = 0;
        numLoadStore = 0;

    }

    public void runForOneCycle() {
        switch (state) {
            case IDLE:
                Instruction instruction = instructions.poll();
                if (instruction != null) {
                    executeInstruction(instruction);
                    instructionCount++;
                }
                break;
            case BLOCKING:
                totalIdleCycles ++;
                break;
            case EXECUTING:
                executingCyclesLeft--;
                if (executingCyclesLeft == 0) {
                    setState(CpuState.IDLE);
                }
        }
        cycleCount++;
    }

    private void executeInstruction(Instruction instruction) {
        switch (instruction.getType()) {
            case READ: {

                numLoadStore ++;
                CacheInstruction cacheInstruction = new CacheInstruction(InstructionType.READ,
                        instruction.getSecondField());
                cache.ask(cacheInstruction);
                setState(CpuState.BLOCKING);
                break;
            }
            case WRITE: {
                numLoadStore ++;
                CacheInstruction cacheInstruction = new CacheInstruction(InstructionType.WRITE,

                        instruction.getSecondField());
                cache.ask(cacheInstruction);
                setState(CpuState.BLOCKING);
                break;
            }
            case OTHER: {
                this.executingCyclesLeft = instruction.getSecondField();
                setState(CpuState.EXECUTING);
                break;
            }
        }
    }

    public void setInstructions(Queue<Instruction> instructions) {
        this.instructions = new LinkedList<>(instructions);
    }


    public void wake() {
        assert (this.state == CpuState.BLOCKING);
        setState(CpuState.IDLE);
    }

    public long getCycleCount() {
        return cycleCount;
    }

    public int getTotalComputingCycles() {
        return totalComputingCycles;
    }

    public int getTotalIdleCycles() {
        return totalIdleCycles;
    }

    public int getNumLoadStore() {
        return numLoadStore;
    }

    public boolean finishedExecution(){
        return this.instructions.isEmpty() && this.state == CpuState.IDLE;
    }
    private void setState(CpuState state) {
        this.state = state;
    }


}

