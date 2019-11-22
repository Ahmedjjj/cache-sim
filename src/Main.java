import bus.Bus;
import bus.BusController;
import cache.Cache;
import cache.Protocol;
import cache.dragon.DragonCache;
import cache.mesi.MesiCache;
import common.Constants;
import cpu.Cpu;
import instruction.Instruction;
import instruction.InstructionParser;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;

public final class Main {


    public static void main(String[] args) {
        if (args.length < 5) {
            System.err.println("Too few arguments, Usage : Protocol Input cache_size " +
                    "associativity block_size");
            System.exit(1);
        }

        String protocolString = args[0];
        String traceFile = args[1];
        int cacheSize = Integer.parseInt(args[2]);
        int associativity = Integer.parseInt(args[3]);
        int blockSize = Integer.parseInt(args[4]);
        Protocol protocol = protocolString.toLowerCase().equals("mesi") ? Protocol.MESI : Protocol.Dragon;

        List<Cpu> processors = new ArrayList<Cpu>();
        List<Cache> caches = new ArrayList<Cache>();
        BusController controller = new BusController();
        Bus bus = new Bus();
        bus.attachTo(controller);
        controller.attachTo(bus);


        File dir = new File(traceFile);
        File[] files = dir.listFiles();


        for (int i = 0; i < 4; i++) {
            Cache cache = protocol == Protocol.MESI ?
                    new MesiCache(i, cacheSize, blockSize, associativity) :
                    new DragonCache(i, cacheSize, blockSize, associativity);
            Cpu p = new Cpu(cache);
            Queue<Instruction> instructions = InstructionParser.parseInstructions(files[i].getAbsolutePath());
            p.setInstructions(instructions);
            processors.add(p);
            caches.add(cache);
            cache.linkCpu(p);
            controller.attach(cache);

        }
        runUntilEnd(processors, caches, bus);
        printResults(processors, caches, bus, controller, protocol);
    }

    private static void runUntilEnd(List<Cpu> processors, List<Cache> caches, Bus bus) {

        while (!allFinished(processors)) {
            bus.runForOneCycle();
            caches.forEach(c -> c.runForOneCycle());
            Collections.shuffle(processors);
            processors.forEach(p -> p.runForOneCycle());

        }
    }

    private static boolean allFinished(List<Cpu> processors) {
        return processors.stream().allMatch(p -> p.finishedExecution());
    }

    private static void printResults(List<Cpu> processors, List<Cache> caches, Bus bus, BusController controller, Protocol protocol) {
        List<Long> executionCycle = new ArrayList<>(Constants.NUM_CPUS);
        processors.forEach(p -> executionCycle.add(p.getCycleCount()));
        long maxExecutionCycle = Collections.max(executionCycle);
        System.out.println("Results:");
        System.out.println("Overall Execution Cycle: " + maxExecutionCycle);
        processors.forEach(p -> System.out.println("Execution cycles for core " + processors.indexOf(p) + ": " + p.getCycleCount()));
        processors.forEach(p -> System.out.println("Compute cycles for core " + processors.indexOf(p) + ": " + p.getTotalComputingCycles()));
        processors.forEach(p -> System.out.println("Number of Load instructions for core " + processors.indexOf(p) + ": " + p.getNumLoad()));
        processors.forEach(p -> System.out.println("Number of Store instructions for core " + processors.indexOf(p) + ": " + p.getNumStore()));
        processors.forEach(p -> System.out.println("Number of Idle cycles for core " + processors.indexOf(p) + ": " + p.getTotalIdleCycles()));
        caches.forEach(c -> System.out.println("Cache miss rate for cache " + c.getId() + ": " + c.getMissRate()));
        System.out.println("Average cache miss rate "+ getAverageMissRate(caches));
        caches.forEach(c -> System.out.println("Number of private accesses for cache " + c.getId() + ": " + c.getPrivateAccess()));
        caches.forEach(c -> System.out.println("Number of shared accesses for cache " + c.getId() + ": " + c.getSharedAccess()));


        if (protocol == Protocol.MESI) {
            System.out.println("Number of invalidations on the bus: " + getTotalNumOfInvalidations(caches));
        } else {
            System.out.println("Number of updates on the bus: " + getTotalNumOfInvalidations(caches));
        }
        System.out.println("Data Sent on the bus :" + controller.getBusTraffic());

    }
    private static double getAverageMissRate (List<Cache> caches){
        return  caches.stream().map(c -> c.getMissRate()).reduce((a, b) -> a + b).get() / 4;
    }
    private static int getTotalNumOfInvalidations(List<Cache> caches) {
        return caches.stream().map(c -> c.getNbInvalidations()).reduce(0, (a, b) -> a + b);
    }

}
