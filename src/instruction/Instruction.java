package instruction;

public class Instruction {

    private final InstructionType type;
    private final int secondField;


    public Instruction(InstructionType type, int secondField) {
        this.type = type;
        this.secondField = secondField;
    }

    public InstructionType getType() {
        return type;
    }

    public int getSecondField() {
        return secondField;
    }

}
