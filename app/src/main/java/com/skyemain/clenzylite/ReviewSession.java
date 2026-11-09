package com.skyemain.clenzylite;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Comparator;
import java.util.Set;

final class ReviewSession {
    record Item(String uri, String title, boolean video, long modified, long size) {}
    record Removed(Item item, boolean starred) {}

    private final Set<String> stars;
    private final ArrayDeque<Removed> undo = new ArrayDeque<>(2);
    private final ArrayList<Item> library = new ArrayList<>();
    private final ArrayList<Item> queue = new ArrayList<>();
    private final Map<String, Long> history = new HashMap<>();
    private long latestRank;
    private int index;
    private boolean starredQueue;

    ReviewSession(Set<String> stars) { this.stars = new HashSet<>(stars); }
    Set<String> stars() { return new HashSet<>(stars); }
    boolean isStarred(Item item) { return stars.contains(item.uri()); }
    boolean isStarredQueue() { return starredQueue; }
    int size() { return queue.size(); }
    int position() { return index; }
    Item current() { return index < queue.size() ? queue.get(index) : null; }

    void load(List<Item> items) { load(items, Map.of()); }

    void load(List<Item> items, Map<String, Long> ranks) {
        library.clear();
        library.addAll(items);
        history.clear();
        history.putAll(ranks);
        latestRank = ranks.values().stream().mapToLong(Long::longValue).max().orElse(0);
        switchQueue(starredQueue);
    }

    void switchQueue(boolean starred) {
        starredQueue = starred;
        queue.clear();
        for (Item item : library) if (isStarred(item) == starred) queue.add(item);
        // The library's recent-first order breaks ties between unreviewed items.
        queue.sort(Comparator.comparingLong(item -> history.getOrDefault(item.uri(), 0L)));
        index = 0;
    }

    void star(Item item) {
        if (!stars.remove(item.uri())) stars.add(item.uri());
    }

    void next() {
        Item item = current();
        if (item == null) return;
        history.put(item.uri(), ++latestRank);
        if (isStarred(item) != starredQueue) queue.remove(index);
        else index++;
    }

    void trashed(Item item) {
        if (!item.equals(current())) return;
        boolean wasStarred = stars.remove(item.uri());
        library.remove(item);
        queue.remove(index);
        if (undo.size() == 2) undo.removeLast();
        undo.push(new Removed(item, wasStarred));
    }

    Removed latestUndo() { return undo.peek(); }
    int undoCount() { return undo.size(); }

    void restored(Removed removed) {
        if (!removed.equals(undo.peek())) return;
        undo.pop();
        Item item = removed.item();
        if (removed.starred()) stars.add(item.uri());
        library.add(item);
        library.sort(java.util.Comparator.comparingLong(Item::modified).reversed());
        switchQueue(removed.starred());
        index = queue.indexOf(item);
    }
}
