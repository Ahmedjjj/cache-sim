package cpu;

import cache.Cache;
import cache.instruction.CacheInstruction;
import cache.instruction.CacheInstructionType;
import common.Clocked;
import instruction.Instruction;
import instruction.InstructionType;

import java.util.LinkedList;
import java.util.Queue;

public final class Cpu implements Clocked {

    private Queue<Instruction> instructions;
    private CpuState state;
    private long cycleCount;
    private int cacheInstructionCount;
    private int executingCyclesLeft;
    private int totalComputingCycles;
    private int totalIdleCycles;
    private int numLoad;
    private int numStore;
    private final Cache cache;

    public Cpu(Cache cache) {

        this.cache = cache;
        this.cycleCount = 0;
        this.cacheInstructionCount = 0;
        this.state = CpuState.IDLE;
        this.instructions = new LinkedList<>();
        this.executingCyclesLeft = 0;

        this.totalComputingCycles = 0;
        this.totalIdleCycles = 0;
        this.numLoad = 0;
        this.numStore = 0;

    }

    public void runForOneCycle() {

        switch (state) {
            case IDLE:
                Instruction instruction = instructions.poll();
                if (instruction != null) {
                    executeInstruction(instruction);
                    if (instruction.getType() == InstructionType.READ || instruction.getType() == InstructionType.WRITE) {
                        cacheInstructionCount++;
                    }
                }
                break;
            case BLOCKING:
                totalIdleCycles++;
                break;
            case EXECUTING:
                executingCyclesLeft--;
                if (executingCyclesLeft == 0) {
                    setState(CpuState.IDLE);
                }
        }
        if (!finishedExecution())
            cycleCount++;
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

    public int getCacheInstructionCount() {
        return cacheInstructionCount;
    }

    public int getTotalComputingCycles() {
        return totalComputingCycles;
    }

    public int getTotalIdleCycles() {
        return totalIdleCycles;
    }

    public int getNumLoad() {
        return numLoad;
    }

    public int getNumStore() {
        return numStore;
    }

    public boolean finishedExecution() {
        return this.instructions.isEmpty() && this.state == CpuState.IDLE;
    }

    private void executeInstruction(Instruction instruction) {
        switch (instruction.getType()) {
            case READ: {
                numLoad++;
                CacheInstruction cacheInstruction = new CacheInstruction(CacheInstructionType.READ,
                        instruction.getSecondField());
                cache.ask(cacheInstruction);
                setState(CpuState.BLOCKING);
                break;
            }
            case WRITE: {
                numStore++;
                CacheInstruction cacheInstruction = new CacheInstruction(CacheInstructionType.WRITE,
                        instruction.getSecondField());
                cache.ask(cacheInstruction);
                setState(CpuState.BLOCKING);
                break;
            }
            case OTHER: {
                this.executingCyclesLeft = instruction.getSecondField();
                totalComputingCycles += executingCyclesLeft;
                setState(CpuState.EXECUTING);
                break;
            }
        }
    }

    private void setState(CpuState state) {
        this.state = state;
    }


}

