package com.ewos.person.application;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

/**
 * Generates monotonic, never-reused Group Person IDs of the form {@code P000000001}. Backed by the
 * {@code person_id_sequence} Postgres sequence — the DB is the source of truth so concurrent
 * inserts across nodes never collide, and rolled-back transactions consume the number rather than
 * recycle it (recycled IDs would violate the "never reuse" constraint).
 */
@Component
public class PersonIdGenerator {

    static final String PREFIX = "P";
    static final int PAD_WIDTH = 9;

    @PersistenceContext private EntityManager em;

    public String nextId() {
        Number next =
                (Number)
                        em.createNativeQuery("SELECT nextval('person_id_sequence')")
                                .getSingleResult();
        return format(next.longValue());
    }

    static String format(long n) {
        return PREFIX + String.format("%0" + PAD_WIDTH + "d", n);
    }
}
