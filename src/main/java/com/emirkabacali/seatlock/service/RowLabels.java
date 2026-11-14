package com.emirkabacali.seatlock.service;

/** Converts a zero-based row index into a spreadsheet-style label: 0 → A, 25 → Z, 26 → AA. */
final class RowLabels {

	private RowLabels() {
	}

	static String of(int index) {
		if (index < 0) {
			throw new IllegalArgumentException("Row index must not be negative: " + index);
		}
		StringBuilder label = new StringBuilder();
		int n = index + 1;
		while (n > 0) {
			int remainder = (n - 1) % 26;
			label.insert(0, (char) ('A' + remainder));
			n = (n - 1) / 26;
		}
		return label.toString();
	}

}
