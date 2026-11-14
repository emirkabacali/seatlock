package com.emirkabacali.seatlock.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RowLabelsTest {

	@ParameterizedTest
	@CsvSource({ "0, A", "1, B", "25, Z", "26, AA", "27, AB", "51, AZ", "52, BA", "701, ZZ", "702, AAA" })
	void convertsIndexToSpreadsheetStyleLabel(int index, String expected) {
		assertThat(RowLabels.of(index)).isEqualTo(expected);
	}

	@Test
	void rejectsNegativeIndex() {
		assertThatThrownBy(() -> RowLabels.of(-1)).isInstanceOf(IllegalArgumentException.class);
	}

}
