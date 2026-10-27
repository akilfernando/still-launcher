package com.still.launcher;

import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;

public final class EssentialsTest {
    public static void main(String[] args) {
        Set<String> selected = new HashSet<>();
        check(Essentials.toggle(selected, "a"), "first essential");
        check(Essentials.toggle(selected, "b"), "second essential");
        check(Essentials.toggle(selected, "c"), "third essential");
        check(!Essentials.toggle(selected, "d"), "fourth rejected");
        check(selected.equals(Set.of("a", "b", "c")), "rejection preserves selection");
        check(Essentials.toggle(selected, "b"), "removal allowed at limit");
        check(Essentials.toggle(selected, "d"), "replacement allowed");
        Set<String> old = Set.of("d", "b", "c", "a", "e");
        check(Essentials.migrate(old).equals(Set.of("a", "b", "c")), "deterministic upgrade cap");
        check(old.size() == 5, "migration does not mutate backup");
        check(Essentials.migrate(Set.of("z")).equals(Set.of("z")), "existing small selection preserved");
        List<String> order = Essentials.order(Set.of("a", "b", "c"), List.of(), List.of("b", "a", "c"));
        check(order.equals(List.of("b", "a", "c")), "upgrade keeps displayed alphabetical order");
        check(Essentials.swap(order, "b", "a"), "move down");
        check(order.equals(List.of("a", "b", "c")), "neighbor swap");
        check(Essentials.swap(order, "c", "b"), "move up");
        check(order.equals(List.of("a", "c", "b")), "upward order");
        check(Essentials.order(Set.of("a", "b", "c"), new ArrayList<>(order), List.of("b", "c", "a"))
            .equals(order), "saved order survives reload and label changes");
        check(!Essentials.swap(order, "a", "missing"), "unavailable target ignored");
        check(!Essentials.swap(order, "a", "a"), "self move ignored");
        order = Essentials.order(Set.of("a", "b"), order, List.of("a", "b"));
        check(order.equals(List.of("a", "b")), "removed essential is dropped from order");
        order = Essentials.order(Set.of("a", "b", "c"), order, List.of("c", "b", "a"));
        check(order.equals(List.of("a", "b", "c")), "new essential appended");
        check(Essentials.order(Set.of("a", "b"), List.of("b", "b", "stale"), List.of("a"))
            .equals(List.of("b", "a")), "duplicate and stale order cleaned");
        check(Essentials.order(Set.of("a"), List.of(), List.of()).equals(List.of("a")), "unavailable selection retained");
        Set<String> installed = new HashSet<>(Set.of("a/.One", "a/.Two", "ab/.Other"));
        List<String> installedOrder = new ArrayList<>(List.of("a/.Two", "ab/.Other", "a/.One"));
        check(!Essentials.removePackage(installed, installedOrder, "missing"), "unrelated uninstall is ignored");
        check(Essentials.removePackage(installed, installedOrder, "a"), "all removed package components cleared");
        check(installed.equals(Set.of("ab/.Other")), "similarly named package preserved");
        check(installedOrder.equals(List.of("ab/.Other")), "uninstall also cleans saved order");
        check(Essentials.toggle(installed, "new/.App"), "uninstall frees essential slot");
        System.out.println("PASS: 27 essentials selection and ordering checks");
    }
    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
