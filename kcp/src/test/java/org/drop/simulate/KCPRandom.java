package org.drop.simulate;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 均匀分布的随机数
 */
public class KCPRandom {

    private int size;
    private final List<Integer> seeds;
    private final Random random;

    KCPRandom(int size) {
        this.size = size;
        this.seeds = new ArrayList<>(size);
        this.random = new Random();
        for (int i = 0; i < size; i++) {
            this.seeds.add(i);
        }
    }

    public int random() {
        if (seeds.isEmpty()) return 0;
        if (size == 0){
            for (int i = 0; i < seeds.size(); i++) {
                seeds.set(i, i);
            }
            size = seeds.size();
        }
        int x, index;
        index = random.nextInt(size);
        x = this.seeds.get(index);
        seeds.set(index, this.seeds.get(size - 1));
        size--;
        return x;
    }

}
