package com.skyemain.clenzylite;

import static org.junit.Assert.*;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public class ReviewSessionTest {
    private final ReviewSession.Item a = new ReviewSession.Item("a", "Photo A", false, 4, 20);
    private final ReviewSession.Item b = new ReviewSession.Item("b", "Video B", true, 3, 40);
    private final ReviewSession.Item c = new ReviewSession.Item("c", "Photo C", false, 2, 10);

    @Test public void consecutiveTrashActionsUndoNewestFirstAndCapAtTwo() {
        var session = new ReviewSession(Set.of());
        session.load(List.of(a, b, c));
        session.trashed(a);
        session.trashed(b);
        assertEquals(2, session.undoCount());
        session.restored(session.latestUndo());
        assertEquals(b, session.current());
        session.restored(session.latestUndo());
        assertEquals(a, session.current());
        assertNull(session.latestUndo());

        session.trashed(a);
        session.trashed(b);
        session.trashed(c);
        session.restored(session.latestUndo());
        assertEquals(c, session.current());
        session.restored(session.latestUndo());
        assertEquals(b, session.current());
        assertEquals(0, session.undoCount());
        assertEquals(2, session.size());
    }

    @Test public void reopeningStartsWithUnreviewedAndOrdersReviewedOldestFirst() {
        var session = new ReviewSession(Set.of());
        session.load(List.of(a, b, c), java.util.Map.of(a.uri(), 2L, b.uri(), 1L));
        assertEquals(c, session.current());
        session.next();
        assertEquals(b, session.current());
        session.next();
        assertEquals(a, session.current());
        session.switchQueue(false);
        assertEquals(a, session.current());
    }

    @Test public void resumePinsCheckpointAheadOfNewPhotosWithoutLosingThem() {
        var session = new ReviewSession(Set.of());
        session.load(List.of(a, b, c), java.util.Map.of(c.uri(), 1L));
        session.resume(b.uri(), false);
        assertEquals(b, session.current());
        session.next();
        assertEquals(a, session.current());
        session.next();
        assertEquals(c, session.current());
    }

    @Test public void missingOrStarredCheckpointFallsBackToAccessibleQueue() {
        var session = new ReviewSession(Set.of(b.uri()));
        session.load(List.of(a, b, c));
        session.resume(b.uri(), false);
        assertEquals(a, session.current());
        session.resume("deleted", false);
        assertEquals(a, session.current());
        session.resume(b.uri(), true);
        assertEquals(b, session.current());
    }

    @Test public void sortOrdersUnseenItemsAndKeepsReviewedAtBack() {
        var session = new ReviewSession(Set.of());
        session.load(List.of(a, b, c));
        session.setSort("oldest");
        assertEquals(c, session.current());
        session.setSort("newest");
        assertEquals(a, session.current());
        session.setSort("size");
        assertEquals(b, session.current());
        session.next();
        session.setSort("size");
        assertEquals(a, session.current());
        session.next();
        assertEquals(c, session.current());
        session.resume(b.uri(), false);
        assertEquals(b, session.current());
        assertEquals("size", session.sort());
    }

    @Test public void starredItemsStayOutOfRegularQueueOnReopen() {
        var session = new ReviewSession(Set.of());
        session.load(List.of(a, b));
        session.star(a);
        assertEquals(a, session.current());
        session.next();
        assertEquals(b, session.current());

        var reopened = new ReviewSession(session.stars());
        reopened.load(List.of(a, b));
        assertEquals(1, reopened.size());
        assertEquals(b, reopened.current());
        reopened.switchQueue(true);
        assertEquals(a, reopened.current());
        reopened.trashed(a);
        reopened.restored(reopened.latestUndo());
        assertTrue(reopened.isStarred(a));
        assertEquals(a, reopened.current());
    }
}
