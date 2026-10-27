package com.still.launcher;

import java.util.Set;
import java.util.TreeSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Arrays;

/** Selection rules shared by the UI and the dependency-free regression checks. */
final class Essentials {
    static final int LIMIT = 3;

    static boolean toggle(Set<String> selected, String key) {
        if (selected.remove(key)) return true;
        if (selected.size() >= LIMIT) return false;
        selected.add(key);
        return true;
    }

    static Set<String> migrate(Set<String> selected) {
        TreeSet<String> limited = new TreeSet<>(selected);
        while (limited.size() > LIMIT) limited.pollLast();
        return limited;
    }

    static List<String> order(Set<String> selected, List<String> saved, List<String> alphabetical) {
        List<String> ordered = new ArrayList<>();
        for (List<String> source : Arrays.asList(saved, alphabetical, new ArrayList<>(new TreeSet<>(selected)))) {
            for (String key : source) {
                if (selected.contains(key) && !ordered.contains(key)) ordered.add(key);
            }
        }
        return ordered;
    }

    static boolean swap(List<String> order, String first, String second) {
        int a = order.indexOf(first), b = order.indexOf(second);
        if (a < 0 || b < 0 || a == b) return false;
        Collections.swap(order, a, b);
        return true;
    }

    static boolean removePackage(Set<String> selected, List<String> order, String packageName) {
        boolean changed = selected.removeIf(key -> key.startsWith(packageName + "/"));
        if (changed) order.retainAll(selected);
        return changed;
    }
}
