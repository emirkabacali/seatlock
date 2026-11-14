package com.emirkabacali.seatlock.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * Lifecycle of an event. Allowed transitions:
 * DRAFT → ON_SALE | CANCELLED, ON_SALE → CANCELLED | FINISHED.
 */
public enum EventStatus {

	DRAFT,
	ON_SALE,
	CANCELLED,
	FINISHED;

	public boolean canTransitionTo(EventStatus target) {
		return allowedTargets().contains(target);
	}

	private Set<EventStatus> allowedTargets() {
		return switch (this) {
			case DRAFT -> EnumSet.of(ON_SALE, CANCELLED);
			case ON_SALE -> EnumSet.of(CANCELLED, FINISHED);
			case CANCELLED, FINISHED -> EnumSet.noneOf(EventStatus.class);
		};
	}

}
