package instruction;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Scanner;

public final class InstructionParser {

    private final static int HEX_RADIX = 16;

    public static Queue<Instruction> parseInstructions(String filePath) {
        Queue<Instruction> instructions = new LinkedList<>();
        int maxInstr = 800;
        try (Scanner scanner = new Scanner(new File(filePath))) {
            while (scanner.hasNextInt()) {
                InstructionType type = InstructionType.values()[scanner.nextInt()];
                int otherField = Integer.parseUnsignedInt(scanner.next().substring(2), HEX_RADIX);
                Instruction instruction = new Instruction(type, otherField);
                instructions.add(instruction);
                maxInstr--;
            }
        } catch (FileNotFoundException e) {
            System.out.println("File " + filePath + " not found");
        } catch (IllegalStateException e) {
            System.out.println("Queue maximum capacity reached");
        }

        return instructions;

    }

    private InstructionParser() {
    }
}

