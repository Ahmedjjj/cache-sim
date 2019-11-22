package cache.lru;

import java.util.LinkedList;
import java.util.List;

public final class LruQueue {
    private final int numBlocks;

    private final List<Node> blocks;
    private final Node[] blockToNodeMap;

    public LruQueue(int numBlocks) {
        this.numBlocks = numBlocks;

        blockToNodeMap = new Node[numBlocks];
        blocks = new LinkedList<>();

        for (int i = 0; i < numBlocks; i++) {
            Node node = new Node(i);
            blockToNodeMap[i] = node;
            blocks.add(node);
        }
    }

    public void update(int block) {
        Node updated = blockToNodeMap[block];
        blocks.remove(updated);
        blocks.add(updated);
    }

    public int blockToEvacuate() {
        return blocks.get(0).block;
    }

    public void evacuate() {
        update(blockToEvacuate());
    }

    private static class Node {
        private final int block;
        private Node(int block) {
            this.block = block;
        }

    }
}
