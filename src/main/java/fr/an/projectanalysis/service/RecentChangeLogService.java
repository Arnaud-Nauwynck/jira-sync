package fr.an.projectanalysis.service;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Keeps an in-memory rolling queue of the most recent {@link ChangeLogEvent}s (Jira issue, GitHub
 * pull-request and mailing-list changes), for display as a "recent activity" feed. Oldest events
 * are dropped once {@link #maxSize} is exceeded; nothing here is persisted to disk.
 * <p>
 * Each event is assigned a monotonically increasing {@code seq} as it is added, so REST clients
 * can poll {@link #getEventsSince} with the highest {@code seq} they've already seen instead of
 * re-fetching the whole feed each time (see {@code fr.an.projectanalysis.rest.ChangeLogRestController}).
 */
@Component
public class RecentChangeLogService {

    public static final int DEFAULT_MAX_SIZE = 500;

    private final int maxSize = DEFAULT_MAX_SIZE;

    private final Deque<ChangeLogEvent> events = new ArrayDeque<>();

    private final AtomicLong seqGenerator = new AtomicLong();

    public synchronized void addEvent(ChangeLogEvent event) {
        event.assignSeq(seqGenerator.incrementAndGet());
        events.addLast(event);
        while (events.size() > maxSize) {
            events.removeFirst();
        }
    }

    /** Most recent events first. */
    public synchronized List<ChangeLogEvent> getRecentEvents() {
        List<ChangeLogEvent> result = new ArrayList<>(events);
        Collections.reverse(result);
        return result;
    }

    /** Events with {@code seq > sinceSeq}, oldest first; pass the highest {@code seq} seen so far
     * (0 on the first call) to poll for what's new since the last call. */
    public synchronized List<ChangeLogEvent> getEventsSince(long sinceSeq) {
        List<ChangeLogEvent> result = new ArrayList<>();
        for (ChangeLogEvent event : events) {
            if (event.getSeq() > sinceSeq) {
                result.add(event);
            }
        }
        return result;
    }

    public synchronized long getLastSeq() {
        return seqGenerator.get();
    }

    public synchronized void clear() {
        events.clear();
    }

}
